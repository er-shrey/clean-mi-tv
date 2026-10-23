package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The control center: a compact liquid-glass sheet on the right with Wi-Fi, aeroplane (Wi-Fi off; a TV app cannot
 * switch real aeroplane mode), mute and volume, then shortcuts. Opened by the Settings pill
 * (settings_pill_panel) or the remote's Settings button (settings_key_panel). Translucent and in a task of its own,
 * so the app behind keeps playing and Back returns to it; pressing Settings again closes it.
 *
 * Brightness and contrast: the MiTV4I's backlight ignores Settings.System brightness, so with config.xml mitv_picture
 * they go to Xiaomi's own picture service (MiTvPicture), and stay hidden wherever that service does not answer.
 */
public final class QuickSettingsActivity extends Activity {
    /** Opened by Home's Settings pill, in Home's task: Back slides the panel away and Home is simply there again. */
    static final String EXTRA_OVER_HOME = "com.gathin.tvhome.extra.OVER_HOME";
    private static final String AIRPLANE_KEY = "cc_airplane";
    private WifiManager wifi;
    private MiTvPicture picture;
    private ConnectivityManager connectivity;
    private final Runnable recheck = this::refresh;
    /** Follows Wi-Fi while the panel is open: turning it back on takes a few seconds to join (MiTV4I: about 3). */
    private final ConnectivityManager.NetworkCallback wifiWatch = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            runOnUiThread(QuickSettingsActivity.this::refresh);
        }

        @Override
        public void onLost(Network network) {
            runOnUiThread(QuickSettingsActivity.this::refresh);
        }
    };
    private boolean closing;
    private AudioManager audio;
    private View wifiTile;
    private View airplaneTile;
    private View muteTile;
    private View marqueeTile;
    private GlassSlider volume;
    private SharedPreferences choices;
    private boolean marquee;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        findViewById(R.id.scrim).setOnClickListener(v -> close());
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        marquee = config.marquee;
        if (config.mitvPicture) connectPicture();
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivity.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiWatch);

        wifiTile = findViewById(R.id.ccWifi);
        wifiTile.setOnClickListener(v -> {
            choices.edit().putBoolean(AIRPLANE_KEY, false).apply();
            setWifi(!wifiOn());
        });
        wifiTile.setOnLongClickListener(v -> {
            open(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        airplaneTile = findViewById(R.id.ccAirplane);
        airplaneTile.setOnClickListener(v -> {
            // Aeroplane is "Wi-Fi off" on a TV, but its own switch: it lights only when it was the one that turned
            // Wi-Fi off, and turning it off brings Wi-Fi back.
            boolean airplane = !airplaneOn();
            choices.edit().putBoolean(AIRPLANE_KEY, airplane).apply();
            setWifi(!airplane);
        });
        muteTile = findViewById(R.id.ccMute);
        muteTile.setOnClickListener(v -> {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
            refresh();
        });

        volume = findViewById(R.id.ccVolume);
        volume.setIcon(R.drawable.ic_volume);
        int maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume.setRange(maxVolume, maxVolume);
        volume.setListener((s, value) -> {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
            refresh();
        });

        shortcut(R.id.ccClock, new Intent(this, ClockActivity.class));
        marqueeTile = findViewById(R.id.ccMarquee);
        marqueeTile.setOnClickListener(v -> {
            // As Launcher settings' toggle does: Home rebuilds with it when it next resumes.
            marquee = !marquee;
            choices.edit().putBoolean(Config.MARQUEE_KEY, marquee)
                    .putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
            refresh();
        });
        shortcut(R.id.ccTvSettings, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        shortcut(R.id.ccApps, SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        shortcut(R.id.ccLauncher, new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));

        ((TextView) findViewById(R.id.ccStats)).setText(stats());
        refresh();
        volume.requestFocus();
        View panel = findViewById(R.id.panel);
        panel.setTranslationX(48 * getResources().getDisplayMetrics().density);
        panel.setAlpha(0f);
        panel.animate().translationX(0f).alpha(1f).setDuration(180).start();
    }

    /** Brightness (the panel's backlight) and contrast, shown once Xiaomi's picture service has answered. */
    private void connectPicture() {
        picture = new MiTvPicture(this);
        boolean bound = picture.connect(p -> {
            GlassSlider backlight = findViewById(R.id.ccBrightness);
            backlight.setIcon(R.drawable.ic_brightness);
            backlight.setRange(100, 20);
            backlight.setValue(p.backlight());
            backlight.setListener((s, value) -> p.setBacklight(value));
            backlight.setVisibility(View.VISIBLE);
            int contrastNow = p.contrast();
            if (contrastNow >= 0) {
                GlassSlider contrast = findViewById(R.id.ccContrast);
                contrast.setIcon(R.drawable.ic_contrast);
                contrast.setRange(100, 20);
                contrast.setValue(contrastNow);
                contrast.setListener((s, value) -> p.setContrast(value));
                contrast.setVisibility(View.VISIBLE);
            }
            findViewById(R.id.ccWifi).setNextFocusDownId(R.id.ccBrightness);
        });
        if (!bound) picture = null;
    }

    @Override
    protected void onDestroy() {
        wifiTile.removeCallbacks(recheck);
        try {
            connectivity.unregisterNetworkCallback(wifiWatch);
        } catch (IllegalArgumentException ignored) {
            // never registered
        }
        if (picture != null) picture.disconnect();
        super.onDestroy();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        close();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    /** Slides the panel back out to the right, then goes, with no window transition. */
    private void close() {
        if (closing) return;
        closing = true;
        View panel = findViewById(R.id.panel);
        panel.animate().translationX(48 * getResources().getDisplayMetrics().density).alpha(0f).setDuration(140)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    /** The remote's own volume keys still work here; the slider follows them. */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            refresh();
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Aeroplane turned Wi-Fi off and Wi-Fi is still off (Wi-Fi switched on elsewhere ends it). */
    private boolean airplaneOn() {
        return choices.getBoolean(AIRPLANE_KEY, false) && !wifiOn();
    }

    /** On, or on its way there: the tile must not read Off while the radio starts. */
    private boolean wifiOn() {
        if (wifi == null) return false;
        int state = wifi.getWifiState();
        return state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING;
    }

    @SuppressWarnings("deprecation")   // setWifiEnabled still works for apps targeting SDK 28 devices like this TV
    private void setWifi(boolean on) {
        try {
            if (wifi == null || !wifi.setWifiEnabled(on)) throw new SecurityException();
        } catch (SecurityException e) {
            open(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
            return;
        }
        refresh();
    }

    private void refresh() {
        boolean on = wifiOn();
        wifiTile.setActivated(on);
        airplaneTile.setActivated(airplaneOn());
        ((TextView) findViewById(R.id.ccWifiStatus)).setText(on ? networkName() : getString(R.string.off));
        // While Wi-Fi is switching (on, not joined yet), check again shortly: a press now would switch it off again.
        wifiTile.removeCallbacks(recheck);
        int state = wifi == null ? WifiManager.WIFI_STATE_UNKNOWN : wifi.getWifiState();
        boolean switching = state == WifiManager.WIFI_STATE_ENABLING || state == WifiManager.WIFI_STATE_DISABLING;
        if (switching || (on && !joined())) wifiTile.postDelayed(recheck, 1000);
        ((TextView) findViewById(R.id.ccAirplaneStatus)).setText(airplaneOn() ? R.string.on : R.string.off);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        muteTile.setActivated(muted);
        volume.setValue(muted ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        marqueeTile.setActivated(marquee);
    }

    private boolean joined() {
        WifiInfo info = wifi.getConnectionInfo();
        return info != null && info.getNetworkId() != -1 && info.getIpAddress() != 0;
    }

    /**
     * The network name, which Android 9 shares only with location access (build-and-install grants it) while
     * Location is on; "device only" is enough on a TV. "Connecting…" until the TV has joined.
     */
    private String networkName() {
        if (!joined()) return getString(R.string.cc_connecting);
        WifiInfo info = wifi.getConnectionInfo();
        String ssid = info != null ? info.getSSID() : null;
        if (ssid != null) {
            ssid = ssid.replace("\"", "").trim();
            if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) return ssid;
        }
        return getString(R.string.cc_connected);
    }

    /** "3.7 GB free · 412 MB memory free", read once when the panel opens. */
    private String stats() {
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        return getString(R.string.cc_stats, Formatter.formatShortFileSize(this, fs.getAvailableBytes()),
                Formatter.formatShortFileSize(this, memory.availMem));
    }

    private void shortcut(int id, Intent target) {
        findViewById(id).setOnClickListener(v -> {
            open(target);
            finish();
        });
    }

    private void open(Intent target) {
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}

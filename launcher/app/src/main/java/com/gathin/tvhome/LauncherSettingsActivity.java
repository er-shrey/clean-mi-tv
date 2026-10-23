package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.text.InputType;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    /** Dock opacity stops, left to right on its slider (Config snaps other values to these). */
    private static final int[] DOCK_OPACITIES = {30, 50, 70, 100};
    private GlassSlider columnsSlider;
    private GlassSlider rowsSlider;
    private GlassSlider dockSlider;
    private String greetingName = "";
    private View marqueeToggle;
    private View marqueeChoice;
    private boolean marquee;
    private String marqueeDefault = "";
    private View animationsChoice;
    private View advancedChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        // Sliders instead of rows of numbers: Left and Right step through the choices, each saved as it lands.
        columnsSlider = findViewById(R.id.columnsSlider);
        columnsSlider.setTextSize(15f);
        columnsSlider.setTitle(getString(R.string.columns_label));
        columnsSlider.setRange(3, 3);
        columnsSlider.setFormat(v -> String.valueOf(v + 4));
        columnsSlider.setListener((sl, v) -> {
            columns = v + 4;
            choices.edit().putInt("columns", columns).apply();
            changed();
            refresh();
        });
        rowsSlider = findViewById(R.id.rowsSlider);
        rowsSlider.setTextSize(15f);
        rowsSlider.setTitle(getString(R.string.rows_below_dock_label));
        rowsSlider.setRange(3, 3);
        rowsSlider.setFormat(String::valueOf);
        rowsSlider.setListener((sl, v) -> {
            rowsBelowDock = v;
            choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, v).apply();
            changed();
            refresh();
        });
        dockSlider = findViewById(R.id.dockSlider);
        dockSlider.setTextSize(15f);
        dockSlider.setTitle(getString(R.string.dock_opacity_label));
        dockSlider.setRange(DOCK_OPACITIES.length - 1, DOCK_OPACITIES.length - 1);
        dockSlider.setFormat(v -> DOCK_OPACITIES[v] + "%");
        dockSlider.setListener((sl, v) -> {
            dockOpacity = DOCK_OPACITIES[v];
            choices.edit().putInt(Config.DOCK_OPACITY_KEY, dockOpacity).apply();
            changed();
            refresh();
        });
        findViewById(R.id.wallpaperChoice).setOnClickListener(v -> showWallpaperPicker());
        greetingName = config.greetingName;
        findViewById(R.id.ownerChoice).setOnClickListener(v -> showOwnerEditor());
        marquee = config.marquee;
        marqueeDefault = config.marqueeText;
        marqueeToggle = findViewById(R.id.marqueeToggle);
        marqueeToggle.setOnClickListener(v -> {
            marquee = !marquee;
            choices.edit().putBoolean(Config.MARQUEE_KEY, marquee).apply();
            changed();
            refresh();
        });
        marqueeChoice = findViewById(R.id.marqueeChoice);
        marqueeChoice.setOnClickListener(v -> showMarqueeEditor());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        findViewById(R.id.followThemeChoice).setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.MARQUEE_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            marquee = current.marquee;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnsSlider.requestFocus();
    }

    /** A row's value, at its right. */
    private void value(int row, String text) {
        ((TextView) findViewById(row).findViewWithTag("value")).setText(text);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The name after the greeting ("GOOD EVENING, SHREY"), typed with the TV's keyboard; Done saves. */
    private void showOwnerEditor() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setText(greetingName);
        field.setSelection(field.getText().length());
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.owner_name_label)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, which) -> saveOwner(field.getText().toString()))
                .setNeutralButton(R.string.marquee_clear, (d, which) -> saveOwner(""))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.setOnEditorActionListener((v, action, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_DONE && !enter) return false;
            saveOwner(field.getText().toString());
            dialog.dismiss();
            return true;
        });
        wireMarqueeFocus(dialog, field);
        field.requestFocus();
    }

    private void saveOwner(String value) {
        greetingName = value.trim();
        choices.edit().putString(Config.GREETING_NAME_KEY, greetingName).apply();
        changed();
        refresh();
    }

    /** The marquee's text, typed with the TV's keyboard; saved to marquee.txt, which Home reads on its next pass. */
    private void showMarqueeEditor() {
        File dir = Config.dir(this);
        String current = Marquee.read(dir);
        // One line, so the keyboard's Enter key is Done and saves: with the keyboard open the remote's arrows
        // move around the keyboard, and OK under it could not be reached (MiTV4I, Android TV 9).
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setHint(R.string.marquee_hint);
        field.setText(Marquee.toField(current != null ? current : marqueeDefault));
        field.setSelection(field.getText().length());
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.marquee_label)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, which) -> saveMarquee(dir, Marquee.fromField(field.getText().toString())))
                .setNeutralButton(R.string.marquee_clear, (d, which) -> saveMarquee(dir, ""))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.setOnEditorActionListener((v, action, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_DONE && !enter) return false;
            saveMarquee(dir, Marquee.fromField(field.getText().toString()));
            dialog.dismiss();
            return true;
        });
        wireMarqueeFocus(dialog, field);
        field.requestFocus();
    }

    /**
     * A remote has no Tab: the stock dialog left Left/Right stuck on the first button (MiTV4I, Android TV 9). With
     * the keyboard hidden, Down from the field goes to OK; the buttons form one row; Up goes back.
     */
    private static void wireMarqueeFocus(AlertDialog dialog, EditText field) {
        View clear = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        View cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        View ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        View[] row = {clear, cancel, ok};
        for (int i = 0; i < row.length; i++) {
            View b = row[i];
            View left = row[Math.max(0, i - 1)];
            View right = row[Math.min(row.length - 1, i + 1)];
            b.setOnKeyListener((v, code, e) -> {
                View target = code == KeyEvent.KEYCODE_DPAD_LEFT ? left
                        : code == KeyEvent.KEYCODE_DPAD_RIGHT ? right
                        : code == KeyEvent.KEYCODE_DPAD_UP ? field : null;
                if (target == null) return false;
                if (e.getAction() == KeyEvent.ACTION_DOWN) target.requestFocus();
                return true;
            });
        }
        field.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN || e.getAction() != KeyEvent.ACTION_DOWN) return false;
            Layout layout = field.getLayout();
            if (layout != null && layout.getLineForOffset(field.getSelectionEnd()) < layout.getLineCount() - 1) {
                return false;   // not on the last line yet: the cursor moves down
            }
            ok.requestFocus();
            return true;
        });
    }

    private void saveMarquee(File dir, String value) {
        if (!Marquee.write(dir, value.length() > 4 * Marquee.MAX_LENGTH ? value.substring(0, 4 * Marquee.MAX_LENGTH) : value)) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        columnsSlider.setValue(Math.max(0, Math.min(3, columns - 4)));
        rowsSlider.setValue(Math.max(0, Math.min(3, rowsBelowDock)));
        int stop = 0;
        for (int i = 0; i < DOCK_OPACITIES.length; i++) if (DOCK_OPACITIES[i] <= dockOpacity) stop = i;
        dockSlider.setValue(stop);
        // A switch row shows on as activated (its knob and green track follow).
        animationsChoice.setActivated(animations);
        advancedChoice.setActivated(advancedEffects);
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        marqueeToggle.setActivated(marquee);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        value(R.id.wallpaperChoice, "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                : wallpaperCurrent != null ? wallpaperCurrent : getString(R.string.wallpaper_theme_default));
        value(R.id.followThemeChoice, getString(hasOnTvChoices() ? R.string.ls_reset : R.string.ls_following));
        value(R.id.ownerChoice, greetingName.isEmpty() ? getString(R.string.wallpaper_none) : greetingName);
        marqueeChoice.setVisibility(marquee ? View.VISIBLE : View.GONE);
        if (marquee) {
            String saved = Marquee.read(Config.dir(this));
            String shown = Marquee.normalize(saved != null ? saved : marqueeDefault);
            if (shown.length() > 32) shown = shown.substring(0, 32).trim() + "…";
            value(R.id.marqueeChoice, shown.isEmpty() ? getString(R.string.wallpaper_none) : shown);
        }
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.MARQUEE_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}

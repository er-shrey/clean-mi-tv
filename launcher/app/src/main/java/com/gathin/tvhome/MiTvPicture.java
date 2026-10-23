package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Backlight and contrast on Xiaomi TVs whose panel ignores Settings.System brightness (config.xml mitv_picture).
 * mitv.service's TvSettingService (system uid) answers the bind with mitv.impl.tv.ITvSettingManager; this calls it
 * with raw transactions, so no Xiaomi class is loaded into the launcher. The codes were read from that interface's
 * Stub on a MiTV4I (magnolia, Android TV 9, PI.3698): another firmware may number them differently, so a call that
 * fails leaves the slider hidden rather than guessing.
 */
final class MiTvPicture {
    interface Ready {
        void onReady(MiTvPicture picture);
    }

    private static final String DESCRIPTOR = "mitv.impl.tv.ITvSettingManager";
    private static final int SET_BACKLIGHT = 42;
    private static final int GET_BACKLIGHT = 43;
    private static final int SET_CONTRAST = 32;
    private static final int GET_CONTRAST = 23;

    private final Context context;
    private IBinder binder;
    private ServiceConnection connection;

    MiTvPicture(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Binds; ready runs on the main thread once the service answers and a read works. False when nothing to bind. */
    boolean connect(Ready ready) {
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder = service;
                if (backlight() >= 0) ready.onReady(MiTvPicture.this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binder = null;
            }
        };
        try {
            Intent intent = new Intent("mitv.service.TvSettingService").setPackage("mitv.service");
            if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) return true;
        } catch (SecurityException ignored) {
            // not exported to apps on this firmware
        }
        connection = null;
        return false;
    }

    void disconnect() {
        if (connection != null) {
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // never bound
            }
        }
        connection = null;
        binder = null;
    }

    /** 0 to 100, or -1 when the call failed. */
    int backlight() {
        return get(GET_BACKLIGHT);
    }

    int contrast() {
        return get(GET_CONTRAST);
    }

    /**
     * Both setters are relative on this firmware: the TV adds the value to the current one (backlight 60 + 55 asked
     * for 115 and fell back to 10; contrast 95 + 90 = 185 was refused, as SystemControl's CPQControl logs on the
     * MiTV4I). So each sends the difference to the value it wants.
     */
    boolean setBacklight(int value) {
        return step(GET_BACKLIGHT, SET_BACKLIGHT, value);
    }

    boolean setContrast(int value) {
        return step(GET_CONTRAST, SET_CONTRAST, value);
    }

    private boolean step(int getCode, int setCode, int value) {
        int now = get(getCode);
        if (now < 0) return false;
        int target = Math.max(0, Math.min(100, value));
        return target == now || set(setCode, target - now);
    }

    private int get(int code) {
        if (binder == null) return -1;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            binder.transact(code, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (RemoteException | RuntimeException e) {
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private boolean set(int code, int value) {
        if (binder == null) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(value);
            binder.transact(code, data, reply, 0);
            reply.readException();
            return true;
        } catch (RemoteException | RuntimeException e) {
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}

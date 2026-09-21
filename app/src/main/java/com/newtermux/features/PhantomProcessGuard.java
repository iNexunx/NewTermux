package com.newtermux.features;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.termux.shared.android.FeatureFlagUtils;
import com.termux.shared.android.PhantomProcessUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Silent, best-effort guard that disables the Android 12+ phantom process monitor so the OS stops
 * killing Termux child processes with SIGKILL (seen in the terminal as "signal 9").
 *
 * The phantom process limit is enforced globally by system_server and cannot be turned off by an
 * ordinary app. It can only be disabled by writing the {@code settings_enable_monitor_phantom_procs}
 * Settings.Global flag, which requires either:
 *   - the WRITE_SECURE_SETTINGS permission granted once via `adb shell pm grant`, or
 *   - root (via `su`).
 *
 * This class performs that write quietly on service start with no user interface. If neither
 * capability is available it does nothing and the system keeps its default behaviour.
 */
public final class PhantomProcessGuard {

    private static final String TAG = "PhantomProcessGuard";
    private static final String KEY_MONITOR_PHANTOM_PROCS = PhantomProcessUtils.FEATURE_FLAG_SETTINGS_ENABLE_MONITOR_PHANTOM_PROCS;
    private static final String PREFS = "newtermux_phantom_guard";
    private static final String PREF_ROOT_DENIED = "root_denied";

    private PhantomProcessGuard() {}

    public static void enforce(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                enforceInternal(appContext);
            } catch (Throwable t) {
                Log.w(TAG, "phantom guard failed: " + t);
            }
        }, "PhantomProcessGuard").start();
    }

    private static void enforceInternal(Context context) {
        if (isMonitorDisabled(context)) {
            Log.i(TAG, "phantom process monitor already disabled");
            return;
        }

        // Path 1: WRITE_SECURE_SETTINGS granted via adb (no root prompt).
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                Settings.Global.putString(context.getContentResolver(), KEY_MONITOR_PHANTOM_PROCS, "false");
                Log.i(TAG, "phantom monitor disabled via WRITE_SECURE_SETTINGS");
                return;
            } catch (Throwable t) {
                Log.w(TAG, "Settings.Global write failed: " + t);
            }
        }

        // Path 2: root, attempted once and remembered if denied to avoid repeated su prompts.
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_ROOT_DENIED, false)) {
            Log.i(TAG, "root previously unavailable, skipping");
            return;
        }

        if (runRoot("settings put global " + KEY_MONITOR_PHANTOM_PROCS + " false")) {
            Log.i(TAG, "phantom monitor disabled via root");
        } else {
            prefs.edit().putBoolean(PREF_ROOT_DENIED, true).apply();
            Log.i(TAG, "root unavailable; phantom monitor left unchanged");
        }
    }

    private static boolean isMonitorDisabled(Context context) {
        try {
            String value = Settings.Global.getString(context.getContentResolver(), KEY_MONITOR_PHANTOM_PROCS);
            if ("false".equalsIgnoreCase(value)) return true;
        } catch (Throwable ignored) {}

        try {
            FeatureFlagUtils.FeatureFlagValue value =
                PhantomProcessUtils.getFeatureFlagMonitorPhantomProcsValueString(context);
            if (value == FeatureFlagUtils.FeatureFlagValue.FALSE
                    || value == FeatureFlagUtils.FeatureFlagValue.UNSUPPORTED) {
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static boolean runRoot(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while (reader.readLine() != null) {
                // Drain output so the process can exit.
            }
            return process.waitFor() == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }
}

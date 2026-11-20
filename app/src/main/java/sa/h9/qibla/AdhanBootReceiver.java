package sa.h9.qibla;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Reschedules background Adhan alarms and launches the dynamic floating ball
 * whenever the car headunit starts up, reboots, or time/timezone changes.
 */
public class AdhanBootReceiver extends BroadcastReceiver {
    private static final String TAG = "AdhanBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action + ", rescheduling next prayer alarm");
        AdhanScheduler.scheduleNextPrayer(context);

        // Flag Store QR display on any package update/reinstall (even same versionCode)
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            try {
                context.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(MainActivity.KEY_PENDING_POST_INSTALL_QR, true)
                        .apply();
            } catch (Throwable t) {
                Log.e(TAG, "Failed to flag post-update QR", t);
            }
        }

        // Auto-launch MainActivity or floating ball upon vehicle start / boot completed
        boolean isBootBroadcast = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || "android.intent.action.REBOOT".equals(action);

        if (isBootBroadcast || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            try {
                MainActivity.verifyHomeLauncherHealth(context);
                SharedPreferences uiPrefs = context.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
                boolean bubbleEnabled = uiPrefs.getBoolean("floating_bubble_enabled", false);
                boolean autoStartCar = uiPrefs.getBoolean("floating_bubble_autostart_car", true);
                if (bubbleEnabled && autoStartCar) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                        Log.d(TAG, "Auto-starting FloatingSpeedBubbleService on car boot");
                        FloatingSpeedBubbleService.start(context);
                    } else {
                        Log.w(TAG, "Overlay permission not granted; cannot auto-start floating bubble");
                    }
                }

                // Direct Primary App Auto-Launch on Boot / Car Ignition
                boolean autoLaunchMain = uiPrefs.getBoolean(MainActivity.KEY_AUTO_LAUNCH_APP_ON_BOOT, false);
                if (isBootBroadcast && autoLaunchMain) {
                    try {
                        Intent launchIntent = new Intent(context, MainActivity.class);
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        context.startActivity(launchIntent);
                        Log.d(TAG, "Auto-launched MainActivity directly on car boot");
                    } catch (Throwable lt) {
                        Log.e(TAG, "Failed to auto-launch MainActivity on boot", lt);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to process boot/update receiver", t);
            }
        }
    }
}

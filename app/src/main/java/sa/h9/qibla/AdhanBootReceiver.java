package sa.h9.qibla;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Reschedules background Adhan alarms whenever the car headunit starts up or time/timezone changes.
 */
public class AdhanBootReceiver extends BroadcastReceiver {
    private static final String TAG = "AdhanBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        Log.d(TAG, "Received broadcast: " + intent.getAction() + ", rescheduling next prayer alarm");
        AdhanScheduler.scheduleNextPrayer(context);
    }
}

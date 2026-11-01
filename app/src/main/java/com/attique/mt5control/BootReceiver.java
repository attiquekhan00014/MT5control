package com.attique.mt5control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** after the phone restarts (or the app is updated) the alarm watcher starts again by itself */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (c.getSharedPreferences("mt5", Context.MODE_PRIVATE).getBoolean("watch", false)) {
            MainActivity.startWatcher(c);
        }
    }
}

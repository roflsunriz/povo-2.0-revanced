package dev.roflsunriz.povo.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AutomationBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Automation.restoreSchedule(context.getApplicationContext());
    }
}

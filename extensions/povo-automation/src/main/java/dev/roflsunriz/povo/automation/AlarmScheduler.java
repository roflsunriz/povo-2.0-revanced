package dev.roflsunriz.povo.automation;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class AlarmScheduler {
    private static final long WAKE_EARLY_MS = 5L * 60L * 1000L;

    private AlarmScheduler() {}

    @SuppressLint("MissingPermission")
    static void schedule(Context context, long expiry) {
        if (expiry <= 0L) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long trigger = Math.max(System.currentTimeMillis() + 1000L, expiry - WAKE_EARLY_MS);
        PendingIntent pendingIntent = pendingIntent(context);
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
            Notifications.status(context, Strings.settingsTitle(), Strings.exactAlarmRequired());
            return;
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
        } catch (SecurityException denied) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
            Notifications.status(context, Strings.settingsTitle(), Strings.exactAlarmRequired());
        }
    }

    static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent(context));
    }

    private static PendingIntent pendingIntent(Context context) {
        return PendingIntent.getBroadcast(
                context,
                7201,
                new Intent(context, AutomationAlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}

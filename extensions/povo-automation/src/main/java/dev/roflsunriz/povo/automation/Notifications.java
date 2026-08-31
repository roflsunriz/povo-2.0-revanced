package dev.roflsunriz.povo.automation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class Notifications {
    static final int FOREGROUND_ID = 7201;
    private static final int STATUS_ID = 7202;
    private static final String CHANNEL = "povo_promo_automation";

    private Notifications() {}

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL,
                Strings.channelName(),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(Strings.settingsTitle());
        manager(context).createNotificationChannel(channel);
    }

    static Notification foreground(Context context, String text) {
        return builder(context)
                .setContentTitle(Strings.foregroundTitle())
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(settingsIntent(context))
                .build();
    }

    @SuppressLint("NotificationPermission")
    static void status(Context context, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager(context).notify(
                STATUS_ID,
                builder(context)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setContentIntent(settingsIntent(context))
                        .build()
        );
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context context) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        return builder.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setShowWhen(true);
    }

    private static PendingIntent settingsIntent(Context context) {
        Intent intent = new Intent(context, AutomationSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                7201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}

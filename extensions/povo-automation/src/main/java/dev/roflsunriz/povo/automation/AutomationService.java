package dev.roflsunriz.povo.automation;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class AutomationService extends Service {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduled;

    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.createChannel(this);
        startForeground(
                Notifications.FOREGROUND_ID,
                Notifications.foreground(this, Strings.foregroundTitle())
        );
        Automation.onServiceStarted(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduleAttempt(0L);
        return START_NOT_STICKY;
    }

    synchronized void scheduleAttempt(long delayMs) {
        if (scheduled != null) scheduled.cancel(false);
        scheduled = executor.schedule(Automation::attempt, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
    }

    void showProgress(String text) {
        startForeground(Notifications.FOREGROUND_ID, Notifications.foreground(this, text));
    }

    void finishWork() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Automation.onServiceStopped(this);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

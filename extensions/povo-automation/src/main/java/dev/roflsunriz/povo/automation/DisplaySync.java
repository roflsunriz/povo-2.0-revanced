package dev.roflsunriz.povo.automation;

import android.app.job.JobInfo;
import android.app.Application;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class DisplaySync {
    private static final int JOB_ID = 0x504f564f;
    private static final Object SEND_LOCK = new Object();
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> pending;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static Application context;

    private DisplaySync() {}

    static synchronized void initialize(Context app) {
        if (context != null) return;
        context = (Application) app.getApplicationContext();
        listener = (preferences, key) -> request();
        context.getSharedPreferences("povo_promo_automation", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(listener);
        schedule(context);
        request();
        EXECUTOR.scheduleWithFixedDelay(() -> publish(context), 5, 5, TimeUnit.MINUTES);
    }

    static void schedule(Context app) {
        JobScheduler scheduler = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (!new AutomationState(app).displayEnabled()) {
            scheduler.cancel(JOB_ID);
            return;
        }
        scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(app, DisplaySyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(15L * 60L * 1000L)
                .build());
    }

    static synchronized void request() {
        if (context == null) return;
        if (pending != null) pending.cancel(false);
        pending = EXECUTOR.schedule(() -> publish(context), 1, TimeUnit.SECONDS);
    }

    static boolean publish(Context app) {
        synchronized (SEND_LOCK) {
            if (Thread.currentThread().isInterrupted()) return false;
            return send(app);
        }
    }

    private static boolean send(Context app) {
        AutomationState state = new AutomationState(app);
        if (!state.displayEnabled()) return true;
        HttpURLConnection connection = null;
        try {
            String[] configuration = state.displayConnection();
            if (configuration == null) return true;
            String token = configuration[1];
            if (!DisplayEndpoint.validToken(token)) throw new IllegalArgumentException();
            JSONObject data = new JSONObject();
            data.put("schema_version", 1);
            data.put("observed_at_ms", System.currentTimeMillis());
            data.put("expiry_at_ms", timestamp(state.currentExpiry()));
            data.put("expiry_source", state.expirySource());
            data.put("expiry_observed_at_ms", timestamp(state.expiryObservedAt()));
            data.put("code_deadline_at_ms", timestamp(state.deadline()));
            data.put("automatic_renewal", state.enabled());
            data.put("applied_uses", state.appliedUses());
            data.put("max_uses", state.maxUses());
            data.put("renewal_state", state.renewalState());
            data.put("last_applied_at_ms", timestamp(state.lastApplied()));
            byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(DisplayEndpoint.validate(configuration[0])).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setDoOutput(true);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            boolean success = status >= 200 && status < 300;
            recordResult(app, success, success ? "" : "HTTP " + status);
            return success;
        } catch (Exception error) {
            // Never expose URLs, credentials, host responses, or account data.
            recordResult(app, false, error.getClass().getSimpleName());
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Object timestamp(long value) { return value > 0 ? value : JSONObject.NULL; }

    private static void recordResult(Context app, boolean success, String error) {
        SharedPreferences.Editor editor = app.getSharedPreferences("povo_display_sync_result", Context.MODE_PRIVATE)
                .edit().putString("error", error);
        if (success) editor.putLong("last_success", System.currentTimeMillis());
        editor.apply();
    }
}

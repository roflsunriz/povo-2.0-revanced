package dev.roflsunriz.povo.automation;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class Automation {
    private static final long MIN_DURATION_TOLERANCE_MS = 10L * 60L * 1000L;
    private static final long CONTROLLER_WARNING_INTERVAL_MS = 30_000L;

    private static final AtomicBoolean inFlight = new AtomicBoolean(false);
    private static final AtomicLong attemptStartedAt = new AtomicLong(0L);
    private static final AtomicLong lastControllerWarningAt = new AtomicLong(0L);
    private static final PromoResultGate promoResultGate = new PromoResultGate();
    private static volatile Application application;
    private static volatile AutomationState state;
    private static volatile Object promoController;
    private static volatile String promoMethodName;
    private static volatile Class<?> promoControllerClass;
    private static volatile String koinClassName;
    private static volatile String koinMethodName;
    private static volatile AutomationService service;
    private static volatile WeakReference<Activity> foregroundActivity = new WeakReference<>(null);

    private Automation() {}

    public static synchronized void initialize(Application app) {
        if (application != null) return;
        application = app;
        state = new AutomationState(app);
        state.resetTransientRenewalState();
        DisplaySync.initialize(app);
        Notifications.createChannel(app);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle bundle) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                foregroundActivity = new WeakReference<>(activity);
                ensurePromoController();
                AutomationEntryCard.attach(activity);
                resumeIfDue();
            }
            @Override public void onActivityPaused(Activity activity) {
                if (foregroundActivity.get() == activity) foregroundActivity.clear();
            }
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
        scheduleKnownExpiry();
    }

    public static void configurePromoController(
            Class<?> controllerClass,
            String koinClass,
            String koinMethod,
            String promoMethod
    ) {
        promoControllerClass = controllerClass;
        koinClassName = koinClass;
        koinMethodName = koinMethod;
        promoMethodName = promoMethod;
    }

    private static boolean ensurePromoController() {
        if (promoController != null) return true;
        Class<?> controllerClass = promoControllerClass;
        String owner = koinClassName;
        String resolver = koinMethodName;
        if (controllerClass == null || owner == null || resolver == null) return false;
        try {
            Class<?> koinClass = Class.forName(owner);
            Method method = koinClass.getDeclaredMethod(resolver, Class.class);
            method.setAccessible(true);
            promoController = method.invoke(null, controllerClass);
            Log.i("povo-automation", "Logged-in promo controller resolved");
            AutomationService running = service;
            if (running != null) running.scheduleAttempt(0L);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            long now = System.currentTimeMillis();
            long previous = lastControllerWarningAt.get();
            if (now - previous >= CONTROLLER_WARNING_INTERVAL_MS
                    && lastControllerWarningAt.compareAndSet(previous, now)) {
                Log.w("povo-automation", "Promo controller is not ready: " + error.getClass().getSimpleName());
            }
            return false;
        }
    }

    public static String preparePromoInput(String input) {
        return registerPromoInput(input, true);
    }

    static String savePromoInput(String input) {
        return registerPromoInput(input, false);
    }

    private static String registerPromoInput(String input, boolean expectHostResult) {
        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(input);
        if (!isPlausibleCode(result.code)) return input == null ? "" : input.trim();
        if (result.product.type == PromoProduct.Type.UNKNOWN) {
            AutomationState existing = requireState();
            if (expectHostResult && result.code.equals(existing.code())) {
                promoResultGate.expectResult();
                Log.i("povo-automation", "Tracking a manual submission of the stored code");
            }
            return result.code;
        }
        AutomationState localState = requireState();
        if (!localState.saveCode(result.code, result.product)) {
            toast(Strings.encryptionFailed());
            return result.code;
        }
        if (result.deadline > System.currentTimeMillis()) localState.setDeadline(result.deadline);
        String savedStatus = result.product.isRepeatableTimeCode()
                ? Strings.codeSaved()
                : Strings.singleCodeSaved();
        localState.setLastStatus(savedStatus);
        Notifications.status(requireContext(), Strings.settingsTitle(), savedStatus);
        toast(savedStatus);
        scheduleKnownExpiry();
        if (expectHostResult) promoResultGate.expectResult();
        else promoResultGate.cancel();
        if (expectHostResult && result.emailLike) openSettingsSoon();
        return result.code;
    }

    public static boolean setManualExpiry(String input) {
        if (input == null) return false;
        String[] patterns = {"yyyy-MM-dd HH:mm", "yyyy/M/d H:mm"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
                format.setLenient(false);
                Date parsed = format.parse(input.trim());
                if (parsed == null || parsed.getTime() <= System.currentTimeMillis()) return false;
                AutomationState localState = requireState();
                localState.setCurrentExpiry(parsed.getTime(), "manual");
                localState.setLastStatus(Strings.manualExpirySaved());
                scheduleKnownExpiry();
                finishService();
                return true;
            } catch (ParseException ignored) {
                // Try the next accepted local format.
            }
        }
        return false;
    }

    public static void setEnabled(boolean enabled) {
        AutomationState localState = requireState();
        if (enabled && (!localState.isRepeatableTimeCode() || !localState.hasRemainingUses())) return;
        localState.setEnabled(enabled);
        localState.setRenewalState("idle");
        if (enabled) scheduleKnownExpiry();
        else {
            AlarmScheduler.cancel(requireContext());
            finishService();
        }
    }

    public static boolean setUseProgress(String maxInput, String currentInput, String durationInput) {
        try {
            int maxUses = Integer.parseInt(maxInput.trim());
            int currentUse = Integer.parseInt(currentInput.trim());
            int durationHours = Integer.parseInt(durationInput.trim());
            if (maxUses < 1 || maxUses > 999 || currentUse < 0 || currentUse > maxUses
                    || durationHours < 1 || durationHours > 8760) return false;
            AutomationState localState = requireState();
            localState.setPlan(maxUses, currentUse, durationHours);
            if (!localState.isRepeatableTimeCode() || !localState.hasRemainingUses()) {
                localState.setEnabled(false);
                if (localState.appliedUses() >= localState.maxUses()) {
                    localState.setRenewalState("completed");
                    localState.setLastStatus(Strings.allUsesCompleted());
                }
                AlarmScheduler.cancel(requireContext());
                finishService();
            } else {
                scheduleKnownExpiry();
            }
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static void onAddonPayload(Object payload) {
        if (payload == null) return;
        try {
            JSONObject addon = payload instanceof JSONObject
                    ? (JSONObject) payload
                    : new JSONObject(payload.toString());
            if (!addon.optBoolean("current", false)) return;
            long start = parseDate(addon.optString("start_date"));
            long expiry = parseDate(addon.optString("expiry_date"));
            recordToppingWindow(start, expiry);
        } catch (Exception ignored) {
            // The host can pass unrelated add-ons through the same parser.
        }
    }

    public static void onUserPlanPayload(Object payload) {
        if (payload == null) return;
        try {
            JSONObject root = payload instanceof JSONObject
                    ? (JSONObject) payload
                    : new JSONObject(payload.toString());
            scanDateWindows(root, 0);
        } catch (Exception ignored) {
            // Ignore unrelated payloads without logging account data.
        }
    }

    private static void scanDateWindows(JSONObject object, int depth) {
        if (depth > 12) return;
        String[][] pairs = {
                {"start_date", "expiry_date"},
                {"startDate", "expiryDate"},
                {"start_at", "expires_at"},
                {"started_at", "expired_at"},
                {"start", "end"}
        };
        for (String[] pair : pairs) {
            if (!object.has(pair[0]) || !object.has(pair[1])) continue;
            recordToppingWindow(parseDateQuietly(object.optString(pair[0])), parseDateQuietly(object.optString(pair[1])));
        }

        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            Object child = object.opt(keys.next());
            if (child instanceof JSONObject) {
                scanDateWindows((JSONObject) child, depth + 1);
            } else if (child instanceof JSONArray) {
                JSONArray array = (JSONArray) child;
                for (int index = 0; index < array.length(); index++) {
                    Object item = array.opt(index);
                    if (item instanceof JSONObject) scanDateWindows((JSONObject) item, depth + 1);
                }
            }
        }
    }

    private static void recordToppingWindow(long start, long expiry) {
        if (start <= 0L || expiry <= 0L) return;
        AutomationState localState = requireState();
        long expectedDuration = localState.durationMillis();
        long tolerance = Math.max(MIN_DURATION_TOLERANCE_MS, expectedDuration / 100L);
        long duration = expiry - start;
        if (Math.abs(duration - expectedDuration) > tolerance) return;
        long now = System.currentTimeMillis();
        if (expiry < now - 5L * 60L * 1000L) return;

        long previous = localState.currentExpiry();
        if (previous > now && previous < expiry && !"estimated".equals(localState.expirySource())) return;
        localState.setCurrentExpiry(expiry, "server");
        localState.setLastStatus(Strings.toppingDetected(localState.durationHours()));
        Log.i("povo-automation", "Detected a configured topping window");
        scheduleKnownExpiry();
    }

    public static void onPromoResult(Object result, Object model) {
        if (!promoResultGate.consumeExpectedResult()) return;
        inFlight.set(false);
        attemptStartedAt.set(0L);
        AutomationState localState = requireState();
        if (localState.code() == null) return;
        boolean transportSucceeded = ReflectionUtils.firstBoolean(result, false);
        boolean codeAccepted = ReflectionUtils.firstBoolean(model, false);
        if (transportSucceeded && codeAccepted) {
            Log.i("povo-automation", "Promo application accepted");
            long now = System.currentTimeMillis();
            localState.recordSuccess(now);
            long nextExpiry = Math.max(now, localState.currentExpiry()) + localState.durationMillis();
            localState.setCurrentExpiry(nextExpiry, "estimated");
            if (!localState.isRepeatableTimeCode() || !localState.hasRemainingUses()) {
                localState.setRenewalState("completed");
                localState.setEnabled(false);
                localState.setLastStatus(Strings.allUsesCompleted());
                AlarmScheduler.cancel(requireContext());
                Notifications.status(requireContext(), Strings.settingsTitle(), Strings.allUsesCompleted());
                finishService();
                return;
            }
            localState.setRenewalState("idle");
            localState.setLastStatus(Strings.success());
            AlarmScheduler.schedule(requireContext(), nextExpiry);
            Notifications.status(requireContext(), Strings.settingsTitle(), Strings.success());
            finishService();
            return;
        }

        if (!localState.enabled() || !localState.isRepeatableTimeCode()) {
            finishService();
            return;
        }

        Object exception = ReflectionUtils.firstObjectField(result);
        int httpCode = ReflectionUtils.invokeInt(exception, "getHttpCode", -1);
        Log.i("povo-automation", "Promo application not accepted; http=" + httpCode);
        if (httpCode == 401 || httpCode == 403) {
            localState.setRenewalState("auth_required");
            localState.setLastStatus(Strings.authRequired());
            Notifications.status(requireContext(), Strings.settingsTitle(), Strings.authRequired());
            finishService();
            return;
        }
        retryOrWait();
    }

    static void restoreSchedule(Context context) {
        DisplaySync.schedule(context);
        AutomationState restored = new AutomationState(context);
        if (restored.enabled() && restored.isRepeatableTimeCode()
                && restored.code() != null && restored.currentExpiry() > 0L) {
            AlarmScheduler.schedule(context, restored.currentExpiry());
        }
    }

    static void onServiceStarted(AutomationService running) {
        service = running;
    }

    static void onServiceStopped(AutomationService stopped) {
        if (service == stopped) service = null;
        requireState().resetTransientRenewalState();
        inFlight.set(false);
        attemptStartedAt.set(0L);
        promoResultGate.cancel();
    }

    static void attempt() {
        AutomationState localState = requireState();
        AutomationService running = service;
        if (running == null) return;
        String code = localState.code();
        if (!localState.enabled() || !localState.isRepeatableTimeCode()
                || code == null || !localState.hasRemainingUses()) {
            running.finishWork();
            return;
        }
        long now = System.currentTimeMillis();
        if (localState.deadline() > 0L && now > localState.deadline()) {
            localState.setRenewalState("code_expired");
            localState.setEnabled(false);
            localState.setLastStatus("The prepaid-code deadline has passed");
            running.finishWork();
            return;
        }
        long expiry = localState.currentExpiry();
        if (expiry <= 0L) {
            localState.setLastStatus("Waiting for the logged-in plan status");
            running.finishWork();
            return;
        }
        long untilAttempt = RetryPolicy.firstAttemptDelay(now, expiry);
        if (untilAttempt > 0L) {
            if (!RetryPolicy.shouldWaitInForeground(now, expiry)) {
                AlarmScheduler.schedule(requireContext(), expiry);
                running.finishWork();
                return;
            }
            running.showProgress(Strings.foregroundTitle());
            running.scheduleAttempt(untilAttempt);
            return;
        }
        if (inFlight.get()) {
            long elapsed = now - attemptStartedAt.get();
            long remaining = RetryPolicy.REQUEST_WATCHDOG_MS - elapsed;
            if (remaining > 0L) {
                running.scheduleAttempt(remaining);
                return;
            }
            Log.w("povo-automation", "Promo request watchdog expired; retrying");
            promoResultGate.cancel();
            inFlight.set(false);
            attemptStartedAt.set(0L);
        }
        if (!inFlight.compareAndSet(false, true)) return;

        if (!ensurePromoController()) {
            inFlight.set(false);
            if (RetryPolicy.shouldGiveUp(now, expiry)) {
                localState.setRenewalState("needs_review");
                localState.setLastStatus("Automatic application needs review");
                Notifications.status(requireContext(), Strings.settingsTitle(), localState.lastStatus());
                running.finishWork();
            } else {
                running.scheduleAttempt(2_000L);
            }
            return;
        }

        int networkState = activeInternetState();
        if (networkState == 0) {
            localState.setRenewalState("retrying");
            inFlight.set(false);
            Log.w("povo-automation", "No active internet network; retrying");
            running.scheduleAttempt(RetryPolicy.NETWORK_RETRY_MS);
            return;
        }

        Object controller = promoController;
        String methodName = promoMethodName;
        if (controller == null || methodName == null) {
            inFlight.set(false);
            running.scheduleAttempt(2_000L);
            return;
        }
        try {
            localState.setRenewalState("applying");
            Method method = controller.getClass().getMethod(methodName, String.class);
            promoResultGate.expectResult();
            attemptStartedAt.set(now);
            Log.i("povo-automation", "Invoking stored promo code; beforeBoundary="
                    + (now < expiry) + "; validatedNetwork=" + (networkState == 2));
            method.invoke(controller, code);
            if (inFlight.get() && promoResultGate.isExpectingResult()) {
                running.scheduleAttempt(RetryPolicy.REQUEST_WATCHDOG_MS);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            localState.setRenewalState("retrying");
            promoResultGate.cancel();
            inFlight.set(false);
            attemptStartedAt.set(0L);
            localState.setLastStatus("Could not invoke the logged-in promo-code API");
            running.scheduleAttempt(10_000L);
        }
    }

    static AutomationState requireState() {
        AutomationState current = state;
        if (current != null) return current;
        Context context = requireContext();
        current = new AutomationState(context);
        state = current;
        return current;
    }

    static Context requireContext() {
        Application current = application;
        if (current != null) return current;
        throw new IllegalStateException("Automation is not initialized");
    }

    private static void retryOrWait() {
        AutomationState localState = requireState();
        long now = System.currentTimeMillis();
        long expiry = localState.currentExpiry();
        AutomationService running = service;
        if (RetryPolicy.shouldGiveUp(now, expiry)) {
            localState.setRenewalState("needs_review");
            localState.setLastStatus("Automatic application needs review");
            Notifications.status(requireContext(), Strings.settingsTitle(), localState.lastStatus());
            finishService();
            return;
        }
        if (running != null) {
            localState.setRenewalState("retrying");
            running.scheduleAttempt(RetryPolicy.retryDelay(now, expiry));
        }
    }

    private static int activeInternetState() {
        ConnectivityManager manager = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager.getActiveNetwork();
        if (network == null) return 0;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        if (capabilities == null
                || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return 0;
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 2 : 1;
    }

    private static void scheduleKnownExpiry() {
        AutomationState localState = requireState();
        if (!localState.enabled() || !localState.isRepeatableTimeCode()
                || localState.code() == null || !localState.hasRemainingUses()) return;
        long expiry = localState.currentExpiry();
        if (expiry > 0L) AlarmScheduler.schedule(requireContext(), expiry);
    }

    private static void resumeIfDue() {
        AutomationState localState = requireState();
        if (!localState.enabled() || !localState.isRepeatableTimeCode() || localState.code() == null) return;
        long expiry = localState.currentExpiry();
        if (expiry > 0L && expiry <= System.currentTimeMillis() + 60_000L) {
            startService();
        }
    }

    private static void startService() {
        Context context = requireContext();
        Intent intent = new Intent(context, AutomationService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    private static void finishService() {
        AutomationService running = service;
        if (running != null) running.finishWork();
    }

    private static boolean isPlausibleCode(String code) {
        if (code == null || code.length() < 8 || code.length() > 64) return false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-') return false;
        }
        return true;
    }

    private static long parseDate(String value) throws ParseException {
        long parsed = parseDateQuietly(value);
        if (parsed <= 0L) throw new ParseException("Unsupported date", 0);
        return parsed;
    }

    private static long parseDateQuietly(String value) {
        if (value == null || value.isEmpty()) return 0L;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = format.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {
                // Try the next supported server format.
            }
        }
        return 0L;
    }

    private static void toast(String text) {
        Context context = requireContext();
        new android.os.Handler(context.getMainLooper()).post(
                () -> Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        );
    }

    private static void openSettingsSoon() {
        Activity activity = foregroundActivity.get();
        if (activity == null) return;
        new android.os.Handler(activity.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(activity, AutomationSettingsActivity.class);
            activity.startActivity(intent);
        }, 700L);
    }
}

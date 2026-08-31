package dev.roflsunriz.povo.automation;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Automation {
    private static final long TOPPING_DURATION_MS = 168L * 60L * 60L * 1000L;
    private static final long DURATION_TOLERANCE_MS = 2L * 60L * 60L * 1000L;
    private static final long APPLY_LEAD_MS = 2_000L;
    private static final long FAST_RETRY_MS = 3_000L;
    private static final long SLOW_RETRY_MS = 60_000L;
    private static final long GIVE_UP_AFTER_MS = 2L * 60L * 60L * 1000L;

    private static final AtomicBoolean inFlight = new AtomicBoolean(false);
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

    private static void ensurePromoController() {
        if (promoController != null) return;
        Class<?> controllerClass = promoControllerClass;
        String owner = koinClassName;
        String resolver = koinMethodName;
        if (controllerClass == null || owner == null || resolver == null) return;
        try {
            Class<?> koinClass = Class.forName(owner);
            Method method = koinClass.getDeclaredMethod(resolver, Class.class);
            method.setAccessible(true);
            promoController = method.invoke(null, controllerClass);
            Log.i("povo-automation", "Logged-in promo controller resolved");
            AutomationService running = service;
            if (running != null) running.scheduleAttempt(0L);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w("povo-automation", "Promo controller is not ready: " + error.getClass().getSimpleName());
        }
    }

    public static String preparePromoInput(String input) {
        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(input);
        if (!isPlausibleCode(result.code)) return input == null ? "" : input.trim();
        AutomationState localState = requireState();
        if (!localState.saveCode(result.code)) {
            toast(Strings.encryptionFailed());
            return result.code;
        }
        if (result.deadline > System.currentTimeMillis()) localState.setDeadline(result.deadline);
        localState.setLastStatus(Strings.codeSaved());
        Notifications.status(requireContext(), Strings.settingsTitle(), Strings.codeSaved());
        toast(Strings.codeSaved());
        scheduleKnownExpiry();
        if (result.emailLike) openSettingsSoon();
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
                localState.setCurrentExpiry(parsed.getTime());
                localState.setLastStatus(Strings.manualExpirySaved());
                scheduleKnownExpiry();
                return true;
            } catch (ParseException ignored) {
                // Try the next accepted local format.
            }
        }
        return false;
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
        long duration = expiry - start;
        if (Math.abs(duration - TOPPING_DURATION_MS) > DURATION_TOLERANCE_MS) return;
        long now = System.currentTimeMillis();
        if (expiry < now - 5L * 60L * 1000L) return;

        AutomationState localState = requireState();
        long previous = localState.currentExpiry();
        if (previous > now && previous <= expiry) return;
        localState.setCurrentExpiry(expiry);
        localState.setLastStatus(Strings.toppingDetected());
        Log.i("povo-automation", "Detected a 168-hour topping window");
        scheduleKnownExpiry();
    }

    public static void onPromoResult(Object result, Object model) {
        AutomationState localState = requireState();
        if (localState.code() == null) return;
        inFlight.set(false);
        boolean transportSucceeded = ReflectionUtils.firstBoolean(result, false);
        boolean codeAccepted = ReflectionUtils.firstBoolean(model, false);
        if (transportSucceeded && codeAccepted) {
            long now = System.currentTimeMillis();
            localState.recordSuccess(now);
            long nextExpiry = Math.max(now, localState.currentExpiry()) + TOPPING_DURATION_MS;
            localState.setCurrentExpiry(nextExpiry);
            localState.setLastStatus(Strings.success());
            AlarmScheduler.schedule(requireContext(), nextExpiry);
            Notifications.status(requireContext(), Strings.settingsTitle(), Strings.success());
            finishService();
            return;
        }

        Object exception = ReflectionUtils.firstObjectField(result);
        int httpCode = ReflectionUtils.invokeInt(exception, "getHttpCode", -1);
        if (httpCode == 401 || httpCode == 403) {
            localState.setLastStatus(Strings.authRequired());
            Notifications.status(requireContext(), Strings.settingsTitle(), Strings.authRequired());
            finishService();
            return;
        }
        retryOrWait();
    }

    static void restoreSchedule(Context context) {
        AutomationState restored = new AutomationState(context);
        if (restored.enabled() && restored.code() != null && restored.currentExpiry() > 0L) {
            AlarmScheduler.schedule(context, restored.currentExpiry());
        }
    }

    static void onServiceStarted(AutomationService running) {
        service = running;
    }

    static void onServiceStopped(AutomationService stopped) {
        if (service == stopped) service = null;
        inFlight.set(false);
    }

    static void attempt() {
        AutomationState localState = requireState();
        AutomationService running = service;
        if (running == null) return;
        String code = localState.code();
        if (!localState.enabled() || code == null) {
            running.finishWork();
            return;
        }
        long now = System.currentTimeMillis();
        if (localState.deadline() > 0L && now > localState.deadline()) {
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
        long untilAttempt = expiry - APPLY_LEAD_MS - now;
        if (untilAttempt > 0L) {
            running.showProgress(Strings.foregroundTitle());
            running.scheduleAttempt(untilAttempt);
            return;
        }
        if (!inFlight.compareAndSet(false, true)) return;

        Object controller = promoController;
        String methodName = promoMethodName;
        if (controller == null || methodName == null) {
            inFlight.set(false);
            running.scheduleAttempt(2_000L);
            return;
        }
        try {
            Method method = controller.getClass().getMethod(methodName, String.class);
            method.invoke(controller, code);
            inFlight.set(false);
            running.scheduleAttempt(15_000L);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException error) {
            inFlight.set(false);
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
        if (expiry > now) {
            AlarmScheduler.schedule(requireContext(), expiry);
            if (running != null) running.finishWork();
            return;
        }
        if (now - expiry > GIVE_UP_AFTER_MS) {
            localState.setLastStatus("Automatic application needs review");
            Notifications.status(requireContext(), Strings.settingsTitle(), localState.lastStatus());
            finishService();
            return;
        }
        if (running != null) {
            long elapsed = now - expiry;
            running.scheduleAttempt(elapsed < 10L * 60L * 1000L ? FAST_RETRY_MS : SLOW_RETRY_MS);
        }
    }

    private static void scheduleKnownExpiry() {
        AutomationState localState = requireState();
        if (!localState.enabled() || localState.code() == null) return;
        long expiry = localState.currentExpiry();
        if (expiry > 0L) AlarmScheduler.schedule(requireContext(), expiry);
    }

    private static void resumeIfDue() {
        AutomationState localState = requireState();
        if (!localState.enabled() || localState.code() == null) return;
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

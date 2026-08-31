package dev.roflsunriz.povo.automation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class ReflectionUtils {
    private ReflectionUtils() {}

    static boolean firstBoolean(Object value, boolean fallback) {
        if (value == null) return fallback;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (field.getType() != boolean.class || Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                return field.getBoolean(value);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static Object firstObjectField(Object value) {
        if (value == null) return null;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            try {
                field.setAccessible(true);
                Object candidate = field.get(value);
                if (candidate != null && !(candidate instanceof String)) return candidate;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next field.
            }
        }
        return null;
    }

    static int invokeInt(Object value, String methodName, int fallback) {
        if (value == null) return fallback;
        try {
            Method method = value.getClass().getMethod(methodName);
            Object result = method.invoke(value);
            return result instanceof Number ? ((Number) result).intValue() : fallback;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }
}

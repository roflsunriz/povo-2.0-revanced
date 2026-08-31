package dev.roflsunriz.povo.automation;

import java.util.Locale;

final class PromoProduct {
    enum Type {
        REPEATABLE_TIME_CODE("repeatable_time_code"),
        SINGLE_TIME_CODE("single_time_code"),
        UNKNOWN("unknown");

        private final String storageValue;

        Type(String storageValue) {
            this.storageValue = storageValue;
        }

        String storageValue() {
            return storageValue;
        }

        static Type fromStorage(String value) {
            if (value == null) return UNKNOWN;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (Type type : values()) {
                if (type.storageValue.equals(normalized)) return type;
            }
            return UNKNOWN;
        }
    }

    final Type type;
    final int durationHours;
    final int codeUses;
    final int packageUses;
    final int immediateUses;

    PromoProduct(Type type, int durationHours, int codeUses, int packageUses, int immediateUses) {
        this.type = type == null ? Type.UNKNOWN : type;
        this.durationHours = validDuration(durationHours);
        this.codeUses = validUses(codeUses);
        this.packageUses = Math.max(this.codeUses, validUses(packageUses));
        this.immediateUses = Math.max(0, Math.min(immediateUses, this.packageUses));
    }

    static PromoProduct unknown(int durationHours) {
        return new PromoProduct(Type.UNKNOWN, durationHours, 1, 1, 0);
    }

    static PromoProduct legacyRepeatable(int maxUses, int durationHours) {
        return new PromoProduct(Type.REPEATABLE_TIME_CODE, durationHours, maxUses, maxUses, 0);
    }

    boolean isRepeatableTimeCode() {
        return type == Type.REPEATABLE_TIME_CODE && durationHours > 0 && codeUses > 1;
    }

    boolean hasRemainingUses(int appliedUses) {
        return isRepeatableTimeCode() && Math.max(0, appliedUses) < codeUses;
    }

    int clampAppliedUses(int appliedUses) {
        return Math.max(0, Math.min(appliedUses, codeUses));
    }

    int nextAppliedUses(int appliedUses) {
        return clampAppliedUses(clampAppliedUses(appliedUses) + 1);
    }

    private static int validUses(int uses) {
        return uses >= 1 && uses <= 999 ? uses : 1;
    }

    private static int validDuration(int hours) {
        return hours >= 1 && hours <= 8760 ? hours : 0;
    }
}

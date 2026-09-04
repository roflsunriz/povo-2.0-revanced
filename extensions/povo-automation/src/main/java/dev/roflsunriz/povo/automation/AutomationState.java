package dev.roflsunriz.povo.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class AutomationState {
    private static final int CURRENT_SCHEMA_VERSION = 3;
    private static final String PREFS = "povo_promo_automation";
    private static final String KEY_ALIAS = "povo_promo_automation_code";
    private static final String KEY_CODE = "encrypted_code";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DEADLINE = "deadline_epoch_ms";
    private static final String KEY_EXPIRY = "current_expiry_epoch_ms";
    private static final String KEY_SUCCESSES = "success_count";
    private static final String KEY_MAX_USES = "max_uses";
    private static final String KEY_APPLIED_USES = "applied_uses";
    private static final String KEY_DURATION_HOURS = "duration_hours";
    private static final String KEY_LAST_APPLIED = "last_applied_epoch_ms";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final String KEY_PRODUCT_TYPE = "product_type";
    private static final String KEY_PACKAGE_USES = "package_uses";
    private static final String KEY_IMMEDIATE_USES = "immediate_uses";

    private final SharedPreferences preferences;

    AutomationState(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateLegacyState();
    }

    synchronized boolean saveCode(String code, PromoProduct product) {
        try {
            String previousCode = code();
            boolean sameCode = code.equals(previousCode);
            SharedPreferences.Editor editor = preferences.edit()
                    .putString(KEY_CODE, encrypt(code))
                    .putString(KEY_PRODUCT_TYPE, product.type.storageValue())
                    .putInt(KEY_MAX_USES, product.codeUses)
                    .putInt(KEY_DURATION_HOURS, product.durationHours)
                    .putInt(KEY_PACKAGE_USES, product.packageUses)
                    .putInt(KEY_IMMEDIATE_USES, product.immediateUses)
                    .putBoolean(KEY_ENABLED, product.isRepeatableTimeCode());
            if (!sameCode) {
                editor.putInt(KEY_APPLIED_USES, 0)
                        .putInt(KEY_SUCCESSES, 0)
                        .putLong(KEY_LAST_APPLIED, 0L)
                        .putLong(KEY_DEADLINE, 0L)
                        .putLong(KEY_EXPIRY, 0L)
                        .putString("expiry_source", "unknown")
                        .putLong("expiry_observed_at", 0L)
                        .putString("renewal_state", "idle");
            } else {
                editor.putInt(KEY_APPLIED_USES, product.clampAppliedUses(appliedUses()));
            }
            editor.apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    synchronized String code() {
        String encrypted = preferences.getString(KEY_CODE, null);
        if (encrypted == null) return null;
        try {
            return decrypt(encrypted);
        } catch (Exception ignored) {
            return null;
        }
    }

    boolean enabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    long deadline() {
        return preferences.getLong(KEY_DEADLINE, 0L);
    }

    void setDeadline(long deadline) {
        preferences.edit().putLong(KEY_DEADLINE, deadline).apply();
    }

    long currentExpiry() {
        return preferences.getLong(KEY_EXPIRY, 0L);
    }

    void setCurrentExpiry(long expiry, String source) {
        preferences.edit().putLong(KEY_EXPIRY, expiry)
                .putString("expiry_source", source)
                .putLong("expiry_observed_at", System.currentTimeMillis()).apply();
    }

    String expirySource() { return preferences.getString("expiry_source", "unknown"); }
    long expiryObservedAt() { return preferences.getLong("expiry_observed_at", 0); }
    String renewalState() { return preferences.getString("renewal_state", "unknown"); }
    void setRenewalState(String value) { preferences.edit().putString("renewal_state", value).apply(); }

    boolean displayEnabled() { return preferences.getBoolean("display_enabled", false); }
    String displayEndpoint() { return preferences.getString("display_endpoint", ""); }
    String displayToken() {
        try {
            String value = preferences.getString("display_token", null);
            return value == null ? null : decrypt(value);
        } catch (Exception error) { return null; }
    }

    String[] displayConnection() {
        java.util.Map<String, ?> snapshot = preferences.getAll();
        if (!Boolean.TRUE.equals(snapshot.get("display_enabled"))) return null;
        try {
            return new String[]{(String) snapshot.get("display_endpoint"),
                    decrypt((String) snapshot.get("display_token"))};
        } catch (Exception error) { throw new IllegalStateException("Display credential unavailable"); }
    }

    void resetTransientRenewalState() {
        if ("applying".equals(renewalState()) || "retrying".equals(renewalState())) {
            setRenewalState("needs_review");
        }
    }

    boolean configureDisplay(String endpoint, String token) {
        try {
            String validated = DisplayEndpoint.validate(endpoint);
            String credential = token.isEmpty() ? displayToken() : token;
            if (!DisplayEndpoint.validToken(credential)) return false;
            return preferences.edit().putString("display_endpoint", validated)
                    .putString("display_token", encrypt(credential))
                    .putBoolean("display_enabled", true).commit();
        } catch (Exception error) { return false; }
    }

    void disableDisplay() {
        preferences.edit().putBoolean("display_enabled", false)
                .remove("display_token").remove("display_endpoint").apply();
    }

    int successCount() {
        return preferences.getInt(KEY_SUCCESSES, 0);
    }

    int maxUses() {
        return Math.max(1, preferences.getInt(KEY_MAX_USES, 1));
    }

    int appliedUses() {
        return Math.max(0, Math.min(preferences.getInt(KEY_APPLIED_USES, 0), maxUses()));
    }

    boolean hasRemainingUses() {
        return product().hasRemainingUses(appliedUses());
    }

    int durationHours() {
        return Math.max(0, preferences.getInt(KEY_DURATION_HOURS, 0));
    }

    long durationMillis() {
        return durationHours() * 60L * 60L * 1000L;
    }

    void setPlan(int maxUses, int appliedUses, int durationHours) {
        PromoProduct current = product();
        PromoProduct.Type type = maxUses > 1 && durationHours > 0
                ? PromoProduct.Type.REPEATABLE_TIME_CODE
                : PromoProduct.Type.SINGLE_TIME_CODE;
        boolean sameProduct = current.codeUses == maxUses && current.durationHours == durationHours;
        preferences.edit()
                .putString(KEY_PRODUCT_TYPE, type.storageValue())
                .putInt(KEY_MAX_USES, maxUses)
                .putInt(KEY_APPLIED_USES, appliedUses)
                .putInt(KEY_DURATION_HOURS, durationHours)
                .putInt(KEY_PACKAGE_USES, sameProduct ? current.packageUses : maxUses)
                .putInt(KEY_IMMEDIATE_USES, sameProduct ? current.immediateUses : 0)
                .apply();
    }

    void recordSuccess(long appliedAt) {
        PromoProduct product = product();
        preferences.edit()
                .putInt(KEY_SUCCESSES, successCount() + 1)
                .putInt(KEY_APPLIED_USES, product.nextAppliedUses(appliedUses()))
                .putLong(KEY_LAST_APPLIED, appliedAt)
                .apply();
    }

    PromoProduct product() {
        return new PromoProduct(
                PromoProduct.Type.fromStorage(preferences.getString(KEY_PRODUCT_TYPE, null)),
                durationHours(),
                maxUses(),
                Math.max(maxUses(), preferences.getInt(KEY_PACKAGE_USES, maxUses())),
                Math.max(0, preferences.getInt(KEY_IMMEDIATE_USES, 0))
        );
    }

    boolean isRepeatableTimeCode() {
        return product().isRepeatableTimeCode();
    }

    long lastApplied() {
        return preferences.getLong(KEY_LAST_APPLIED, 0L);
    }

    String lastStatus() {
        return preferences.getString(KEY_LAST_STATUS, "");
    }

    void setLastStatus(String status) {
        preferences.edit().putString(KEY_LAST_STATUS, status).apply();
    }

    void clear() {
        preferences.edit().clear().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply();
    }

    private synchronized void migrateLegacyState() {
        if (preferences.getInt(KEY_SCHEMA_VERSION, 0) >= CURRENT_SCHEMA_VERSION) return;

        if (preferences.getInt(KEY_SCHEMA_VERSION, 0) == 2) {
            preferences.edit().putString("expiry_source", "unknown")
                    .putLong("expiry_observed_at", 0L)
                    .putString("renewal_state", "unknown")
                    .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).commit();
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        if (preferences.contains(KEY_CODE)) {
            int maxUses = Math.max(1, preferences.getInt(KEY_MAX_USES, 24));
            int appliedUses = Math.max(0, Math.min(preferences.getInt(KEY_APPLIED_USES, 0), maxUses));
            int durationHours = Math.max(1, preferences.getInt(KEY_DURATION_HOURS, 168));
            PromoProduct legacy = PromoProduct.legacyRepeatable(maxUses, durationHours);
            editor.putString(KEY_PRODUCT_TYPE, legacy.type.storageValue())
                    .putInt(KEY_MAX_USES, legacy.codeUses)
                    .putInt(KEY_APPLIED_USES, appliedUses)
                    .putInt(KEY_DURATION_HOURS, legacy.durationHours)
                    .putInt(KEY_PACKAGE_USES, legacy.packageUses)
                    .putInt(KEY_IMMEDIATE_USES, legacy.immediateUses);
        } else {
            editor.putString(KEY_PRODUCT_TYPE, PromoProduct.Type.UNKNOWN.storageValue())
                    .putInt(KEY_MAX_USES, 1)
                    .putInt(KEY_APPLIED_USES, 0)
                    .putInt(KEY_DURATION_HOURS, 0)
                    .putInt(KEY_PACKAGE_USES, 1)
                    .putInt(KEY_IMMEDIATE_USES, 0);
        }
        editor.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).commit();
    }

    private String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[cipher.getIV().length + encrypted.length];
        System.arraycopy(cipher.getIV(), 0, result, 0, cipher.getIV().length);
        System.arraycopy(encrypted, 0, result, cipher.getIV().length, encrypted.length);
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    private String decrypt(String value) throws Exception {
        byte[] bytes = Base64.decode(value, Base64.NO_WRAP);
        if (bytes.length <= 12) throw new IllegalArgumentException("Invalid encrypted code");
        byte[] iv = new byte[12];
        byte[] encrypted = new byte[bytes.length - 12];
        System.arraycopy(bytes, 0, iv, 0, iv.length);
        System.arraycopy(bytes, iv.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey key() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) return existing;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}

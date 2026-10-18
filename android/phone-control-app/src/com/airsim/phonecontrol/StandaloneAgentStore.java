package com.airsim.phonecontrol;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Private persistent state for the Agent embedded in the standalone APK. */
final class StandaloneAgentStore {
    private static final String FILE = "standalone_agent";
    private static final String REGISTRATION = "registration";
    private static final String LAST_ERROR = "last_error";
    private static final String LAST_SEEN = "last_seen";

    private final SharedPreferences preferences;

    StandaloneAgentStore(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    synchronized JSONObject registration() {
        String encoded = preferences.getString(REGISTRATION, "");
        if (encoded == null || encoded.isBlank()) return null;
        try { return new JSONObject(encoded); }
        catch (Exception error) {
            BridgeLog.error("standalone_registration_corrupt", error);
            return null;
        }
    }

    synchronized JSONObject saveRegistration(String encoded) throws Exception {
        JSONObject value = new JSONObject(encoded);
        // AirSim-Lite：云端 Relay 已移除，配对凭据一律按“本地直连模式”保存。
        value.put("local_mode", true).put("cloud_enabled", false);
        if (value.has("device_id")) value.put("device_id", value.optString("device_id", "").trim());
        validateRegistration(value);
        preferences.edit().putString(REGISTRATION, value.toString()).remove(LAST_ERROR).apply();
        return value;
    }

    synchronized void clear() {
        preferences.edit()
                .remove(REGISTRATION)
                .remove(LAST_ERROR)
                .remove(LAST_SEEN)
                .apply();
    }

    boolean configured() {
        JSONObject value = registration();
        if (value != null && value.optBoolean("local_mode", false)) return true;
        // 兼容从上游版本升级前的旧云端注册数据：只要凭据完整就仍视为已配对。
        return value != null && !value.optString("device_id", "").isBlank()
                && !value.optString("device_secret", "").isBlank()
                && !value.optString("relay_url", "").isBlank();
    }

    void recordSuccess() {
        preferences.edit().putLong(LAST_SEEN, System.currentTimeMillis()).remove(LAST_ERROR).apply();
    }

    void recordError(String error) {
        preferences.edit().putString(LAST_ERROR, DebugRedactor.sanitize(error)).apply();
    }

    String lastError() { return preferences.getString(LAST_ERROR, ""); }
    long lastSeen() { return preferences.getLong(LAST_SEEN, 0L); }

    JSONObject pushStatus() throws Exception {
        JSONObject registration = registration();
        boolean configured = registration != null;
        boolean localMode = configured && registration.optBoolean("local_mode", false);
        JSONObject result = new JSONObject()
                .put("local_mode", localMode)
                .put("cloud_enabled", false)
                .put("configured", configured)
                .put("call_push_ready", false)
                .put("message_push_ready", false)
                .put("last_error", lastError())
                .put("wan_interface", "android-default");
        if (configured) {
            result.put("device_id", registration.optString("device_id", ""));
            result.put("environment", registration.optString("environment", ""));
            result.put("relay_url", "");
        }
        return result;
    }

    /** AirSim-Lite 只接受本地直连（VoWLAN）配对注册。 */
    static void validateRegistration(JSONObject value) {
        if (!value.optBoolean("local_mode", false)) {
            throw new IllegalArgumentException("AirSim-Lite 仅支持本地直连配对");
        }
    }
}

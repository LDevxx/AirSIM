package org.json;

import java.util.LinkedHashMap;
import java.util.Map;

public class JSONObject {
    private final Map<String, Object> map = new LinkedHashMap<>();

    public JSONObject() {}

    public JSONObject(String json) {}

    public JSONObject put(String key, Object value) throws JSONException {
        map.put(key, value);
        return this;
    }

    public JSONObject put(String key, boolean value) throws JSONException {
        map.put(key, value);
        return this;
    }

    public JSONObject put(String key, int value) throws JSONException {
        map.put(key, value);
        return this;
    }

    public JSONObject put(String key, long value) throws JSONException {
        map.put(key, value);
        return this;
    }

    public String optString(String key, String fallback) {
        Object val = map.get(key);
        return val != null ? val.toString() : fallback;
    }

    public int optInt(String key, int fallback) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return fallback;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object v = entry.getValue();
            if (v instanceof String) {
                sb.append("\"").append(v).append("\"");
            } else {
                sb.append(v);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}

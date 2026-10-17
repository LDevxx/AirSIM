package com.airsim.phonecontrol;

import android.content.Context;
import android.util.Base64;

import java.util.Arrays;

public final class VoWLANPairingStore {
    private static final String FILE = "vowlan_pairing";
    private static final String SECRET = "secret_v1";

    private VoWLANPairingStore() {}

    public static void saveEncoded(Context context, String encoded) {
        byte[] decoded = decode(encoded);
        try {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                    .putString(SECRET, Base64.encodeToString(decoded, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING))
                    .apply();
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    public static byte[] load(Context context) {
        String encoded = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(SECRET, "");
        if (encoded.isEmpty()) return new byte[0];
        try { return decode(encoded); }
        catch (IllegalArgumentException error) { return new byte[0]; }
    }

    public static boolean configured(Context context) {
        byte[] secret = load(context);
        try { return secret.length >= 32; }
        finally { Arrays.fill(secret, (byte) 0); }
    }

    public static void clear(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(SECRET).apply();
    }

    private static byte[] decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("VoWLAN secret empty");
        }
        byte[] value = null;
        try {
            value = Base64.decode(encoded, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
        } catch (Exception ignored) {}

        if (value == null || value.length < 32) {
            String trimmed = encoded.trim();
            if (trimmed.length() == 64 && trimmed.matches("^[0-9a-fA-F]+$")) {
                value = hexToBytes(trimmed);
            } else {
                try {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                    value = md.digest(trimmed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new IllegalArgumentException("VoWLAN secret derivation failed", e);
                }
            }
        }
        if (value == null || value.length < 32) {
            if (value != null) Arrays.fill(value, (byte) 0);
            throw new IllegalArgumentException("VoWLAN secret too short");
        }
        return value;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

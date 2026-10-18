package com.airsim.phonecontrol;

/** Tracks whether the VoWLAN PCM owner has actually completed its handshake. */
public final class RemoteAudioSessionRegistry {
    private static boolean vowlan;

    private RemoteAudioSessionRegistry() {}

    public static synchronized void setVoWLAN(boolean active) {
        vowlan = active;
        AirSIMInCallService.refreshAudioPolicy();
    }

    public static synchronized String diagnostic() {
        return "vowlan=" + vowlan;
    }

    static synchronized boolean ownsCall(java.util.List<String> calls) {
        return vowlan && RemoteCallAudioScope.SHARED.owns(calls);
    }

    static synchronized void resetForTests() { vowlan = false; }
}

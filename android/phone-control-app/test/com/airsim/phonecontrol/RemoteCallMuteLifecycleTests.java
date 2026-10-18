package com.airsim.phonecontrol;

import android.telecom.Call;

/** Real InCallService, ownership registry and mute controller; only OS/Binder boundaries are doubled. */
public final class RemoteCallMuteLifecycleTests {
    private static int passed;
    public static void main(String[] args) throws Exception {
        dialingWaitsForAnswerBeforeReducingSamsungOutput();
        ordinaryCallsStayAudible();
        for (String mode : new String[]{"remote_silent", "local_and_push"}) {
            remoteLifecycle(mode);
        }
        ringingIsNotAnsweredOwnership();
        extraNativeCallRestoresOutput();
        disconnectingRestoresBeforeRemoval();
        destroyRestoresOriginalVolume();
        deadPCMRestoresOutput();
        queuedRefreshCannotRemuteAfterDestroy();
        minimumVolumeUsesAnsweredOwnershipAndRestoresOnMediaLoss();
        wireDecodedCallIdentityTriggersMinimumVolume();
        differentDecodedCallCannotLowerVolume();
        System.out.println("RemoteCallMuteLifecycleTests passed: " + passed + " scenarios");
    }

    /** VoWLAN PCM 完成握手：只有确认了具体通话 ID 的媒体租约才拥有远端音频。 */
    private static void ownVoWLAN(String callId) {
        RemoteAudioSessionRegistry.setVoWLAN(true);
        DirectCallOwnership.Lease lease = DirectCallOwnership.begin("fixture-host");
        DirectCallOwnership.record("fixture-host", callId, lease);
        DirectCallOwnership.pcmReady(lease);
        AirSIMInCallService.refreshAudioPolicy();
    }

    private static void releaseVoWLAN() {
        DirectCallOwnership.end(DirectCallOwnership.capture("fixture-host"), false);
        RemoteAudioSessionRegistry.setVoWLAN(false);
        AirSIMInCallService.refreshAudioPolicy();
    }

    private static void wireDecodedCallIdentityTriggersMinimumVolume() {
        ShizukuBridgeManager manager = ShizukuBridgeManager.INSTANCE;
        manager.output = new LocalOutputMuteController(manager.platform,
                LocalOutputMuteController.Strategy.MINIMUM_VOLUME);
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            // JSON/WebSocket parsing produces equal text, not the repository's String object.
            String decodedID = new String(f.id.toCharArray());
            ownVoWLAN(decodedID);
            check(f.audio.volume == 1 && manager.output.isVerifiedMinimum(),
                    "ready vowlan media with a wire-decoded native call ID must lower Samsung output");
            check(!f.audio.muted && manager.watchdog,
                    "minimum policy must start its watchdog without claiming a mute bit");
            releaseVoWLAN();
            check(f.audio.volume == 6 && !manager.output.isVerifiedMinimum(),
                    "releasing the exact media owner must restore the original volume");
        } finally { manager.output = new LocalOutputMuteController(manager.platform); }
        passed++;
    }

    private static void differentDecodedCallCannotLowerVolume() {
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            RemoteAudioSessionRegistry.setVoWLAN(true);
            DirectCallOwnership.Lease other = DirectCallOwnership.begin("fixture-host");
            DirectCallOwnership.record("fixture-host", f.id + "-other-call", other);
            DirectCallOwnership.pcmReady(other);
            AirSIMInCallService.refreshAudioPolicy();
            check(f.audio.volume == 6 && !f.audio.muted && !ShizukuBridgeManager.INSTANCE.watchdog,
                    "ready media belonging to a different call must not lower an ordinary call");
            DirectCallOwnership.end(other, false);
            AirSIMInCallService.refreshAudioPolicy();

            DirectCallOwnership.Lease owner = DirectCallOwnership.begin("fixture-host");
            DirectCallOwnership.record("fixture-host", new String(f.id.toCharArray()), owner);
            DirectCallOwnership.pcmReady(owner);
            AirSIMInCallService.refreshAudioPolicy();
            check(f.audio.muted, "the exact decoded call id must claim ownership");

            DirectCallOwnership.Lease bystander = DirectCallOwnership.begin("bystander-host");
            DirectCallOwnership.record("bystander-host", f.id + "-bystander", bystander);
            DirectCallOwnership.pcmReady(bystander);
            DirectCallOwnership.end(bystander, false);
            AirSIMInCallService.refreshAudioPolicy();
            check(f.audio.muted, "releasing a different owner must not revoke the actual owner's policy");

            DirectCallOwnership.end(owner, false);
            RemoteAudioSessionRegistry.setVoWLAN(false);
            AirSIMInCallService.refreshAudioPolicy();
            check(f.audio.volume == 6 && !f.audio.muted, "exact owner release must restore output");
        }
        passed++;
    }

    private static void minimumVolumeUsesAnsweredOwnershipAndRestoresOnMediaLoss() {
        ShizukuBridgeManager manager = ShizukuBridgeManager.INSTANCE;
        manager.output = new LocalOutputMuteController(manager.platform,
                LocalOutputMuteController.Strategy.MINIMUM_VOLUME);
        try (Fixture f = new Fixture(Call.STATE_DIALING)) {
            ownVoWLAN(f.id);
            check(f.audio.volume == 6 && !f.audio.muted, "dialing alone must preserve Samsung volume");
            f.call.setStateForTest(Call.STATE_ACTIVE);
            check(f.audio.volume == 1 && !f.audio.muted && manager.output.isVerifiedMinimum(),
                    "answered remote-owned call must lower the actual stream index, not claim mute");
            f.audio.volume = 3;
            releaseVoWLAN();
            check(f.audio.volume == 6 && !f.audio.muted && !manager.output.isVerifiedMinimum(),
                    "lost remote audio must restore the original index while carrier call remains active");
            check(f.call.getState() == Call.STATE_ACTIVE, "volume restoration must never disconnect the call");
        } finally { manager.output = new LocalOutputMuteController(manager.platform); }
        passed++;
    }

    private static void dialingWaitsForAnswerBeforeReducingSamsungOutput() {
        try (Fixture f = new Fixture(Call.STATE_DIALING)) {
            ownVoWLAN(f.id);
            check(!f.audio.muted && f.audio.volume == 6,
                    "prepared remote audio must not lower Samsung output before the call is answered");
            f.call.setStateForTest(Call.STATE_ACTIVE);
            check(f.audio.muted, "answered remote audio may apply the selected suppression strategy");
            releaseVoWLAN();
            check(!f.audio.muted && f.audio.volume == 6, "media loss must restore the original output");
        }
        passed++;
    }

    private static void ordinaryCallsStayAudible() {
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            check(!f.audio.muted, "default remote_silent must not silence an ordinary Android call without remote media");
            check(!ShizukuBridgeManager.INSTANCE.watchdog, "ordinary call must not start mute repair");
        }
        passed++;
    }

    private static void remoteLifecycle(String mode) {
        AppConfig.audioMode = mode;
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            check(!f.audio.muted, "pairing or native active state alone must not mute");
            RemoteAudioSessionRegistry.setVoWLAN(true);
            AirSIMInCallService.refreshAudioPolicy();
            check(!f.audio.muted, "VoWLAN control without PCM readiness must not mute");
            DirectCallOwnership.Lease lease = DirectCallOwnership.begin("fixture-host");
            DirectCallOwnership.record("fixture-host", f.id, lease);
            DirectCallOwnership.pcmReady(lease);
            AirSIMInCallService.refreshAudioPolicy();
            check(f.audio.muted && ShizukuBridgeManager.INSTANCE.watchdog,
                    "vowlan ready and owned call must mute using the Shizuku backend in " + mode);
            f.audio.volume = 2;
            DirectCallOwnership.end(lease, false);
            RemoteAudioSessionRegistry.setVoWLAN(false);
            AirSIMInCallService.refreshAudioPolicy();
            check(!f.audio.muted && f.audio.volume == 6,
                    "vowlan media loss must restore the original output while the carrier call is still active");
            check(!ShizukuBridgeManager.INSTANCE.watchdog, "media loss must stop repair");
            check(f.call.getState() == Call.STATE_ACTIVE, "audio policy must not hang up the carrier call");
        } finally { AppConfig.audioMode = "remote_silent"; }
        passed++;
    }

    private static void ringingIsNotAnsweredOwnership() {
        try (Fixture f = new Fixture(Call.STATE_RINGING)) {
            ownVoWLAN(f.id);
            check(!f.audio.muted, "preparing incoming remote PCM must not claim an unanswered call");
            f.call.answer(0);
            check(f.audio.muted, "answer state must activate remote output mute");
            f.call.disconnect();
            check(!f.audio.muted, "terminal call must restore output even before media teardown");
            releaseVoWLAN();
        }
        passed++;
    }

    private static void extraNativeCallRestoresOutput() {
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            ownVoWLAN(f.id);
            check(f.audio.muted, "remote call should initially mute");
            Call ordinary = new Call(Call.STATE_RINGING);
            f.service.onCallAdded(ordinary);
            check(!f.audio.muted, "global stream mute must not silence a second ordinary call");
            f.service.onCallRemoved(ordinary);
            check(f.audio.muted, "remaining unique remote call may resume mute");
            releaseVoWLAN();
        }
        passed++;
    }

    private static void disconnectingRestoresBeforeRemoval() {
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            ownVoWLAN(f.id);
            check(f.audio.muted, "remote call should initially mute");
            f.call.setStateForTest(Call.STATE_DISCONNECTING);
            check(!f.audio.muted, "disconnecting must never be interpreted as a fresh dialing request");
            releaseVoWLAN();
        }
        passed++;
    }

    private static void destroyRestoresOriginalVolume() {
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            ownVoWLAN(f.id);
            check(f.audio.muted, "remote call should initially mute");
            f.audio.volume = 1;
            f.destroy();
            check(!f.audio.muted && f.audio.volume == 6, "InCallService destruction must restore the original output");
            releaseVoWLAN();
        }
        passed++;
    }

    private static void deadPCMRestoresOutput() {
        // A confirmed PCM lease that ends (relay stop / TCP EOF) must revoke mute ownership
        // even while the control connection and carrier call remain alive.
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            ownVoWLAN(f.id);
            check(f.audio.muted, "live VoWLAN PCM must first mute output");
            DirectCallOwnership.end(DirectCallOwnership.capture("fixture-host"), false);
            AirSIMInCallService.refreshAudioPolicy();
            check(!f.audio.muted, "PCM loss must restore local output immediately");
            check(f.call.getState() == Call.STATE_ACTIVE, "PCM failure must not end the carrier call");
            RemoteAudioSessionRegistry.setVoWLAN(false);
        }
        passed++;
    }

    private static void queuedRefreshCannotRemuteAfterDestroy() {
        try (Fixture f = new Fixture(Call.STATE_ACTIVE)) {
            ownVoWLAN(f.id);
            check(f.audio.muted, "remote call should initially mute");
            android.os.Handler.deferForTest = true;
            try {
                AirSIMInCallService.refreshAudioPolicy();
                f.destroy();
                android.os.Handler.runPendingForTest();
                check(!f.audio.muted, "queued policy refresh must not re-mute after service destruction restored output");
            } finally {
                android.os.Handler.deferForTest = false;
                releaseVoWLAN();
            }
        }
        passed++;
    }

    private static final class Fixture implements AutoCloseable {
        final AirSIMInCallService service = new AirSIMInCallService();
        final ShizukuBridgeManager.Platform audio = ShizukuBridgeManager.INSTANCE.platform;
        final Call call;
        final String id;
        boolean destroyed;
        Fixture(int state) {
            RemoteAudioSessionRegistry.resetForTests();
            DirectCallOwnership.end(DirectCallOwnership.capture("fixture-host"), false);
            DirectCallOwnership.end(DirectCallOwnership.capture("bystander-host"), false);
            audio.muted = false; audio.volume = 6;
            call = new Call(state);
            service.onCallAdded(call);
            id = CallRepository.id(call);
        }
        public void close() {
            if (!destroyed) service.onCallRemoved(call);
            else CallRepository.remove(call);
            if (!destroyed) destroy();
            RemoteAudioSessionRegistry.resetForTests();
            DirectCallOwnership.end(DirectCallOwnership.capture("fixture-host"), false);
            DirectCallOwnership.end(DirectCallOwnership.capture("bystander-host"), false);
        }
        void destroy() { service.onDestroy(); destroyed = true; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

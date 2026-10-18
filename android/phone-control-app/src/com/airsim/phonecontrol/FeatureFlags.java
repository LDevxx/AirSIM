package com.airsim.phonecontrol;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

/**
 * Capability detection driven by the manifest, not by build-time constants.
 *
 * <p>The fork ships two Android Standalone variants from the same source tree:
 * the full build ({@code V0.9.7.2 LDev 911}) and the lightweight Lite build
 * ({@code AirSim-Lite V0.9.7.2 LDev 1}). The Lite manifest omits the VoBLE
 * gateway service and the Bluetooth permissions, so those features must be
 * reported as unavailable without any conditional compilation.
 *
 * <p>This mirrors {@link RuntimeMode}: the manifest is the single source of
 * truth for what a given APK can actually do.
 */
public final class FeatureFlags {
    private static final String VOBLE_SERVICE =
            "com.airsim.phonecontrol.voble.VoBLEGatewayService";
    private static final String CLOUD_RELAY_META_DATA =
            "com.airsim.phonecontrol.CLOUD_RELAY";

    private FeatureFlags() {}

    /** True when the manifest declares the VoBLE gateway service. */
    public static boolean voBLEAvailable(Context context) {
        if (context == null) return false;
        try {
            ServiceInfo[] services = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_SERVICES).services;
            if (services == null) return false;
            for (ServiceInfo service : services) {
                if (service != null && VOBLE_SERVICE.equals(service.name)) return true;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Fall through: an unresolvable package cannot expose the feature.
        }
        return false;
    }

    /**
     * True when the manifest opts into the Cloudflare Relay transport via
     * {@code com.airsim.phonecontrol.CLOUD_RELAY}. The Lite variant omits the
     * flag, so its embedded Agent never starts registration, heartbeat or
     * command polling.
     */
    public static boolean cloudRelayAvailable(Context context) {
        if (context == null) return false;
        try {
            android.content.pm.ApplicationInfo application = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            android.os.Bundle metadata = application.metaData;
            return metadata != null && metadata.getBoolean(CLOUD_RELAY_META_DATA, false);
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    /**
     * True when the manifest declares the VoBLE service AND the Bluetooth
     * runtime permissions, so the UI never offers a pairing flow that would
     * immediately fail its permission request.
     */
    public static boolean voBLEPairingAvailable(Context context) {
        if (!voBLEAvailable(context)) return false;
        String[] required = {
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_ADVERTISE",
        };
        for (String permission : required) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}

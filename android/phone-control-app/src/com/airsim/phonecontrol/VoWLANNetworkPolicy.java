package com.airsim.phonecontrol;

import java.net.InetAddress;

public final class VoWLANNetworkPolicy {
    private VoWLANNetworkPolicy() {}

    public static boolean isHotspotInterface(String name, String host) {
        if (name == null || host == null || !(name.startsWith("swlan") || name.startsWith("ap"))) return false;
        String[] fields = host.split("\\.");
        if (fields.length != 4) return false;
        int first;
        int second;
        try {
            first = Integer.parseInt(fields[0]);
            second = Integer.parseInt(fields[1]);
        } catch (NumberFormatException error) {
            return false;
        }
        return first == 10 || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    public static boolean isVoWLANInterface(String name, String host) {
        if (name == null || host == null) return false;
        if (!isPrivateIPv4(host) && !isTailscaleIPv4(host)) return false;
        return isHotspotInterface(name, host) || name.startsWith("wlan")
                || name.startsWith("tun") || name.startsWith("tailscale") || name.startsWith("wg");
    }

    public static boolean isPrivateIPv4(String host) {
        if (host == null) return false;
        String[] fields = host.split("\\.");
        if (fields.length != 4) return false;
        try {
            int first = Integer.parseInt(fields[0]);
            int second = Integer.parseInt(fields[1]);
            for (String field : fields) {
                int value = Integer.parseInt(field);
                if (value < 0 || value > 255) return false;
            }
            return first == 10 || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        } catch (NumberFormatException error) {
            return false;
        }
    }

    public static boolean isTailscaleIPv4(String host) {
        if (host == null) return false;
        String[] fields = host.split("\\.");
        if (fields.length != 4) return false;
        try {
            int first = Integer.parseInt(fields[0]);
            int second = Integer.parseInt(fields[1]);
            for (String field : fields) {
                int value = Integer.parseInt(field);
                if (value < 0 || value > 255) return false;
            }
            return first == 100 && second >= 64 && second <= 127;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    public static boolean isAllowedPeer(InetAddress address) {
        if (address == null) return false;
        if (address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()) {
            return true;
        }
        String host = address.getHostAddress();
        if (host != null) {
            if (host.startsWith("::ffff:")) {
                String ipv4 = host.substring(7);
                return isPrivateIPv4(ipv4) || isTailscaleIPv4(ipv4) || "127.0.0.1".equals(ipv4);
            }
            if (host.contains("%")) {
                host = host.substring(0, host.indexOf("%"));
            }
        }
        return isTailscaleIPv4(host) || isPrivateIPv4(host);
    }

    public static boolean shouldAdvertise(
            boolean hotspotReady, boolean paired, boolean agentReady, boolean pcmReady,
            boolean dialerRoleHeld) {
        return hotspotReady && paired && agentReady && pcmReady && dialerRoleHeld;
    }
}

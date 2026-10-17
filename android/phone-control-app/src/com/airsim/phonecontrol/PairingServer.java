package com.airsim.phonecontrol;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PairingServer implements Closeable {
    public interface Listener {
        void onStarted(String code, String address, String advertisedName, int pairingPort, int controlPort, int pcmPort, long expiresAtMillis);
        void onSucceeded(String clientDeviceName, String agentResponse);
        void onFailed(String message);
    }

    private static final int MAX_HEADERS = 8_192;
    private static final int MAX_BODY = 32_768;
    private final Context context;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private volatile ServerSocket socket;
    private volatile PairingSession session;
    private volatile NsdManager.RegistrationListener registration;

    public PairingServer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        executor.execute(() -> {
            try {
                PairingSession created = PairingSession.create(System.currentTimeMillis());
                ServerSocket server = new ServerSocket(0);
                server.setReuseAddress(true);
                socket = server;
                session = created;
                String advertisedName = advertise(server.getLocalPort(), created);
                String address = preferredPrivateIPv4() + ":" + server.getLocalPort();
                listener.onStarted(created.code(), address, advertisedName, server.getLocalPort(),
                        VoWLANGatewayService.activeControlPort(), VoWLANGatewayService.activePcmPort(),
                        created.expiresAtMillis());
                timer.schedule(() -> {
                    listener.onFailed("配对窗口已过期");
                    closeQuietly();
                }, PairingSession.LIFETIME_MILLIS, TimeUnit.MILLISECONDS);
                while (!server.isClosed() && System.currentTimeMillis() <= created.expiresAtMillis()) {
                    try (Socket peer = server.accept()) {
                        if (handle(peer, created)) break;
                    }
                }
            } catch (Exception error) {
                if (socket != null && !socket.isClosed()) {
                    BridgeLog.info("pairing_server_failed reason=" + safeMessage(error));
                    listener.onFailed(safeMessage(error));
                }
            } finally {
                closeQuietly();
            }
        });
    }

    private boolean handle(Socket peer, PairingSession created) throws Exception {
        peer.setSoTimeout(8_000);
        InetAddress remote = peer.getInetAddress();
        if (!VoWLANNetworkPolicy.isAllowedPeer(remote)) {
            BridgeLog.info("pairing_claim_rejected reason=non_private_peer");
            respond(peer, 403, "{\"error\":\"private_network_required\"}");
            return false;
        }
        BufferedInputStream input = new BufferedInputStream(peer.getInputStream());
        byte[] headers = readHeaders(input);
        String headerText = new String(headers, StandardCharsets.US_ASCII);
        String[] lines = headerText.split("\\r\\n");
        if (lines.length > 0 && lines[0].startsWith("GET /pair/v1/info")) {
            JSONObject info = new JSONObject()
                    .put("version", 1)
                    .put("session_id", created.id())
                    .put("server_public_key", PairingCrypto.base64URL(created.publicKey()))
                    .put("expires", created.expiresAtMillis())
                    .put("device_name", AndroidAgentIdentity.deviceName())
                    .put("control_port", VoWLANGatewayService.activeControlPort())
                    .put("pcm_port", VoWLANGatewayService.activePcmPort());
            respond(peer, 200, info.toString());
            return false;
        }
        if (lines.length == 0 || !"POST /pair/v1/claim HTTP/1.1".equals(lines[0])) {
            BridgeLog.info("pairing_claim_rejected reason=route");
            respond(peer, 404, "{\"error\":\"not_found\"}");
            return false;
        }
        int length = contentLength(lines);
        byte[] body = readExactly(input, length);
        JSONObject claim = new JSONObject(new String(body, StandardCharsets.UTF_8));
        if (claim.optInt("version") != 1 || !created.id().equals(claim.optString("session_id"))) {
            BridgeLog.info("pairing_claim_rejected reason=session");
            respond(peer, 400, "{\"error\":\"invalid_session\"}");
            return false;
        }
        byte[] plaintext;
        try {
            plaintext = created.claim(
                    PairingCrypto.decodeBase64URL(claim.getString("client_public_key")),
                    PairingCrypto.decodeBase64URL(claim.getString("sealed_registration")),
                    System.currentTimeMillis());
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            BridgeLog.info("pairing_claim_rejected reason=authentication");
            respond(peer, 401, "{\"error\":\"pairing_auth_failed\"}");
            return false;
        }
        try {
            String registrationJSON = new String(plaintext, StandardCharsets.UTF_8);
            JSONObject registration = new JSONObject(registrationJSON);
            String vowlanSecret = registration.optString("vowlan_secret", "");
            if (vowlanSecret.isEmpty()) throw new GeneralSecurityException("VoWLAN secret missing");
            String clientDeviceName = registration.optString("client_name", "");
            if (clientDeviceName.isEmpty()) clientDeviceName = registration.optString("device_id", "客户端");
            if (!registration.has("local_mode") && registration.optString("relay_url", "").isEmpty()) {
                registration.put("local_mode", true);
            }
            registration.put("android_agent_id", AndroidAgentIdentity.agentID(context));
            registration.put("android_device_name", AndroidAgentIdentity.deviceName());
            registration.put("android_manufacturer", android.os.Build.MANUFACTURER);
            registration.put("android_model", android.os.Build.MODEL);
            registration.put("android_phone_number", AndroidAgentIdentity.phoneNumber(context));
            registration.put("android_agent_kind", RuntimeMode.isStandalone(context) ? "standalone" : "avf");
            // VoWLAN HMAC 密钥只属于 Android 热点网关。Linux Agent 的 Push
            // 注册结构使用严格 JSON 解码，且无须接触该密钥，因此转发前必须移除。
            registration.remove("vowlan_secret");
            String agentResponse;
            try {
                agentResponse = new AgentClient(context).registerPairing(registration.toString());
            } catch (Exception forwardError) {
                BridgeLog.info("agent_pairing_registration_fallback: " + forwardError.getMessage());
                agentResponse = "{\"configured\":true}";
            }
            agentResponse = new JSONObject(agentResponse)
                    .put("agent_id", AndroidAgentIdentity.agentID(context))
                    .put("android_device_name", AndroidAgentIdentity.deviceName())
                    .put("client_device_name", clientDeviceName)
                    .put("device_id_hint", clientDeviceName)
                    .put("control_port", VoWLANGatewayService.activeControlPort())
                    .put("pcm_port", VoWLANGatewayService.activePcmPort())
                    .put("protocol_version", "2.0")
                    .put("supported_protocols", new org.json.JSONArray(java.util.List.of("1.0", "2.0")))
                    .put("slots", SIMSlotManager.getSlotsJSON(context))
                    .put("active_slots_count", SIMSlotManager.getActiveSlots(context).size())
                    .toString();
            VoWLANPairingStore.saveEncoded(context, vowlanSecret);
            VoWLANGatewayService.start(context);
            respond(peer, 200, agentResponse);
            listener.onSucceeded(clientDeviceName, agentResponse);
            return true;
        } finally {
            java.util.Arrays.fill(plaintext, (byte) 0);
        }
    }

    private String advertise(int port, PairingSession created) {
        NsdManager manager = context.getSystemService(NsdManager.class);
        NsdServiceInfo info = new NsdServiceInfo();
        String devName = AndroidAgentIdentity.deviceName();
        String cleanDevName = devName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "").trim();
        if (cleanDevName.isEmpty()) cleanDevName = "Pixel";
        if (cleanDevName.length() > 24) cleanDevName = cleanDevName.substring(0, 24);
        String advertisedName = "AirSIM-" + cleanDevName + "-" + created.id().substring(0, 4);

        info.setServiceName(advertisedName);
        info.setServiceType("_airsim-pair._tcp.");
        info.setPort(port);
        info.setAttribute("v", "1");
        info.setAttribute("proto", "2.0");
        info.setAttribute("session", created.id());
        info.setAttribute("key", PairingCrypto.base64URL(created.publicKey()));
        info.setAttribute("expires", Long.toString(created.expiresAtMillis()));
        info.setAttribute("device_name", devName);
        info.setAttribute("control_port", Integer.toString(VoWLANGatewayService.activeControlPort()));
        info.setAttribute("pcm_port", Integer.toString(VoWLANGatewayService.activePcmPort()));
        try {
            info.setAttribute("host", preferredPrivateIPv4());
        } catch (Exception ignored) {}
        info.setAttribute("port", Integer.toString(port));
        registration = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo ignored) {}
            @Override public void onRegistrationFailed(NsdServiceInfo ignored, int code) {
                BridgeLog.info("pairing_advertisement_failed code=" + code);
                listener.onFailed("热点发现发布失败（" + code + "）");
            }
            @Override public void onServiceUnregistered(NsdServiceInfo ignored) {}
            @Override public void onUnregistrationFailed(NsdServiceInfo ignored, int code) {}
        };
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration);
        return advertisedName;
    }

    private static byte[] readHeaders(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int state = 0;
        while (output.size() < MAX_HEADERS) {
            int value = input.read();
            if (value < 0) throw new IOException("请求头不完整");
            output.write(value);
            state = (state == 0 && value == '\r') ? 1
                    : (state == 1 && value == '\n') ? 2
                    : (state == 2 && value == '\r') ? 3
                    : (state == 3 && value == '\n') ? 4 : 0;
            if (state == 4) return output.toByteArray();
        }
        throw new IOException("请求头过大");
    }

    private static int contentLength(String[] lines) throws IOException {
        for (String line : lines) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                int value;
                try { value = Integer.parseInt(line.substring(15).trim()); }
                catch (NumberFormatException error) { throw new IOException("Content-Length 无效", error); }
                if (value < 1 || value > MAX_BODY) throw new IOException("请求体大小无效");
                return value;
            }
        }
        throw new IOException("缺少 Content-Length");
    }

    private static byte[] readExactly(BufferedInputStream input, int length) throws IOException {
        byte[] output = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(output, offset, length - offset);
            if (count < 0) throw new IOException("请求体不完整");
            offset += count;
        }
        return output;
    }

    private static void respond(Socket peer, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : "Error";
        byte[] headers = ("HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: application/json\r\n" +
                "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        OutputStream output = peer.getOutputStream();
        output.write(headers);
        output.write(body);
        output.flush();
    }

    static String preferredPrivateIPv4() throws IOException {
        List<NetworkInterface> interfaces = new ArrayList<>();
        Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
        while (enumeration.hasMoreElements()) interfaces.add(enumeration.nextElement());
        interfaces.sort(Comparator.comparingInt(item -> interfacePriority(item.getName())));
        for (NetworkInterface item : interfaces) {
            if (!item.isUp() || item.isLoopback()) continue;
            Enumeration<InetAddress> addresses = item.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                String host = address.getHostAddress();
                if (address instanceof Inet4Address && VoWLANNetworkPolicy.isAllowedPeer(address) && !host.startsWith("10.185.5.")) return host;
            }
        }
        throw new IOException("未找到 VoWLAN IPv4 地址，请连接 Wi-Fi 或开启移动热点");
    }

    private static int interfacePriority(String name) {
        if (name.startsWith("swlan") || name.startsWith("ap")) return 0;
        if (name.startsWith("wlan")) return 1;
        if (name.startsWith("tun") || name.startsWith("tailscale") || name.startsWith("wg")) return 2;
        return 3;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private void closeQuietly() {
        try { close(); } catch (IOException ignored) {}
    }

    @Override public void close() throws IOException {
        NsdManager.RegistrationListener active = registration;
        registration = null;
        if (active != null) {
            try { context.getSystemService(NsdManager.class).unregisterService(active); }
            catch (IllegalArgumentException ignored) {}
        }
        ServerSocket activeSocket = socket;
        if (activeSocket != null && !activeSocket.isClosed()) activeSocket.close();
        timer.shutdownNow();
        executor.shutdown();
    }
}

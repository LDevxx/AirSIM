package com.airsim.phonecontrol;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AVF-free foreground Agent for AirSim-Lite: keeps the local VoWLAN gateway and the
 * optional SMS-inbox scan alive. 云端 Relay 已在 Lite 版移除，因此这里不再有任何
 * 注册 / 心跳 / 命令通道循环。
 */
public final class StandaloneAgentService extends Service {
    private static volatile StandaloneAgentService instance;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final NotificationUpdateGate notificationGate = new NotificationUpdateGate();
    private volatile boolean running;
    private StandaloneAgentStore store;

    public static void start(Context context) {
        try { context.startForegroundService(new Intent(context, StandaloneAgentService.class)); }
        catch (RuntimeException error) { BridgeLog.error("standalone_agent_start_failed", error); }
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        store = new StandaloneAgentStore(this);
        startForeground(BridgeNotification.WATCHDOG_ID,
                BridgeNotification.status(this, "内置 Agent 正在启动"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        running = true;
        ShizukuBridgeManager.get(this).ensureStarted();
        VoWLANGatewayService.start(this);
        worker.execute(this::loop);
        BridgeLog.info("standalone_agent_created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void loop() {
        while (running) {
            try {
                SMSInboxScanner.scanIfDue(this);
                update("内置 Agent 在线 · 等待 AirChat 配对");
                Thread.sleep(2_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                store.recordError(error.getClass().getSimpleName() + ": " + error.getMessage());
                BridgeLog.error("standalone_agent_cycle_failed", error);
                try { Thread.sleep(2_000); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private void update(String detail) {
        long now = System.currentTimeMillis();
        if (!notificationGate.shouldPublish(detail, now)) return;
        getSystemService(android.app.NotificationManager.class).notify(
                BridgeNotification.WATCHDOG_ID, BridgeNotification.status(this, detail));
        notificationGate.published(detail, now);
    }

    /** 清除配对后刷新常驻通知文案；Lite 版没有需要断开的云端连接。 */
    public static void resetPairing(Context context) {
        StandaloneAgentService active = instance;
        if (active != null) active.update("内置 Agent 在线 · 等待 AirChat 配对");
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        running = false;
        worker.shutdownNow();
        BridgeLog.info("standalone_agent_destroyed");
        super.onDestroy();
    }
}

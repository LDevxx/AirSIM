package com.airsim.phonecontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import org.json.JSONObject;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Receives carrier SMS without requiring the default-SMS-app role. */
public final class IncomingSMSReceiver extends BroadcastReceiver {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        Context application = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (parts == null || parts.length == 0) return;
                StringBuilder content = new StringBuilder();
                String sender = parts[0].getOriginatingAddress();
                long timestamp = parts[0].getTimestampMillis();
                for (SmsMessage part : parts) {
                    if (part == null) continue;
                    content.append(part.getMessageBody() == null ? "" : part.getMessageBody());
                }
                if (content.length() == 0) return;
                long eventTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
                String id = SMSDeliveryIdentity.id(sender, content.toString(), eventTimestamp);

                int subId = intent.getIntExtra("subscription",
                        intent.getIntExtra(android.telephony.SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)));
                int slotId = intent.getIntExtra("slot",
                        intent.getIntExtra("simId",
                        intent.getIntExtra("slot_id", -1)));

                SIMSlotInfo slot = SIMSlotManager.slotForSMS(application, subId, slotId);
                int finalSlotId = slot != null ? slot.slotIndex() : slotId;
                int finalSubId = slot != null ? slot.subscriptionId() : subId;
                String carrier = slot != null ? slot.carrierName() : "";
                String recipient = slot != null ? slot.number() : "";

                JSONObject event = new JSONObject(WireJson.smsEvent(UUID.randomUUID().toString(),
                        id, sender == null ? "" : sender, content.toString(),
                        Instant.ofEpochMilli(eventTimestamp).toString(),
                        finalSlotId, finalSubId, carrier, recipient));
                if (RuntimeMode.isStandalone(application)) {
                    StandaloneAgentGateway.get(application).sendSMSEvent(event.toString());
                } else {
                    new StandaloneSMSStore(application).add(event, true);
                    AgentWatchdogService.start(application);
                }
                BridgeLog.info("incoming_sms_queued delivery_id=" + DebugRedactor.safeIdentifier(id)
                        + (finalSlotId >= 0 ? " slot=" + finalSlotId : ""));
            } catch (Exception error) {
                BridgeLog.error("incoming_sms_queue_failed", error);
            } finally {
                pending.finish();
            }
        });
    }
}

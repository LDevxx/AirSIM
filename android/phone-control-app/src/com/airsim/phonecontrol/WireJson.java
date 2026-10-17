package com.airsim.phonecontrol;

public final class WireJson {
    private WireJson() {}

    public static String callEvent(String eventId, String callId, String direction, String state, String number, String mode) {
        return callEvent(eventId, callId, direction, state, number, mode, -1, -1, "", "");
    }

    public static String callEvent(String eventId, String callId, String direction, String state, String number, String mode,
                                   int slotId, int subId, String carrier, String targetNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("{")
                .append(field("event_id", eventId)).append(",")
                .append(field("call_id", callId)).append(",")
                .append(field("direction", direction)).append(",")
                .append(field("state", state)).append(",")
                .append(field("number", number)).append(",")
                .append(field("mode", mode));
        if (slotId >= 0) {
            sb.append(",\"slot_id\":").append(slotId);
        }
        if (subId >= 0) {
            sb.append(",\"sub_id\":").append(subId);
        }
        if (carrier != null && carrier.length() > 0) {
            sb.append(",").append(field("carrier", carrier));
        }
        if (targetNumber != null && targetNumber.length() > 0) {
            sb.append(",").append(field("target_number", targetNumber));
        }
        sb.append("}");
        return sb.toString();
    }

    public static String commandResult(String id, boolean success, String error) {
        return "{" + field("id", id) + ",\"success\":" + success + "," + field("error", error) + "}";
    }

	public static String commandResult(String id, boolean success, String error, int segments) {
		return "{" + field("id", id) + ",\"success\":" + success + "," + field("error", error)
				+ ",\"segments\":" + Math.max(0, segments) + "}";
	}

	public static String smsEvent(String eventId, String deliveryId, String sender, String content, String timestamp) {
        return smsEvent(eventId, deliveryId, sender, content, timestamp, -1, -1, "", "");
    }

	public static String smsEvent(String eventId, String deliveryId, String sender, String content, String timestamp,
                                  int slotId, int subId, String carrier, String recipient) {
		String displaySender = sender == null || sender.isBlank() ? "未知号码" : sender;
		StringBuilder sb = new StringBuilder();
		sb.append("{")
				.append(field("event_id", eventId)).append(",")
				.append(field("delivery_id", deliveryId)).append(",")
				.append(field("sender", displaySender)).append(",")
				.append(field("content", content)).append(",")
				.append(field("timestamp", timestamp));
		if (slotId >= 0) {
			sb.append(",\"slot_id\":").append(slotId);
		}
		if (subId >= 0) {
			sb.append(",\"sub_id\":").append(subId);
		}
		if (carrier != null && carrier.length() > 0) {
			sb.append(",").append(field("carrier", carrier));
		}
		if (recipient != null && recipient.length() > 0) {
			sb.append(",").append(field("recipient", recipient));
		}
		sb.append("}");
		return sb.toString();
	}

    private static String field(String key, String value) {
        return "\"" + key + "\":\"" + escape(value == null ? "" : value) + "\"";
    }

    static String escape(String value) {
        StringBuilder output = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> output.append("\\\\");
                case '"' -> output.append("\\\"");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) output.append(String.format("\\u%04x", (int) character));
                    else output.append(character);
                }
            }
        }
        return output.toString();
    }
}

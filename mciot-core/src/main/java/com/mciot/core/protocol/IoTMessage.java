package com.mciot.core.protocol;

/**
 * 双向通用物联网数据包 (ESP32 <-> Minecraft)
 * 无论上行（传感器/按钮上报）还是下行（MC 天气/红石/方块事件下发），均使用此结构。
 */
public final class IoTMessage {
    private final String device;
    private final String channel;
    private final String event;
    private final int value;
    private final String data;

    public IoTMessage(String device, String channel, String event, int value, String data) {
        this.device = device == null ? "unknown" : device;
        this.channel = channel == null ? "" : channel;
        this.event = event == null ? "" : event;
        this.value = value;
        this.data = data == null ? "" : data;
    }

    public String device() { return device; }
    public String channel() { return channel; }
    public String event() { return event; }
    public int value() { return value; }
    public String data() { return data; }

    public String toJson() {
        return "{\"device\":\"" + escape(device) + "\","
             + "\"channel\":\"" + escape(channel) + "\","
             + "\"event\":\"" + escape(event) + "\","
             + "\"value\":" + value + ","
             + "\"data\":\"" + escape(data) + "\"}";
    }

    public static IoTMessage fromJson(String json) {
        if (json == null || json.isEmpty()) return null;
        String channel = extract(json, "channel");
        if (channel.isEmpty()) return null;
        String device = extract(json, "device");
        String event = extract(json, "event");
        String valStr = extract(json, "value");
        String data = extract(json, "data");
        int value = 0;
        try {
            if (!valStr.isEmpty()) value = Integer.parseInt(valStr.trim());
        } catch (NumberFormatException ignored) {}
        return new IoTMessage(device, channel, event, value, data);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String extract(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return "";
        idx += pattern.length();
        while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == '\t')) idx++;
        if (idx >= json.length()) return "";

        if (json.charAt(idx) == '"') {
            idx++;
            StringBuilder sb = new StringBuilder();
            while (idx < json.length() && json.charAt(idx) != '"') {
                if (json.charAt(idx) == '\\' && idx + 1 < json.length()) idx++;
                sb.append(json.charAt(idx++));
            }
            return sb.toString();
        } else {
            int end = idx;
            while (end < json.length() && ",} \r\n\t".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(idx, end);
        }
    }

    @Override
    public String toString() {
        return "IoTMessage[device=" + device + ", channel=" + channel + ", event=" + event + ", value=" + value + ", data=" + data + "]";
    }
}

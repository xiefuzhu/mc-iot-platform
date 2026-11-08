#pragma once
#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#include <esp_http_server.h>
#include <lwip/sockets.h>
#include <fcntl.h>
#include <errno.h>
#include <vector>

// ============================================================================
// 1. 统一双向消息结构体 (ESP32 <-> Minecraft)
// ============================================================================
struct IoTMsg {
  String device;   // 设备 ID (例如 "esp32-s3-01")
  String channel;  // 逻辑通道 (例如 "btn_boot", "knob_1", "weather")
  String event;    // 事件名称 (例如 "toggle", "press", "change", "thunder")
  int value;       // 整型数值 (例如 0~15 红石信号强度, 0/1 开关, 传感器数值)
  String data;     // 附加数据 (例如 "#FF00FF" 颜色代码或屏幕显示文本)
};

class McIoTNode;

// ============================================================================
// 2. 极简插件基类：后期扩展任何传感器/执行器只需继承此类
// ============================================================================
class McIoTPlugin {
public:
  virtual ~McIoTPlugin() {}

  // 插件唯一名称
  virtual const char* name() = 0;

  // 开机初始化时调用一次（例如配置 pinMode）
  virtual void setup(McIoTNode& node) {}

  // 主循环高频调用（例如检测按钮、读取传感器并通过 node.send() 发送给 MC）
  virtual void loop(McIoTNode& node) {}

  // 【反向接收能力】当收到来自 Minecraft 的事件时自动触发
  // 例如：MC 打雷 (channel="weather", event="thunder") 或 MC 方块被右键/红石激活
  virtual void onMessage(McIoTNode& node, const IoTMsg& msg) {}
};

// ============================================================================
// 3. 核心通讯与插件管理引擎 (自带零依赖 WebSocket 客户端 + 本地 Web 调试台)
// ============================================================================
class McIoTNode {
public:
  McIoTNode(const char* deviceId, const char* mcHost, uint16_t mcPort, const char* mcPath = "/ws")
      : _deviceId(deviceId), _mcHost(mcHost), _mcPort(mcPort), _mcPath(mcPath) {}

  // 注册插件
  void addPlugin(McIoTPlugin* plugin) {
    if (plugin) _plugins.push_back(plugin);
  }

  // 动态修改 MC 服务器地址（支持局域网 IP 或内网穿透域名）
  void setServer(const String& host, uint16_t port, const String& path = "/ws") {
    _mcHost = host;
    _mcPort = port;
    _mcPath = path;
    disconnectWs();
    _lastReconnectAttempt = 0; // 立即触发重连
  }

  String getDeviceId() const { return _deviceId; }
  String getServerHost() const { return _mcHost; }
  uint16_t getServerPort() const { return _mcPort; }
  bool isConnected() const { return _wsConnected; }
  String getLastLog() const { return _lastLog; }

  // 初始化所有插件与本地调试面板
  void begin() {
    _prefs.begin("mciot", false);
    _cachedHost = _prefs.getString("last_ip", "");
    if ((_mcHost.length() == 0 || _mcHost == "auto" || _mcHost == "192.168.1.100") && _cachedHost.length() > 0) {
      _mcHost = _cachedHost;
      Serial.printf("[McIoT] Loaded last known MC server IP from NVS: %s\n", _mcHost.c_str());
    }
    for (auto* plugin : _plugins) {
      plugin->setup(*this);
      Serial.printf("[McIoT] Plugin initialized: %s\n", plugin->name());
    }
  }

  // 主循环调度
  void loop() {
    const unsigned long now = millis();

    // 1. 维护 Wi-Fi 与本地调试 Web 服务
    if (WiFi.status() == WL_CONNECTED) {
      if (!_httpServer) startLocalWebPanel();

      // 2. 维护与 Minecraft 模组的 WebSocket 双向长连接
      if (!_wsConnected) {
        if (now - _lastReconnectAttempt >= 3000) {
          _lastReconnectAttempt = now;
          if (!connectWs()) {
            // 当前 IP 无法连接时，自动在局域网搜索开启了 25580 端口的 Minecraft 电脑
            String foundIp = discoverLanServer();
            if (foundIp.length() > 0) {
              _mcHost = foundIp;
              connectWs();
            }
          }
        }
      } else {
        pollWs();
        // 定时发送心跳保活 (每 10 秒)
        if (now - _lastPingTime >= 10000) {
          _lastPingTime = now;
          sendRawWsFrame("{\"type\":\"ping\"}");
        }
      }
    } else {
      if (_wsConnected) disconnectWs();
    }

    // 3. 运行所有挂载的硬件插件
    pollPlugins();
  }

  // 【核心上行接口】向 Minecraft 发送事件（任何插件一行代码即可调用）
  bool send(const String& channel, const String& event, int value = 0, const String& data = "") {
    String json = "{\"device\":\"" + escapeJson(_deviceId) + "\","
                  "\"channel\":\"" + escapeJson(channel) + "\","
                  "\"event\":\"" + escapeJson(event) + "\","
                  "\"value\":" + String(value) + ","
                  "\"data\":\"" + escapeJson(data) + "\"}";

    _lastLog = "TX -> [" + channel + "] " + event + " = " + String(value);
    Serial.println("[McIoT] " + _lastLog);

    if (_wsConnected) {
      return sendRawWsFrame(json);
    } else if (WiFi.status() == WL_CONNECTED && _mcHost.length() > 0) {
      // 如果 WebSocket 暂未连上，自动尝试通过 HTTP POST 保底发送
      return sendHttpPostFallback(json);
    }
    return false;
  }

  // 手动注入一条下行消息（用于本地网页调试台直接模拟 MC 下发雷暴/红石事件）
  void dispatchIncoming(const IoTMsg& msg) {
    _lastLog = "RX <- [" + msg.channel + "] " + msg.event + " (" + String(msg.value) + ")";
    Serial.println("[McIoT] " + _lastLog);
    for (auto* plugin : _plugins) {
      plugin->onMessage(*this, msg);
    }
  }

private:
  String _deviceId;
  String _mcHost;
  String _cachedHost;
  uint16_t _mcPort;
  String _mcPath;

  WiFiClient _wsClient;
  WiFiUDP _udp;
  Preferences _prefs;
  bool _wsConnected = false;
  unsigned long _lastReconnectAttempt = 0;
  unsigned long _lastPingTime = 0;
  String _lastLog = "Ready";

  std::vector<McIoTPlugin*> _plugins;
  httpd_handle_t _httpServer = NULL;

  void pollPlugins() {
    for (auto* plugin : _plugins) {
      plugin->loop(*this);
    }
  }

  static String escapeJson(const String& s) {
    String out;
    out.reserve(s.length());
    for (size_t i = 0; i < s.length(); ++i) {
      char c = s[i];
      if (c == '\"') out += "\\\"";
      else if (c == '\\') out += "\\\\";
      else if (c == '\n') out += "\\n";
      else if (c == '\r') out += "\\r";
      else out += c;
    }
    return out;
  }

  // 极简 JSON 字段提取器（免第三方 ArduinoJson 库依赖）
  static String extractJsonString(const String& json, const char* key) {
    String pattern = String("\"") + key + "\":";
    int idx = json.indexOf(pattern);
    if (idx < 0) return "";
    idx += pattern.length();
    while (idx < (int)json.length() && (json[idx] == ' ' || json[idx] == '\t')) idx++;
    if (idx >= (int)json.length()) return "";

    if (json[idx] == '\"') {
      idx++;
      String out;
      while (idx < (int)json.length() && json[idx] != '\"') {
        if (json[idx] == '\\' && idx + 1 < (int)json.length()) {
          idx++;
        }
        out += json[idx++];
      }
      return out;
    } else {
      int end = idx;
      while (end < (int)json.length() && json[end] != ',' && json[end] != '}' && json[end] != ' ') {
        end++;
      }
      return json.substring(idx, end);
    }
  }

  // 建立原生 RFC6455 WebSocket 握手
  bool connectWs() {
    if (_mcHost.length() == 0 || _mcHost == "0.0.0.0" || _mcHost == "auto") return false;
    Serial.printf("[McIoT] Connecting to MC Server ws://%s:%u%s ...\n",
                  _mcHost.c_str(), _mcPort, _mcPath.c_str());

    _wsClient.stop();
    if (!_wsClient.connect(_mcHost.c_str(), _mcPort, 800)) {
      return false;
    }
    _wsClient.setNoDelay(true); // 开启 TCP_NODELAY 降低延迟

    String handshake =
        "GET " + _mcPath + " HTTP/1.1\r\n"
        "Host: " + _mcHost + ":" + String(_mcPort) + "\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "X-Device-Id: " + _deviceId + "\r\n\r\n";

    _wsClient.print(handshake);

    unsigned long start = millis();
    while (_wsClient.connected() && !_wsClient.available()) {
      if (millis() - start > 1500) {
        _wsClient.stop();
        return false;
      }
      delay(5);
    }

    String statusLine = _wsClient.readStringUntil('\n');
    if (statusLine.indexOf("101") < 0) {
      _wsClient.stop();
      return false;
    }

    // 跳过剩余 HTTP 响应头
    while (_wsClient.connected()) {
      String line = _wsClient.readStringUntil('\n');
      if (line == "\r" || line.length() == 0) break;
    }

    _wsConnected = true;
    _lastPingTime = millis();
    if (_cachedHost != _mcHost) {
      _cachedHost = _mcHost;
      _prefs.putString("last_ip", _mcHost);
    }
    _lastLog = "Connected to MC (" + _mcHost + ":" + String(_mcPort) + ")";
    Serial.printf("[McIoT] WebSocket connected to %s:%u\n", _mcHost.c_str(), _mcPort);

    // 发送设备上线注册包
    send("system", "hello", 1, _deviceId);
    return true;
  }

  // 自动在局域网内搜索运行了 MC-IoT 模组的电脑 (UDP 广播优先 + TCP 子网并发探测兜底)
  String discoverLanServer() {
    if (WiFi.status() != WL_CONNECTED) return "";
    _lastLog = "Auto-discovering MC Server on LAN...";
    Serial.printf("[McIoT] Auto-discovering MC Server on LAN (port %u)...\n", _mcPort);

    // 1. 尝试 UDP 广播毫秒级发现
    String found = discoverViaUdp();
    if (found.length() > 0) {
      Serial.printf("[McIoT] Discovered MC Server via UDP: %s\n", found.c_str());
      return found;
    }

    // 2. 尝试局域网同网段非阻塞并发 TCP 端口扫描 (即使路由器隔离广播或使用旧版模组也能秒级发现)
    found = discoverViaTcpScan();
    if (found.length() > 0) {
      Serial.printf("[McIoT] Discovered MC Server via TCP subnet scan: %s\n", found.c_str());
      return found;
    }

    Serial.println("[McIoT] No active MC Server found on LAN yet, will retry...");
    return "";
  }

  String discoverViaUdp() {
    IPAddress localIp = WiFi.localIP();
    IPAddress mask = WiFi.subnetMask();
    IPAddress subnetBroadcast((uint32_t)localIp | (~(uint32_t)mask));
    IPAddress globalBroadcast(255, 255, 255, 255);

    _udp.begin(0);
    const char* msg = "MCIOT_DISCOVER";

    _udp.beginPacket(subnetBroadcast, _mcPort);
    _udp.write((const uint8_t*)msg, strlen(msg));
    _udp.endPacket();

    _udp.beginPacket(globalBroadcast, _mcPort);
    _udp.write((const uint8_t*)msg, strlen(msg));
    _udp.endPacket();

    unsigned long start = millis();
    while (millis() - start < 220) {
      int pktSize = _udp.parsePacket();
      if (pktSize > 0) {
        char buf[64] = {0};
        int len = _udp.read(buf, sizeof(buf) - 1);
        if (len > 0 && String(buf).startsWith("MCIOT_HERE")) {
          String ip = _udp.remoteIP().toString();
          _udp.stop();
          return ip;
        }
      }
      pollPlugins();
      delay(5);
    }
    _udp.stop();
    return "";
  }

  String discoverViaTcpScan() {
    IPAddress localIp = WiFi.localIP();
    if (localIp[0] == 0) return "";

    const int batchSize = 16;
    int socks[batchSize];
    int hostIds[batchSize];

    for (int base = 1; base <= 254; base += batchSize) {
      int count = 0;
      for (int i = 0; i < batchSize && (base + i) <= 254; ++i) {
        int lastOctet = base + i;
        if (lastOctet == localIp[3]) continue; // 跳过本机 IP

        int s = lwip_socket(AF_INET, SOCK_STREAM, 0);
        if (s < 0) break;

        int flags = lwip_fcntl(s, F_GETFL, 0);
        lwip_fcntl(s, F_SETFL, flags | O_NONBLOCK);

        struct sockaddr_in addr;
        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_port = htons(_mcPort);
        IPAddress targetIp(localIp[0], localIp[1], localIp[2], lastOctet);
        addr.sin_addr.s_addr = (uint32_t)targetIp;

        lwip_connect(s, (struct sockaddr*)&addr, sizeof(addr));
        socks[count] = s;
        hostIds[count] = lastOctet;
        count++;
      }

      if (count == 0) continue;

      fd_set writeSet;
      FD_ZERO(&writeSet);
      int maxFd = -1;
      for (int i = 0; i < count; ++i) {
        FD_SET(socks[i], &writeSet);
        if (socks[i] > maxFd) maxFd = socks[i];
      }

      struct timeval tv;
      tv.tv_sec = 0;
      tv.tv_usec = 75000; // 每批 16 个 IP 仅等待 75ms，全网段扫描约 1.1 秒

      String foundIp = "";
      int ready = lwip_select(maxFd + 1, NULL, &writeSet, NULL, &tv);
      if (ready > 0) {
        for (int i = 0; i < count; ++i) {
          if (FD_ISSET(socks[i], &writeSet)) {
            int err = 0;
            socklen_t errLen = sizeof(err);
            if (lwip_getsockopt(socks[i], SOL_SOCKET, SO_ERROR, &err, &errLen) == 0 && err == 0) {
              IPAddress hit(localIp[0], localIp[1], localIp[2], hostIds[i]);
              foundIp = hit.toString();
              break;
            }
          }
        }
      }

      for (int i = 0; i < count; ++i) {
        lwip_close(socks[i]);
      }

      pollPlugins(); // 保持按钮与灯光在扫描期间零卡顿响应
      if (foundIp.length() > 0) {
        return foundIp;
      }
    }
    return "";
  }

  void disconnectWs() {
    _wsConnected = false;
    _wsClient.stop();
  }

  // 发送标准掩码 WebSocket 文本帧 (Client -> Server 必须带 Mask)
  bool sendRawWsFrame(const String& payload) {
    if (!_wsConnected || !_wsClient.connected()) {
      disconnectWs();
      return false;
    }
    size_t len = payload.length();
    uint8_t header[10];
    size_t headLen = 0;

    header[0] = 0x81; // FIN + Text Frame
    if (len <= 125) {
      header[1] = 0x80 | (uint8_t)len;
      headLen = 2;
    } else if (len <= 65535) {
      header[1] = 0x80 | 126;
      header[2] = (len >> 8) & 0xFF;
      header[3] = len & 0xFF;
      headLen = 4;
    } else {
      return false;
    }

    const uint8_t mask[4] = {0x12, 0x34, 0x56, 0x78};
    memcpy(header + headLen, mask, 4);
    headLen += 4;

    if (_wsClient.write(header, headLen) != headLen) {
      disconnectWs();
      return false;
    }

    std::vector<uint8_t> masked(len);
    for (size_t i = 0; i < len; ++i) {
      masked[i] = ((uint8_t)payload[i]) ^ mask[i & 3];
    }
    if (_wsClient.write(masked.data(), len) != len) {
      disconnectWs();
      return false;
    }
    return true;
  }

  // 非阻塞接收来自 Minecraft 的 WebSocket 帧
  void pollWs() {
    if (!_wsClient.connected()) {
      disconnectWs();
      return;
    }
    while (_wsClient.available() >= 2) {
      uint8_t b0 = _wsClient.read();
      uint8_t b1 = _wsClient.read();
      uint8_t opcode = b0 & 0x0F;
      bool isMasked = (b1 & 0x80) != 0;
      uint64_t len = b1 & 0x7F;

      if (len == 126) {
        if (_wsClient.available() < 2) return;
        len = ((uint16_t)_wsClient.read() << 8) | _wsClient.read();
      } else if (len == 127) {
        disconnectWs();
        return;
      }

      uint8_t mask[4] = {0, 0, 0, 0};
      if (isMasked) {
        for (int i = 0; i < 4; ++i) mask[i] = _wsClient.read();
      }

      if (opcode == 0x08) { // Close frame
        disconnectWs();
        return;
      }

      String text;
      text.reserve(len);
      unsigned long waitStart = millis();
      for (uint64_t i = 0; i < len; ++i) {
        while (!_wsClient.available()) {
          if (millis() - waitStart > 500) {
            disconnectWs();
            return;
          }
          delay(1);
        }
        uint8_t c = _wsClient.read();
        if (isMasked) c ^= mask[i & 3];
        text += (char)c;
      }

      if (opcode == 0x01 && text.length() > 0) {
        handleIncomingJson(text);
      }
    }
  }

  void handleIncomingJson(const String& json) {
    String channel = extractJsonString(json, "channel");
    if (channel.length() == 0) return; // 忽略心跳回包

    IoTMsg msg;
    msg.device  = extractJsonString(json, "device");
    msg.channel = channel;
    msg.event   = extractJsonString(json, "event");
    msg.value   = extractJsonString(json, "value").toInt();
    msg.data    = extractJsonString(json, "data");

    dispatchIncoming(msg);
  }

  // HTTP POST 备用发送（适用于纯 HTTP 隧道穿透场景）
  bool sendHttpPostFallback(const String& json) {
    WiFiClient client;
    if (!client.connect(_mcHost.c_str(), _mcPort, 800)) return false;
    client.print(String("POST /api/event HTTP/1.1\r\n") +
                 "Host: " + _mcHost + ":" + String(_mcPort) + "\r\n" +
                 "Content-Type: application/json\r\n" +
                 "Content-Length: " + String(json.length()) + "\r\n" +
                 "Connection: close\r\n\r\n" + json);
    client.stop();
    return true;
  }

  // ==========================================================================
  // 本地 Web 调试与配置面板（手机/电脑浏览器访问 ESP32 IP 即可可视化调试）
  // ==========================================================================
  static McIoTNode* _instance;

  static esp_err_t handleWebRoot(httpd_req_t* req) {
    const char* html = R"rawliteral(
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MC-IoT 硬件节点控制台</title>
<style>
  * { box-sizing: border-box; }
  body { max-width: 620px; margin: auto; padding: 20px; background: #0f1218; color: #e8ecf2; font: 15px system-ui, sans-serif; }
  .card { background: #181d27; border: 1px solid #2b3245; border-radius: 14px; padding: 18px; margin-bottom: 16px; }
  h2 { margin: 0 0 10px; font-size: 18px; color: #68d391; }
  label { display: block; margin: 8px 0 4px; color: #a0aec0; font-size: 13px; }
  input, select, button { width: 100%; padding: 10px; border-radius: 8px; border: 1px solid #364156; background: #0f1218; color: #fff; font-size: 14px; }
  button { background: #3182ce; border: none; font-weight: bold; cursor: pointer; margin-top: 10px; }
  button.alt { background: #805ad5; }
  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
  .status { font-family: monospace; padding: 10px; background: #090b10; border-radius: 8px; color: #90cdf4; }
</style>
</head>
<body>
  <div class="card">
    <h2>MC-IoT 双向物联节点控制台</h2>
    <div id="stat" class="status">正在获取状态...</div>
  </div>

  <div class="card">
    <h2>1. Minecraft 服务器 / 内网穿透地址配置</h2>
    <label>MC 服务器 IP 或穿透域名 (Host)</label>
    <input id="host" placeholder="例如 192.168.1.100 或 frp.example.com">
    <label>端口 (Port，默认 25580)</label>
    <input id="port" type="number" value="25580">
    <button onclick="saveServer()">保存并立即重连 MC</button>
  </div>

  <div class="card">
    <h2>2. 模拟硬件发送 -> Minecraft (上行测试)</h2>
    <div class="grid">
      <button onclick="simTx('btn_boot','toggle',15)">模拟按钮切换 (btn_boot)</button>
      <button onclick="simTx('knob_1','change',Math.floor(Math.random()*16))">模拟旋钮随机红石强度</button>
    </div>
  </div>

  <div class="card">
    <h2>3. 模拟 Minecraft -> 单片机 (下行雷暴/事件测试)</h2>
    <div class="grid">
      <button class="alt" onclick="simRx('weather','thunder',15)">模拟 MC 雷暴 (紫电闪烁)</button>
      <button class="alt" onclick="simRx('weather','rain',8)">模拟 MC 下雨 (蓝色呼吸)</button>
      <button class="alt" onclick="simRx('weather','clear',0)">模拟 MC 晴天 (暖阳常亮)</button>
      <button class="alt" onclick="simRx('mc_block','power',15)">模拟 MC 方块充能满格</button>
    </div>
  </div>

<script>
async function refresh() {
  try {
    const r = await fetch('/api/status');
    const d = await r.json();
    document.getElementById('stat').textContent =
      `设备: ${d.device} | MC连接: ${d.connected ? '已连接' : '未连接'} (${d.host}:${d.port}) | 最近日志: ${d.log}`;
    if (!document.getElementById('host').value) {
      document.getElementById('host').value = d.host;
      document.getElementById('port').value = d.port;
    }
  } catch (e) {}
}
async function saveServer() {
  const host = document.getElementById('host').value.trim();
  const port = document.getElementById('port').value.trim();
  await fetch(`/api/config?host=${encodeURIComponent(host)}&port=${encodeURIComponent(port)}`, {method:'POST'});
  refresh();
}
async function simTx(ch, ev, val) {
  await fetch(`/api/sim_tx?channel=${ch}&event=${ev}&value=${val}`, {method:'POST'});
  refresh();
}
async function simRx(ch, ev, val) {
  await fetch(`/api/sim_rx?channel=${ch}&event=${ev}&value=${val}`, {method:'POST'});
  refresh();
}
setInterval(refresh, 1500);
refresh();
</script>
</body>
</html>
)rawliteral";
    httpd_resp_set_type(req, "text/html; charset=utf-8");
    return httpd_resp_send(req, html, HTTPD_RESP_USE_STRLEN);
  }

  static esp_err_t handleApiStatus(httpd_req_t* req) {
    if (!_instance) return ESP_FAIL;
    String json = "{\"device\":\"" + _instance->getDeviceId() + "\","
                  "\"connected\":" + String(_instance->isConnected() ? "true" : "false") + ","
                  "\"host\":\"" + _instance->getServerHost() + "\","
                  "\"port\":" + String(_instance->getServerPort()) + ","
                  "\"log\":\"" + escapeJson(_instance->getLastLog()) + "\"}";
    httpd_resp_set_type(req, "application/json; charset=utf-8");
    return httpd_resp_sendstr(req, json.c_str());
  }

  static String getQueryParam(httpd_req_t* req, const char* key) {
    size_t bufLen = httpd_req_get_url_query_len(req) + 1;
    if (bufLen <= 1) return "";
    std::vector<char> buf(bufLen);
    if (httpd_req_get_url_query_str(req, buf.data(), bufLen) != ESP_OK) return "";
    char val[128] = {0};
    if (httpd_query_key_value(buf.data(), key, val, sizeof(val)) != ESP_OK) return "";
    return String(val);
  }

  static esp_err_t handleApiConfig(httpd_req_t* req) {
    if (!_instance) return ESP_FAIL;
    String host = getQueryParam(req, "host");
    String port = getQueryParam(req, "port");
    if (host.length() > 0 && port.toInt() > 0) {
      _instance->setServer(host, (uint16_t)port.toInt());
    }
    return httpd_resp_sendstr(req, "OK");
  }

  static esp_err_t handleApiSimTx(httpd_req_t* req) {
    if (!_instance) return ESP_FAIL;
    _instance->send(getQueryParam(req, "channel"),
                    getQueryParam(req, "event"),
                    getQueryParam(req, "value").toInt());
    return httpd_resp_sendstr(req, "OK");
  }

  static esp_err_t handleApiSimRx(httpd_req_t* req) {
    if (!_instance) return ESP_FAIL;
    IoTMsg msg;
    msg.device = "mc-server";
    msg.channel = getQueryParam(req, "channel");
    msg.event = getQueryParam(req, "event");
    msg.value = getQueryParam(req, "value").toInt();
    _instance->dispatchIncoming(msg);
    return httpd_resp_sendstr(req, "OK");
  }

  void startLocalWebPanel() {
    _instance = this;
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.max_uri_handlers = 6;
    config.lru_purge_enable = true;
    if (httpd_start(&_httpServer, &config) == ESP_OK) {
      httpd_uri_t rRoot = {"/", HTTP_GET, handleWebRoot, NULL};
      httpd_uri_t rStat = {"/api/status", HTTP_GET, handleApiStatus, NULL};
      httpd_uri_t rConf = {"/api/config", HTTP_POST, handleApiConfig, NULL};
      httpd_uri_t rSimTx = {"/api/sim_tx", HTTP_POST, handleApiSimTx, NULL};
      httpd_uri_t rSimRx = {"/api/sim_rx", HTTP_POST, handleApiSimRx, NULL};
      httpd_register_uri_handler(_httpServer, &rRoot);
      httpd_register_uri_handler(_httpServer, &rStat);
      httpd_register_uri_handler(_httpServer, &rConf);
      httpd_register_uri_handler(_httpServer, &rSimTx);
      httpd_register_uri_handler(_httpServer, &rSimRx);
      Serial.print("[McIoT] Local Debug Web UI: http://");
      Serial.println(WiFi.localIP());
    }
  }
};

McIoTNode* McIoTNode::_instance = nullptr;

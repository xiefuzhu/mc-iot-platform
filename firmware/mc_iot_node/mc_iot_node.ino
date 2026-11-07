#include <WiFi.h>
#include "McIoTNode.h"
#include "BuiltinPlugins.h"

// ============================================================================
// 1. 基础网络与服务器配置（烧录后也可以直接在浏览器打开 ESP32 的 IP 动态修改）
// ============================================================================
const char* WIFI_NAME     = "OpenWrt_2.4G";
const char* WIFI_PASSWORD = "";

// Minecraft 电脑的局域网 IP，或者内网穿透出来的域名 (如 "frp.example.com")
const char* MC_SERVER_HOST = "192.168.1.100";
const uint16_t MC_SERVER_PORT = 25580;

// 实例化核心物联节点 (设备名: "esp32-s3-01")
McIoTNode iotNode("esp32-s3-01", MC_SERVER_HOST, MC_SERVER_PORT);

// ============================================================================
// 2. 插件挂载区：以后添加任何新按钮、旋钮、传感器，只需在这里 new 一行即可！
// ============================================================================

// [默认启用 1] 板载 BOOT 按键 (GPIO 0)：无需外接任何电路即可测试！
// 按一下切换 MC 内绑定 "btn_boot" 通道的方块开关与红石信号 (0 <-> 15)
DigitalButtonPlugin bootButton(0, "btn_boot", true, true);

// [默认启用 2] 板载 RGB 彩灯 (GPIO 48)：演示 MC -> 单片机 反向控制！
// 当 MC 世界发生雷暴(thunder)、下雨(rain)、晴天(clear) 或玩家激活方块时自动变幻灯效
OnboardRgbEffectPlugin onboardRgb(48);

// [按需取消注释] 如果你外接了轻触按钮（例如接在 GPIO 4 和 GND 之间）：
// DigitalButtonPlugin extButton1(4, "btn_1", true, true);

// [按需取消注释] 如果你外接了电位器旋钮或模拟传感器（例如接在 GPIO 5）：
// AnalogKnobPlugin knob1(5, "knob_1");

// [按需取消注释] 如果你外接了 EC11 旋转编码器旋钮（例如 A 相接 GPIO 6，B 相接 GPIO 7）：
// RotaryEncoderPlugin encoder1(6, 7, "knob_1");

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=== Starting MC-IoT Node (ESP32-S3) ===");

  // 注册启用的插件
  iotNode.addPlugin(&bootButton);
  iotNode.addPlugin(&onboardRgb);
  // iotNode.addPlugin(&extButton1);
  // iotNode.addPlugin(&knob1);
  // iotNode.addPlugin(&encoder1);

  iotNode.begin();

  // 启动 Wi-Fi（关闭无线休眠，保证双向毫秒级响应）
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_NAME, WIFI_PASSWORD);

  Serial.print("[WiFi] Connecting");
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 10000) {
    Serial.print(".");
    delay(250);
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("[WiFi] Connected! IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("[WiFi] Still connecting in background...");
  }
}

void loop() {
  // 自动维护双向长连接、Web 控制台与所有硬件插件轮询
  iotNode.loop();
}

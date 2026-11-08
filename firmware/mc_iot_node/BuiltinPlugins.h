#pragma once
#include "McIoTNode.h"

// ============================================================================
// 插件 1: 通用数字按键/开关插件 (支持轻触按钮、微动开关、触摸模块)
// ============================================================================
class DigitalButtonPlugin : public McIoTPlugin {
public:
  // pin: 引脚号
  // channel: 发送给 MC 的通道名 (例如 "btn_boot", "btn_ext1")
  // activeLow: true 表示接 GND (使用内部上拉 INPUT_PULLUP)，false 表示接 3.3V (使用内部下拉)
  // toggleMode: true 表示按一下翻转开关状态 (0 <-> 15)，false 表示按下发 15、松开发 0
  DigitalButtonPlugin(int pin, const char* channel, bool activeLow = true, bool toggleMode = true)
      : _pin(pin), _channel(channel), _activeLow(activeLow), _toggleMode(toggleMode) {}

  const char* name() override { return _channel; }

  void setup(McIoTNode& node) override {
    pinMode(_pin, _activeLow ? INPUT_PULLUP : INPUT_PULLDOWN);
    _lastRaw = readPressed();
    _stablePressed = _lastRaw;
  }

  void loop(McIoTNode& node) override {
    bool current = readPressed();
    unsigned long now = millis();

    if (current != _lastRaw) {
      _lastDebounceTime = now;
      _lastRaw = current;
    }

    // 35ms 硬件消抖
    if ((now - _lastDebounceTime) > 35 && current != _stablePressed) {
      _stablePressed = current;
      if (_toggleMode) {
        if (_stablePressed) {
          _toggledState = !_toggledState;
          node.send(_channel, "toggle", _toggledState ? 15 : 0);
        }
      } else {
        node.send(_channel, _stablePressed ? "press" : "release", _stablePressed ? 15 : 0);
      }
    }
  }

private:
  int _pin;
  const char* _channel;
  bool _activeLow;
  bool _toggleMode;
  bool _lastRaw = false;
  bool _stablePressed = false;
  bool _toggledState = false;
  unsigned long _lastDebounceTime = 0;

  bool readPressed() const {
    int level = digitalRead(_pin);
    return _activeLow ? (level == LOW) : (level == HIGH);
  }
};

// ============================================================================
// 插件 2: 通用模拟量旋钮 / 传感器插件 (自动映射为 MC 0~15 红石信号强度)
// ============================================================================
class AnalogKnobPlugin : public McIoTPlugin {
public:
  // adcPin: 模拟输入引脚 (ESP32-S3 推荐 GPIO 1 ~ 10)
  // channel: 通道名称 (例如 "knob_1", "light_sensor")
  AnalogKnobPlugin(int adcPin, const char* channel)
      : _adcPin(adcPin), _channel(channel) {}

  const char* name() override { return _channel; }

  void setup(McIoTNode& node) override {
    pinMode(_adcPin, INPUT);
  }

  void loop(McIoTNode& node) override {
    unsigned long now = millis();
    if (now - _lastSampleTime < 80) return; // 80ms 采样一次
    _lastSampleTime = now;

    int raw = analogRead(_adcPin); // 0 ~ 4095
    // 将 0~4095 映射为 Minecraft 红石信号等级 0~15
    int redstoneLevel = map(constrain(raw, 0, 4095), 0, 4095, 0, 15);

    // 加上死区过滤，防止边界抖动频繁发包
    if (abs(raw - _lastRaw) > 140 && redstoneLevel != _lastLevel) {
      _lastRaw = raw;
      _lastLevel = redstoneLevel;
      node.send(_channel, "change", redstoneLevel, String(raw));
    }
  }

private:
  int _adcPin;
  const char* _channel;
  int _lastRaw = -1000;
  int _lastLevel = -1;
  unsigned long _lastSampleTime = 0;
};

// ============================================================================
// 插件 3: 增量式旋转编码器插件 (EC11 旋钮：支持顺时针/逆时针步进调节 0~15)
// ============================================================================
class RotaryEncoderPlugin : public McIoTPlugin {
public:
  RotaryEncoderPlugin(int pinA, int pinB, const char* channel)
      : _pinA(pinA), _pinB(pinB), _channel(channel) {}

  const char* name() override { return _channel; }

  void setup(McIoTNode& node) override {
    pinMode(_pinA, INPUT_PULLUP);
    pinMode(_pinB, INPUT_PULLUP);
    _lastStateA = digitalRead(_pinA);
  }

  void loop(McIoTNode& node) override {
    int stateA = digitalRead(_pinA);
    if (stateA != _lastStateA && stateA == LOW) {
      if (digitalRead(_pinB) != stateA) {
        _value = min(15, _value + 1);
        node.send(_channel, "rotate_cw", _value);
      } else {
        _value = max(0, _value - 1);
        node.send(_channel, "rotate_ccw", _value);
      }
    }
    _lastStateA = stateA;
  }

private:
  int _pinA;
  int _pinB;
  const char* _channel;
  int _lastStateA = HIGH;
  int _value = 0;
};

// ============================================================================
// 插件 4: 板载 RGB 彩灯反向响应插件 (演示 MC -> 单片机 反向传输)
// 响应来自 Minecraft 的天气事件 (晴天/下雨/雷暴) 和 MC 方块的反向红石信号。
// ============================================================================
class OnboardRgbEffectPlugin : public McIoTPlugin {
public:
  explicit OnboardRgbEffectPlugin(int rgbPin = 48) : _rgbPin(rgbPin) {}

  const char* name() override { return "onboard_rgb_effect"; }

  void setup(McIoTNode& node) override {
    rgbLedWrite(_rgbPin, 0, 15, 0); // 开机微亮绿灯代表就绪
  }

  // 【核心：反向接收 Minecraft 世界事件】
  void onMessage(McIoTNode& node, const IoTMsg& msg) override {
    if (msg.channel == "weather") {
      if (msg.event == "thunder") {
        _mode = MODE_THUNDER;
      } else if (msg.event == "rain") {
        _mode = MODE_RAIN;
      } else if (msg.event == "clear") {
        _mode = MODE_CLEAR;
        rgbLedWrite(_rgbPin, 40, 28, 5); // 暖阳黄光
      }
    } else if (msg.channel == "mc_block" || msg.channel == "redstone") {
      // 当 MC 里玩家点击方块或红石激活方块时，按红石强度 (0~15) 点亮翡翠绿光
      _mode = MODE_MANUAL;
      int brightness = constrain(msg.value, 0, 15) * 17; // 0 ~ 255
      rgbLedWrite(_rgbPin, 0, brightness, brightness / 3);
    } else if (msg.channel == "rgb_raw") {
      // 支持任意插件直接下发 "#RRGGBB" 十六进制颜色
      _mode = MODE_MANUAL;
      if (msg.data.length() == 7 && msg.data[0] == '#') {
        long hex = strtol(msg.data.c_str() + 1, NULL, 16);
        rgbLedWrite(_rgbPin, (hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF);
      }
    }
  }

  void loop(McIoTNode& node) override {
    unsigned long now = millis();
    if (_mode == MODE_THUNDER) {
      // 模拟 MC 雷暴：随机高频紫白色闪电爆发
      if (now - _lastAnimTick > 70) {
        _lastAnimTick = now;
        if (random(0, 10) < 3) {
          rgbLedWrite(_rgbPin, 220, 180, 255); // 闪电亮起
        } else {
          rgbLedWrite(_rgbPin, 8, 0, 20);      // 阴云暗紫底色
        }
      }
    } else if (_mode == MODE_RAIN) {
      // 模拟 MC 下雨：平滑蓝色呼吸灯
      if (now - _lastAnimTick > 30) {
        _lastAnimTick = now;
        _breathPhase += 0.08f;
        int blue = (int)((sin(_breathPhase) + 1.0f) * 60.0f) + 10;
        rgbLedWrite(_rgbPin, 0, blue / 4, blue);
      }
    }
  }

private:
  enum EffectMode { MODE_MANUAL, MODE_CLEAR, MODE_RAIN, MODE_THUNDER };
  int _rgbPin;
  EffectMode _mode = MODE_MANUAL;
  unsigned long _lastAnimTick = 0;
  float _breathPhase = 0.0f;
};

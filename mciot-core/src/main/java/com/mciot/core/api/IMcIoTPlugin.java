package com.mciot.core.api;

import com.mciot.core.protocol.IoTMessage;

/**
 * 极简 MC 端物联插件接口（跨 1.20.1 ~ 26.1 全版本、Fabric/Forge/NeoForge 100% 通用！）
 * 后期你想添加任何新传感器处理逻辑或新的世界事件推送，只需实现此接口并注册到 McIoTHub。
 */
public interface IMcIoTPlugin {
    /**
     * 插件唯一标识符
     */
    String id();

    /**
     * 【上行：单片机 -> MC】当收到单片机发来的按键、旋钮、传感器消息时触发
     * 注意：此回调已经自动切回 Minecraft 服务端主线程，可直接安全操作世界！
     */
    default void onDeviceMessage(IMcWorldBridge world, IoTMessage message) {}

    /**
     * 【下行：MC -> 单片机】当 Minecraft 世界内发生事件（如雷暴、下雨、方块被激活）时触发
     */
    default void onWorldEvent(IMcWorldBridge world, WorldEventContext event) {}
}

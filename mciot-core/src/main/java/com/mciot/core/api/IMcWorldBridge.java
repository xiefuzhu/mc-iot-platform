package com.mciot.core.api;

/**
 * 跨版本 Minecraft 世界桥接接口。
 * 1.20.1 ~ 26.1 各版本只需实现此接口中的 3 个方法，即可让所有核心功能与插件跨版本运行。
 */
public interface IMcWorldBridge {
    /**
     * 将任务投递到 Minecraft 服务端主线程安全执行
     */
    void runOnServerThread(Runnable task);

    /**
     * 更新游戏内指定坐标的 IoT 方块状态（开关亮灭 & 输出 0~15 红石信号强度）
     */
    void applyIoTBlockState(BlockPosKey pos, boolean powered, int redstoneSignal);

    /**
     * 在游戏聊天栏向管理员/玩家广播调试提示信息
     */
    void broadcastSystemChat(String message);

    /**
     * 向所有已连接的单片机广播下行事件 (MC -> ESP32)
     */
    default void broadcastToDevices(String channel, String event, int value, String data) {
        McIoTHub.getInstance().broadcastToDevices(channel, event, value, data);
    }
}

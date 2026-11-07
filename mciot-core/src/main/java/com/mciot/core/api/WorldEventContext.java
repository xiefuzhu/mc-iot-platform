package com.mciot.core.api;

/**
 * 跨版本 Minecraft 世界事件上下文（用于 MC -> 单片机 反向触发）
 */
public final class WorldEventContext {
    public enum Type {
        WEATHER_CHANGE,       // 天气改变 (clear / rain / thunder)
        TIME_PHASE_CHANGE,    // 昼夜交替 (day / night)
        BLOCK_INTERACT,       // 玩家右键点击了 IoT 方块
        BLOCK_REDSTONE_CHANGE,// 游戏内外部红石信号激活了 IoT 方块
        CUSTOM_EVENT          // 其他模组或指令触发的自定义事件
    }

    private final Type type;
    private final String name;
    private final int value;
    private final String extraData;
    private final BlockPosKey pos;

    public WorldEventContext(Type type, String name, int value, String extraData, BlockPosKey pos) {
        this.type = type;
        this.name = name == null ? "" : name;
        this.value = value;
        this.extraData = extraData == null ? "" : extraData;
        this.pos = pos;
    }

    public Type type() { return type; }
    public String name() { return name; }
    public int value() { return value; }
    public String extraData() { return extraData; }
    public BlockPosKey pos() { return pos; }
}

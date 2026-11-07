package com.mciot.core.plugin;

import com.mciot.core.api.IMcIoTPlugin;
import com.mciot.core.api.IMcWorldBridge;
import com.mciot.core.api.WorldEventContext;

/**
 * 内置核心插件 2：Minecraft 世界天气与环境反向推送插件 (MC -> ESP32)。
 * 当游戏世界发生晴天 (clear)、下雨 (rain)、雷暴 (thunder) 或昼夜变化时，
 * 自动向所有连接的 ESP32 单片机推送事件！
 */
public final class WorldEnvironmentSyncPlugin implements IMcIoTPlugin {
    private String lastWeather = "";

    @Override
    public String id() {
        return "builtin_world_environment_sync";
    }

    @Override
    public void onWorldEvent(IMcWorldBridge world, WorldEventContext event) {
        if (event.type() == WorldEventContext.Type.WEATHER_CHANGE) {
            String currentWeather = event.name();
            if (!currentWeather.equals(lastWeather)) {
                lastWeather = currentWeather;
                world.broadcastToDevices("weather", currentWeather, event.value(), event.extraData());
                world.broadcastSystemChat("§d[MC-IoT] 世界天气变化已同步至单片机: §e" + currentWeather);
            }
        } else if (event.type() == WorldEventContext.Type.TIME_PHASE_CHANGE) {
            world.broadcastToDevices("world_time", event.name(), event.value(), "");
        } else if (event.type() == WorldEventContext.Type.CUSTOM_EVENT) {
            world.broadcastToDevices("custom", event.name(), event.value(), event.extraData());
        }
    }
}

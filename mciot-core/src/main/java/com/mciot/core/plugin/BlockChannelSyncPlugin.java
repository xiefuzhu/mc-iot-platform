package com.mciot.core.plugin;

import com.mciot.core.api.IMcIoTPlugin;
import com.mciot.core.api.IMcWorldBridge;
import com.mciot.core.api.McIoTHub;
import com.mciot.core.api.WorldEventContext;
import com.mciot.core.protocol.IoTMessage;

/**
 * 内置核心插件 1：双向方块状态与红石信号同步插件。
 * - 上行 (ESP32 -> MC)：当收到单片机的按钮/旋钮通道信号时，自动改变对应通道绑定的所有 IoT 方块的开关与红石信号强度 (0~15)。
 * - 下行 (MC -> ESP32)：当玩家在游戏里点击 IoT 方块，或者用红石电路激活该方块时，自动把信号反向发回给单片机！
 */
public final class BlockChannelSyncPlugin implements IMcIoTPlugin {
    @Override
    public String id() {
        return "builtin_block_channel_sync";
    }

    @Override
    public void onDeviceMessage(IMcWorldBridge world, IoTMessage msg) {
        if ("system".equals(msg.channel())) {
            world.broadcastSystemChat("§a[MC-IoT] 硬件设备已上线: §e" + msg.device());
            return;
        }

        // 将硬件发来的数值 (0~15) 更新到所有绑定了该 channel 的游戏方块上
        int signal = Math.max(0, Math.min(15, msg.value()));
        McIoTHub.getInstance().updateChannelState(msg.channel(), signal);
        world.broadcastSystemChat("§b[MC-IoT] 收到硬件信号 §f[" + msg.channel() + "] §e"
                + msg.event() + " §7-> 红石强度: §a" + signal);
    }

    @Override
    public void onWorldEvent(IMcWorldBridge world, WorldEventContext event) {
        if (event.type() == WorldEventContext.Type.BLOCK_INTERACT
                || event.type() == WorldEventContext.Type.BLOCK_REDSTONE_CHANGE) {
            // 将游戏内方块的变化反向推送给 ESP32 (触发板载彩灯或外接执行器)
            world.broadcastToDevices("mc_block", event.name(), event.value(), event.extraData());
        }
    }
}

package com.mciot.core.plugin;

import com.mciot.core.api.BlockIoTConfig;
import com.mciot.core.api.IMcIoTPlugin;
import com.mciot.core.api.IMcWorldBridge;
import com.mciot.core.api.McIoTHub;
import com.mciot.core.api.WorldEventContext;
import com.mciot.core.protocol.IoTMessage;

/**
 * 内置核心插件 1：双向方块状态与红石信号同步插件。
 * - 上行 (ESP32 -> MC)：当收到单片机的按钮/旋钮通道信号时，根据方块独立的 BlockIoTConfig 过滤并更新红石信号 (0~15)。
 * - 下行 (MC -> ESP32)：当玩家在游戏里点击 IoT 方块，或者用红石电路激活该方块时，按该方块配置的开关与目标通道发回单片机。
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

        // 将硬件发来的数值 (0~15) 更新到所有绑定了该 channel 且启用了接收开关的游戏方块上
        int signal = Math.max(0, Math.min(15, msg.value()));
        McIoTHub.getInstance().updateChannelState(msg.channel(), signal);
        if (McIoTHub.getInstance().isChatBroadcastEnabled()) {
            world.broadcastSystemChat("§b[MC-IoT] 收到硬件信号 §f[" + msg.channel() + "] §e"
                    + msg.event() + " §7-> 红石强度: §a" + signal);
        }
    }

    @Override
    public void onWorldEvent(IMcWorldBridge world, WorldEventContext event) {
        if (event.type() == WorldEventContext.Type.BLOCK_INTERACT
                || event.type() == WorldEventContext.Type.BLOCK_REDSTONE_CHANGE) {
            BlockIoTConfig cfg = event.pos() != null
                    ? McIoTHub.getInstance().getBlockConfig(event.pos())
                    : null;
            if (cfg != null) {
                if (event.type() == WorldEventContext.Type.BLOCK_INTERACT && !cfg.sendOnClick()) {
                    return; // 该方块已在 GUI 中关闭“右键向硬件发送”
                }
                if (event.type() == WorldEventContext.Type.BLOCK_REDSTONE_CHANGE && !cfg.sendOnRedstone()) {
                    return; // 该方块已在 GUI 中关闭“外部红石向硬件发送”
                }
            }
            String targetChannel = (cfg != null) ? cfg.downlinkChannel() : "mc_block";
            world.broadcastToDevices(targetChannel, event.name(), event.value(), event.extraData());
        }
    }
}

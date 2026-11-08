package com.mciot.core.api;

import java.util.Objects;

/**
 * 单个 IoT 方块的独立通信与路由配置。
 * 支持为每个方块独立配置：
 * - 绑定的硬件通道 (channel，如 "btn_boot", "btn_1", "knob_1")
 * - 接收方向开关 (acceptFromDevice)：是否接收单片机发来的信号并输出红石
 * - 发送方向开关 (sendOnClick)：普通右键方块时是否向单片机发送信号
 * - 红石反向开关 (sendOnRedstone)：外部红石激活方块时是否向单片机发送信号
 * - 信号反相模式 (invertSignal)：是否将收到的 0~15 信号反相为 15-val
 * - 下行目标通道 (downlinkChannel)：反向发给单片机的通道名（默认 "mc_block"，也可设为 "rgb_raw" 等）
 */
public final class BlockIoTConfig {
    private String channel;
    private boolean acceptFromDevice;
    private boolean sendOnClick;
    private boolean sendOnRedstone;
    private boolean invertSignal;
    private String downlinkChannel;

    public BlockIoTConfig(String channel) {
        this(channel, true, true, true, false, "mc_block");
    }

    public BlockIoTConfig(String channel,
                          boolean acceptFromDevice,
                          boolean sendOnClick,
                          boolean sendOnRedstone,
                          boolean invertSignal,
                          String downlinkChannel) {
        this.channel = (channel == null || channel.isBlank()) ? "btn_boot" : channel.trim();
        this.acceptFromDevice = acceptFromDevice;
        this.sendOnClick = sendOnClick;
        this.sendOnRedstone = sendOnRedstone;
        this.invertSignal = invertSignal;
        this.downlinkChannel = (downlinkChannel == null || downlinkChannel.isBlank()) ? "mc_block" : downlinkChannel.trim();
    }

    public String channel() { return channel; }
    public void setChannel(String channel) {
        if (channel != null && !channel.isBlank()) {
            this.channel = channel.trim();
        }
    }

    public boolean acceptFromDevice() { return acceptFromDevice; }
    public void setAcceptFromDevice(boolean acceptFromDevice) { this.acceptFromDevice = acceptFromDevice; }

    public boolean sendOnClick() { return sendOnClick; }
    public void setSendOnClick(boolean sendOnClick) { this.sendOnClick = sendOnClick; }

    public boolean sendOnRedstone() { return sendOnRedstone; }
    public void setSendOnRedstone(boolean sendOnRedstone) { this.sendOnRedstone = sendOnRedstone; }

    public boolean invertSignal() { return invertSignal; }
    public void setInvertSignal(boolean invertSignal) { this.invertSignal = invertSignal; }

    public String downlinkChannel() { return downlinkChannel; }
    public void setDownlinkChannel(String downlinkChannel) {
        if (downlinkChannel != null && !downlinkChannel.isBlank()) {
            this.downlinkChannel = downlinkChannel.trim();
        }
    }

    public BlockIoTConfig copy() {
        return new BlockIoTConfig(channel, acceptFromDevice, sendOnClick, sendOnRedstone, invertSignal, downlinkChannel);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockIoTConfig that)) return false;
        return acceptFromDevice == that.acceptFromDevice
                && sendOnClick == that.sendOnClick
                && sendOnRedstone == that.sendOnRedstone
                && invertSignal == that.invertSignal
                && Objects.equals(channel, that.channel)
                && Objects.equals(downlinkChannel, that.downlinkChannel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, acceptFromDevice, sendOnClick, sendOnRedstone, invertSignal, downlinkChannel);
    }
}

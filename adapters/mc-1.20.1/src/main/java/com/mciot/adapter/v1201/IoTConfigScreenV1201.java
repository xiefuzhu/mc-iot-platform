package com.mciot.adapter.v1201;

import com.mciot.core.api.BlockIoTConfig;
import com.mciot.core.api.BlockPosKey;
import com.mciot.core.api.McIoTHub;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * ModernUI 扁平卡片风格的 IoT 方块通道绑定与双向通讯配置界面。
 * 潜行(Shift) + 右键任意 IoT 方块即可打开。
 */
public final class IoTConfigScreenV1201 extends Screen {
    private final BlockPosKey posKey;
    private final BlockIoTConfig draft;
    private EditBox channelInput;
    private EditBox downlinkInput;

    public IoTConfigScreenV1201(BlockPosKey posKey) {
        super(Component.literal("MC-IoT 物联方块配置终端"));
        this.posKey = posKey;
        this.draft = McIoTHub.getInstance().getBlockConfig(posKey);
    }

    public static void open(String dimension, int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new IoTConfigScreenV1201(new BlockPosKey(dimension, x, y, z))));
    }

    @Override
    protected void init() {
        super.init();
        int panelW = 340;
        int panelH = 240;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        // 1. 硬件绑定通道输入框 (Custom / Preset Channel)
        this.channelInput = new EditBox(this.font, left + 16, top + 50, 150, 18, Component.literal("Channel"));
        this.channelInput.setMaxLength(32);
        this.channelInput.setValue(draft.channel());
        this.channelInput.setResponder(draft::setChannel);
        this.addRenderableWidget(this.channelInput);

        // 下行目标通道输入框 (Downlink Target)
        this.downlinkInput = new EditBox(this.font, left + 174, top + 50, 150, 18, Component.literal("Downlink"));
        this.downlinkInput.setMaxLength(32);
        this.downlinkInput.setValue(draft.downlinkChannel());
        this.downlinkInput.setResponder(draft::setDownlinkChannel);
        this.addRenderableWidget(this.downlinkInput);

        // 2. 预设与已发现的硬件通道快捷标签按钮（最多展示 8 个，避免手打，一键切换不串台）
        List<String> channels = McIoTHub.getInstance().getAvailableChannels();
        int maxChips = Math.min(8, channels.size());
        int chipW = 74;
        for (int i = 0; i < maxChips; i++) {
            String ch = channels.get(i);
            int col = i % 4;
            int row = i / 4;
            int bx = left + 16 + col * (chipW + 4);
            int by = top + 74 + row * 22;
            this.addRenderableWidget(Button.builder(Component.literal(formatChipLabel(ch)), btn -> {
                draft.setChannel(ch);
                this.channelInput.setValue(ch);
                rebuildWidgets();
            }).bounds(bx, by, chipW, 18).build());
        }

        // 3. 独立通讯方向与信号滤波开关（防止多方块信号互相覆盖）
        int toggleY1 = top + 132;
        int toggleY2 = top + 156;
        int toggleY3 = top + 180;
        int halfW = 150;

        // [开关 1] 接收单片机上行信号 (ESP32 -> 本方块红石输出)
        this.addRenderableWidget(Button.builder(
                Component.literal("硬件->方块接收: " + (draft.acceptFromDevice() ? "§a开启" : "§c关闭")),
                btn -> {
                    draft.setAcceptFromDevice(!draft.acceptFromDevice());
                    rebuildWidgets();
                }).bounds(left + 16, toggleY1, halfW, 20).build());

        // [开关 2] 右键交互反向发送 (点击本方块 -> ESP32)
        this.addRenderableWidget(Button.builder(
                Component.literal("右键->硬件发送: " + (draft.sendOnClick() ? "§a开启" : "§c关闭")),
                btn -> {
                    draft.setSendOnClick(!draft.sendOnClick());
                    rebuildWidgets();
                }).bounds(left + 174, toggleY1, halfW, 20).build());

        // [开关 3] 外部红石反向发送 (红石激活本方块 -> ESP32)
        this.addRenderableWidget(Button.builder(
                Component.literal("红石->硬件发送: " + (draft.sendOnRedstone() ? "§a开启" : "§c关闭")),
                btn -> {
                    draft.setSendOnRedstone(!draft.sendOnRedstone());
                    rebuildWidgets();
                }).bounds(left + 16, toggleY2, halfW, 20).build());

        // [开关 4] 红石信号反相 (0 <-> 15)
        this.addRenderableWidget(Button.builder(
                Component.literal("红石信号反相: " + (draft.invertSignal() ? "§e开启(15-x)" : "§7关闭")),
                btn -> {
                    draft.setInvertSignal(!draft.invertSignal());
                    rebuildWidgets();
                }).bounds(left + 174, toggleY2, halfW, 20).build());

        // [开关 5] 全局天气同步至单片机开关
        McIoTHub hub = McIoTHub.getInstance();
        this.addRenderableWidget(Button.builder(
                Component.literal("全局天气同步: " + (hub.isWeatherSyncEnabled() ? "§a开启" : "§c关闭")),
                btn -> {
                    hub.setWeatherSyncEnabled(!hub.isWeatherSyncEnabled());
                    rebuildWidgets();
                }).bounds(left + 16, toggleY3, halfW, 20).build());

        // [开关 6] 聊天栏调试提示开关
        this.addRenderableWidget(Button.builder(
                Component.literal("聊天栏信号提示: " + (hub.isChatBroadcastEnabled() ? "§a开启" : "§c关闭")),
                btn -> {
                    hub.setChatBroadcastEnabled(!hub.isChatBroadcastEnabled());
                    rebuildWidgets();
                }).bounds(left + 174, toggleY3, halfW, 20).build());

        // 4. 底部操作栏：保存并应用 / 设为默认放置通道 / 关闭
        int bottomY = top + 208;
        this.addRenderableWidget(Button.builder(Component.literal("§a✔ 保存并绑定此方块"), btn -> {
            saveConfig(false);
            this.onClose();
        }).bounds(left + 16, bottomY, 128, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§b★ 设为默认放置通道"), btn -> {
            saveConfig(true);
            this.onClose();
        }).bounds(left + 150, bottomY, 114, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("取消"), btn -> this.onClose())
                .bounds(left + 270, bottomY, 54, 20).build());
    }

    private String formatChipLabel(String ch) {
        if (ch.equals(draft.channel())) {
            return "§a● " + ch;
        }
        return "§7○ " + ch;
    }

    private void saveConfig(boolean alsoSetPending) {
        draft.setChannel(this.channelInput.getValue());
        draft.setDownlinkChannel(this.downlinkInput.getValue());
        McIoTHub hub = McIoTHub.getInstance();
        hub.updateBlockConfig(posKey, draft);
        if (alsoSetPending) {
            hub.setPendingBindChannel(draft.channel());
        }
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[MC-IoT] 已保存方块 [" + posKey.x() + "," + posKey.y() + "," + posKey.z()
                            + "] -> 通道 §e" + draft.channel()
                            + " §7(接收:" + (draft.acceptFromDevice() ? "开" : "关")
                            + " / 右键发:" + (draft.sendOnClick() ? "开" : "关")
                            + " / 红石发:" + (draft.sendOnRedstone() ? "开" : "关") + ")"),
                    true
            );
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelW = 340;
        int panelH = 240;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        // ModernUI 风格半透明磨砂暗色背景与发光蓝绿顶栏
        g.fill(0, 0, this.width, this.height, 0x88080C14);
        // 外发光阴影边框
        g.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, 0xAA00E5FF);
        // 卡片主背景 (深灰蓝 #121824)
        g.fill(left, top, left + panelW, top + panelH, 0xF2121824);
        // 顶部标题栏背景 (#1A2436)
        g.fill(left, top, left + panelW, top + 28, 0xFF1A2436);
        // 顶部霓虹青分割线
        g.fill(left, top + 28, left + panelW, top + 29, 0xFF00E5FF);

        // 标题与实时在线状态徽章
        McIoTHub hub = McIoTHub.getInstance();
        g.drawString(this.font, "§b§lMC-IoT §f物联方块配置中心", left + 12, top + 10, 0xFFFFFF);
        String statusBadge = "§7端口:§e" + hub.getPort()
                + "  §7在线设备:§a" + hub.getConnectedDeviceCount()
                + "  §7坐标:§f(" + posKey.x() + "," + posKey.y() + "," + posKey.z() + ")";
        g.drawString(this.font, statusBadge, left + panelW - this.font.width(statusBadge) - 12, top + 10, 0xCCCCCC);

        // 分区小标题
        int curVal = hub.getChannelLastValue(draft.channel());
        g.drawString(this.font, "§e▸ 绑定上行硬件通道 §7(当前信号: §a" + curVal + "§7)", left + 16, top + 38, 0xFFFFFF);
        g.drawString(this.font, "§b▸ 反向发送目标通道 §7(MC->ESP32)", left + 174, top + 38, 0xFFFFFF);
        g.drawString(this.font, "§e▸ 独立通讯方向与防串台隔离设置", left + 16, top + 120, 0xFFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 打开配置面板时不暂停服务端与硬件实时通信
    }
}

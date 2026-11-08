package com.mciot.adapter.v1201;

import com.mciot.core.api.BlockIoTConfig;
import com.mciot.core.api.BlockPosKey;
import com.mciot.core.api.McIoTHub;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.mc.ui.ThemeControl;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.Editable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.Checkable;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.Switch;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.Toast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * 原生 ModernUI 矢量卡片配置界面 (Fragment)。
 * 采用与 ModernUI 偏好设置中心一致的 OutlinedCard、Switch 开关、EditText 矢量输入框与流式布局。
 */
public final class IoTModernUiFragment extends Fragment {
    private final BlockPosKey posKey;
    private final BlockIoTConfig draft;

    private EditText channelInput;
    private EditText downlinkInput;
    private final List<Button> channelChipButtons = new ArrayList<>();
    private final List<String> channelChipNames = new ArrayList<>();

    public IoTModernUiFragment(BlockPosKey posKey) {
        this.posKey = posKey;
        this.draft = McIoTHub.getInstance().getBlockConfig(posKey);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = requireContext();
        McIoTHub hub = McIoTHub.getInstance();

        ClampingScrollView scrollView = new ClampingScrollView(context);
        scrollView.setFillViewport(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int dp20 = root.dp(20.0f);
        int dp16 = root.dp(16.0f);
        int dp12 = root.dp(12.0f);
        int dp8 = root.dp(8.0f);
        int dp6 = root.dp(6.0f);
        root.setPadding(dp20, dp16, dp20, dp20);

        // ============================================================
        // 1. 顶部概览与实时硬件状态卡片
        // ============================================================
        LinearLayout headerCard = createOutlinedCard(context, dp16);
        TextView titleView = new TextView(context);
        titleView.setText("MC-IoT 物联方块配置中心");
        titleView.setTextSize(18.0f);
        titleView.setTextColor(0xFFF3C6B6);
        headerCard.addView(titleView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        int curVal = hub.getChannelLastValue(draft.channel());
        TextView statusView = new TextView(context);
        statusView.setText("方块坐标: (" + posKey.x() + ", " + posKey.y() + ", " + posKey.z() + ")   |   "
                + "WebSocket 端口: " + hub.getPort() + "   |   "
                + "在线单片机: " + hub.getConnectedDeviceCount() + " 台   |   "
                + "当前通道实时信号: " + curVal + " / 15");
        statusView.setTextSize(13.0f);
        statusView.setTextColor(0xFFCCCCCC);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        statusLp.topMargin = dp6;
        headerCard.addView(statusView, statusLp);

        addCardToRoot(root, headerCard, 0);

        // ============================================================
        // 2. 硬件通道路由与快捷选择卡片
        // ============================================================
        LinearLayout channelCard = createCategoryCard(context, "硬件通道路由与绑定 (防多方块串台)", dp16, dp6);

        // 2.1 上行绑定通道输入行
        this.channelInput = new EditText(context, null, R.attr.editTextOutlinedStyle);
        this.channelInput.setSingleLine();
        this.channelInput.setText(draft.channel());
        this.channelInput.setHint("输入硬件上行通道名 (如 btn_boot)");
        this.channelInput.setMinimumWidth(root.dp(200.0f));
        channelCard.addView(createInputRow(context,
                "绑定上行硬件通道 (ESP32 -> 本方块红石输出)",
                this.channelInput, dp6));

        // 2.2 快捷通道切换矩阵 (每行 4 个按钮)
        TextView chipHint = new TextView(context);
        chipHint.setText("点击下方快捷预设或已发现的单片机通道一键切换:");
        chipHint.setTextSize(12.0f);
        chipHint.setTextColor(0xFFAAAAAA);
        LinearLayout.LayoutParams chipHintLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        chipHintLp.setMargins(dp6, dp8, dp6, dp6);
        channelCard.addView(chipHint, chipHintLp);

        List<String> channels = hub.getAvailableChannels();
        this.channelChipButtons.clear();
        this.channelChipNames.clear();

        LinearLayout chipRow = null;
        for (int i = 0; i < channels.size(); i++) {
            if (i % 4 == 0) {
                chipRow = new LinearLayout(context);
                chipRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                rowLp.setMargins(dp6, 0, dp6, dp6);
                channelCard.addView(chipRow, rowLp);
            }
            final String ch = channels.get(i);
            Button chipBtn = new Button(context, null, R.attr.buttonOutlinedStyle);
            chipBtn.setTextSize(13.0f);
            chipBtn.setOnClickListener(v -> {
                draft.setChannel(ch);
                if (this.channelInput != null) {
                    this.channelInput.setText(ch);
                }
                refreshChipSelection(ch);
                statusView.setText("方块坐标: (" + posKey.x() + ", " + posKey.y() + ", " + posKey.z() + ")   |   "
                        + "WebSocket 端口: " + hub.getPort() + "   |   "
                        + "在线单片机: " + hub.getConnectedDeviceCount() + " 台   |   "
                        + "当前通道实时信号: " + hub.getChannelLastValue(ch) + " / 15");
            });
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
            if (i % 4 != 0) {
                btnLp.leftMargin = dp8;
            }
            chipRow.addView(chipBtn, btnLp);
            this.channelChipButtons.add(chipBtn);
            this.channelChipNames.add(ch);
        }
        refreshChipSelection(draft.channel());

        // 2.3 下行目标通道输入行
        this.downlinkInput = new EditText(context, null, R.attr.editTextOutlinedStyle);
        this.downlinkInput.setSingleLine();
        this.downlinkInput.setText(draft.downlinkChannel());
        this.downlinkInput.setHint("输入下行目标通道名 (如 mc_block)");
        this.downlinkInput.setMinimumWidth(root.dp(200.0f));
        channelCard.addView(createInputRow(context,
                "反向发送目标通道 (本方块触发 -> 发送至 ESP32)",
                this.downlinkInput, dp6));

        addCardToRoot(root, channelCard, dp12);

        // ============================================================
        // 3. 独立通讯方向与信号滤波卡片 (Switch 矢量滑块开关)
        // ============================================================
        LinearLayout isolationCard = createCategoryCard(context, "本方块独立通讯方向与信号隔离", dp16, dp6);

        isolationCard.addView(createSwitchRow(context,
                "接收单片机上行信号 (ESP32 -> 方块红石强度输出)",
                draft.acceptFromDevice(),
                (view, checked) -> draft.setAcceptFromDevice(checked),
                dp6));

        isolationCard.addView(createSwitchRow(context,
                "右键点击此方块时反向发送脉冲至单片机 (MC -> ESP32)",
                draft.sendOnClick(),
                (view, checked) -> draft.setSendOnClick(checked),
                dp6));

        isolationCard.addView(createSwitchRow(context,
                "外部红石充能此方块时反向发送信号至单片机 (MC -> ESP32)",
                draft.sendOnRedstone(),
                (view, checked) -> draft.setSendOnRedstone(checked),
                dp6));

        isolationCard.addView(createSwitchRow(context,
                "红石信号反相输出 (将 0~15 强度反转为 15-x)",
                draft.invertSignal(),
                (view, checked) -> draft.setInvertSignal(checked),
                dp6));

        addCardToRoot(root, isolationCard, dp12);

        // ============================================================
        // 4. 全局网关设置与硬件联调测试卡片
        // ============================================================
        LinearLayout globalCard = createCategoryCard(context, "全局网关设置与硬件联调", dp16, dp6);

        globalCard.addView(createSwitchRow(context,
                "全局世界天气变化自动同步至单片机 (weather 通道)",
                hub.isWeatherSyncEnabled(),
                (view, checked) -> hub.setWeatherSyncEnabled(checked),
                dp6));

        globalCard.addView(createSwitchRow(context,
                "聊天栏显示硬件信号实时收发提示",
                hub.isChatBroadcastEnabled(),
                (view, checked) -> hub.setChatBroadcastEnabled(checked),
                dp6));

        // 快捷下行测试行
        LinearLayout testRow = new LinearLayout(context);
        testRow.setOrientation(LinearLayout.HORIZONTAL);
        testRow.setGravity(Gravity.CENTER_VERTICAL);
        testRow.setPadding(dp6, dp6, dp6, dp6);
        TextView testLabel = new TextView(context);
        testLabel.setText("硬件下行链路联调测试 (立即向当前下行通道发送强度 15)");
        testLabel.setTextSize(14.0f);
        testRow.addView(testLabel, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f));

        Button testBtn = new Button(context, null, R.attr.buttonOutlinedStyle);
        testBtn.setText("⚡ 发送测试脉冲");
        testBtn.setTextSize(13.0f);
        testBtn.setOnClickListener(v -> {
            String target = readText(this.downlinkInput, draft.downlinkChannel());
            if (target.isEmpty()) {
                target = "mc_block";
            }
            hub.broadcastToDevices(target, "ui_test", 15, "pos=" + posKey.x() + "," + posKey.y() + "," + posKey.z());
            Toast.makeText(context, "已向硬件下行通道 [" + target + "] 发送脉冲 15", Toast.LENGTH_SHORT).show();
        });
        testRow.addView(testBtn, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        globalCard.addView(testRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        addCardToRoot(root, globalCard, dp12);

        // ============================================================
        // 5. 底部操作栏 (保存并绑定 / 设为默认放置通道 / 取消)
        // ============================================================
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        bottomLp.topMargin = dp16;

        Button cancelBtn = new Button(context, null, R.attr.buttonOutlinedStyle);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(14.0f);
        cancelBtn.setOnClickListener(v -> closeScreen());
        bottomBar.addView(cancelBtn, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        Button defaultBtn = new Button(context, null, R.attr.buttonTonalStyle);
        defaultBtn.setText("★ 保存并设为默认放置通道");
        defaultBtn.setTextSize(14.0f);
        defaultBtn.setOnClickListener(v -> {
            saveConfig(context, true);
            closeScreen();
        });
        LinearLayout.LayoutParams defaultLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        defaultLp.leftMargin = dp12;
        bottomBar.addView(defaultBtn, defaultLp);

        Button saveBtn = new Button(context, null, R.attr.buttonElevatedStyle);
        saveBtn.setText("✔ 保存并绑定此方块");
        saveBtn.setTextSize(14.0f);
        saveBtn.setOnClickListener(v -> {
            saveConfig(context, false);
            closeScreen();
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        saveLp.leftMargin = dp12;
        bottomBar.addView(saveBtn, saveLp);

        root.addView(bottomBar, bottomLp);

        scrollView.addView(root, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        scrollView.setMaxWidth(scrollView.dp(720.0f));
        return scrollView;
    }

    private void refreshChipSelection(String selectedChannel) {
        for (int i = 0; i < channelChipButtons.size(); i++) {
            Button btn = channelChipButtons.get(i);
            String ch = channelChipNames.get(i);
            boolean active = ch.equals(selectedChannel);
            btn.setText((active ? "● " : "○ ") + ch);
            btn.setTextColor(active ? 0xFF76FF9B : 0xFFE0E0E0);
        }
    }

    private void saveConfig(Context context, boolean alsoSetPending) {
        String up = readText(this.channelInput, draft.channel());
        String down = readText(this.downlinkInput, draft.downlinkChannel());
        draft.setChannel(up);
        draft.setDownlinkChannel(down);

        McIoTHub hub = McIoTHub.getInstance();
        hub.updateBlockConfig(posKey, draft);
        if (alsoSetPending) {
            hub.setPendingBindChannel(draft.channel());
        }

        Toast.makeText(context,
                "已保存方块 (" + posKey.x() + "," + posKey.y() + "," + posKey.z() + ") -> 通道 [" + draft.channel() + "]",
                Toast.LENGTH_SHORT).show();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§a[MC-IoT] 已保存方块 [" + posKey.x() + "," + posKey.y() + "," + posKey.z()
                            + "] -> 通道 §e" + draft.channel()
                            + " §7(接收:" + (draft.acceptFromDevice() ? "开" : "关")
                            + " / 右键发:" + (draft.sendOnClick() ? "开" : "关")
                            + " / 红石发:" + (draft.sendOnRedstone() ? "开" : "关") + ")"),
                    true
            );
        }
    }

    private static String readText(EditText input, String fallback) {
        if (input == null) {
            return fallback;
        }
        Editable editable = input.getText();
        if (editable == null) {
            return fallback;
        }
        String trimmed = editable.toString().trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(null));
    }

    private static LinearLayout createOutlinedCard(Context context, int padding) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(padding, padding, padding, padding);
        ThemeControl.makeOutlinedCard(context, card, new TypedValue());
        return card;
    }

    private static LinearLayout createCategoryCard(Context context, String title, int padOuter, int padInner) {
        LinearLayout card = createOutlinedCard(context, padOuter);

        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(16.0f);
        header.setTextColor(0xFFF3C6B6);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        headerLp.setMargins(padInner, 0, padInner, padInner);
        card.addView(header, headerLp);

        ShapeDrawable divider = new ShapeDrawable();
        divider.setShape(ShapeDrawable.HLINE);
        divider.setSize(-1, Math.max(1, card.dp(1.0f)));
        divider.setColor(0x28FFFFFF);
        card.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        card.setDividerDrawable(divider);
        card.setDividerPadding(padInner);
        return card;
    }

    private static LinearLayout createInputRow(Context context, String labelText, EditText input, int pad) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, pad, pad, pad);

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextSize(14.0f);
        label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        labelLp.rightMargin = pad * 2;
        row.addView(label, labelLp);

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(row.dp(210.0f), WRAP_CONTENT);
        row.addView(input, inputLp);
        return row;
    }

    private static LinearLayout createSwitchRow(
            Context context,
            String labelText,
            boolean initialChecked,
            Checkable.OnCheckedChangeListener listener,
            int pad
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, pad, pad, pad);
        ThemeControl.addBackground(row);

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextSize(14.0f);
        label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.0f);
        labelLp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(label, labelLp);

        Switch button = new Switch(context);
        button.setChecked(initialChecked);
        button.setOnCheckedChangeListener(listener);
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        switchLp.gravity = Gravity.CENTER_VERTICAL;
        switchLp.topMargin = row.dp(3.0f);
        switchLp.bottomMargin = row.dp(3.0f);
        row.addView(button, switchLp);

        row.setOnClickListener(v -> button.toggle());
        return row;
    }

    private static void addCardToRoot(LinearLayout root, LinearLayout card, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        lp.topMargin = topMargin;
        root.addView(card, lp);
    }
}

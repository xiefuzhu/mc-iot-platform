package com.mciot.adapter.v1201;

import com.mciot.core.api.BlockIoTConfig;
import com.mciot.core.api.BlockPosKey;
import com.mciot.core.api.McIoTHub;
import com.mciot.core.api.WorldEventContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * [Minecraft 1.20.1 ~ 1.20.4 适配层] IoT 物联信号收发方块
 * 功能：
 * 1. 放置时自动绑定当前选定的硬件通道 (默认 "btn_boot")
 * 2. 潜行(Shift)+右键：弹出 ModernUI 风格的物联方块配置中心 GUI（选择绑定通道、独立开关上下行通讯、防信号覆盖）
 * 3. 普通右键：在游戏内手动切换方块开关，并按配置【反向推送】信号给 ESP32 单片机。
 * 4. 外部红石激活：当游戏内红石电路给此方块充能时，按配置自动【反向推送】红石强度给 ESP32。
 */
public class IoTTransceiverBlockV1201 extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty POWER = BlockStateProperties.POWER; // 0 ~ 15 红石信号等级

    public IoTTransceiverBlockV1201(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, POWER);
    }

    private static BlockPosKey toKey(Level level, BlockPos pos) {
        return new BlockPosKey(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            String channel = McIoTHub.getInstance().getPendingBindChannel();
            McIoTHub.getInstance().bindBlock(toKey(level, pos), channel);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            McIoTHub.getInstance().unbindBlock(toKey(level, pos));
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            // 潜行+右键：在客户端弹出 ModernUI 风格的通道绑定与通讯配置界面
            if (level.isClientSide) {
                openConfigScreenClient(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) return InteractionResult.SUCCESS;

        // 普通右键：翻转方块开关状态，并按该方块独立配置决定是否反向发送给 ESP32 单片机
        BlockPosKey key = toKey(level, pos);
        boolean nextPowered = !state.getValue(POWERED);
        int nextSignal = nextPowered ? 15 : 0;
        level.setBlock(pos, state.setValue(POWERED, nextPowered).setValue(POWER, nextSignal), 3);
        level.updateNeighborsAt(pos, this);
        for (Direction dir : Direction.values()) {
            level.updateNeighborsAt(pos.relative(dir), this);
        }

        BlockIoTConfig cfg = McIoTHub.getInstance().getBlockConfig(key);
        if (cfg.sendOnClick()) {
            McIoTHub.getInstance().fireWorldEvent(new WorldEventContext(
                    WorldEventContext.Type.BLOCK_INTERACT,
                    "click",
                    nextSignal,
                    cfg.channel(),
                    key
            ));
            player.displayClientMessage(Component.literal("§b[MC-IoT] 已向单片机发送反向信号 (目标: "
                    + cfg.downlinkChannel() + " / 绑定通道: " + cfg.channel() + ", 强度: " + nextSignal + ")"), true);
        } else {
            player.displayClientMessage(Component.literal("§7[MC-IoT] 本地切换方块红石强度: §e" + nextSignal
                    + " §8(右键发送已在 GUI 中关闭)"), true);
        }
        return InteractionResult.CONSUME;
    }

    private static void openConfigScreenClient(String dimension, int x, int y, int z) {
        try {
            Class<?> cls = Class.forName("com.mciot.adapter.v1201.IoTConfigScreenV1201");
            cls.getMethod("open", String.class, int.class, int.class, int.class)
                    .invoke(null, dimension, x, y, z);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
        // 检测外部红石电路输入并按该方块配置反向推送给单片机
        int receivedPower = level.getBestNeighborSignal(pos);
        if (receivedPower > 0 && receivedPower != state.getValue(POWER)) {
            BlockPosKey key = toKey(level, pos);
            BlockIoTConfig cfg = McIoTHub.getInstance().getBlockConfig(key);
            if (cfg.sendOnRedstone()) {
                McIoTHub.getInstance().fireWorldEvent(new WorldEventContext(
                        WorldEventContext.Type.BLOCK_REDSTONE_CHANGE,
                        "redstone_input",
                        receivedPower,
                        cfg.channel(),
                        key
                ));
            }
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? state.getValue(POWER) : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? state.getValue(POWER) : 0;
    }
}

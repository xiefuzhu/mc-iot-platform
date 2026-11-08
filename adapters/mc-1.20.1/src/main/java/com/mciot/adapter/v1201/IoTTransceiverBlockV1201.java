package com.mciot.adapter.v1201;

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
 * 2. 潜行(Shift)+右键：将方块重新绑定到 /mciot bind <channel> 设定的通道
 * 3. 普通右键：在游戏内手动切换方块开关，并【反向推送】信号给 ESP32 单片机。
 * 4. 外部红石激活：当游戏内红石电路给此方块充能时，自动【反向推送】红石强度给 ESP32。
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
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPosKey key = toKey(level, pos);
        if (player.isShiftKeyDown()) {
            // 潜行+右键：绑定到当前待选硬件通道
            String channel = McIoTHub.getInstance().getPendingBindChannel();
            McIoTHub.getInstance().bindBlock(key, channel);
            player.displayClientMessage(Component.literal("§a[MC-IoT] 该方块已绑定至硬件通道: §e" + channel), true);
        } else {
            // 普通右键：翻转方块开关状态，并反向发送给 ESP32 单片机。
            boolean nextPowered = !state.getValue(POWERED);
            int nextSignal = nextPowered ? 15 : 0;
            level.setBlock(pos, state.setValue(POWERED, nextPowered).setValue(POWER, nextSignal), 3);
            level.updateNeighborsAt(pos, this);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }

            String boundChannel = McIoTHub.getInstance().getBoundChannel(key);
            McIoTHub.getInstance().fireWorldEvent(new WorldEventContext(
                    WorldEventContext.Type.BLOCK_INTERACT,
                    "click",
                    nextSignal,
                    boundChannel,
                    key
            ));
            player.displayClientMessage(Component.literal("§b[MC-IoT] 已向单片机发送反向信号 (通道: "
                    + boundChannel + ", 强度: " + nextSignal + ")"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
        // 检测外部红石电路输入并反向推送给单片机
        int receivedPower = level.getBestNeighborSignal(pos);
        if (receivedPower > 0 && receivedPower != state.getValue(POWER)) {
            BlockPosKey key = toKey(level, pos);
            McIoTHub.getInstance().fireWorldEvent(new WorldEventContext(
                    WorldEventContext.Type.BLOCK_REDSTONE_CHANGE,
                    "redstone_input",
                    receivedPower,
                    McIoTHub.getInstance().getBoundChannel(key),
                    key
            ));
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

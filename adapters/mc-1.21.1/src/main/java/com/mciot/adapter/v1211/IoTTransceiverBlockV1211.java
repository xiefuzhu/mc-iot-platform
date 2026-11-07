package com.mciot.adapter.v1211;

import com.mciot.core.api.BlockPosKey;
import com.mciot.core.api.McIoTHub;
import com.mciot.core.api.WorldEventContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
 * [Minecraft 1.20.5 ~ 1.21.1+ 适配层] IoT 物联信号收发方块
 * 适配 1.20.5+ 将 Block.use() 拆分为 useWithoutItem() 的 API 变更。
 */
public class IoTTransceiverBlockV1211 extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public IoTTransceiverBlockV1211(Properties properties) {
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            McIoTHub.getInstance().bindBlock(toKey(level, pos), McIoTHub.getInstance().getPendingBindChannel());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            McIoTHub.getInstance().unbindBlock(toKey(level, pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPosKey key = toKey(level, pos);
        if (player.isShiftKeyDown()) {
            String channel = McIoTHub.getInstance().getPendingBindChannel();
            McIoTHub.getInstance().bindBlock(key, channel);
            player.displayClientMessage(Component.literal("§a[MC-IoT] 该方块已绑定至硬件通道: §e" + channel), true);
        } else {
            boolean nextPowered = !state.getValue(POWERED);
            int nextSignal = nextPowered ? 15 : 0;
            level.setBlock(pos, state.setValue(POWERED, nextPowered).setValue(POWER, nextSignal), 3);
            level.updateNeighborsAt(pos, this);

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
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
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
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? state.getValue(POWER) : 0;
    }
}

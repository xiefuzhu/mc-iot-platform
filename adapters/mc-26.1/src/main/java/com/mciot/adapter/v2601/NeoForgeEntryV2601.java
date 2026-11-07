package com.mciot.adapter.v2601;

import com.mciot.adapter.v1201.McWorldBridgeV1201;
import com.mciot.adapter.v1211.IoTTransceiverBlockV1211;
import com.mciot.core.api.BlockPosKey;
import com.mciot.core.api.IMcWorldBridge;
import com.mciot.core.api.McIoTHub;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * [NeoForge 1.21.2 ~ 26.1+ 入口点]
 * 使用 registerBlock 自带 Properties -> ResourceKey 注入，兼容 26.1 无混淆服务端与客户端。
 */
@Mod(NeoForgeEntryV2601.MOD_ID)
public class NeoForgeEntryV2601 {
    public static final String MOD_ID = "mciot";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredBlock<IoTTransceiverBlockV1211> IOT_TRANSCEIVER = BLOCKS.registerBlock(
            "iot_transceiver",
            IoTTransceiverBlockV1211::new,
            BlockBehaviour.Properties.of()
                    .strength(1.5f)
                    .lightLevel(state -> state.getValue(IoTTransceiverBlockV1211.POWERED) ? 15 : 0)
    );

    public static final DeferredItem<BlockItem> IOT_TRANSCEIVER_ITEM = ITEMS.registerSimpleBlockItem(IOT_TRANSCEIVER);

    public NeoForgeEntryV2601(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener((RegisterCommandsEvent e) -> McWorldBridgeV1201.registerCommands(e.getDispatcher()));
        gameBus.addListener((ServerStartedEvent e) -> McIoTHub.getInstance().onServerStarted(new Bridge(e.getServer())));
        gameBus.addListener((ServerStoppingEvent e) -> McIoTHub.getInstance().onServerStopping());
        gameBus.addListener((ServerTickEvent.Post e) -> McWorldBridgeV1201.onServerTick(e.getServer()));
    }

    private static final class Bridge implements IMcWorldBridge {
        private final MinecraftServer server;
        Bridge(MinecraftServer server) { this.server = server; }

        @Override
        public void runOnServerThread(Runnable task) { server.execute(task); }

        @Override
        public void applyIoTBlockState(BlockPosKey posKey, boolean powered, int redstoneSignal) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(posKey.dimension())) {
                    BlockPos pos = new BlockPos(posKey.x(), posKey.y(), posKey.z());
                    if (!level.isLoaded(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof IoTTransceiverBlockV1211) {
                        BlockState next = state
                                .setValue(IoTTransceiverBlockV1211.POWERED, powered)
                                .setValue(IoTTransceiverBlockV1211.POWER, redstoneSignal);
                        level.setBlock(pos, next, 3);
                        level.updateNeighborsAt(pos, state.getBlock());
                    }
                }
            }
        }

        @Override
        public void broadcastSystemChat(String message) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        }
    }
}

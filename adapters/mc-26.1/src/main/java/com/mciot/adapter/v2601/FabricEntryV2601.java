package com.mciot.adapter.v2601;

import com.mciot.adapter.v1201.McWorldBridgeV1201;
import com.mciot.adapter.v1211.IoTTransceiverBlockV1211;
import com.mciot.core.api.BlockPosKey;
import com.mciot.core.api.IMcWorldBridge;
import com.mciot.core.api.McIoTHub;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [Minecraft 1.21.2 ~ 26.1+ 适配层]
 * 适配 1.21.2 ~ 26.1 强制要求在构造 Block 和 Item 之前调用 .setId(ResourceKey) 的注册表规则，
 * 以及 26.1 官方无混淆 (Unobfuscated) 映射。
 */
public class FabricEntryV2601 implements ModInitializer {
    public static final String MOD_ID = "mciot";

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "iot_transceiver");
    public static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, ID);
    public static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, ID);

    public static final Block IOT_TRANSCEIVER = new IoTTransceiverBlockV1211(
            BlockBehaviour.Properties.of()
                    .setId(BLOCK_KEY)
                    .strength(1.5f)
                    .lightLevel(state -> state.getValue(IoTTransceiverBlockV1211.POWERED) ? 15 : 0)
    );

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY, IOT_TRANSCEIVER);
        Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
                new BlockItem(IOT_TRANSCEIVER, new Item.Properties().setId(ITEM_KEY).useBlockDescriptionPrefix()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                McWorldBridgeV1201.registerCommands(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                McIoTHub.getInstance().onServerStarted(new IMcWorldBridge() {
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
                }));

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                McIoTHub.getInstance().onServerStopping());

        ServerTickEvents.END_SERVER_TICK.register(McWorldBridgeV1201::onServerTick);
    }
}

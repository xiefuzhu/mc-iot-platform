package com.mciot.adapter.v1201;

import com.mciot.core.api.McIoTHub;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * [Fabric 1.20.1 ~ 1.20.4 入口点] 仅 35 行胶水代码。
 */
public class FabricEntryV1201 implements ModInitializer {
    public static final String MOD_ID = "mciot";

    public static final Block IOT_TRANSCEIVER = new IoTTransceiverBlockV1201(
            BlockBehaviour.Properties.of()
                    .strength(1.5f)
                    .lightLevel(state -> state.getValue(IoTTransceiverBlockV1201.POWERED) ? 15 : 0)
    );

    @Override
    public void onInitialize() {
        ResourceLocation id = new ResourceLocation(MOD_ID, "iot_transceiver");
        Registry.register(BuiltInRegistries.BLOCK, id, IOT_TRANSCEIVER);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(IOT_TRANSCEIVER, new Item.Properties()));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(MOD_ID, "main"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.mciot"))
                        .icon(() -> new ItemStack(IOT_TRANSCEIVER))
                        .displayItems((params, output) -> output.accept(IOT_TRANSCEIVER))
                        .build());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                McWorldBridgeV1201.registerCommands(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                McIoTHub.getInstance().onServerStarted(new McWorldBridgeV1201(server)));

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                McIoTHub.getInstance().onServerStopping());

        ServerTickEvents.END_SERVER_TICK.register(McWorldBridgeV1201::onServerTick);
    }
}

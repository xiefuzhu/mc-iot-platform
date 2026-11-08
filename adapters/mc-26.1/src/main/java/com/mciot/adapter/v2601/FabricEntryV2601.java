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
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

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
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"),
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

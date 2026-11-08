package com.mciot.adapter.v1201;

import com.mciot.core.api.McIoTHub;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * [Forge 1.20.1 入口点] 同样仅 45 行胶水代码。
 */
@Mod(ForgeEntryV1201.MOD_ID)
public class ForgeEntryV1201 {
    public static final String MOD_ID = "mciot";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Block> IOT_TRANSCEIVER = BLOCKS.register("iot_transceiver",
            () -> new IoTTransceiverBlockV1201(BlockBehaviour.Properties.of()
                    .strength(1.5f)
                    .lightLevel(state -> state.getValue(IoTTransceiverBlockV1201.POWERED) ? 15 : 0)));

    public static final RegistryObject<Item> IOT_TRANSCEIVER_ITEM = ITEMS.register("iot_transceiver",
            () -> new BlockItem(IOT_TRANSCEIVER.get(), new Item.Properties()));

    public ForgeEntryV1201() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener((RegisterCommandsEvent e) -> McWorldBridgeV1201.registerCommands(e.getDispatcher()));
        forgeBus.addListener((ServerStartedEvent e) -> McIoTHub.getInstance().onServerStarted(new McWorldBridgeV1201(e.getServer())));
        forgeBus.addListener((ServerStoppingEvent e) -> McIoTHub.getInstance().onServerStopping());
        forgeBus.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                McWorldBridgeV1201.onServerTick(e.getServer());
            }
        });
    }
}

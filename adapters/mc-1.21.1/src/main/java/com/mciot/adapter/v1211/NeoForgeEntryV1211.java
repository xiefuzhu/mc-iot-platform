package com.mciot.adapter.v1211;

import com.mciot.adapter.v1201.McWorldBridgeV1201;
import com.mciot.core.api.BlockPosKey;
import com.mciot.core.api.IMcWorldBridge;
import com.mciot.core.api.McIoTHub;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * [NeoForge 1.21.1 入口点与桥接器]
 */
@Mod(NeoForgeEntryV1211.MOD_ID)
public class NeoForgeEntryV1211 {
    public static final String MOD_ID = "mciot";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredBlock<Block> IOT_TRANSCEIVER = BLOCKS.register("iot_transceiver",
            () -> new IoTTransceiverBlockV1211(BlockBehaviour.Properties.of()
                    .strength(1.5f)
                    .lightLevel(state -> state.getValue(IoTTransceiverBlockV1211.POWERED) ? 15 : 0)));

    public static final DeferredItem<BlockItem> IOT_TRANSCEIVER_ITEM = ITEMS.registerSimpleBlockItem(IOT_TRANSCEIVER);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MCIOT_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mciot"))
                    .icon(() -> new ItemStack(IOT_TRANSCEIVER_ITEM.get()))
                    .displayItems((params, output) -> output.accept(IOT_TRANSCEIVER_ITEM.get()))
                    .build());

    public NeoForgeEntryV1211(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener((RegisterCommandsEvent e) -> McWorldBridgeV1201.registerCommands(e.getDispatcher()));
        gameBus.addListener((ServerStartedEvent e) -> McIoTHub.getInstance().onServerStarted(new McWorldBridgeV1201(e.getServer())));
        gameBus.addListener((ServerStoppingEvent e) -> McIoTHub.getInstance().onServerStopping());
        gameBus.addListener((ServerTickEvent.Post e) -> McWorldBridgeV1201.onServerTick(e.getServer()));
    }
}

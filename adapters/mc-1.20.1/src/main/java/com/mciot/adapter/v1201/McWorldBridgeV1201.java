package com.mciot.adapter.v1201;

import com.mciot.core.api.BlockPosKey;
import com.mciot.core.api.IMcWorldBridge;
import com.mciot.core.api.McIoTHub;
import com.mciot.core.api.WorldEventContext;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * [Minecraft 1.20.1 ~ 1.20.4 适配层] 世界桥接与指令注册
 * Fabric 与 Forge 共用这一个不到 120 行的桥接器。
 */
public final class McWorldBridgeV1201 implements IMcWorldBridge {
    private final MinecraftServer server;

    public McWorldBridgeV1201(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void runOnServerThread(Runnable task) {
        server.execute(task);
    }

    @Override
    public void applyIoTBlockState(BlockPosKey posKey, boolean powered, int redstoneSignal) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(posKey.dimension())) {
                BlockPos pos = new BlockPos(posKey.x(), posKey.y(), posKey.z());
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(BlockStateProperties.POWERED) && state.hasProperty(BlockStateProperties.POWER)) {
                    BlockState next = state
                            .setValue(BlockStateProperties.POWERED, powered)
                            .setValue(BlockStateProperties.POWER, redstoneSignal);
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

    /**
     * 在服务端 Tick 中调用（每 20 Tick = 1 秒检查一次主世界天气变化并反向同步给 ESP32）
     */
    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld.getGameTime() % 20 != 0) return;

        String weather = overworld.isThundering() ? "thunder" : (overworld.isRaining() ? "rain" : "clear");
        int intensity = overworld.isThundering() ? 15 : (overworld.isRaining() ? 8 : 0);
        McIoTHub.getInstance().fireWorldEvent(new WorldEventContext(
                WorldEventContext.Type.WEATHER_CHANGE,
                weather,
                intensity,
                "",
                null
        ));
    }

    /**
     * 注册 /mciot 指令集：
     * - /mciot status                        查看当前连接的单片机数量与监听端口
     * - /mciot bind <channel>                设置接下来放置/潜行右键方块时绑定的硬件通道名
     * - /mciot port <port>                   动态修改监听端口（方便内网穿透）
     * - /mciot send <channel> <event> <val>  在游戏内直接向单片机发送任意反向信号。
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mciot")
                .then(Commands.literal("status").executes(ctx -> {
                    McIoTHub hub = McIoTHub.getInstance();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a[MC-IoT] 端口: §e" + hub.getPort()
                            + " §a| 在线单片机: §e" + hub.getConnectedDeviceCount()
                            + " §a| 当前待绑通道: §e" + hub.getPendingBindChannel()
                            + " §a| 已加载插件数: §e" + hub.getPlugins().size()), false);
                    return 1;
                }))
                .then(Commands.literal("bind")
                        .then(Commands.argument("channel", StringArgumentType.word()).executes(ctx -> {
                            String ch = StringArgumentType.getString(ctx, "channel");
                            McIoTHub.getInstance().setPendingBindChannel(ch);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§a[MC-IoT] 当前绑定通道已设为: §e" + ch + " §7(放置或 Shift+右键 IoT 方块即可绑定)"), false);
                            return 1;
                        })))
                .then(Commands.literal("port")
                        .then(Commands.argument("port", IntegerArgumentType.integer(1024, 65535)).executes(ctx -> {
                            int p = IntegerArgumentType.getInteger(ctx, "port");
                            boolean ok = McIoTHub.getInstance().changePort(p);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    ok ? "§a[MC-IoT] 服务已切换至端口: §e" + p : "§c[MC-IoT] 端口绑定失败: " + p), false);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("send")
                        .then(Commands.argument("channel", StringArgumentType.word())
                                .then(Commands.argument("event", StringArgumentType.word())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 255)).executes(ctx -> {
                                            String ch = StringArgumentType.getString(ctx, "channel");
                                            String ev = StringArgumentType.getString(ctx, "event");
                                            int val = IntegerArgumentType.getInteger(ctx, "value");
                                            McIoTHub.getInstance().broadcastToDevices(ch, ev, val, "");
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "§b[MC-IoT] 已向所有单片机下发: [" + ch + "] " + ev + " = " + val), false);
                                            return 1;
                                        })))))
        );
    }
}

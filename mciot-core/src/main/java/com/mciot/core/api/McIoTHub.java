package com.mciot.core.api;

import com.mciot.core.network.McIoTServer;
import com.mciot.core.plugin.BlockChannelSyncPlugin;
import com.mciot.core.plugin.WorldEnvironmentSyncPlugin;
import com.mciot.core.protocol.IoTMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MC-IoT 跨版本核心中枢（单例）。
 * 负责管理：
 * 1. 内置双向 WebSocket/HTTP 服务端 (McIoTServer)
 * 2. 跨版本插件注册表 (IMcIoTPlugin)
 * 3. 游戏内 IoT 方块坐标 <-> 硬件通道 (Channel) 的绑定映射表
 */
public final class McIoTHub {
    private static final Path BINDINGS_FILE = Paths.get("mciot-bindings.txt");
    private static final McIoTHub INSTANCE = new McIoTHub();

    private final McIoTServer netServer;
    private final List<IMcIoTPlugin> plugins = new CopyOnWriteArrayList<>();
    private final Map<BlockPosKey, String> posToChannel = new ConcurrentHashMap<>();
    private final Map<String, Set<BlockPosKey>> channelToPositions = new ConcurrentHashMap<>();
    private final Map<String, Integer> channelLastValue = new ConcurrentHashMap<>();

    private volatile IMcWorldBridge worldBridge;
    private volatile String pendingBindChannel = "btn_boot"; // 默认右键绑定的通道名
    private int configuredPort = 25580;

    private McIoTHub() {
        this.netServer = new McIoTServer(this::handleIncomingFromDevice);
        loadBindingsFromDisk();
        // 默认挂载两个核心双向插件
        registerPlugin(new BlockChannelSyncPlugin());
        registerPlugin(new WorldEnvironmentSyncPlugin());
    }

    public static McIoTHub getInstance() {
        return INSTANCE;
    }

    /**
     * 注册自定义 MC 物联插件（一次编写，1.20.1 ~ 26.1 全版本生效）
     */
    public void registerPlugin(IMcIoTPlugin plugin) {
        if (plugin == null) return;
        plugins.removeIf(p -> p.id().equals(plugin.id()));
        plugins.add(plugin);
        System.out.println("[McIoT] Registered plugin: " + plugin.id());
    }

    public List<IMcIoTPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    /**
     * 当 Minecraft 服务端启动时由各版本 Adapter 调用
     */
    public void onServerStarted(IMcWorldBridge bridge) {
        this.worldBridge = bridge;
        loadBindingsFromDisk();
        this.netServer.start(configuredPort);
    }

    /**
     * 当 Minecraft 服务端关闭时由各版本 Adapter 调用
     */
    public void onServerStopping() {
        saveBindingsToDisk();
        this.netServer.stop();
        this.worldBridge = null;
    }

    /**
     * 动态切换监听端口并重启网络服务
     */
    public boolean changePort(int newPort) {
        this.configuredPort = newPort;
        return this.netServer.start(newPort);
    }

    public int getPort() { return netServer.getPort(); }
    public int getConnectedDeviceCount() { return netServer.getConnectedCount(); }

    public String getPendingBindChannel() { return pendingBindChannel; }
    public void setPendingBindChannel(String channel) {
        if (channel != null && !channel.isBlank()) {
            this.pendingBindChannel = channel.trim();
        }
    }

    // ========================================================================
    // 方块坐标 <-> 硬件通道 (Channel) 绑定管理
    // ========================================================================
    public void bindBlock(BlockPosKey pos, String channel) {
        unbindBlockInternal(pos);
        posToChannel.put(pos, channel);
        channelToPositions.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(pos);
        saveBindingsToDisk();

        // 如果该通道已有最新状态，立即同步到新绑定的方块
        Integer lastVal = channelLastValue.get(channel);
        if (lastVal != null && worldBridge != null) {
            int signal = Math.max(0, Math.min(15, lastVal));
            worldBridge.applyIoTBlockState(pos, signal > 0, signal);
        }
    }

    public void unbindBlock(BlockPosKey pos) {
        if (unbindBlockInternal(pos)) {
            saveBindingsToDisk();
        }
    }

    private boolean unbindBlockInternal(BlockPosKey pos) {
        String oldChannel = posToChannel.remove(pos);
        if (oldChannel != null) {
            Set<BlockPosKey> set = channelToPositions.get(oldChannel);
            if (set != null) set.remove(pos);
            return true;
        }
        return false;
    }

    public boolean isBlockBound(BlockPosKey pos) {
        return posToChannel.containsKey(pos);
    }

    private synchronized void saveBindingsToDisk() {
        try (BufferedWriter writer = Files.newBufferedWriter(BINDINGS_FILE, StandardCharsets.UTF_8)) {
            for (Map.Entry<BlockPosKey, String> entry : posToChannel.entrySet()) {
                BlockPosKey k = entry.getKey();
                writer.write(k.dimension() + "|" + k.x() + "|" + k.y() + "|" + k.z() + "|" + entry.getValue());
                writer.newLine();
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void loadBindingsFromDisk() {
        if (!Files.exists(BINDINGS_FILE)) return;
        try (BufferedReader reader = Files.newBufferedReader(BINDINGS_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\|");
                if (parts.length == 5) {
                    BlockPosKey key = new BlockPosKey(
                            parts[0],
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3])
                    );
                    String ch = parts[4];
                    posToChannel.put(key, ch);
                    channelToPositions.computeIfAbsent(ch, k -> ConcurrentHashMap.newKeySet()).add(key);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public String getBoundChannel(BlockPosKey pos) {
        return posToChannel.getOrDefault(pos, pendingBindChannel);
    }

    public Set<BlockPosKey> getBlocksForChannel(String channel) {
        return channelToPositions.getOrDefault(channel, Collections.emptySet());
    }

    public void updateChannelState(String channel, int signalValue) {
        int clamped = Math.max(0, Math.min(15, signalValue));
        channelLastValue.put(channel, clamped);
        IMcWorldBridge bridge = this.worldBridge;
        if (bridge == null) return;

        for (BlockPosKey pos : getBlocksForChannel(channel)) {
            bridge.applyIoTBlockState(pos, clamped > 0, clamped);
        }
    }

    // ========================================================================
    // 上下行事件路由总线
    // ========================================================================

    /**
     * 收到来自 ESP32 的上行数据包 -> 自动切换到 MC 主线程并分发给所有插件
     */
    private void handleIncomingFromDevice(IoTMessage message) {
        IMcWorldBridge bridge = this.worldBridge;
        if (bridge == null) return;

        bridge.runOnServerThread(() -> {
            for (IMcIoTPlugin plugin : plugins) {
                try {
                    plugin.onDeviceMessage(bridge, message);
                } catch (Exception e) {
                    System.err.println("[McIoT] Plugin " + plugin.id() + " error onDeviceMessage: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 收到来自 Minecraft 世界的事件（如天气变成雷暴、玩家点击方块） -> 分发给所有插件
     */
    public void fireWorldEvent(WorldEventContext event) {
        IMcWorldBridge bridge = this.worldBridge;
        if (bridge == null) return;

        for (IMcIoTPlugin plugin : plugins) {
            try {
                plugin.onWorldEvent(bridge, event);
            } catch (Exception e) {
                System.err.println("[McIoT] Plugin " + plugin.id() + " error onWorldEvent: " + e.getMessage());
            }
        }
    }

    /**
     * 【核心下行能力】主动向所有连接的单片机发送信号 (MC -> ESP32)
     */
    public void broadcastToDevices(String channel, String event, int value, String data) {
        IoTMessage msg = new IoTMessage("mc-server", channel, event, value, data);
        netServer.broadcast(msg);
    }
}

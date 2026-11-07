package com.mciot.core.network;

import com.mciot.core.protocol.IoTMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 零第三方库依赖的轻量级 WebSocket + HTTP 双模服务端。
 * 体积不到 200 行，天然支持局域网直连与 FRP / Ngrok / Cloudflare Tunnel 内网穿透。
 */
public final class McIoTServer {
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Consumer<IoTMessage> onMessageCallback;
    private final List<ClientSession> sessions = new CopyOnWriteArrayList<>();
    private ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private int currentPort = 25580;

    public McIoTServer(Consumer<IoTMessage> onMessageCallback) {
        this.onMessageCallback = onMessageCallback;
    }

    public synchronized boolean start(int port) {
        stop();
        this.currentPort = port;
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
            running = true;
            executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "McIoT-NetWorker");
                t.setDaemon(true);
                return t;
            });
            executor.submit(this::acceptLoop);
            System.out.println("[McIoT] Bidirectional Server started on port " + port + " (WebSocket /ws & HTTP /api/event)");
            return true;
        } catch (IOException e) {
            System.err.println("[McIoT] Failed to bind port " + port + ": " + e.getMessage());
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        for (ClientSession session : sessions) {
            session.close();
        }
        sessions.clear();
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public int getPort() { return currentPort; }
    public boolean isRunning() { return running; }
    public int getConnectedCount() { return sessions.size(); }

    /**
     * 【核心反向推送】向所有已连接的 ESP32 单片机实时下发 JSON 消息 (MC -> ESP32)
     */
    public void broadcast(IoTMessage message) {
        String json = message.toJson();
        byte[] frame = encodeWsTextFrame(json);
        for (ClientSession session : sessions) {
            session.sendFrame(frame);
        }
    }

    private void acceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                client.setTcpNoDelay(true);
                executor.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (running) {
                    System.err.println("[McIoT] Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            String wsKey = null;
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String headerName = line.substring(0, colon).trim().toLowerCase();
                    String headerVal = line.substring(colon + 1).trim();
                    if ("sec-websocket-key".equals(headerName)) {
                        wsKey = headerVal;
                    } else if ("content-length".equals(headerName)) {
                        try { contentLength = Integer.parseInt(headerVal); } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // 1. 如果是 WebSocket 升级请求 (ESP32 双向长连接)
            if (wsKey != null) {
                String acceptKey = computeWebSocketAccept(wsKey);
                String response = "HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();

                ClientSession session = new ClientSession(socket, out);
                sessions.add(session);
                try {
                    readWebSocketLoop(in, session);
                } finally {
                    sessions.remove(session);
                    session.close();
                }
                return;
            }

            // 2. 如果是 HTTP POST /api/event (支持 curl / 网页 / 纯 HTTP 内网穿透直接发包)
            if (requestLine.startsWith("POST ") && contentLength > 0 && contentLength <= 4096) {
                char[] bodyChars = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = reader.read(bodyChars, read, contentLength - read);
                    if (r < 0) break;
                    read += r;
                }
                String jsonBody = new String(bodyChars, 0, read);
                IoTMessage msg = IoTMessage.fromJson(jsonBody);
                if (msg != null && onMessageCallback != null) {
                    onMessageCallback.accept(msg);
                }
                String resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 11\r\nConnection: close\r\n\r\n{\"ok\":true}";
                out.write(resp.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } else {
                String info = "{\"service\":\"MC-IoT Bidirectional Server\",\"clients\":" + sessions.size() + "}";
                byte[] body = info.getBytes(StandardCharsets.UTF_8);
                String resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                out.write(resp.getBytes(StandardCharsets.UTF_8));
                out.write(body);
                out.flush();
            }
            socket.close();
        } catch (Exception e) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void readWebSocketLoop(InputStream in, ClientSession session) throws IOException {
        while (running && !session.socket.isClosed()) {
            int b0 = in.read();
            if (b0 < 0) break;
            int b1 = in.read();
            if (b1 < 0) break;

            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long payloadLen = b1 & 0x7F;

            if (payloadLen == 126) {
                payloadLen = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else if (payloadLen == 127 || payloadLen > 8192) {
                break;
            }

            byte[] mask = new byte[4];
            if (masked) {
                readFully(in, mask, 4);
            }

            byte[] payload = new byte[(int) payloadLen];
            readFully(in, payload, (int) payloadLen);

            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i & 3];
                }
            }

            if (opcode == 0x08) { // Close
                break;
            } else if (opcode == 0x09) { // Ping -> Pong
                session.sendFrame(new byte[]{(byte) 0x8A, 0x00});
            } else if (opcode == 0x01) { // Text Frame
                String text = new String(payload, StandardCharsets.UTF_8);
                if (text.contains("\"ping\"")) {
                    session.sendFrame(encodeWsTextFrame("{\"type\":\"pong\"}"));
                    continue;
                }
                IoTMessage msg = IoTMessage.fromJson(text);
                if (msg != null && onMessageCallback != null) {
                    onMessageCallback.accept(msg);
                }
            }
        }
    }

    private static void readFully(InputStream in, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) throw new IOException("EOF");
            off += r;
        }
    }

    private static byte[] encodeWsTextFrame(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int len = data.length;
        byte[] frame;
        if (len <= 125) {
            frame = new byte[2 + len];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) len;
            System.arraycopy(data, 0, frame, 2, len);
        } else {
            frame = new byte[4 + len];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) 126;
            frame[2] = (byte) ((len >> 8) & 0xFF);
            frame[3] = (byte) (len & 0xFF);
            System.arraycopy(data, 0, frame, 4, len);
        }
        return frame;
    }

    private static String computeWebSocketAccept(String key) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hash = sha1.digest((key + WS_GUID).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static final class ClientSession {
        final Socket socket;
        final OutputStream out;

        ClientSession(Socket socket, OutputStream out) {
            this.socket = socket;
            this.out = out;
        }

        synchronized void sendFrame(byte[] frame) {
            try {
                out.write(frame);
                out.flush();
            } catch (IOException e) {
                close();
            }
        }

        void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}

package com.configtool.agent;

import com.google.gson.*;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.*;

public class WebSocketClient extends org.java_websocket.client.WebSocketClient {
    private static final int CHUNK_SIZE = 512 * 1024; // 512 KB chunks
    private final ConfigToolAgent plugin;
    private final FileOperations fileOps;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeat;

    public WebSocketClient(ConfigToolAgent plugin, String uri, String token, FileOperations fileOps) throws Exception {
        super(new URI(uri));
        this.plugin = plugin;
        this.fileOps = fileOps;
        addHeader("Authorization", "Bearer " + token);
        setConnectionLostTimeout(60);
    }

    @Override
    public void onOpen(ServerHandshake h) {
        plugin.getLogger().info("Connected to ConfigTool server");
        heartbeat = scheduler.scheduleAtFixedRate(() -> {
            if (isOpen()) send(gson.toJson(Map.of("type", "HEARTBEAT")));
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(String msg) {
        try {
            JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
            String type = json.get("type").getAsString();
            switch (type) {
                case "WELCOME" -> plugin.getLogger().info("Server acknowledged connection");
                case "HEARTBEAT_ACK" -> {}
                case "LIST_FILES" -> handleListFiles(json);
                case "READ_FILE" -> handleReadFile(json);
                case "WRITE_FILE" -> handleWriteFile(json);
                case "DELETE_FILE" -> handleDeleteFile(json);
                case "CREATE_FILE" -> handleCreateFile(json);
                case "RENAME_FILE" -> handleRenameFile(json);
                case "EXECUTE_COMMAND" -> handleExecuteCommand(json);
                case "BROADCAST", "KICK_PLAYER", "TOGGLE_WHITELIST" -> handleQuickAction(json);
                case "GET_WORLDS", "SET_GAMERULE" -> handleWorldAction(json);
                case "GET_PLUGINS", "ENABLE_PLUGIN", "DISABLE_PLUGIN" -> handlePluginAction(json);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing message: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (heartbeat != null) heartbeat.cancel(false);
        if (code == 1008) {
            plugin.getLogger().severe("Connection rejected: Invalid token. Check your config.yml");
            return;
        }
        plugin.getLogger().warning("Disconnected (code=" + code + "): " + reason);
        if (remote && code != 1000) plugin.reconnect();
    }

    @Override
    public void onError(Exception e) {
        plugin.getLogger().warning("WebSocket error: " + e.getMessage());
    }

    private void handleListFiles(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        String dir = data.has("directory") ? data.get("directory").getAsString() : "plugins/";
        int offset = data.has("offset") ? data.get("offset").getAsInt() : 0;
        int limit = data.has("limit") ? data.get("limit").getAsInt() : 100;
        plugin.getLogger().info("Listing files in: " + dir + " (offset=" + offset + ", limit=" + limit + ")");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var result = fileOps.listFiles(dir, offset, limit);
                plugin.getLogger().info("Returning " + ((java.util.List<?>)result.get("files")).size() + " of " + result.get("total") + " files");
                sendResponse(reqId, result);
            } catch (Exception e) {
                plugin.getLogger().warning("Error listing files: " + e.getMessage());
                sendError(reqId, e.getMessage());
            }
        });
    }

    private void handleReadFile(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        String path = json.getAsJsonObject("data").get("path").getAsString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String content = fileOps.readFile(path);
                if (content.length() <= CHUNK_SIZE) {
                    sendResponse(reqId, Map.of("content", content));
                } else {
                    sendChunked(reqId, content);
                }
            } catch (Exception e) {
                sendError(reqId, e.getMessage());
            }
        });
    }

    private void sendChunked(String reqId, String content) {
        int totalChunks = (int) Math.ceil((double) content.length() / CHUNK_SIZE);
        plugin.getLogger().info("Sending large file in " + totalChunks + " chunks");

        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, content.length());
            String chunk = content.substring(start, end);
            boolean isLast = (i == totalChunks - 1);

            Map<String, Object> chunkData = Map.of(
                "chunkIndex", i,
                "totalChunks", totalChunks,
                "content", chunk,
                "isLast", isLast
            );

            if (isOpen()) {
                send(gson.toJson(Map.of(
                    "type", "FILE_CHUNK",
                    "requestId", reqId,
                    "data", chunkData
                )));
            }

            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
    }

    private void handleWriteFile(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        String path = data.get("path").getAsString();
        String content = data.get("content").getAsString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { fileOps.writeFile(path, content); sendResponse(reqId, Map.of("success", true)); }
            catch (Exception e) { sendError(reqId, e.getMessage()); }
        });
    }

    private void handleDeleteFile(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        String path = json.getAsJsonObject("data").get("path").getAsString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { fileOps.deleteFile(path); sendResponse(reqId, Map.of("success", true)); }
            catch (Exception e) { sendError(reqId, e.getMessage()); }
        });
    }

    private void handleCreateFile(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        String path = data.get("path").getAsString();
        boolean isDirectory = data.has("isDirectory") && data.get("isDirectory").getAsBoolean();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { fileOps.createFile(path, isDirectory); sendResponse(reqId, Map.of("success", true)); }
            catch (Exception e) { sendError(reqId, e.getMessage()); }
        });
    }

    private void handleRenameFile(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        String oldPath = data.get("oldPath").getAsString();
        String newPath = data.get("newPath").getAsString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { fileOps.renameFile(oldPath, newPath); sendResponse(reqId, Map.of("success", true)); }
            catch (Exception e) { sendError(reqId, e.getMessage()); }
        });
    }

    private void handleExecuteCommand(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        String cmd = json.getAsJsonObject("data").get("command").getAsString();
        if (!isAllowedCommand(cmd)) { sendError(reqId, "Command not allowed"); return; }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
                sendResponse(reqId, Map.of("output", "Command executed"));
            } catch (Exception e) { sendError(reqId, e.getMessage()); }
        });
    }

    private boolean isAllowedCommand(String cmd) {
        String l = cmd.toLowerCase().trim();
        return l.endsWith(" reload") || l.equals("reload") || l.equals("rl")
            || l.startsWith("plugman ") || l.startsWith("say ") || l.startsWith("kick ")
            || l.startsWith("whitelist ") || l.startsWith("gamerule ") || l.startsWith("worldborder ");
    }

    private void sendResponse(String reqId, Map<String, Object> data) {
        if (isOpen()) send(gson.toJson(Map.of("type", "RESPONSE", "requestId", reqId, "data", data)));
    }

    private void sendError(String reqId, String error) {
        if (isOpen()) send(gson.toJson(Map.of("type", "ERROR", "requestId", reqId, "data", Map.of("error", error))));
    }

    public void sendPush(String type, Map<String, Object> data) {
        if (isOpen()) send(gson.toJson(Map.of("type", type, "data", data)));
    }

    private void handleWorldAction(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        String type = json.get("type").getAsString();
        JsonObject data = json.has("data") ? json.getAsJsonObject("data") : new JsonObject();
        if (plugin.getWorldManager() != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                var result = plugin.getWorldManager().handleAction(type, data);
                sendResponse(reqId, Map.of("data", result.toString()));
            });
        } else {
            sendError(reqId, "World management not available");
        }
    }

    private void handlePluginAction(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        String type = json.get("type").getAsString();
        JsonObject data = json.has("data") ? json.getAsJsonObject("data") : new JsonObject();
        if (plugin.getPluginManager() != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                var result = plugin.getPluginManager().handleAction(type, data);
                sendResponse(reqId, Map.of("data", result.toString()));
            });
        } else {
            sendError(reqId, "Plugin management not available");
        }
    }

    private void handleQuickAction(JsonObject json) {
        String reqId = json.get("requestId").getAsString();
        String type = json.get("type").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        if (plugin.getQuickActions() != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var result = plugin.getQuickActions().handleAction(type, data);
                sendResponse(reqId, result);
            });
        } else {
            sendError(reqId, "Quick actions not available");
        }
    }
}

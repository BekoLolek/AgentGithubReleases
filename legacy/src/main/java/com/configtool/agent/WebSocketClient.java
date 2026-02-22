package com.configtool.agent;

import com.google.gson.*;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class WebSocketClient extends org.java_websocket.client.WebSocketClient {
    private static final int CHUNK_SIZE = 512 * 1024;
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
        heartbeat = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (isOpen()) {
                    Map<String, Object> msg = new HashMap<String, Object>();
                    msg.put("type", "HEARTBEAT");
                    send(gson.toJson(msg));
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(String msg) {
        try {
            JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
            String type = json.get("type").getAsString();
            switch (type) {
                case "WELCOME":
                    plugin.getLogger().info("Server acknowledged connection");
                    break;
                case "HEARTBEAT_ACK":
                    break;
                case "LIST_FILES":
                    handleListFiles(json);
                    break;
                case "READ_FILE":
                    handleReadFile(json);
                    break;
                case "WRITE_FILE":
                    handleWriteFile(json);
                    break;
                case "DELETE_FILE":
                    handleDeleteFile(json);
                    break;
                case "CREATE_FILE":
                    handleCreateFile(json);
                    break;
                case "RENAME_FILE":
                    handleRenameFile(json);
                    break;
                case "EXECUTE_COMMAND":
                    handleExecuteCommand(json);
                    break;
                case "BROADCAST":
                case "KICK_PLAYER":
                case "TOGGLE_WHITELIST":
                    handleQuickAction(json);
                    break;
                case "GET_WORLDS":
                case "SET_GAMERULE":
                    handleWorldAction(json);
                    break;
                case "GET_PLUGINS":
                case "ENABLE_PLUGIN":
                case "DISABLE_PLUGIN":
                    handlePluginAction(json);
                    break;
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

    private void handleListFiles(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        final String dir = data.has("directory") ? data.get("directory").getAsString() : "plugins/";
        final int offset = data.has("offset") ? data.get("offset").getAsInt() : 0;
        final int limit = data.has("limit") ? data.get("limit").getAsInt() : 100;
        plugin.getLogger().info("Listing files in: " + dir + " (offset=" + offset + ", limit=" + limit + ")");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    Map<String, Object> result = fileOps.listFiles(dir, offset, limit);
                    @SuppressWarnings("unchecked")
                    List<?> files = (List<?>) result.get("files");
                    plugin.getLogger().info("Returning " + files.size() + " of " + result.get("total") + " files");
                    sendResponse(reqId, result);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error listing files: " + e.getMessage());
                    sendError(reqId, e.getMessage());
                }
            }
        });
    }

    private void handleReadFile(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        final String path = json.getAsJsonObject("data").get("path").getAsString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    String content = fileOps.readFile(path);
                    if (content.length() <= CHUNK_SIZE) {
                        Map<String, Object> data = new HashMap<String, Object>();
                        data.put("content", content);
                        sendResponse(reqId, data);
                    } else {
                        sendChunked(reqId, content);
                    }
                } catch (Exception e) {
                    sendError(reqId, e.getMessage());
                }
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

            Map<String, Object> chunkData = new HashMap<String, Object>();
            chunkData.put("chunkIndex", i);
            chunkData.put("totalChunks", totalChunks);
            chunkData.put("content", chunk);
            chunkData.put("isLast", isLast);

            if (isOpen()) {
                Map<String, Object> msg = new HashMap<String, Object>();
                msg.put("type", "FILE_CHUNK");
                msg.put("requestId", reqId);
                msg.put("data", chunkData);
                send(gson.toJson(msg));
            }

            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
    }

    private void handleWriteFile(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        final String path = data.get("path").getAsString();
        final String content = data.get("content").getAsString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    fileOps.writeFile(path, content);
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("success", true);
                    sendResponse(reqId, result);
                } catch (Exception e) {
                    sendError(reqId, e.getMessage());
                }
            }
        });
    }

    private void handleDeleteFile(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        final String path = json.getAsJsonObject("data").get("path").getAsString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    fileOps.deleteFile(path);
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("success", true);
                    sendResponse(reqId, result);
                } catch (Exception e) {
                    sendError(reqId, e.getMessage());
                }
            }
        });
    }

    private void handleCreateFile(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        final JsonObject data = json.getAsJsonObject("data");
        final String path = data.get("path").getAsString();
        final boolean isDirectory = data.has("isDirectory") && data.get("isDirectory").getAsBoolean();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    fileOps.createFile(path, isDirectory);
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("success", true);
                    sendResponse(reqId, result);
                } catch (Exception e) {
                    sendError(reqId, e.getMessage());
                }
            }
        });
    }

    private void handleRenameFile(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        final JsonObject data = json.getAsJsonObject("data");
        final String oldPath = data.get("oldPath").getAsString();
        final String newPath = data.get("newPath").getAsString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    fileOps.renameFile(oldPath, newPath);
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("success", true);
                    sendResponse(reqId, result);
                } catch (Exception e) {
                    sendError(reqId, e.getMessage());
                }
            }
        });
    }

    private void handleExecuteCommand(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        final String cmd = json.getAsJsonObject("data").get("command").getAsString();
        if (!isAllowedCommand(cmd)) {
            sendError(reqId, "Command not allowed");
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("output", "Command executed");
                    sendResponse(reqId, result);
                } catch (Exception e) {
                    sendError(reqId, e.getMessage());
                }
            }
        });
    }

    private boolean isAllowedCommand(String cmd) {
        String l = cmd.toLowerCase().trim();
        return l.endsWith(" reload") || l.equals("reload") || l.equals("rl")
            || l.startsWith("plugman ") || l.startsWith("say ") || l.startsWith("kick ")
            || l.startsWith("whitelist ") || l.startsWith("gamerule ") || l.startsWith("worldborder ");
    }

    private void handleQuickAction(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        final String type = json.get("type").getAsString();
        final JsonObject data = json.getAsJsonObject("data");
        if (plugin.getQuickActions() != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    Map<String, Object> result = plugin.getQuickActions().handleAction(type, data);
                    sendResponse(reqId, result);
                }
            });
        } else {
            sendError(reqId, "Quick actions not available");
        }
    }

    private void handleWorldAction(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        final String type = json.get("type").getAsString();
        final JsonObject data = json.has("data") ? json.getAsJsonObject("data") : new JsonObject();
        if (plugin.getWorldManager() != null) {
            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    Map<String, Object> result = plugin.getWorldManager().handleAction(type, data);
                    Map<String, Object> response = new HashMap<String, Object>();
                    response.put("data", result.toString());
                    sendResponse(reqId, response);
                }
            });
        } else {
            sendError(reqId, "World management not available");
        }
    }

    private void handlePluginAction(final JsonObject json) {
        final String reqId = json.get("requestId").getAsString();
        final String type = json.get("type").getAsString();
        final JsonObject data = json.has("data") ? json.getAsJsonObject("data") : new JsonObject();
        if (plugin.getPluginManager() != null) {
            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    Map<String, Object> result = plugin.getPluginManager().handleAction(type, data);
                    Map<String, Object> response = new HashMap<String, Object>();
                    response.put("data", result.toString());
                    sendResponse(reqId, response);
                }
            });
        } else {
            sendError(reqId, "Plugin management not available");
        }
    }

    private void sendResponse(String reqId, Map<String, Object> data) {
        if (isOpen()) {
            Map<String, Object> msg = new HashMap<String, Object>();
            msg.put("type", "RESPONSE");
            msg.put("requestId", reqId);
            msg.put("data", data);
            send(gson.toJson(msg));
        }
    }

    private void sendError(String reqId, String error) {
        if (isOpen()) {
            Map<String, Object> errorData = new HashMap<String, Object>();
            errorData.put("error", error);
            Map<String, Object> msg = new HashMap<String, Object>();
            msg.put("type", "ERROR");
            msg.put("requestId", reqId);
            msg.put("data", errorData);
            send(gson.toJson(msg));
        }
    }

    public void sendPush(String type, Map<String, Object> data) {
        if (isOpen()) {
            Map<String, Object> msg = new HashMap<String, Object>();
            msg.put("type", type);
            msg.put("data", data);
            send(gson.toJson(msg));
        }
    }
}

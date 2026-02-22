package com.configtool.agent;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class ConsoleInterceptor {
    private final ConfigToolAgent plugin;
    private final WebSocketClient wsClient;
    private final int bufferSize;
    private final int flushIntervalSeconds;
    private final ConcurrentLinkedQueue<Map<String, Object>> buffer = new ConcurrentLinkedQueue<Map<String, Object>>();
    private Handler logHandler;

    public ConsoleInterceptor(ConfigToolAgent plugin, WebSocketClient wsClient, int bufferSize, int flushIntervalSeconds) {
        this.plugin = plugin;
        this.wsClient = wsClient;
        this.bufferSize = bufferSize;
        this.flushIntervalSeconds = flushIntervalSeconds;
    }

    public void start() {
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || record.getMessage() == null) return;
                String msg = record.getMessage();
                String level = classifyLevel(record, msg);
                Map<String, Object> entry = new HashMap<String, Object>();
                entry.put("logLevel", level);
                entry.put("message", msg);
                entry.put("source", record.getLoggerName());
                entry.put("recordedAt", System.currentTimeMillis());
                buffer.add(entry);
                while (buffer.size() > bufferSize) buffer.poll();
            }
            @Override public void flush() {}
            @Override public void close() throws SecurityException {}
        };
        Bukkit.getLogger().getParent().addHandler(logHandler);

        new BukkitRunnable() {
            @Override
            public void run() {
                doFlush();
            }
        }.runTaskTimerAsynchronously(plugin, flushIntervalSeconds * 20L, flushIntervalSeconds * 20L);
    }

    public void doFlush() {
        if (buffer.isEmpty() || wsClient == null || !wsClient.isOpen()) return;
        List<Map<String, Object>> batch = new ArrayList<Map<String, Object>>();
        Map<String, Object> entry;
        while ((entry = buffer.poll()) != null && batch.size() < 200) {
            batch.add(entry);
        }
        if (!batch.isEmpty()) {
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("entries", batch);
            wsClient.sendPush("CONSOLE_BATCH", data);
        }
    }

    public void stop() {
        if (logHandler != null) {
            Bukkit.getLogger().getParent().removeHandler(logHandler);
        }
    }

    private String classifyLevel(LogRecord record, String msg) {
        if (msg.startsWith("<") || msg.contains("issued server command") ||
            (record.getLoggerName() != null && record.getLoggerName().contains("Chat"))) {
            return "CHAT";
        }
        java.util.logging.Level level = record.getLevel();
        if (level.intValue() >= java.util.logging.Level.SEVERE.intValue()) return "ERROR";
        if (level.intValue() >= java.util.logging.Level.WARNING.intValue()) return "WARN";
        return "INFO";
    }
}

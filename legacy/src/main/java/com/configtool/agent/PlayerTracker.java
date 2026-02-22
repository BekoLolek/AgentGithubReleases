package com.configtool.agent;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerTracker implements Listener {
    private final ConfigToolAgent plugin;
    private final WebSocketClient wsClient;
    private final int batchIntervalSeconds;
    private final ConcurrentLinkedQueue<Map<String, Object>> eventBuffer = new ConcurrentLinkedQueue<Map<String, Object>>();

    public PlayerTracker(ConfigToolAgent plugin, WebSocketClient wsClient, int batchIntervalSeconds) {
        this.plugin = plugin;
        this.wsClient = wsClient;
        this.batchIntervalSeconds = batchIntervalSeconds;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        new BukkitRunnable() {
            @Override
            public void run() {
                flush();
            }
        }.runTaskTimerAsynchronously(plugin, batchIntervalSeconds * 20L, batchIntervalSeconds * 20L);
    }

    private void flush() {
        if (eventBuffer.isEmpty() || wsClient == null || !wsClient.isOpen()) return;
        List<Map<String, Object>> batch = new ArrayList<Map<String, Object>>();
        Map<String, Object> entry;
        while ((entry = eventBuffer.poll()) != null && batch.size() < 200) {
            batch.add(entry);
        }
        if (!batch.isEmpty()) {
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("events", batch);
            wsClient.sendPush("PLAYER_EVENT", data);
        }
    }

    private void addEvent(Player player, String eventType, Map<String, Object> extra) {
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("playerUuid", player.getUniqueId().toString());
        event.put("playerName", player.getName());
        event.put("eventType", eventType);
        event.put("worldName", player.getWorld().getName());
        event.put("x", player.getLocation().getX());
        event.put("y", player.getLocation().getY());
        event.put("z", player.getLocation().getZ());
        if (extra != null) event.putAll(extra);
        eventBuffer.add(event);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String ipHash = hashIp(e.getPlayer().getAddress() != null ? e.getPlayer().getAddress().getAddress().getHostAddress() : "");
        Map<String, Object> extra = new HashMap<String, Object>();
        extra.put("ipHash", ipHash);
        addEvent(e.getPlayer(), "JOIN", extra);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        addEvent(e.getPlayer(), "LEAVE", null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Map<String, Object> extra = new HashMap<String, Object>();
        extra.put("eventData", e.getDeathMessage() != null ? e.getDeathMessage() : "");
        addEvent(e.getEntity(), "DEATH", extra);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Map<String, Object> extra = new HashMap<String, Object>();
        extra.put("eventData", e.getMessage());
        addEvent(e.getPlayer(), "CHAT", extra);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Map<String, Object> extra = new HashMap<String, Object>();
        extra.put("eventData", e.getMessage());
        addEvent(e.getPlayer(), "COMMAND", extra);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        Map<String, Object> extra = new HashMap<String, Object>();
        extra.put("eventData", e.getAdvancement().getKey().getKey());
        addEvent(e.getPlayer(), "ADVANCEMENT", extra);
    }

    public void shutdown() {
        flush();
    }

    private String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ip.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().substring(0, 16);
        } catch (Exception e) {
            return "";
        }
    }
}

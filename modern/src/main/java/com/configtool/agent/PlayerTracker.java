package com.configtool.agent;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
    private final ConcurrentLinkedQueue<Map<String, Object>> eventBuffer = new ConcurrentLinkedQueue<>();

    public PlayerTracker(ConfigToolAgent plugin, WebSocketClient wsClient, int batchIntervalSeconds) {
        this.plugin = plugin;
        this.wsClient = wsClient;
        this.batchIntervalSeconds = batchIntervalSeconds;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        new BukkitRunnable() {
            @Override public void run() { flush(); }
        }.runTaskTimerAsynchronously(plugin, batchIntervalSeconds * 20L, batchIntervalSeconds * 20L);
    }

    private void flush() {
        if (eventBuffer.isEmpty() || wsClient == null || !wsClient.isOpen()) return;
        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> entry;
        while ((entry = eventBuffer.poll()) != null && batch.size() < 200) batch.add(entry);
        if (!batch.isEmpty()) wsClient.sendPush("PLAYER_EVENT", Map.of("events", batch));
    }

    private void addEvent(Player player, String eventType, Map<String, Object> extra) {
        Map<String, Object> event = new HashMap<>();
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

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        String ipHash = hashIp(e.getPlayer().getAddress() != null ? e.getPlayer().getAddress().getAddress().getHostAddress() : "");
        addEvent(e.getPlayer(), "JOIN", Map.of("ipHash", ipHash));
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { addEvent(e.getPlayer(), "LEAVE", null); }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        addEvent(e.getEntity(), "DEATH", Map.of("eventData", e.getDeathMessage() != null ? e.getDeathMessage() : ""));
    }

    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        addEvent(e.getPlayer(), "CHAT", Map.of("eventData", e.getMessage()));
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent e) {
        addEvent(e.getPlayer(), "COMMAND", Map.of("eventData", e.getMessage()));
    }

    @EventHandler public void onAdvancement(PlayerAdvancementDoneEvent e) {
        addEvent(e.getPlayer(), "ADVANCEMENT", Map.of("eventData", e.getAdvancement().getKey().getKey()));
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
        } catch (Exception e) { return ""; }
    }
}

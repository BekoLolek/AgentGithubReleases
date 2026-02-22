package com.configtool.agent;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class QuickActions {
    private final ConfigToolAgent plugin;

    public QuickActions(ConfigToolAgent plugin) {
        this.plugin = plugin;
    }

    public Map<String, Object> handleAction(String actionType, JsonObject data) {
        Map<String, Object> result = new HashMap<>();
        try {
            switch (actionType) {
                case "BROADCAST" -> {
                    String message = data.get("message").getAsString();
                    Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.broadcastMessage(message));
                    result.put("success", true);
                    result.put("output", "Broadcast sent");
                }
                case "KICK_PLAYER" -> {
                    String playerName = data.get("playerName").getAsString();
                    String reason = data.has("reason") ? data.get("reason").getAsString() : "Kicked by server admin";
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player player = Bukkit.getPlayer(playerName);
                        if (player != null) player.kickPlayer(reason);
                    });
                    result.put("success", true);
                    result.put("output", "Player kicked");
                }
                case "TOGGLE_WHITELIST" -> {
                    boolean enabled = data.get("enabled").getAsBoolean();
                    Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.setWhitelist(enabled));
                    result.put("success", true);
                    result.put("output", "Whitelist " + (enabled ? "enabled" : "disabled"));
                }
                default -> {
                    result.put("success", false);
                    result.put("output", "Unknown action: " + actionType);
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("output", "Error: " + e.getMessage());
        }
        return result;
    }
}

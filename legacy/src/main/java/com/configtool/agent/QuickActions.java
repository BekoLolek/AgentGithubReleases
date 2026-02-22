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
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            switch (actionType) {
                case "BROADCAST": {
                    final String message = data.get("message").getAsString();
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            Bukkit.broadcastMessage(message);
                        }
                    });
                    result.put("success", true);
                    result.put("output", "Broadcast sent");
                    break;
                }
                case "KICK_PLAYER": {
                    final String playerName = data.get("playerName").getAsString();
                    final String reason = data.has("reason") ? data.get("reason").getAsString() : "Kicked by server admin";
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            Player player = Bukkit.getPlayer(playerName);
                            if (player != null) player.kickPlayer(reason);
                        }
                    });
                    result.put("success", true);
                    result.put("output", "Player kicked");
                    break;
                }
                case "TOGGLE_WHITELIST": {
                    final boolean enabled = data.get("enabled").getAsBoolean();
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            Bukkit.setWhitelist(enabled);
                        }
                    });
                    result.put("success", true);
                    result.put("output", "Whitelist " + (enabled ? "enabled" : "disabled"));
                    break;
                }
                default:
                    result.put("success", false);
                    result.put("output", "Unknown action: " + actionType);
                    break;
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("output", "Error: " + e.getMessage());
        }
        return result;
    }
}

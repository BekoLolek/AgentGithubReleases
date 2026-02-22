package com.configtool.agent;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.io.File;
import java.util.*;

public class WorldManager {
    private final ConfigToolAgent plugin;

    public WorldManager(ConfigToolAgent plugin) {
        this.plugin = plugin;
    }

    public List<Map<String, Object>> getWorlds() {
        List<Map<String, Object>> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            Map<String, Object> world = new HashMap<>();
            world.put("worldName", w.getName());
            world.put("worldType", w.getEnvironment().name());
            world.put("seed", w.getSeed());
            world.put("difficulty", w.getDifficulty().name());
            world.put("playerCount", w.getPlayers().size());

            File worldDir = w.getWorldFolder();
            world.put("sizeMb", dirSize(worldDir) / (1024 * 1024));

            Map<String, String> gamerules = new HashMap<>();
            for (GameRule<?> rule : GameRule.values()) {
                Object val = w.getGameRuleValue(rule);
                if (val != null) gamerules.put(rule.getName(), val.toString());
            }
            world.put("gamerules", gamerules);

            WorldBorder border = w.getWorldBorder();
            world.put("borderCenterX", border.getCenter().getX());
            world.put("borderCenterZ", border.getCenter().getZ());
            world.put("borderSize", border.getSize());

            worlds.add(world);
        }
        return worlds;
    }

    public Map<String, Object> handleAction(String actionType, JsonObject data) {
        Map<String, Object> result = new HashMap<>();
        try {
            switch (actionType) {
                case "GET_WORLDS" -> {
                    result.put("success", true);
                    result.put("worlds", getWorlds());
                }
                case "SET_GAMERULE" -> {
                    String worldName = data.get("worldName").getAsString();
                    String rule = data.get("rule").getAsString();
                    String value = data.get("value").getAsString();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule " + rule + " " + value);
                        }
                    });
                    result.put("success", true);
                    result.put("output", "Gamerule set");
                }
                default -> { result.put("success", false); result.put("output", "Unknown action"); }
            }
        } catch (Exception e) { result.put("success", false); result.put("output", e.getMessage()); }
        return result;
    }

    private long dirSize(File dir) {
        long size = 0;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) size += dirSize(f);
        } else { size = dir.length(); }
        return size;
    }
}

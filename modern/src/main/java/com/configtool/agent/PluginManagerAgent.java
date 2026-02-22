package com.configtool.agent;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class PluginManagerAgent {
    private final ConfigToolAgent plugin;

    public PluginManagerAgent(ConfigToolAgent plugin) {
        this.plugin = plugin;
    }

    public List<Map<String, Object>> getPlugins() {
        List<Map<String, Object>> plugins = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", p.getName());
            info.put("version", p.getDescription().getVersion());
            info.put("description", p.getDescription().getDescription());
            info.put("authors", String.join(", ", p.getDescription().getAuthors()));
            info.put("enabled", p.isEnabled());
            info.put("dependencies", String.join(", ", p.getDescription().getDepend()));
            info.put("softDependencies", String.join(", ", p.getDescription().getSoftDepend()));
            info.put("apiVersion", p.getDescription().getAPIVersion());
            plugins.add(info);
        }
        return plugins;
    }

    public Map<String, Object> handleAction(String actionType, JsonObject data) {
        Map<String, Object> result = new HashMap<>();
        try {
            switch (actionType) {
                case "GET_PLUGINS" -> {
                    result.put("success", true);
                    result.put("plugins", getPlugins());
                }
                case "ENABLE_PLUGIN" -> {
                    String name = data.get("pluginName").getAsString();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Plugin p = Bukkit.getPluginManager().getPlugin(name);
                        if (p != null) Bukkit.getPluginManager().enablePlugin(p);
                    });
                    result.put("success", true); result.put("output", "Plugin enabled");
                }
                case "DISABLE_PLUGIN" -> {
                    String name = data.get("pluginName").getAsString();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Plugin p = Bukkit.getPluginManager().getPlugin(name);
                        if (p != null) Bukkit.getPluginManager().disablePlugin(p);
                    });
                    result.put("success", true); result.put("output", "Plugin disabled");
                }
                default -> { result.put("success", false); result.put("output", "Unknown action"); }
            }
        } catch (Exception e) { result.put("success", false); result.put("output", e.getMessage()); }
        return result;
    }
}

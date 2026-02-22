package com.configtool.agent;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;

public class PerformanceTracker extends BukkitRunnable {
    private final ConfigToolAgent plugin;
    private final WebSocketClient wsClient;

    public PerformanceTracker(ConfigToolAgent plugin, WebSocketClient wsClient) {
        this.plugin = plugin;
        this.wsClient = wsClient;
    }

    @Override
    public void run() {
        if (wsClient == null || !wsClient.isOpen()) return;
        try {
            List<Map<String, Object>> pluginTicks = new ArrayList<Map<String, Object>>();
            try {
                for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
                    Map<String, Object> entry = new HashMap<String, Object>();
                    entry.put("pluginName", p.getName());
                    entry.put("tickTimeMs", 0.0);
                    pluginTicks.add(entry);
                }
            } catch (Exception ignored) {}

            Map<String, Object> data = new HashMap<String, Object>();
            data.put("pluginTicks", pluginTicks);

            long gcCount = 0, gcTime = 0;
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += gc.getCollectionCount();
                gcTime += gc.getCollectionTime();
            }
            data.put("gcCount", gcCount);
            data.put("gcTimeMs", gcTime);

            wsClient.sendPush("PERFORMANCE_DATA", data);
        } catch (Exception e) {
            plugin.getLogger().warning("Performance tracking failed: " + e.getMessage());
        }
    }
}

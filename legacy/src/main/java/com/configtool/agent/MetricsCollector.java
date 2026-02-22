package com.configtool.agent;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

public class MetricsCollector extends BukkitRunnable {
    private final ConfigToolAgent plugin;
    private final WebSocketClient wsClient;

    public MetricsCollector(ConfigToolAgent plugin, WebSocketClient wsClient) {
        this.plugin = plugin;
        this.wsClient = wsClient;
    }

    @Override
    public void run() {
        if (wsClient == null || !wsClient.isOpen()) return;

        Map<String, Object> metrics = new HashMap<String, Object>();
        metrics.put("tps", getTps());
        metrics.put("cpuPercent", getCpuUsage());

        Runtime rt = Runtime.getRuntime();
        metrics.put("ramUsedMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        metrics.put("ramMaxMb", rt.maxMemory() / (1024 * 1024));

        File serverDir = plugin.getDataFolder().getParentFile().getParentFile();
        metrics.put("diskUsedMb", (serverDir.getTotalSpace() - serverDir.getFreeSpace()) / (1024 * 1024));
        metrics.put("diskTotalMb", serverDir.getTotalSpace() / (1024 * 1024));

        metrics.put("playerCount", Bukkit.getOnlinePlayers().size());
        metrics.put("maxPlayers", Bukkit.getMaxPlayers());

        int chunks = 0, entities = 0;
        for (World w : Bukkit.getWorlds()) {
            chunks += w.getLoadedChunks().length;
            entities += w.getEntities().size();
        }
        metrics.put("chunkCount", chunks);
        metrics.put("entityCount", entities);

        metrics.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

        long gcCount = 0, gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gc.getCollectionCount();
            gcTime += gc.getCollectionTime();
        }
        metrics.put("gcCount", gcCount);
        metrics.put("gcTimeMs", gcTime);

        wsClient.sendPush("METRICS_BATCH", metrics);
    }

    private double getTps() {
        try {
            Object server = Bukkit.getServer();
            java.lang.reflect.Method method = server.getClass().getMethod("getTPS");
            double[] tps = (double[]) method.invoke(server);
            return Math.min(tps[0], 20.0);
        } catch (Exception e) {
            try {
                Object mcServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                java.lang.reflect.Field tpsField = mcServer.getClass().getField("recentTps");
                double[] tps = (double[]) tpsField.get(mcServer);
                return Math.min(tps[0], 20.0);
            } catch (Exception ex) {
                return 20.0;
            }
        }
    }

    private double getCpuUsage() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOs = (com.sun.management.OperatingSystemMXBean) os;
                return Math.round(sunOs.getProcessCpuLoad() * 100.0 * 10.0) / 10.0;
            }
            return os.getSystemLoadAverage();
        } catch (Exception e) {
            return -1;
        }
    }
}

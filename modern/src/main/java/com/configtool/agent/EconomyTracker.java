package com.configtool.agent;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class EconomyTracker extends BukkitRunnable {
    private final ConfigToolAgent plugin;
    private final WebSocketClient wsClient;
    private Object economy;

    public EconomyTracker(ConfigToolAgent plugin, WebSocketClient wsClient) {
        this.plugin = plugin;
        this.wsClient = wsClient;
        detectVault();
    }

    private void detectVault() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
            var rsp = Bukkit.getServicesManager().getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"));
            if (rsp != null) economy = rsp.getProvider();
        } catch (Exception e) {
            plugin.getLogger().info("Vault economy not available: " + e.getMessage());
        }
    }

    public boolean isVaultDetected() { return economy != null; }

    @Override
    public void run() {
        if (wsClient == null || !wsClient.isOpen() || economy == null) return;
        try {
            Map<String, Object> snapshot = new HashMap<>();
            double totalMoney = 0;
            List<Map<String, Object>> topBalances = new ArrayList<>();

            var method = economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            for (var player : Bukkit.getOnlinePlayers()) {
                double balance = (double) method.invoke(economy, player);
                totalMoney += balance;
                topBalances.add(Map.of("name", player.getName(), "balance", balance));
            }
            topBalances.sort((a, b) -> Double.compare((double) b.get("balance"), (double) a.get("balance")));
            if (topBalances.size() > 10) topBalances = topBalances.subList(0, 10);

            snapshot.put("totalMoney", totalMoney);
            snapshot.put("topBalances", topBalances);
            snapshot.put("transactionCount", 0L);
            wsClient.sendPush("ECONOMY_SNAPSHOT", snapshot);
        } catch (Exception e) {
            plugin.getLogger().warning("Economy snapshot failed: " + e.getMessage());
        }
    }
}

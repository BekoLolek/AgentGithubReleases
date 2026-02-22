package com.configtool.agent;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;

public class ConfigToolAgent extends JavaPlugin implements CommandExecutor {
    private WebSocketClient wsClient;
    private FileOperations fileOps;
    private String serverUrl;
    private String token;
    private ModuleConfig moduleConfig;
    private BukkitRunnable metricsCollector;
    private QuickActions quickActions;
    private ConsoleInterceptor consoleInterceptor;
    private PlayerTracker playerTracker;
    private EconomyTracker economyTracker;
    private PerformanceTracker performanceTracker;
    private WorldManager worldManager;
    private PluginManagerAgent pluginManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverUrl = getConfig().getString("server-url", "wss://your-backend.fly.dev/agent");
        token = getConfig().getString("token", "");

        if (token.isEmpty() || token.equals("paste-your-token-here")) {
            getLogger().severe("No token configured! Add your token to plugins/ConfigToolAgent/config.yml");
            return;
        }

        fileOps = new FileOperations(getDataFolder().getParentFile(), getLogger());
        moduleConfig = new ModuleConfig(getConfig());

        if (moduleConfig.isEnabled("quick-actions")) {
            quickActions = new QuickActions(this);
        }

        getCommand("configtool").setExecutor(this);
        connect();

        if (moduleConfig.isEnabled("dashboard")) {
            startMetricsCollector();
        }

        if (moduleConfig.isEnabled("console")) {
            consoleInterceptor = new ConsoleInterceptor(this, wsClient,
                    moduleConfig.getConsoleBufferSize(), moduleConfig.getConsoleFlushIntervalSeconds());
            consoleInterceptor.start();
        }

        if (moduleConfig.isEnabled("analytics")) {
            playerTracker = new PlayerTracker(this, wsClient, moduleConfig.getAnalyticsBatchIntervalSeconds());
            playerTracker.start();
        }

        if (moduleConfig.isEnabled("economy")) {
            economyTracker = new EconomyTracker(this, wsClient);
            int intervalTicks = moduleConfig.getInt("economy", "snapshot-interval-seconds", 300) * 20;
            economyTracker.runTaskTimerAsynchronously(this, 100L, intervalTicks);
        }

        if (moduleConfig.isEnabled("performance")) {
            performanceTracker = new PerformanceTracker(this, wsClient);
            int intervalTicks = moduleConfig.getInt("performance", "tick-sample-interval-ticks", 600);
            performanceTracker.runTaskTimerAsynchronously(this, 200L, intervalTicks);
        }

        if (moduleConfig.isEnabled("world-management")) {
            worldManager = new WorldManager(this);
        }

        pluginManager = new PluginManagerAgent(this);

        getLogger().info("ConfigTool Agent enabled!");
    }

    @Override
    public void onDisable() {
        if (metricsCollector != null) { metricsCollector.cancel(); metricsCollector = null; }
        if (consoleInterceptor != null) { consoleInterceptor.stop(); consoleInterceptor = null; }
        if (economyTracker != null) { economyTracker.cancel(); economyTracker = null; }
        if (performanceTracker != null) { performanceTracker.cancel(); performanceTracker = null; }
        if (playerTracker != null) { playerTracker.shutdown(); playerTracker = null; }
        disconnect();
        getLogger().info("ConfigTool Agent disabled!");
    }

    private void startMetricsCollector() {
        if (metricsCollector != null) {
            metricsCollector.cancel();
        }
        metricsCollector = new MetricsCollector(this, wsClient);
        int intervalTicks = moduleConfig.getMetricsIntervalSeconds() * 20;
        metricsCollector.runTaskTimerAsynchronously(this, 20L, intervalTicks);
    }

    public void connect() {
        if (wsClient != null && wsClient.isOpen()) return;
        try {
            wsClient = new WebSocketClient(this, serverUrl + "?token=" + token, token, fileOps);
            wsClient.connect();
        } catch (Exception e) {
            getLogger().severe("Failed to connect: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (wsClient != null) { wsClient.close(); wsClient = null; }
    }

    public void reconnect() {
        disconnect();
        final ConfigToolAgent self = this;
        getServer().getScheduler().runTaskLaterAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                self.connect();
            }
        }, 60L);
    }

    public ModuleConfig getModuleConfig() {
        return moduleConfig;
    }

    public QuickActions getQuickActions() {
        return quickActions;
    }

    public WebSocketClient getWsClient() { return wsClient; }
    public WorldManager getWorldManager() { return worldManager; }
    public PluginManagerAgent getPluginManager() { return pluginManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("\u00a76ConfigTool Agent \u00a77- " + (wsClient != null && wsClient.isOpen() ? "\u00a7aConnected" : "\u00a7cDisconnected"));
            return true;
        }
        String subCmd = args[0].toLowerCase();
        switch (subCmd) {
            case "reconnect":
                reconnect();
                sender.sendMessage("\u00a7aReconnecting...");
                break;
            case "status":
                sender.sendMessage("\u00a77Status: " + (wsClient != null && wsClient.isOpen() ? "\u00a7aConnected" : "\u00a7cDisconnected"));
                break;
            case "reload":
                reloadConfig();
                serverUrl = getConfig().getString("server-url");
                token = getConfig().getString("token");
                moduleConfig = new ModuleConfig(getConfig());
                sender.sendMessage("\u00a7aConfig reloaded");
                break;
            default:
                sender.sendMessage("\u00a7cUsage: /configtool [reconnect|status|reload]");
                break;
        }
        return true;
    }
}

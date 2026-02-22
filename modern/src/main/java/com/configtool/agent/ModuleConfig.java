package com.configtool.agent;

import org.bukkit.configuration.file.FileConfiguration;

public class ModuleConfig {
    private final FileConfiguration config;

    public ModuleConfig(FileConfiguration config) {
        this.config = config;
    }

    public boolean isEnabled(String module) {
        return config.getBoolean("modules." + module + ".enabled", true);
    }

    public int getInt(String module, String key, int defaultValue) {
        return config.getInt("modules." + module + "." + key, defaultValue);
    }

    public boolean getBoolean(String module, String key, boolean defaultValue) {
        return config.getBoolean("modules." + module + "." + key, defaultValue);
    }

    public int getMetricsIntervalSeconds() {
        return getInt("dashboard", "metrics-interval-seconds", 10);
    }

    public boolean isAllowEnableDisable() {
        return getBoolean("plugins", "allow-enable-disable", true);
    }

    public boolean isPlayerTrackingEnabled() {
        return isEnabled("analytics") && getBoolean("analytics", "player-tracking", true);
    }

    public int getAnalyticsBatchIntervalSeconds() {
        return getInt("analytics", "batch-interval-seconds", 30);
    }

    public int getEconomySnapshotIntervalSeconds() {
        return getInt("economy", "snapshot-interval-seconds", 300);
    }

    public int getTickSampleIntervalTicks() {
        return getInt("performance", "tick-sample-interval-ticks", 600);
    }

    public int getConsoleBufferSize() {
        return getInt("console", "buffer-size", 1000);
    }

    public int getConsoleFlushIntervalSeconds() {
        return getInt("console", "flush-interval-seconds", 5);
    }
}

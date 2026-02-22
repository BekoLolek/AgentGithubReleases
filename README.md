# ConfigTool Agent Plugin

The official Minecraft server plugin for ConfigTool - enabling real-time configuration management, server monitoring, and remote administration.

## Download

Download the latest version from the [Releases](https://github.com/BekoLolek/AgentGithubReleases/releases) page.

## Versions

### Modern (Recommended)
- **Java:** 17+
- **Minecraft:** 1.17+
- **Server Software:** Paper, Purpur, Folia

### Legacy
- **Java:** 8+
- **Minecraft:** 1.13 - 1.16.5
- **Server Software:** Spigot, Paper

## Features

- Real-time file editing (YAML, JSON)
- Server dashboard with live metrics (TPS, CPU, RAM, disk, players)
- Console log streaming
- Player analytics (join/quit/death/chat/commands/advancements)
- Economy tracking (Vault integration)
- Performance monitoring (GC stats, plugin tick times)
- World management (gamerules, world info, world border)
- Plugin management (list/enable/disable)
- Quick actions (broadcast, kick, whitelist)
- File operations (create, rename, delete)

## Installation

1. Download the correct JAR for your server version from releases
2. Place in your server's `plugins` folder
3. Start the server once to generate config
4. Edit `plugins/ConfigToolAgent/config.yml` and add your server token
5. Restart the server

## Configuration

```yaml
# plugins/ConfigToolAgent/config.yml
server-url: "wss://configtoolapi.onrender.com/agent"
token: "your-server-token-here"

# Module configuration - toggle features on/off
modules:
  dashboard:
    enabled: true
    metrics-interval-seconds: 10
  console:
    enabled: true
    buffer-size: 1000
    flush-interval-seconds: 5
  analytics:
    enabled: true
    player-tracking: true
    batch-interval-seconds: 30
  economy:
    enabled: true
    snapshot-interval-seconds: 300
  performance:
    enabled: true
    tick-sample-interval-ticks: 600
  world-management:
    enabled: true
  quick-actions:
    enabled: true
  plugins:
    enabled: true
    allow-enable-disable: true
```

Get your server token from the [ConfigTool Dashboard](https://configtool.dev).

## Building from Source

Each version is a standalone Maven project:

```bash
# Modern
cd modern && mvn package

# Legacy
cd legacy && mvn package
```

Output JARs will be in the respective `target/` directories.

## Links

- [ConfigTool Website](https://configtool.dev)
- [Discord Support](https://discord.gg/yBdaGmFaSX)

## License

MIT License - see LICENSE file for details.

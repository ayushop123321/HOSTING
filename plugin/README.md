# MelonMC Shop Plugin

A custom Minecraft plugin for the MelonMC server to handle shop transactions, coin management, and rank assignments.

## Features

- Integration with Vault for economy
- Integration with LuckPerms for rank/permission management
- Command to give coins to players
- Command to assign ranks to players
- Support for offline player transactions (applied when they next join)
- Configurable ranks and permissions
- Support for different gamemodes (LifeSteal, Classic, etc.)

## Requirements

- Spigot/Paper 1.16.5 or newer
- Vault
- LuckPerms
- An economy plugin compatible with Vault (e.g. EssentialsX)

## Installation

1. Download the latest release JAR from the releases section
2. Place the JAR file in your server's `plugins` folder
3. Restart your server or use a plugin manager to load the plugin
4. Configure the plugin in the generated `config.yml` file

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/givecoin <player> <amount> <gamemode>` | Give coins to a player | `melonmc.shop.givecoin` |
| `/giverank <player> <rank> <gamemode>` | Give a rank to a player | `melonmc.shop.giverank` |
| `/is_player_exists <player>` | Check if a player exists | `melonmc.shop.checkplayer` |

## Configuration

The plugin creates a `config.yml` file in its data folder with the following sections:

### General Settings

```yaml
settings:
  database-type: NONE  # MYSQL, SQLITE or NONE
  debug: false  # Enable for more verbose logging
  default-gamemode: lifesteal  # Default gamemode if not specified
```

### Rank Settings

Ranks are defined for each gamemode with their prices and benefits:

```yaml
ranks:
  lifesteal:
    boss:
      price: 749
      bonus-coins: 3500
      fly: true
      homes: 5
      kits: [basic, advanced, elite]
    # more ranks...
  
  classic:
    supreme:
      price: 749
      bonus-coins: 3200
      fly: true
      homes: 5
      kits: [basic, advanced, elite]
    # more ranks...
    
  player:
    eternal:
      price: 749
      fly: true
      homes: 6
      kits: [basic, advanced, elite, special]
    # more ranks...
```

## Integration with Discord Bot

The MelonMC Shop plugin is designed to work with the Discord bot in the `discord-bot` folder. The bot can send commands to the Minecraft server using RCON to process purchases.

### How it Works

1. A player makes a purchase through the Discord bot
2. The bot verifies the payment and sends a command to the server via RCON
3. The plugin processes the command and grants the appropriate coins or ranks
4. The player receives the benefits in-game

## Building from Source

To build the plugin from source:

1. Clone the repository
2. Navigate to the `plugin` directory
3. Run `mvn clean package`
4. The compiled JAR will be in the `target` directory

## License

This plugin is created exclusively for MelonMC and is not intended for redistribution. 
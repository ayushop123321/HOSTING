# MelonMC Rank Assignment Plugin

This document explains how to set up your Minecraft server to work with the Discord billform system.

## Requirements

1. A Minecraft server with:
   - [LuckPerms](https://luckperms.net/) plugin installed
   - RCON enabled
   - [Vault](https://www.spigotmc.org/resources/vault.34315/) plugin installed

## How to Set Up RCON on Your Minecraft Server

1. Edit your `server.properties` file and add these lines:
   ```
   enable-rcon=true
   rcon.port=25575
   rcon.password=your_secure_password_here
   ```

2. Make sure the RCON port (25575 by default) is accessible from where your Discord bot will run

3. Restart your Minecraft server

## Plugin Installation

1. Download the `MelonMCShop-1.0.0.jar` file from the `target` directory
2. Place it in your Minecraft server's `plugins` folder
3. Restart your server or use `/reload` if you have permissions
4. The plugin will generate a default configuration file

## Configuration

1. Edit the `plugins/MelonMCShop/config.yml` file to set up:
   - Discord integration settings
   - Security settings (auth token)
   - Rank definitions and commands

2. Make sure your `.env` file in the Discord bot directory has these settings:
   ```
   MC_RCON_HOST=play.melon-mc.fun
   MC_RCON_PORT=25575
   MC_RCON_PASSWORD=your_rcon_password_here
   ```

3. Update the password to match what you set in your `server.properties` file

## Available Commands

### Admin Commands
- `/setrank <player> <rank> [auth_token]` - Set a player's rank directly
- `/checkrank <player>` - Check a player's current rank
- `/givecoin <player> <amount> <gamemode>` - Give coins to a player
- `/giverank <player> <rank> <gamemode>` - Give a rank to a player
- `/is_player_exists <player>` - Check if a player exists

### User Commands
- `/rankinfo` - Show information about available ranks
- `/shopgui` - Open the shop GUI
- `/redeem` - Redeem pending rewards
- `/rewards` - Check your pending rewards
- `/daily` - Claim your daily reward

## How the Rank System Works

1. Staff use `/billform` in Discord to create a new bill
2. They enter the player's Minecraft name and Discord username
3. They select a rank to assign
4. The Discord bot monitors for when that player logs into the server
5. When the player comes online, the plugin automatically:
   - Applies the LuckPerms rank to the player
   - Notifies the player that their rank was applied
   - Updates the bill status to "completed"
   - Logs the successful rank assignment

## Troubleshooting

If ranks aren't being assigned, check:

1. RCON connection - Make sure your server.properties has RCON enabled
2. RCON password - Verify the password in .env matches server.properties
3. RCON port - Check that the port is correct and not blocked by a firewall
4. LuckPerms - Ensure the LuckPerms plugin is working correctly
5. Plugin logs - Check for error messages in the server console
6. Player name - Verify the exact in-game name was used in the bill form

## LuckPerms Setup

Make sure your LuckPerms plugin has the ranks defined properly:

```
/lp creategroup eternal
/lp creategroup immortal  
/lp creategroup knight
/lp creategroup lord
/lp creategroup lady
/lp creategroup duke
/lp creategroup duchess
/lp creategroup vip_plus
```

Then set up the permissions and hierarchy for these ranks using:

```
/lp group <rank> permission set <permission.node>
```

## Discord Bot Integration

The plugin communicates with your Discord bot to:
1. Notify when ranks are successfully applied
2. Check for pending rank assignments
3. Update bill status in Discord

Make sure the Discord bot is running and the API token in the plugin config matches your bot's security settings. 
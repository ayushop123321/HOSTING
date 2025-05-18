# MelonMC Discord Bot

A Discord bot for managing MelonMC Minecraft server announcements, payments, and more.

## Features

- **Announcement Management**: Automatically syncs Discord announcements to the website
- **Payment Processing**: Handles payment verification through screenshots
- **Server Information**: Provides information about server ranks and coins
- **Admin Commands**: Special commands for server administrators

## Setup Instructions

### Prerequisites

- Node.js 16.x or later
- npm (comes with Node.js)
- Discord bot token (from [Discord Developer Portal](https://discord.com/developers/applications))

### Installation

1. **Run the setup script**:
   - Windows: `setup.bat`
   - Linux/Mac: `bash setup.sh`

2. **Edit the `.env` file** with your Discord bot token and other settings:
   ```
   # Discord Bot Configuration
   DISCORD_TOKEN=your_discord_bot_token_here
   ADMIN_ROLE_ID=your_admin_role_id_here
   LOG_CHANNEL_ID=your_log_channel_id_here
   PAYMENT_CHANNEL_ID=your_payment_verification_channel_id_here
   ANNOUNCEMENT_CHANNEL_ID=your_announcements_channel_id_here
   ```

3. **Start the bot**:
   ```
   node main.js
   ```

## Bot Commands

These commands are available in Discord:

| Command | Description | Usage |
|---------|-------------|-------|
| `/syncannouncements` | Syncs recent announcements from Discord to the website | `/syncannouncements count:10` |
| `/rankinfo` | Shows information about server ranks | `/rankinfo` |
| `/coininfo` | Shows information about server coins | `/coininfo` |
| `/help` | Shows available commands and how to use them | `/help` |

## Announcement System

The bot will automatically:

1. Monitor the configured announcement channel
2. Convert Discord messages to website announcements
3. Save announcements to both:
   - Website/announcements/announcements.json
   - LocalStorage backup in the admin panel

### Manual Syncing

If you need to manually sync announcements from Discord:

```
node sync-all.js 50
```

This will process the last 50 messages in the announcement channel.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Bot doesn't log in | Check your Discord token in the `.env` file |
| Commands aren't showing | Restart the bot to register the commands with Discord |
| Announcements not syncing | Make sure the announcement channel ID is correct in `.env` |
| Database errors | The bot will still work without Firebase, using local file storage instead |

## Directory Structure

- `main.js` - Main bot file
- `commands.js` - Command definitions and handlers
- `data/` - Local storage for announcements
- `sync-all.js` - Utility to bulk sync announcements

## Advanced Configuration

For advanced users, you can modify:

- `main.js` to add new Discord event handlers
- `commands.js` to add new commands
- Environment variables in `.env` to change channel IDs and tokens 
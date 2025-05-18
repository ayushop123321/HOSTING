# MelonMC Discord Bot Troubleshooting Guide

This guide covers common issues you might encounter with the MelonMC Discord Bot and their solutions.

## Connection Issues

### Bot Won't Log In

**Symptoms:**
- "Error: An invalid token was provided"
- Bot starts but immediately disconnects
- "Error initializing bot: Error [TokenInvalid]"

**Solutions:**
1. **Check your token**: Make sure your Discord bot token in the `.env` file is correct and hasn't expired
   - Go to the [Discord Developer Portal](https://discord.com/developers/applications)
   - Select your bot application
   - Go to the "Bot" tab
   - Click "Reset Token" to generate a new one
   - Copy the new token to your `.env` file

2. **Verify token format**: The token should not have any spaces, quotes, or extra characters
   ```
   # Correct
   DISCORD_TOKEN=your.token.here
   
   # Incorrect
   DISCORD_TOKEN="your.token.here"
   DISCORD_TOKEN= your.token.here
   ```

3. **Check file encoding**: Make sure the `.env` file is saved with UTF-8 encoding

### Bot Can Connect But Doesn't Respond to Commands

**Symptoms:**
- Bot shows as online in Discord
- Slash commands don't appear or don't work
- "Unknown interaction" errors

**Solutions:**
1. **Restart the bot**: Run `node main.js` or use the `start.bat` script
   - Sometimes command registration needs a fresh restart

2. **Check Guild ID**: Make sure your `GUILD_ID` in the `.env` file is correct
   - Enable Developer Mode in Discord (Settings → Advanced → Developer Mode)
   - Right-click your server and select "Copy ID"
   - Put this ID in your `.env` file

3. **Check bot permissions**: Make sure your bot has the following permissions:
   - Send Messages
   - Embed Links
   - Read Message History
   - Use Slash Commands
   - Add Reactions

4. **Re-invite the bot**: Generate a new invite link with proper scopes
   - Go to Discord Developer Portal → OAuth2 → URL Generator
   - Select scopes: `bot`, `applications.commands`
   - Select bot permissions: `Send Messages`, `Embed Links`, etc.
   - Use the generated URL to add the bot to your server again

## Announcement Syncing Issues

### Announcements Aren't Showing on the Website

**Symptoms:**
- Bot reacts to messages with ✅ but announcements don't appear on the website
- The `/syncannouncements` command runs but nothing changes

**Solutions:**
1. **Check file locations**: Make sure the announcements are being saved to the correct locations
   ```
   discord-bot/data/announcements.json
   Website/announcements/announcements.json
   Website/admin/localStorage_backup.json
   ```

2. **Manual sync**: Run `node sync-all.js 50` to manually sync the last 50 announcements

3. **Check website code**: Make sure your website is correctly loading announcements from the JSON file
   - Open the browser console (F12) to check for any JavaScript errors
   - Verify that the website is looking in the right location for the announcement files

4. **Check file permissions**: Make sure the bot has permission to write to these directories

### Error When Running sync-all.js

**Symptoms:**
- "Error: ANNOUNCEMENT_CHANNEL_ID not found in environment variables"
- "Error: Channel with ID xxx not found"

**Solutions:**
1. **Check your .env file**: Make sure it contains the correct channel ID
   ```
   ANNOUNCEMENT_CHANNEL_ID=your_channel_id_here
   ```

2. **Verify the channel exists**: Make sure the bot has access to the specified channel
   - The bot needs to be in the server containing that channel
   - The bot needs permission to view the channel

3. **Check Discord permissions**: Make sure the bot has the correct permissions
   - View Channels
   - Read Message History

## Other Common Issues

### Bot Crashes with "Out of Memory" Error

**Symptoms:**
- "JavaScript heap out of memory" error
- Bot crashes after running for a while

**Solutions:**
1. **Increase Node.js memory limit**: Run the bot with increased memory
   ```
   node --max-old-space-size=4096 main.js
   ```

### File System Errors

**Symptoms:**
- "EACCES: permission denied"
- "ENOENT: no such file or directory"

**Solutions:**
1. **Check directory permissions**: Make sure the bot has write permissions to:
   - `discord-bot/data/`
   - `Website/announcements/`
   - `Website/admin/`

2. **Create missing directories**: Run the setup script again:
   ```
   ./setup.bat
   ```

3. **Run as administrator**: On Windows, try running Command Prompt as administrator

## Getting Help

If you're still experiencing issues, please:

1. Check the console output for specific error messages
2. Make sure all environment variables are correctly set in the `.env` file
3. Try running the bot with:
   ```
   node --trace-warnings main.js
   ```
   This will provide more detailed error information

## Common Error Codes

| Error | Meaning | Solution |
|-------|---------|----------|
| DISALLOWED_INTENTS | Your bot isn't allowed to use required intents | Enable Message Content Intent in the Developer Portal |
| TOKEN_INVALID | Invalid Discord token | Reset your token in the Developer Portal |
| ECONNREFUSED | Connection to Discord failed | Check your internet connection |
| UnhandledPromiseRejection | Unhandled promise rejection | Check bot code for promises without proper catch handling | 
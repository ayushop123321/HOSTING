# How to Reset Your Discord Bot Token

Your Discord bot is showing a `TokenInvalid` error, which means the current token in your `.env` file is no longer valid. Follow these steps to get a new token:

## Step 1: Visit the Discord Developer Portal

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Log in with your Discord account

## Step 2: Select Your Application

1. Click on your bot application (the one with Client ID: `1370100708333850624`)

## Step 3: Reset Your Token

1. Go to the "Bot" tab on the left sidebar
2. Under the "Token" section, click "Reset Token"
3. Confirm that you want to reset the token when prompted
4. Copy the new token (it will only be shown once!)

## Step 4: Update Your .env File

1. Open your `.env` file in the discord-bot folder
2. Replace the current token with your new token:

```
DISCORD_TOKEN=your_new_token_here
```

## Step 5: Enable Required Intents

While in the Developer Portal, make sure these settings are enabled:

1. Under the "Bot" tab, scroll down to "Privileged Gateway Intents"
2. Enable "SERVER MEMBERS INTENT"
3. Enable "MESSAGE CONTENT INTENT"
4. Click "Save Changes" at the bottom

## Step 6: Restart Your Bot

1. Run `.\start.bat` to start the bot with the new token

## Important Notes

- Never share your bot token with anyone
- If you accidentally expose your token, reset it immediately
- The token is like a password - it provides full access to your bot 
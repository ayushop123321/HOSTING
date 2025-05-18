@echo off
echo ======================================
echo MelonMC Discord Bot Setup (Windows)
echo ======================================
echo.

echo Checking Node.js installation...
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Node.js is not installed. Please install Node.js before continuing.
    exit /b 1
)

echo Installing dependencies...
call npm install discord.js dotenv

echo Creating required directories...
if not exist data mkdir data
if not exist ..\Website\announcements mkdir ..\Website\announcements
if not exist ..\Website\admin mkdir ..\Website\admin

echo Checking for .env file...
if not exist .env (
    echo Creating .env file...
    (
        echo # Discord Bot Configuration
        echo DISCORD_TOKEN=YOUR_DISCORD_TOKEN_HERE
        echo ADMIN_ROLE_ID=ADMIN_ROLE_ID_HERE
        echo LOG_CHANNEL_ID=LOG_CHANNEL_ID_HERE
        echo PAYMENT_CHANNEL_ID=PAYMENT_CHANNEL_ID_HERE
        echo ANNOUNCEMENT_CHANNEL_ID=ANNOUNCEMENT_CHANNEL_ID_HERE
        echo.
        echo # Minecraft Server Configuration
        echo MINECRAFT_SERVER_IP=play.melon-mc.fun:19141
        echo MINECRAFT_SERVER_PORT=19141
        echo MINECRAFT_RCON_PASSWORD=YOUR_RCON_PASSWORD_HERE
        echo CLIENT_ID=YOUR_CLIENT_ID_HERE
    ) > .env
    echo .env file created. Please edit it with your Discord token and other configuration.
) else (
    echo .env file already exists. Skipping creation.
)

echo Setting up announcements files...
if not exist data\announcements.json (
    echo [] > data\announcements.json
    echo Created empty announcements.json in data directory.
)

if not exist ..\Website\announcements\announcements.json (
    echo [] > ..\Website\announcements\announcements.json
    echo Created empty announcements.json in Website/announcements directory.
)

echo.
echo ======================================
echo Setup Complete!
echo ======================================
echo.
echo Next steps:
echo 1. Edit the .env file with your Discord bot token and other settings
echo 2. Run the bot with: node main.js
echo 3. To sync announcements from Discord, use: node sync-all.js [messageCount]
echo.
echo If you encounter any issues, check the console output for error messages.
echo ======================================

pause 
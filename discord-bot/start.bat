@echo off
title MelonMC Discord Bot

:: Check if Node.js is installed
where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: Node.js is not installed or not in PATH
    echo Please install Node.js from https://nodejs.org/
    pause
    exit /b 1
)

:: Check if node_modules exists
if not exist node_modules (
    echo Installing dependencies...
    npm install
    if %ERRORLEVEL% neq 0 (
        echo Error installing dependencies
        pause
        exit /b 1
    )
)

:: Check if .env file exists
if not exist .env (
    echo Creating .env file...
    (
        echo # Discord Bot Configuration
        echo DISCORD_TOKEN=your_discord_bot_token_here
        echo ADMIN_ROLE_ID=your_admin_role_id_here
        echo LOG_CHANNEL_ID=your_log_channel_id_here
        echo PAYMENT_CHANNEL_ID=your_payment_verification_channel_id_here
        echo ANNOUNCEMENT_CHANNEL_ID=your_announcements_channel_id_here
        echo.
        echo # Minecraft Server Configuration
        echo MINECRAFT_SERVER_IP=play.melon-mc.fun
        echo MINECRAFT_SERVER_PORT=19141
        echo MC_RCON_HOST=play.melon-mc.fun
        echo MC_RCON_PORT=25575
        echo MC_RCON_PASSWORD=your_rcon_password_here
    ) > .env
    echo Please edit the .env file with your Discord bot token and other settings
    notepad .env
)

:: Ensure data directory exists
if not exist data mkdir data

:: Ensure announcements.json exists
if not exist data\announcements.json (
    echo [] > data\announcements.json
)

:: Ensure bills.json exists
if not exist data\bills.json (
    echo [] > data\bills.json
)

:: Ensure pending directory exists
if not exist data\pending mkdir data\pending

:: Ensure Website directories exist
if not exist ..\Website\announcements mkdir ..\Website\announcements
if not exist ..\Website\admin mkdir ..\Website\admin

:: Copy announcements.json to Website if it doesn't exist
if not exist ..\Website\announcements\announcements.json (
    echo [] > ..\Website\announcements\announcements.json
)

echo Starting MelonMC Discord Bot...
echo.
echo ==========================================================
echo Commands available:
echo /billform - Create a new bill for rank purchase
echo /pastbills - View past bills and their status
echo /rankinfo - Show information about ranks
echo /syncannouncements - Sync announcements to website
echo ==========================================================
echo.
echo Press Ctrl+C to stop the bot
echo.

:: Start the bot with auto-restart on crash
:start
node start-bot.js
echo.
echo Bot exited or crashed with code %ERRORLEVEL%
echo Restarting in 5 seconds...
timeout /t 5 /nobreak
goto start 
#!/bin/bash

# MelonMC Discord Bot Setup Script
echo "======================================"
echo "MelonMC Discord Bot Setup"
echo "======================================"
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "Node.js is not installed. Please install Node.js before continuing."
    exit 1
fi

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    echo "npm is not installed. Please install npm before continuing."
    exit 1
fi

echo "Installing dependencies..."
npm install discord.js dotenv

# Make sure directories exist
echo "Creating required directories..."
mkdir -p data
mkdir -p ../Website/announcements
mkdir -p ../Website/admin

# Check if .env file exists
if [ ! -f .env ]; then
    echo "Creating .env file..."
    cat > .env << EOL
# Discord Bot Configuration
DISCORD_TOKEN=YOUR_DISCORD_TOKEN_HERE
ADMIN_ROLE_ID=ADMIN_ROLE_ID_HERE
LOG_CHANNEL_ID=LOG_CHANNEL_ID_HERE
PAYMENT_CHANNEL_ID=PAYMENT_CHANNEL_ID_HERE
ANNOUNCEMENT_CHANNEL_ID=ANNOUNCEMENT_CHANNEL_ID_HERE

# Minecraft Server Configuration
MINECRAFT_SERVER_IP=play.melon-mc.fun:19141
MINECRAFT_SERVER_PORT=19141
MINECRAFT_RCON_PASSWORD=YOUR_RCON_PASSWORD_HERE
CLIENT_ID=YOUR_CLIENT_ID_HERE
EOL
    echo ".env file created. Please edit it with your Discord token and other configuration."
else
    echo ".env file already exists. Skipping creation."
fi

# Create empty announcements.json files
echo "Setting up announcements files..."
if [ ! -f data/announcements.json ]; then
    echo "[]" > data/announcements.json
    echo "Created empty announcements.json in data directory."
fi

if [ ! -f ../Website/announcements/announcements.json ]; then
    echo "[]" > ../Website/announcements/announcements.json
    echo "Created empty announcements.json in Website/announcements directory."
fi

echo ""
echo "======================================"
echo "Setup Complete!"
echo "======================================"
echo ""
echo "Next steps:"
echo "1. Edit the .env file with your Discord bot token and other settings"
echo "2. Run the bot with: node main.js"
echo "3. To sync announcements from Discord, use: node sync-all.js [messageCount]"
echo ""
echo "If you encounter any issues, check the console output for error messages."
echo "======================================" 
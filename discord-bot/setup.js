#!/usr/bin/env node

/**
 * MelonMC Discord Bot - Setup Script
 * This script helps with the initial setup of the MelonMC Discord bot
 */

const fs = require('fs');
const path = require('path');
const readline = require('readline');

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

// Environment file template
const envTemplate = `# Discord Bot Configuration
DISCORD_TOKEN=your_discord_bot_token_here
ADMIN_ROLE_ID=admin_role_id_here
LOG_CHANNEL_ID=log_channel_id_here
PAYMENT_CHANNEL_ID=payment_verification_channel_id

# Minecraft Server Configuration
MINECRAFT_SERVER_IP=localhost
MINECRAFT_SERVER_PORT=25575
MINECRAFT_RCON_PASSWORD=your_rcon_password_here
`;

// Colors for console output
const colors = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m'
};

// Helper functions
const print = (message, color = '') => {
  console.log(`${color}${message}${colors.reset}`);
};

const printHeader = (message) => {
  console.log('\n');
  print('='.repeat(message.length + 4), colors.blue);
  print(`  ${message}  `, colors.blue + colors.bright);
  print('='.repeat(message.length + 4), colors.blue);
  console.log('\n');
};

const question = (query) => new Promise((resolve) => {
  rl.question(`${colors.yellow}${query}${colors.reset}`, (answer) => {
    resolve(answer.trim());
  });
});

const questionWithDefault = async (query, defaultValue) => {
  const answer = await question(`${query} (${defaultValue}): `);
  return answer || defaultValue;
};

// Main setup function
async function setup() {
  try {
    printHeader('MelonMC Discord Bot Setup');
    print('This script will help you set up your Discord bot for MelonMC.', colors.green);
    print('You will need the following information ready:', colors.green);
    print('  - Discord Bot Token', colors.green);
    print('  - Admin Role ID', colors.green);
    print('  - Log Channel ID', colors.green);
    print('  - Payment Verification Channel ID', colors.green);
    print('  - Minecraft RCON Password', colors.green);
    console.log('\n');

    // Check if .env file already exists
    const envPath = path.join(__dirname, '.env');
    if (fs.existsSync(envPath)) {
      const overwrite = await question('A .env file already exists. Do you want to overwrite it? (y/n): ');
      if (overwrite.toLowerCase() !== 'y') {
        print('Setup aborted. Existing .env file was not modified.', colors.yellow);
        rl.close();
        return;
      }
    }

    // Get configuration values
    print('Discord Bot Configuration:', colors.magenta);
    const discordToken = await question('Enter your Discord Bot Token: ');
    const adminRoleId = await question('Enter the Admin Role ID: ');
    const logChannelId = await question('Enter the Log Channel ID: ');
    const paymentChannelId = await question('Enter the Payment Verification Channel ID: ');

    print('\nMinecraft Server Configuration:', colors.magenta);
    const minecraftIp = await questionWithDefault('Enter your Minecraft Server IP', 'localhost');
    const minecraftPort = await questionWithDefault('Enter your Minecraft RCON Port', '25575');
    const rconPassword = await question('Enter your Minecraft RCON Password: ');

    // Create the .env file
    let envContent = envTemplate
      .replace('your_discord_bot_token_here', discordToken)
      .replace('admin_role_id_here', adminRoleId)
      .replace('log_channel_id_here', logChannelId)
      .replace('payment_verification_channel_id', paymentChannelId)
      .replace('localhost', minecraftIp)
      .replace('25575', minecraftPort)
      .replace('your_rcon_password_here', rconPassword);
    
    fs.writeFileSync(envPath, envContent);
    print('\n✅ .env file has been created successfully!', colors.green);

    // Verify node_modules
    const nodeModulesPath = path.join(__dirname, 'node_modules');
    if (!fs.existsSync(nodeModulesPath)) {
      print('\n⚠️ The node_modules directory does not exist.', colors.yellow);
      print('You should run "npm install" to install dependencies.', colors.yellow);
    }

    // Instructions for next steps
    print('\nNext Steps:', colors.blue + colors.bright);
    print('1. Make sure you have invited your bot to your Discord server with the correct permissions', colors.blue);
    print('2. Run "npm start" to start the bot', colors.blue);
    print('3. Use the /help command in your Discord server to verify the bot is working', colors.blue);

    rl.close();
  } catch (error) {
    print(`\n❌ Error during setup: ${error.message}`, colors.red);
    rl.close();
  }
}

// Run the setup
setup(); 
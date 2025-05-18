// Direct token test without .env
const { Client, GatewayIntentBits } = require('discord.js');

// Create a minimal client
const client = new Client({
    intents: [GatewayIntentBits.Guilds]
});

// Log when ready
client.once('ready', () => {
    console.log(`✅ SUCCESS! Logged in as ${client.user.tag}`);
    console.log('Your token is working correctly!');
    process.exit(0);
});

// Directly set your token here
const TOKEN = 'MTM3MDEwMDcwODMzMzg1MDYyNA.G531Ri.AneXbbngpwX248vuNoLQK3LjtF_XtAW1-oxRNQ';

console.log('Attempting to connect with direct token...');

// Try to login
client.login(TOKEN).catch(error => {
    console.error('❌ ERROR: Failed to login with direct token');
    console.error('Error details:', error.message);
    console.log('\nYour token may have been invalidated. Try getting a new token:');
    console.log('1. Go to Discord Developer Portal');
    console.log('2. Reset your bot token');
    console.log('3. Copy the new token');
    
    process.exit(1);
}); 
// Simple Discord bot token test
require('dotenv').config();
const { Client, GatewayIntentBits } = require('discord.js');

console.log('Starting token test...');

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

// Get token from .env
const token = process.env.DISCORD_TOKEN;
console.log('Token from .env file:', token ? 'Found (starts with ' + token.substring(0, 5) + '...)' : 'Not found');

// Check if token exists
if (!token) {
    console.error('❌ ERROR: No token found in .env file');
    console.log('Please update your .env file with your token');
    process.exit(1);
}

console.log('Token found in .env, attempting to connect...');

// Try to login
client.login(token).catch(error => {
    console.error('❌ ERROR: Failed to login with token from .env file');
    console.error('Error details:', error.message);
    console.log('\nTry this:');
    console.log('1. Go to Discord Developer Portal');
    console.log('2. Reset your bot token');
    console.log('3. Copy the new token');
    console.log('4. Replace the token in your .env file');
    
    // Alternative direct method
    console.log('\nAlternatively, you can edit this file and try with a direct token:');
    console.log('- Uncomment the code at the bottom of this file');
    console.log('- Replace YOUR_TOKEN_HERE with your actual token');
    console.log('- Run this script again');
    
    process.exit(1);
});

// Test with a direct token
// Uncomment this section and replace YOUR_TOKEN_HERE with your token to test directly
/*
console.log('\nTrying with direct token...');
client.login('YOUR_TOKEN_HERE').catch(err => {
    console.error('❌ Direct token also failed:', err.message);
});
*/ 
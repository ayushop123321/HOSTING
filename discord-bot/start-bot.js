// MelonMC Discord Bot Starter
require('dotenv').config();
const { Client } = require('discord.js');

// Import the main bot file which contains all the event handlers and logic
require('./main.js');

console.log('MelonMC Discord Bot is starting...');
console.log('IMPORTANT: Make sure to set up your .env file with Minecraft RCON settings!');
console.log('To create bills and assign ranks, use the /billform command'); 
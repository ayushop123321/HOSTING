// MelonMC Discord Bot - Integration File
// This file shows how to combine all the Discord bot components

// Import required modules
require('dotenv').config();
const { Client, GatewayIntentBits, Partials, Events, ActivityType } = require('discord.js');
const { REST, Routes } = require('discord.js');
const fs = require('fs').promises;
const path = require('path');

// Import command handlers from commands.js
const { 
    handleRankInfo, 
    handleHelp, 
    handleSyncAnnouncements, 
    handleBillForm, 
    processBillForm,
    processRankSelection,
    handlePastBills 
} = require('./commands');

const {
    createPaymentRequest,
    getPaymentStatus,
    updatePaymentStatus,
    getUserPendingPayments,
    createVerificationEmbed,
    createPaymentReceiptEmbed,
    createPaymentRejectionEmbed
} = require('./payment-handler');
const { executeMinecraftCommand, connectRCON } = require('./minecraft-rcon');

// Define bot commands
const commands = [
    {
        name: 'help',
        description: 'Show available commands'
    },
    {
        name: 'rankinfo',
        description: 'Show information about server ranks'
    },
    {
        name: 'billform',
        description: 'Create a new bill for rank purchase',
        default_member_permissions: '0' // Restricted to admin users
    },
    {
        name: 'pastbills',
        description: 'View past bills and their status',
        default_member_permissions: '0' // Restricted to admin users  
    }
];

// Add sync announcements command
const announcementCommands = [
    {
        name: 'syncannouncements',
        description: 'Sync recent announcements from this channel to the website',
        default_member_permissions: null, // Available to all users
        options: [
            {
                name: 'count',
                description: 'Number of recent messages to process (default: 10)',
                type: 4, // INTEGER type
                required: false
            }
        ]
    }
];

// Combine all commands
const allCommands = [...commands, ...announcementCommands];

// Discord Bot Configuration
const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent,
        GatewayIntentBits.DirectMessages,
    ],
    partials: [Partials.Channel, Partials.Message]
});

// Environment variables
const TOKEN = process.env.DISCORD_TOKEN;
const ADMIN_ROLE_ID = process.env.ADMIN_ROLE_ID;
const LOG_CHANNEL_ID = process.env.LOG_CHANNEL_ID;
const PAYMENT_CHANNEL_ID = process.env.PAYMENT_CHANNEL_ID;
const ANNOUNCEMENT_CHANNEL_ID = process.env.ANNOUNCEMENT_CHANNEL_ID;
const GUILD_ID = process.env.GUILD_ID;
const MC_RCON_HOST = process.env.MC_RCON_HOST || 'localhost';
const MC_RCON_PORT = parseInt(process.env.MC_RCON_PORT || '25575');
const MC_RCON_PASSWORD = process.env.MC_RCON_PASSWORD;

// Bot initialization
async function initBot() {
    try {
        console.log('Starting bot initialization...');
        
        // Register commands
        if (GUILD_ID && client.application) {
        const rest = new REST({ version: '10' }).setToken(TOKEN);
        
        console.log('Started refreshing application (/) commands.');
        
            // Register guild commands
            try {
                await rest.put(
                    Routes.applicationGuildCommands(client.application.id, GUILD_ID),
                    { body: allCommands }
                );
                console.log(`Successfully registered application commands for guild ${GUILD_ID}.`);
            } catch (error) {
                console.error('Error registering guild commands:', error);
                
                // Try global commands if guild commands fail
                try {
        await rest.put(
            Routes.applicationCommands(client.application.id),
                        { body: allCommands }
                    );
                    console.log('Successfully registered global application commands.');
                } catch (globalError) {
                    console.error('Error registering global commands:', globalError);
                }
            }
        } else {
            console.warn('Either GUILD_ID is not set or client.application is not available yet. Commands will be registered when the bot is ready.');
        }
        
        // Set up announcement sync functionality
        if (ANNOUNCEMENT_CHANNEL_ID) {
            console.log(`Setting up announcement monitoring for channel ID: ${ANNOUNCEMENT_CHANNEL_ID}`);
            setupAnnouncementMonitoring();
        } else {
            console.warn('Announcement channel ID not provided. Website announcement sync will not be available.');
        }
        
        // Initialize Minecraft monitoring
        initializeMinecraftMonitoring();
        
        console.log('Bot initialization completed.');
    } catch (error) {
        console.error('Error initializing bot:', error);
    }
}

// Set up announcement monitoring
function setupAnnouncementMonitoring() {
    console.log('Setting up announcement monitoring...');
    
    // Create data directory if it doesn't exist
    const dataDir = path.join(__dirname, 'data');
    fs.mkdir(dataDir, { recursive: true })
        .then(() => console.log('Data directory ready'))
        .catch(err => console.error('Error creating data directory:', err));
        
    // Also ensure website announcements directory exists
    const websiteDir = path.join(__dirname, '..', 'Website', 'announcements');
    fs.mkdir(websiteDir, { recursive: true })
        .then(() => console.log('Website announcements directory ready'))
        .catch(err => console.error('Error creating website announcements directory:', err));
}

// Process announcement message
async function processAnnouncementMessage(message, announcementType = null) {
    try {
        if (message.author.bot) return false;
        
        // Generate a unique ID for the announcement
        const announcementId = 'ann_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        const content = message.content.trim();
        
        // Skip empty messages
        if (!content) {
            console.log("Skipping empty message");
            return false;
        }
        
        // Extract title and content
        let title, body;
        const lines = content.split('\n');
        
        if (lines.length > 1) {
            title = lines[0].trim();
            body = lines.slice(1).join('\n').trim();
        } else {
            title = content.substring(0, 50) + (content.length > 50 ? '...' : '');
            body = content;
        }
        
        // Determine announcement type if not provided
        if (!announcementType) {
            const lowerContent = content.toLowerCase();
            
            if (lowerContent.includes('update') || lowerContent.includes('new')) {
                announcementType = 'update';
            } else if (lowerContent.includes('warning') || lowerContent.includes('caution') || lowerContent.includes('attention')) {
                announcementType = 'warning';
            } else if (lowerContent.includes('critical') || lowerContent.includes('urgent') || lowerContent.includes('emergency')) {
                announcementType = 'critical';
            } else {
                announcementType = 'info';
            }
        }
        
        const timestamp = new Date().toISOString();
        
        // Create announcement object
        const announcement = {
            id: announcementId,
            title: title,
            content: body,
            type: announcementType,
            timestamp: timestamp,
            createdBy: `Discord (${message.author.username})`,
            source: {
                platform: 'discord',
                messageId: message.id,
                channelId: message.channelId,
                authorId: message.author.id,
                authorUsername: message.author.username
            }
        };
        
        // Save the announcement to file
        const success = await saveAnnouncementToFile(announcement);
        console.log(`Announcement processed and saved: ${success ? 'Success' : 'Failed'}`);
        
        // React to the message to indicate it's been processed
        try {
            if (success) {
                await message.react('✅');
            } else {
                await message.react('❌');
            }
        } catch (reactionError) {
            console.error('Error adding reaction:', reactionError);
        }
        
        return success;
    } catch (error) {
        console.error('Error processing announcement message:', error);
        return false;
    }
}

// Save announcement to file
async function saveAnnouncementToFile(announcement) {
    try {
        // Ensure data directories exist
        const botDataDir = path.join(__dirname, 'data');
        const websiteDir = path.join(__dirname, '..', 'Website', 'announcements');
        const adminDir = path.join(__dirname, '..', 'Website', 'admin');
        
        await fs.mkdir(botDataDir, { recursive: true });
        await fs.mkdir(websiteDir, { recursive: true });
        await fs.mkdir(adminDir, { recursive: true });
        
        // Define file paths
        const botDataFile = path.join(botDataDir, 'announcements.json');
        const websiteDataFile = path.join(websiteDir, 'announcements.json');
        const localStorageBackupFile = path.join(adminDir, 'localStorage_backup.json');
        
        // Load existing announcements or create empty arrays
        const files = [botDataFile, websiteDataFile, localStorageBackupFile];
        const existingAnnouncements = await Promise.all(
            files.map(async (file) => {
                try {
                    const data = await fs.readFile(file, 'utf8');
                    return JSON.parse(data);
                } catch (error) {
                    console.log(`Creating new announcements file at: ${file}`);
                    return [];
                }
            })
        );
        
        // Add the new announcement to each array
        const updatedAnnouncements = existingAnnouncements.map(
            announcements => [announcement, ...announcements]
        );
        
        // Save updated announcements back to files
        await Promise.all([
            fs.writeFile(botDataFile, JSON.stringify(updatedAnnouncements[0], null, 2)),
            fs.writeFile(websiteDataFile, JSON.stringify(updatedAnnouncements[1], null, 2)),
            fs.writeFile(localStorageBackupFile, JSON.stringify(updatedAnnouncements[2], null, 2))
        ]);
        
        return true;
    } catch (error) {
        console.error('Error saving announcement to file:', error);
        return false;
    }
}

// Initialize Minecraft monitoring system
async function initializeMinecraftMonitoring() {
    // Create the directory for pending rank assignments
    const pendingDir = path.join(__dirname, 'data', 'pending');
    await fs.mkdir(pendingDir, { recursive: true });
    
    // Start the rank monitoring system
    setInterval(checkPendingRankAssignments, 60000); // Check every 1 minute
}

// Check for pending rank assignments
async function checkPendingRankAssignments() {
    try {
        // Read the bills file
        const billsFile = path.join(__dirname, 'data', 'bills.json');
        let bills = [];
        
        try {
            const data = await fs.readFile(billsFile, 'utf8');
            bills = JSON.parse(data);
        } catch (error) {
            // No bills file or invalid data
            return;
        }
        
        // Find pending bills
        const pendingBills = bills.filter(bill => bill.status === 'pending');
        
        if (pendingBills.length === 0) {
            return; // No pending bills to check
        }
        
        console.log(`Checking ${pendingBills.length} pending rank assignments...`);
        
        // Check each pending bill
        for (const bill of pendingBills) {
            // Check if the bill has expired (more than 10 hours old)
            const creationTime = new Date(bill.timestamp).getTime();
            const currentTime = Date.now();
            const tenHoursInMs = 10 * 60 * 60 * 1000;
            
            if (currentTime - creationTime > tenHoursInMs) {
                // The bill has expired
                console.log(`Bill ${bill.id} for ${bill.playerName} has expired`);
                bill.status = 'expired';
                
                // Notify the admin who created the bill
                const adminUser = await client.users.fetch(bill.assignedBy.split('#')[0]);
                if (adminUser) {
                    try {
                        await adminUser.send({
                            content: `⏰ The rank order for **${bill.playerName}** (${bill.rank}) has expired because the player did not come online within 10 hours.`
                        });
                    } catch (dmError) {
                        console.error(`Failed to DM admin ${bill.assignedBy}:`, dmError);
                    }
                }
                
                continue; // Skip to the next bill
            }
            
            // Check if the player is online
            try {
                // Try to connect to the Minecraft server using RCON
                const rcon = await connectRCON(MC_RCON_HOST, MC_RCON_PORT, MC_RCON_PASSWORD);
                
                // Check if the player is online
                const response = await executeMinecraftCommand(rcon, `list`);
                const playerName = bill.playerName.toLowerCase();
                
                // Check if the player's name appears in the online players list
                if (response.includes(playerName)) {
                    console.log(`Player ${bill.playerName} is online. Applying rank...`);
                    
                    // Apply the rank using LuckPerms command
                    const lpCommand = `lp user ${bill.playerName} parent set ${bill.rank}`;
                    const rankResult = await executeMinecraftCommand(rcon, lpCommand);
                    
                    if (rankResult.includes('success') || !rankResult.includes('error')) {
                        // Rank was successfully applied
                        bill.status = 'completed';
                        bill.completedAt = new Date().toISOString();
                        
                        // Send success message to the player
                        await executeMinecraftCommand(
                            rcon, 
                            `tell ${bill.playerName} Your rank ${bill.rank} has been successfully applied!`
                        );
                        
                        // Send a message to a log channel if configured
                        if (LOG_CHANNEL_ID) {
                            try {
                                const logChannel = await client.channels.fetch(LOG_CHANNEL_ID);
                                await logChannel.send({
                                    content: `✅ Rank **${bill.rank}** has been applied to player **${bill.playerName}**`
                                });
                            } catch (channelError) {
                                console.error('Error sending log message:', channelError);
                            }
                        }
                    } else {
                        // There was an error applying the rank
                        console.error(`Failed to apply rank to ${bill.playerName}:`, rankResult);
                    }
                }
                
                // Close the RCON connection
                rcon.end();
                
            } catch (rconError) {
                console.error('RCON connection error:', rconError);
            }
        }
        
        // Save the updated bills back to the file
        await fs.writeFile(billsFile, JSON.stringify(bills, null, 2));
        
    } catch (error) {
        console.error('Error checking pending rank assignments:', error);
    }
}

// Set up event handlers
client.once(Events.ClientReady, async () => {
    console.log(`Logged in as ${client.user.tag}`);
    
    // Set bot activity status
    client.user.setPresence({
        activities: [{
            name: 'MelonMC Server',
            type: ActivityType.Playing
        }],
        status: 'online'
    });
    
    // Register all commands once the bot is ready
    try {
        console.log('Starting command registration process...');
        const rest = new REST({ version: '10' }).setToken(TOKEN);
        
        // First try to delete any existing commands to avoid duplicates
        try {
            if (GUILD_ID) {
                console.log(`Removing existing guild commands from ${GUILD_ID}...`);
                await rest.put(
                    Routes.applicationGuildCommands(client.user.id, GUILD_ID),
                    { body: [] }
                );
            }
            console.log('Removing existing global commands...');
            await rest.put(
                Routes.applicationCommands(client.user.id),
                { body: [] }
            );
        } catch (deleteError) {
            console.error('Error removing existing commands:', deleteError);
        }
        
        // Register commands globally first as a fallback
        console.log('Registering global commands...');
        await rest.put(
            Routes.applicationCommands(client.user.id),
            { body: allCommands }
        );
        console.log('Successfully registered global application commands.');
        
        // Then try to register guild-specific commands if GUILD_ID is provided
        if (GUILD_ID) {
            console.log(`Registering guild-specific commands for ${GUILD_ID}...`);
            try {
                await rest.put(
                    Routes.applicationGuildCommands(client.user.id, GUILD_ID),
                    { body: allCommands }
                );
                console.log('Successfully registered guild-specific commands.');
            } catch (guildError) {
                console.error('Error registering guild commands:', guildError);
                console.log('Will use global commands instead.');
            }
        }
    } catch (error) {
        console.error('Error registering commands:', error);
    }
    
    // Initialize the bot
    await initBot();
});

// Handle interactions (commands and components)
client.on(Events.InteractionCreate, async (interaction) => {
    try {
        // Handle slash commands
        if (interaction.isChatInputCommand()) {
            const { commandName } = interaction;
            
            console.log(`Received command: ${commandName} from ${interaction.user.tag}`);
            
            // Route to appropriate command handler
            switch(commandName) {
                case 'help':
                    await handleHelp(interaction);
                    break;
                case 'rankinfo':
                    await handleRankInfo(interaction);
                    break;
                case 'syncannouncements':
                    await handleSyncAnnouncements(interaction);
                    break;
                case 'billform':
                    await handleBillForm(interaction);
                    break;
                case 'pastbills':
                    await handlePastBills(interaction);
                    break;
                default:
            await interaction.reply({
                        content: `Command not implemented: ${commandName}`,
                ephemeral: true
            });
        }
    }
        // Handle modal submissions
        else if (interaction.isModalSubmit()) {
            // Handle billform modal submission
            if (interaction.customId === 'billform-modal') {
                await processBillForm(interaction);
            }
        }
        // Handle select menus
        else if (interaction.isStringSelectMenu()) {
            // Handle rank selection for bills
            if (interaction.customId.startsWith('rank-select-')) {
                await processRankSelection(interaction);
            }
        }
    } catch (error) {
        console.error('Error handling interaction:', error);
        try {
            if (!interaction.replied && !interaction.deferred) {
            await interaction.reply({
                    content: '❌ An error occurred while processing your request.',
                    ephemeral: true
                });
            } else {
                await interaction.followUp({
                    content: '❌ An error occurred while processing your request.',
                ephemeral: true
            });
            }
        } catch (replyError) {
            console.error('Error sending error response:', replyError);
        }
    }
});

// Monitor announcement channel for new messages
client.on(Events.MessageCreate, async (message) => {
    try {
        // Process announcements in the announcement channel
        if (message.channelId === ANNOUNCEMENT_CHANNEL_ID && !message.author.bot) {
            console.log(`New message in announcements from ${message.author.tag}`);
            await processAnnouncementMessage(message);
        }
    } catch (error) {
        console.error('Error handling message:', error);
    }
});

// Handle bot errors
client.on('error', (error) => {
    console.error('Discord client error:', error);
});

// Login to Discord
client.login(TOKEN).catch(error => {
    console.error('Failed to login to Discord:', error);
});

// Export client for other modules
module.exports = { client }; 
// Discord bot commands for MelonMC server

const { SlashCommandBuilder } = require('@discordjs/builders');
const { EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle, ModalBuilder, TextInputBuilder, TextInputStyle, StringSelectMenuBuilder, StringSelectMenuOptionBuilder } = require('discord.js');
const fs = require('fs').promises;
const path = require('path');

// Define slash commands
const commands = [
    new SlashCommandBuilder()
        .setName('billform')
        .setDescription('Open a form to process a customer bill')
        .setDefaultMemberPermissions(0), // Restricted to admin users

    new SlashCommandBuilder()
        .setName('pastbills')
        .setDescription('View past bills and their status')
        .setDefaultMemberPermissions(0), // Restricted to admin users

    new SlashCommandBuilder()
        .setName('rankinfo')
        .setDescription('Get information about a specific rank')
        .addStringOption(option =>
            option.setName('rank')
                .setDescription('The rank you want information about')
                .setRequired(true)
                .addChoices(
                    { name: 'Eternal', value: 'eternal' },
                    { name: 'Immortal', value: 'immortal' },
                    { name: 'Knight', value: 'knight' },
                    { name: 'Lord', value: 'lord' },
                    { name: 'Lady', value: 'lady' },
                    { name: 'Duke', value: 'duke' },
                    { name: 'Duchess', value: 'duchess' },
                    { name: 'VIP+', value: 'vip_plus' }
                )),

    new SlashCommandBuilder()
        .setName('coininfo')
        .setDescription('Get information about coin packages')
        .addStringOption(option =>
            option.setName('gamemode')
                .setDescription('The gamemode to get coin information for')
                .setRequired(true)
                .addChoices(
                    { name: 'LifeSteal', value: 'lifesteal' },
                    { name: 'Classic', value: 'classic' }
                )),

    new SlashCommandBuilder()
        .setName('verifypayment')
        .setDescription('Verify a payment and process delivery')
        .setDefaultMemberPermissions(0) // Restricted to admin users
        .addStringOption(option =>
            option.setName('username')
                .setDescription('Minecraft username of the player')
                .setRequired(true))
        .addStringOption(option =>
            option.setName('product')
                .setDescription('Product purchased (rank or coins)')
                .setRequired(true))
        .addStringOption(option =>
            option.setName('gamemode')
                .setDescription('Gamemode for the product')
                .setRequired(true)
                .addChoices(
                    { name: 'LifeSteal', value: 'lifesteal' },
                    { name: 'Classic', value: 'classic' },
                    { name: 'Player', value: 'player' }
                )),

    new SlashCommandBuilder()
        .setName('checkstatus')
        .setDescription('Check status of your purchase')
        .addStringOption(option =>
            option.setName('ordernumber')
                .setDescription('Your order number (if you have one)')
                .setRequired(false)),

    new SlashCommandBuilder()
        .setName('help')
        .setDescription('Get help with using the MelonMC shop bot'),
        
    new SlashCommandBuilder()
        .setName('syncannouncements')
        .setDescription('Manually sync Discord announcements with the website')
        .setDefaultMemberPermissions(0) // Restricted to admin users
        .addIntegerOption(option =>
            option.setName('count')
                .setDescription('Number of recent announcements to sync (default: 10)')
                .setRequired(false))
];

/**
 * Handle the help command
 * @param {import('discord.js').CommandInteraction} interaction 
 */
async function handleHelp(interaction) {
    try {
        const helpEmbed = new EmbedBuilder()
            .setColor('#0099ff')
            .setTitle('MelonMC Discord Bot Commands')
            .setDescription('Here are the available commands for the MelonMC Discord Bot')
            .addFields(
                { name: '/help', value: 'Shows this help message' },
                { name: '/syncannouncements [count]', value: 'Syncs recent announcements from Discord to the website' },
                { name: '/rankinfo', value: 'Shows server ranks and their prices' }
            )
            .setFooter({ text: 'MelonMC Server • Made with ❤️' });
        
        await interaction.reply({ embeds: [helpEmbed] });
    } catch (error) {
        console.error('Error handling help command:', error);
        await safeReply(interaction, '❌ An error occurred while processing the help command.');
    }
}

/**
 * Handle the rankinfo command
 * @param {import('discord.js').CommandInteraction} interaction 
 */
async function handleRankInfo(interaction) {
    try {
        const rankEmbed = new EmbedBuilder()
            .setColor('#00FF00')
            .setTitle('MelonMC Rank Information')
            .setDescription('Here are the ranks available on our server:')
            .addFields(
                { name: 'BOSS RANK', value: '949rs - Access to /fly command, Exclusive BOSS prefix, Colored chat & custom prefix, 6 home locations, Access to all exclusive kits, Priority server access, Special cosmetic effects' },
                { name: 'KING RANK', value: '749rs - Access to /fly command, KING prefix, Colored chat & custom prefix, 5 home locations, Access to premium kits, Priority server access' },
                { name: 'PRO RANK', value: '499rs - Access to /fly command, PRO prefix, Colored chat, 4 home locations, Access to exclusive kits, Priority server access' },
                { name: 'SLAYER RANK', value: '349rs - Access to /fly in specific areas, SLAYER prefix, Colored nickname, 3 home locations, Access to special kits' },
                { name: 'MONSTER RANK', value: '249rs - Access to /fly in spawn, MONSTER prefix, Colored nickname, 2 home locations, Access to basic kits' },
                { name: 'ELITE', value: '149rs - ELITE prefix, Colored nickname, 2 home locations, Access to starter kits, Extended playtime rewards' },
                { name: 'VIP', value: '69rs - VIP prefix, 1 home location, Access to basic features, Daily rewards' }
            )
            .setFooter({ text: 'To purchase a rank, use the /buy command or visit our website: melonmc.xyz' });
        
        await interaction.reply({ embeds: [rankEmbed] });
    } catch (error) {
        console.error('Error handling rank info command:', error);
        await interaction.reply('There was an error displaying rank information. Please try again later.');
    }
}

/**
 * Handle the coininfo command
 * @param {import('discord.js').CommandInteraction} interaction 
 */
async function handleCoinInfo(interaction) {
    try {
        const coinEmbed = new EmbedBuilder()
            .setColor('#ffd700')
            .setTitle('MelonMC Server Coins')
            .setDescription('Information about coin packages available on the MelonMC server')
        .addFields(
                { 
                    name: '🪙 500 Coins - 79rs', 
                    value: 'Basic package for new players' 
                },
                { 
                    name: '💰 1,000 Coins - 149rs', 
                    value: 'Standard package (10% bonus)' 
                },
                { 
                    name: '💸 3,000 Coins - 429rs', 
                    value: 'Value package (15% bonus)' 
                },
                { 
                    name: '🏆 5,000 Coins - 699rs', 
                    value: 'Premium package (20% bonus)' 
                },
                {
                    name: '💎 10,000 Coins - 1,299rs',
                    value: 'Ultimate package (25% bonus)'
                }
            )
            .addFields({
                name: 'How to use coins',
                value: 'Coins can be used to purchase in-game items, claim land, and unlock special features.'
            })
            .setFooter({ text: 'Purchase coins through our store at melonmc.com/store' });
        
        await interaction.reply({ embeds: [coinEmbed] });
    } catch (error) {
        console.error('Error handling coininfo command:', error);
        await safeReply(interaction, '❌ An error occurred while processing the coininfo command.');
    }
}

/**
 * Handle the syncannouncements command
 * @param {import('discord.js').CommandInteraction} interaction 
 */
async function handleSyncAnnouncements(interaction) {
    try {
        // Get count option
        const count = interaction.options.getInteger('count') || 10;
        
        // Reply immediately to let user know we're working on it
        await interaction.reply({
            content: `⏳ Syncing the last ${count} announcements from Discord to the website...`,
            ephemeral: true
        });
        
        // Get the announcement channel
        const channelId = process.env.ANNOUNCEMENT_CHANNEL_ID;
        if (!channelId) {
            return await interaction.followUp({
                content: '❌ ANNOUNCEMENT_CHANNEL_ID not set in environment variables.',
                ephemeral: true
            });
        }
        
        const client = interaction.client;
        const channel = await client.channels.fetch(channelId);
        if (!channel) {
            return await interaction.followUp({
                content: `❌ Could not find announcement channel with ID ${channelId}.`,
                ephemeral: true
            });
        }
        
        // Fetch messages
        const messages = await channel.messages.fetch({ limit: Math.min(count, 100) });
        console.log(`Fetched ${messages.size} messages for sync`);
        
        // Import the sync-all.js functions
        const { processMessage, saveAnnouncementsToAllLocations } = require('./sync-all');
        
        // Process messages into announcements
        const announcements = [];
        messages.forEach(message => {
            if (!message.author.bot) {
                const announcement = processMessage(message);
                if (announcement) {
                    announcements.push(announcement);
                }
            }
        });
        
        if (announcements.length === 0) {
            return await interaction.followUp({
                content: '❌ No valid announcements found in the messages.',
                ephemeral: true
            });
        }
        
        // Sort announcements by date (newest first)
        announcements.sort((a, b) => new Date(b.date) - new Date(a.date));
        
        // Save announcements
        const saveResult = await saveAnnouncementsToAllLocations(announcements);
        
        // Send results
        await interaction.followUp({
            content: `✅ Sync completed!\n\n📊 Results:\n- Processed: ${announcements.length} announcements\n- Saved to ${saveResult} locations\n\nThe announcements should now be visible on the website.`,
            ephemeral: true
        });
        
    } catch (error) {
        console.error('Error handling syncannouncements command:', error);
        await safeReply(interaction, `❌ An error occurred while processing the command: ${error.message}`);
    }
}

/**
 * Handle the billform command
 * @param {import('discord.js').CommandInteraction} interaction 
 */
async function handleBillForm(interaction) {
    try {
        // Create a modal for the bill form
        const modal = new ModalBuilder()
            .setCustomId('billform-modal')
            .setTitle('MelonMC Rank Order Form');

        // Add components to modal
        const playerNameInput = new TextInputBuilder()
            .setCustomId('player-name')
            .setLabel('Player In-Game Name')
            .setStyle(TextInputStyle.Short)
            .setPlaceholder('Enter the player\'s in-game name')
            .setRequired(true)
            .setMinLength(3)
            .setMaxLength(16);

        const discordNameInput = new TextInputBuilder()
            .setCustomId('discord-name')
            .setLabel('Player Discord Username')
            .setStyle(TextInputStyle.Short)
            .setPlaceholder('Enter the player\'s Discord username/tag')
            .setRequired(true)
            .setMinLength(2)
            .setMaxLength(32);

        const notesInput = new TextInputBuilder()
            .setCustomId('notes')
            .setLabel('Additional Notes')
            .setStyle(TextInputStyle.Paragraph)
            .setPlaceholder('Any additional information about the order')
            .setRequired(false)
            .setMaxLength(1000);

        // Add inputs to the modal
        modal.addComponents(
            new ActionRowBuilder().addComponents(playerNameInput),
            new ActionRowBuilder().addComponents(discordNameInput),
            new ActionRowBuilder().addComponents(notesInput)
        );

        // Show the modal
        await interaction.showModal(modal);
    } catch (error) {
        console.error('Error handling billform command:', error);
        await safeReply(interaction, '❌ An error occurred while creating the bill form.');
    }
}

/**
 * Process the bill form submission
 * @param {import('discord.js').ModalSubmitInteraction} interaction 
 */
async function processBillForm(interaction) {
    try {
        // Get form values
        const playerName = interaction.fields.getTextInputValue('player-name');
        const discordName = interaction.fields.getTextInputValue('discord-name');
        const notes = interaction.fields.getTextInputValue('notes') || 'N/A';

        // Acknowledge the interaction first
        await interaction.deferReply({ ephemeral: true });

        // Create rank selection component
        const row = new ActionRowBuilder()
            .addComponents(
                new StringSelectMenuBuilder()
                    .setCustomId(`rank-select-${playerName}-${Date.now()}`)
                    .setPlaceholder('Select the rank to assign')
                    .addOptions([
                        {
                            label: 'Eternal',
                            value: 'eternal',
                            description: 'Eternal Rank - 749rs',
                            emoji: '🌟'
                        },
                        {
                            label: 'Immortal',
                            value: 'immortal',
                            description: 'Immortal Rank - 699rs',
                            emoji: '⚜️'
                        },
                        {
                            label: 'Knight',
                            value: 'knight',
                            description: 'Knight Rank - 599rs',
                            emoji: '🛡️'
                        },
                        {
                            label: 'Lord',
                            value: 'lord',
                            description: 'Lord Rank - 499rs',
                            emoji: '👑'
                        },
                        {
                            label: 'Lady',
                            value: 'lady',
                            description: 'Lady Rank - 399rs',
                            emoji: '👸'
                        },
                        {
                            label: 'Duke',
                            value: 'duke',
                            description: 'Duke Rank - 199rs',
                            emoji: '🏆'
                        },
                        {
                            label: 'Duchess',
                            value: 'duchess',
                            description: 'Duchess Rank - 99rs',
                            emoji: '🎀'
                        },
                        {
                            label: 'VIP+',
                            value: 'vip_plus',
                            description: 'VIP+ Rank - 59rs',
                            emoji: '💎'
                        }
                    ])
            );

        // Send the rank selection menu
        await interaction.followUp({
            content: `📝 Bill form completed for **${playerName}**. Please select the rank to assign:`,
            components: [row],
            ephemeral: true
        });

    } catch (error) {
        console.error('Error processing bill form:', error);
        await interaction.reply({ content: '❌ An error occurred while processing the form.', ephemeral: true });
    }
}

/**
 * Process rank selection for a bill
 * @param {import('discord.js').StringSelectMenuInteraction} interaction 
 */
async function processRankSelection(interaction) {
    try {
        const customId = interaction.customId;
        const parts = customId.split('-');
        const playerName = parts[2];
        
        const selectedRank = interaction.values[0];
        
        // Get information from the interaction
        const discordName = interaction.message.content.match(/for \*\*(.*?)\*\*\./)?.[1] || playerName;
        
        // Generate order ID
        const orderId = `ORD-${Date.now().toString(36)}-${Math.random().toString(36).substring(2, 7).toUpperCase()}`;
        
        // Create order object
        const order = {
            id: orderId,
            playerName: playerName,
            discordName: discordName,
            rank: selectedRank,
            timestamp: new Date().toISOString(),
            status: 'pending', // pending, completed, expired
            completedAt: null,
            assignedBy: interaction.user.tag,
            notes: interaction.message.content.includes('Additional notes:') ? 
                interaction.message.content.split('Additional notes:')[1].trim() : 'N/A'
        };

        // Save the order
        await saveBill(order);

        // Replace the select menu with a confirmation message
    const embed = new EmbedBuilder()
            .setColor('#00ff00')
            .setTitle('🧾 Rank Order Created')
            .setDescription(`Order has been created and is waiting for player login`)
        .addFields(
                { name: 'Order ID', value: orderId, inline: true },
                { name: 'Player', value: playerName, inline: true },
                { name: 'Discord', value: discordName, inline: true },
                { name: 'Rank', value: selectedRank.charAt(0).toUpperCase() + selectedRank.slice(1).replace('_', ' '), inline: true },
                { name: 'Status', value: 'Pending - Waiting for player login', inline: true },
                { name: 'Expiry', value: '10 hours from now', inline: true }
            )
            .setFooter({ text: `The rank will be applied when the player logs in (must be within 10 hours)` })
        .setTimestamp();
    
        await interaction.update({
            content: null,
            embeds: [embed],
            components: []
        });

        // Also send notification to the channel
        const publicEmbed = new EmbedBuilder()
            .setColor('#00ff00')
            .setTitle('🧾 New Rank Order')
            .setDescription(`A new rank order has been created`)
        .addFields(
                { name: 'Order ID', value: orderId, inline: true },
                { name: 'Player', value: playerName, inline: true },
                { name: 'Rank', value: selectedRank.charAt(0).toUpperCase() + selectedRank.slice(1).replace('_', ' '), inline: true },
                { name: 'Status', value: 'Pending - Waiting for player login', inline: true }
            )
            .setFooter({ text: 'The rank will be applied when the player logs in' })
            .setTimestamp();

        await interaction.channel.send({ embeds: [publicEmbed] });

    } catch (error) {
        console.error('Error processing rank selection:', error);
        await interaction.reply({ content: '❌ An error occurred while processing the rank selection.', ephemeral: true });
    }
}

/**
 * Save a bill to the bills data file
 * @param {Object} bill - The bill object to save
 */
async function saveBill(bill) {
    try {
        // Ensure data directory exists
        const dataDir = path.join(__dirname, 'data');
        await fs.mkdir(dataDir, { recursive: true });
        
        const billsFile = path.join(dataDir, 'bills.json');
        
        // Read existing bills or create empty array
        let bills = [];
        try {
            const billsData = await fs.readFile(billsFile, 'utf8');
            bills = JSON.parse(billsData);
        } catch (error) {
            // File doesn't exist or is invalid, start with empty array
            bills = [];
        }
        
        // Add new bill
        bills.push(bill);
        
        // Save back to file
        await fs.writeFile(billsFile, JSON.stringify(bills, null, 2));
        return true;
    } catch (error) {
        console.error('Error saving bill:', error);
        return false;
    }
}

/**
 * Handle the pastbills command to view past bills
 * @param {import('discord.js').CommandInteraction} interaction 
 */
async function handlePastBills(interaction) {
    try {
        // Read bills data file
        const billsFile = path.join(__dirname, 'data', 'bills.json');
        
        let bills = [];
        try {
            const billsData = await fs.readFile(billsFile, 'utf8');
            bills = JSON.parse(billsData);
        } catch (error) {
            return await interaction.reply({
                content: 'No bills found. Create a bill using the `/billform` command.',
                ephemeral: true
            });
        }
        
        if (bills.length === 0) {
            return await interaction.reply({
                content: 'No bills found. Create a bill using the `/billform` command.',
                ephemeral: true
            });
        }
        
        // Sort bills by timestamp (newest first)
        bills.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
        
        // Get the 10 most recent bills
        const recentBills = bills.slice(0, 10);
        
        // Create an embed with the bills
        const embed = new EmbedBuilder()
            .setColor('#0099ff')
            .setTitle('Recent Bills')
            .setDescription(`Showing the ${recentBills.length} most recent bills`)
            .setTimestamp();
        
        recentBills.forEach((bill, index) => {
            const status = bill.status === 'completed' ? '✅ Completed' : 
                          bill.status === 'expired' ? '⏱️ Expired' : '⏳ Pending';
            
            embed.addFields({
                name: `Bill #${index + 1}: ${bill.id}`,
                value: `Player: **${bill.playerName}**\nRank: **${bill.rank}**\nStatus: ${status}\nCreated: <t:${Math.floor(new Date(bill.timestamp).getTime() / 1000)}:R>`
            });
        });
        
        // Send the embed
        await interaction.reply({ embeds: [embed], ephemeral: true });
        
    } catch (error) {
        console.error('Error handling pastbills command:', error);
        await safeReply(interaction, '❌ An error occurred while retrieving past bills.');
    }
}

/**
 * Safe reply helper function
 * @param {import('discord.js').CommandInteraction} interaction 
 * @param {string} content 
 */
async function safeReply(interaction, content) {
    try {
        if (interaction.replied || interaction.deferred) {
            await interaction.followUp({
                content,
                ephemeral: true
            });
        } else {
            await interaction.reply({
                content,
                ephemeral: true
            });
        }
    } catch (error) {
        console.error('Error sending reply:', error);
    }
}

module.exports = {
    handleHelp,
    handleRankInfo,
    handleSyncAnnouncements,
    handleBillForm,
    processBillForm,
    processRankSelection,
    handlePastBills
}; 
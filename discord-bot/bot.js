require('dotenv').config();
const { Client, GatewayIntentBits, Partials, EmbedBuilder, ButtonBuilder, ButtonStyle, ActionRowBuilder, ModalBuilder, TextInputBuilder, TextInputStyle, REST, Routes } = require('discord.js');
const { Rcon } = require('rcon-client');
const { commands, handleRankInfo, handleCoinInfo, handleHelpCommand } = require('./commands');
const paymentHandler = require('./payment-handler');

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

// Environment variables (create a .env file)
const TOKEN = process.env.DISCORD_TOKEN;
const ADMIN_ROLE_ID = process.env.ADMIN_ROLE_ID;
const LOG_CHANNEL_ID = process.env.LOG_CHANNEL_ID;
const MINECRAFT_SERVER_IP = process.env.MINECRAFT_SERVER_IP || 'localhost';
const MINECRAFT_SERVER_PORT = process.env.MINECRAFT_SERVER_PORT || 25575;
const MINECRAFT_SERVER_PASSWORD = process.env.MINECRAFT_RCON_PASSWORD;

// Store active orders
const activeOrders = new Map();

// RCON Connection to Minecraft Server
async function connectRCON() {
    try {
        const rcon = await Rcon.connect({
            host: MINECRAFT_SERVER_IP,
            port: MINECRAFT_SERVER_PORT,
            password: MINECRAFT_SERVER_PASSWORD
        });
        
        console.log('Connected to Minecraft server via RCON');
        return rcon;
    } catch (error) {
        console.error('Failed to connect to RCON:', error);
        return null;
    }
}

// Execute a command on the Minecraft server
async function executeMinecraftCommand(command) {
    try {
        const rcon = await connectRCON();
        if (!rcon) return { success: false, message: 'Failed to connect to the Minecraft server' };
        
        const response = await rcon.send(command);
        await rcon.end();
        
        return { success: true, message: response };
    } catch (error) {
        console.error('Error executing Minecraft command:', error);
        return { success: false, message: error.message };
    }
}

// Slash command handling
client.on('interactionCreate', async interaction => {
    if (!interaction.isCommand()) return;

    const { commandName } = interaction;

    try {
        switch (commandName) {
            case 'billform':
                await handleBillForm(interaction);
                break;
            
            case 'rankinfo':
                await handleRankInfo(interaction);
                break;
            
            case 'coininfo':
                await handleCoinInfo(interaction);
                break;
            
            case 'help':
                await handleHelpCommand(interaction);
                break;
            
            case 'verifypayment':
                await handleVerifyPayment(interaction);
                break;
            
            case 'checkstatus':
                await handleCheckStatus(interaction);
                break;
        }
    } catch (error) {
        console.error(`Error handling command ${commandName}:`, error);
        
        // Only reply if we haven't already
        if (!interaction.replied && !interaction.deferred) {
            await interaction.reply({
                content: 'There was an error executing this command. Please try again later.',
                ephemeral: true
            });
        }
    }
});

// Handle Bill Form
async function handleBillForm(interaction) {
    // Check if user has admin role
    if (!interaction.member.roles.cache.has(ADMIN_ROLE_ID)) {
        return interaction.reply({ content: 'You do not have permission to use this command.', ephemeral: true });
    }

    // Show product selection menu first
    const rankRow = new ActionRowBuilder().addComponents(
        new ButtonBuilder()
            .setCustomId('bill_rank_form')
            .setLabel('Create Rank Bill')
            .setStyle(ButtonStyle.Primary),
    );
    
    const coinsRow = new ActionRowBuilder().addComponents(
        new ButtonBuilder()
            .setCustomId('bill_coins_form')
            .setLabel('Create Coins Bill')
            .setStyle(ButtonStyle.Success),
    );

    await interaction.reply({ 
        content: 'Choose what type of bill to create:',
        components: [rankRow, coinsRow],
        ephemeral: true 
    });
}

// Handle VerifyPayment
async function handleVerifyPayment(interaction) {
    // Check if user has admin role
    if (!interaction.member.roles.cache.has(ADMIN_ROLE_ID)) {
        return interaction.reply({ content: 'You do not have permission to use this command.', ephemeral: true });
    }

    const username = interaction.options.getString('username');
    const product = interaction.options.getString('product');
    const gamemode = interaction.options.getString('gamemode');

    if (!username || !product || !gamemode) {
        await interaction.reply({ 
            content: "Please provide all required information: username, product, and gamemode.", 
            ephemeral: true 
        });
        return;
    }

    // Format product string for processVerifiedPayment
    const formattedProduct = `${product.includes(':') ? product : product + ':' + gamemode}`;

    await interaction.deferReply({ ephemeral: true });

    // Generate a unique order ID
    const orderId = generateOrderId();

    // Process the payment and deliver the product
    const result = await paymentHandler.processVerifiedPayment(
        orderId,
        username,
        formattedProduct,
        interaction,
        client
    );

    if (result) {
        console.log(`Successfully processed payment for ${username}: ${formattedProduct}`);
    } else {
        console.log(`Failed to process payment for ${username}: ${formattedProduct}`);
    }
}

// Handle CheckStatus
async function handleCheckStatus(interaction) {
    const orderNumber = interaction.options.getString('ordernumber');
    
    if (orderNumber && activeOrders.has(orderNumber)) {
        const order = activeOrders.get(orderNumber);
        
        const embed = new EmbedBuilder()
            .setColor(0x00ff00)
            .setTitle(`Order Status - #${orderNumber}`)
            .addFields(
                { name: 'Minecraft Username', value: order.username, inline: true },
                { name: 'Product', value: order.product, inline: true },
                { name: 'Gamemode', value: order.gamemode, inline: true },
                { name: 'Status', value: order.status, inline: true },
                { name: 'Timestamp', value: order.timestamp.toLocaleString(), inline: true }
            );
        
        await interaction.reply({ embeds: [embed], ephemeral: true });
    } else {
        // Show recent purchases for this user
        const userPurchases = Array.from(activeOrders.entries())
            .filter(([orderId, order]) => 
                order.username?.toLowerCase() === interaction.user.username.toLowerCase() ||
                order.discordId === interaction.user.id
            )
            .sort(([id1, a], [id2, b]) => b.timestamp - a.timestamp)
            .slice(0, 5);
        
        if (userPurchases.length > 0) {
            const embed = new EmbedBuilder()
                .setColor(0x00ff00)
                .setTitle(`Your Recent Purchases`)
                .setDescription(userPurchases.map(([id, order]) => 
                    `**Order #${id}** - ${order.product} (${order.status})\n` +
                    `*Purchased: ${order.timestamp.toLocaleString()}*`
                ).join('\n\n'));
            
            await interaction.reply({ embeds: [embed], ephemeral: true });
        } else {
            // No orders found
            await interaction.reply({ 
                content: `No order found with this ID. If you've recently made a purchase, please allow up to 20 minutes for processing.`,
                ephemeral: true 
            });
            
            // Add instructions for verifying payment
            const instructionEmbed = new EmbedBuilder()
                .setColor(0xffaa00)
                .setTitle(`How to Verify Your Purchase`)
                .setDescription(
                    `If you've made a payment, please follow these steps:\n\n` +
                    `1. Share your payment screenshot in the <#${process.env.PAYMENT_CHANNEL_ID || 'payment-verification'}> channel\n` +
                    `2. Include your Minecraft username in the message\n` +
                    `3. An admin will verify and process your purchase as soon as possible\n\n` +
                    `If it's been more than 20 minutes, please tag an admin for assistance.`
                );
            
            await interaction.followUp({ embeds: [instructionEmbed], ephemeral: true });
        }
    }
}

// Handle Modal Submissions
client.on('interactionCreate', async interaction => {
    if (!interaction.isModalSubmit()) return;
    
    if (interaction.customId === 'rank-bill-form-modal') {
        // Get data from modal
        const discordId = interaction.fields.getTextInputValue('discord-id-input');
        const minecraftName = interaction.fields.getTextInputValue('minecraft-name-input');
        const rank = interaction.fields.getTextInputValue('rank-input');
        const gamemode = interaction.fields.getTextInputValue('gamemode-input');
        const amount = interaction.fields.getTextInputValue('amount-input');

        // Format the product string
        const product = `${rank} Rank (${gamemode})`;
        
        // Process the bill
        await processBill(interaction, discordId, minecraftName, product, amount, 'N/A');
    }
    else if (interaction.customId === 'coins-bill-form-modal') {
        // Get data from modal
        const discordId = interaction.fields.getTextInputValue('discord-id-input');
        const minecraftName = interaction.fields.getTextInputValue('minecraft-name-input');
        const coins = interaction.fields.getTextInputValue('coins-input');
        const gamemode = interaction.fields.getTextInputValue('gamemode-input');
        const amount = interaction.fields.getTextInputValue('amount-input');

        // Format the product string
        const product = `${coins} Coins (${gamemode})`;
        
        // Process the bill
        await processBill(interaction, discordId, minecraftName, product, amount, 'N/A');
    }
    else if (interaction.customId === 'bill-form-modal') {
        // Original handler for backward compatibility
        const discordId = interaction.fields.getTextInputValue('discord-id-input');
        const minecraftName = interaction.fields.getTextInputValue('minecraft-name-input');
        const product = interaction.fields.getTextInputValue('product-input');
        const amount = interaction.fields.getTextInputValue('amount-input');
        const paymentId = interaction.fields.getTextInputValue('payment-id-input') || 'N/A';

        // Process the bill
        await processBill(interaction, discordId, minecraftName, product, amount, paymentId);
    }
});

// New function to process bills
async function processBill(interaction, discordId, minecraftName, product, amount, paymentId) {
    // Generate bill number
    const billNumber = `MC-${Date.now().toString().slice(-6)}`;

    // Acknowledge the form submission
    await interaction.reply({ 
        content: `✅ Bill form received! Processing bill #${billNumber}...`,
        ephemeral: true 
    });

    try {
        // Try to fetch the user to verify they exist
        const buyer = await client.users.fetch(discordId);
        
        // Check if Minecraft player exists on the server
        const checkPlayerResult = await executeMinecraftCommand(`is_player_exists ${minecraftName}`);
        
        // If player doesn't exist on the server
        if (!checkPlayerResult.success || checkPlayerResult.message.includes('not found')) {
            await interaction.followUp({
                content: `⚠️ The Minecraft player "${minecraftName}" was not found on the server. Please verify the username.`,
                ephemeral: true
            });
            return;
        }

        // Create confirmation embed
        const confirmEmbed = new EmbedBuilder()
            .setColor(0x00ff00)
            .setTitle(`🧾 MelonMC Purchase - Bill #${billNumber}`)
            .setDescription('Please confirm your purchase details below')
            .addFields(
                { name: 'Product', value: product, inline: true },
                { name: 'Price', value: `₹${amount} INR`, inline: true },
                { name: 'Minecraft Username', value: minecraftName, inline: false },
                { name: 'Payment Reference', value: paymentId, inline: false }
            )
            .setFooter({ text: `Bill #${billNumber}` })
            .setTimestamp();

        // Create confirmation buttons
        const confirmButton = new ButtonBuilder()
            .setCustomId(`confirm-purchase-${billNumber}`)
            .setLabel('Confirm Purchase')
            .setStyle(ButtonStyle.Success);

        const cancelButton = new ButtonBuilder()
            .setCustomId(`cancel-purchase-${billNumber}`)
            .setLabel('Cancel')
            .setStyle(ButtonStyle.Danger);

        const row = new ActionRowBuilder().addComponents(confirmButton, cancelButton);

        // Store the order information
        activeOrders.set(billNumber, {
            username: minecraftName,
            product: product,
            gamemode: determineGamemode(product),
            amount: amount,
            timestamp: new Date(),
            status: 'pending',
            discordId: discordId,
            paymentId: paymentId
        });

        // Send confirmation message to buyer
        try {
            const dmChannel = await buyer.createDM();
            await dmChannel.send({ embeds: [confirmEmbed], components: [row] });
            
            // Send confirmation to the admin
            await interaction.followUp({
                content: `✅ Bill #${billNumber} has been sent to the buyer (<@${discordId}>) for confirmation.`,
                ephemeral: true
            });
        } catch (error) {
            // If we can't DM the user
            console.error('Error sending DM:', error);
            await interaction.followUp({
                content: `⚠️ Could not send DM to the buyer. They might have DMs disabled.\n\nPlease ask them to enable DMs from server members or try an alternate method.`,
                ephemeral: true
            });
        }
    } catch (error) {
        // If the user doesn't exist
        console.error('Error processing bill form:', error);
        await interaction.followUp({
            content: `⚠️ Error: Could not find a Discord user with ID: ${discordId}. Please check the ID and try again.`,
            ephemeral: true
        });
    }
}

// Add a new handler for the bill type buttons
client.on('interactionCreate', async interaction => {
    if (!interaction.isButton()) return;
    
    const customId = interaction.customId;
    
    if (customId === 'bill_rank_form') {
        await createRankBillForm(interaction);
    } else if (customId === 'bill_coins_form') {
        await createCoinsBillForm(interaction);
    } else if (customId.startsWith('confirm-purchase-')) {
        const billNumber = customId.replace('confirm-purchase-', '');
        
        // Parse the embed to get the details
        const embed = interaction.message.embeds[0];
        const productField = embed.fields.find(field => field.name === 'Product');
        const usernameField = embed.fields.find(field => field.name === 'Minecraft Username');
        
        if (!productField || !usernameField) {
            return interaction.reply({
                content: '⚠️ Could not process confirmation: Missing data in the purchase details.',
                ephemeral: true
            });
        }
        
        const product = productField.value;
        const minecraftName = usernameField.value;
        
        // Acknowledge the confirmation
        await interaction.reply({
            content: `✅ Thank you for confirming your purchase of "${product}"! Your item will be delivered to your Minecraft account shortly.`,
            ephemeral: true
        });
        
        // Disable the buttons
        const disabledRow = ActionRowBuilder.from(interaction.message.components[0])
            .setComponents(
                ButtonBuilder.from(interaction.message.components[0].components[0])
                    .setDisabled(true),
                ButtonBuilder.from(interaction.message.components[0].components[1])
                    .setDisabled(true)
            );
        
        await interaction.message.edit({ components: [disabledRow] });
        
        // Update the embed to show confirmed status
        const updatedEmbed = EmbedBuilder.from(embed)
            .setColor(0x00ff00)
            .setTitle(`✅ Purchase Confirmed - Bill #${billNumber}`)
            .setDescription('Your purchase has been confirmed and will be delivered shortly.');
        
        await interaction.message.edit({ embeds: [updatedEmbed] });
        
        // Update order status
        if (activeOrders.has(billNumber)) {
            const order = activeOrders.get(billNumber);
            order.status = 'confirmed';
            activeOrders.set(billNumber, order);
        }
        
        // Process the delivery of the item based on product name
        let command = '';
        const gamemode = determineGamemode(product);
        
        if (product.toLowerCase().includes('rank') || isRankName(product)) {
            // Process rank purchase
            // Extract rank name
            const rankName = extractRankName(product);
            command = `giverank ${minecraftName} ${rankName} ${gamemode}`;
        } else if (product.toLowerCase().includes('coin')) {
            // Process coin purchase
            // Extract coin amount
            const coinAmount = extractCoinAmount(product);
            command = `givecoin ${minecraftName} ${coinAmount} ${gamemode}`;
        } else {
            // Generic item
            command = `give_item ${minecraftName} "${product}"`;
        }
        
        // Execute the command on the Minecraft server
        const result = await executeMinecraftCommand(command);
        
        // Create a log channel message for admins
        const logEmbed = new EmbedBuilder()
            .setColor(result.success ? 0x00ff00 : 0xff0000)
            .setTitle(`${result.success ? '✅' : '❌'} Purchase Delivery - Bill #${billNumber}`)
            .setDescription(`Delivery for <@${interaction.user.id}>`)
            .addFields(
                { name: 'Product', value: product, inline: true },
                { name: 'Minecraft Username', value: minecraftName, inline: true },
                { name: 'Command', value: `\`${command}\``, inline: false },
                { name: 'Result', value: result.message || 'No response from server', inline: false }
            )
            .setTimestamp();
        
        // Send to log channel
        const logChannel = client.channels.cache.get(LOG_CHANNEL_ID);
        if (logChannel) {
            logChannel.send({ embeds: [logEmbed] });
        }
        
        // Notify the user of the result
        const deliveryEmbed = new EmbedBuilder()
            .setColor(result.success ? 0x00ff00 : 0xff0000)
            .setTitle(`${result.success ? '✅' : '⚠️'} Delivery Status - Bill #${billNumber}`)
            .setDescription(result.success 
                ? `Your "${product}" has been successfully delivered to your Minecraft account!` 
                : `There was an issue delivering your purchase. Please contact our support team.`)
            .setFooter({ text: 'Thank you for supporting MelonMC!' })
            .setTimestamp();
        
        await interaction.followUp({ embeds: [deliveryEmbed], ephemeral: true });
        
        // Update order status
        if (activeOrders.has(billNumber)) {
            const order = activeOrders.get(billNumber);
            order.status = result.success ? 'completed' : 'failed';
            activeOrders.set(billNumber, order);
        }
        
    } else if (customId.startsWith('cancel-purchase-')) {
        const billNumber = customId.replace('cancel-purchase-', '');
        
        // Acknowledge the cancellation
        await interaction.reply({
            content: '❌ Purchase cancelled. If this was a mistake, please contact an admin.',
            ephemeral: true
        });
        
        // Disable the buttons
        const disabledRow = ActionRowBuilder.from(interaction.message.components[0])
            .setComponents(
                ButtonBuilder.from(interaction.message.components[0].components[0])
                    .setDisabled(true),
                ButtonBuilder.from(interaction.message.components[0].components[1])
                    .setDisabled(true)
            );
        
        await interaction.message.edit({ components: [disabledRow] });
        
        // Update the embed to show cancelled status
        const embed = interaction.message.embeds[0];
        const updatedEmbed = EmbedBuilder.from(embed)
            .setColor(0xff0000)
            .setTitle(`❌ Purchase Cancelled - Bill #${billNumber}`)
            .setDescription('This purchase has been cancelled. Contact an admin if this was a mistake.');
        
        await interaction.message.edit({ embeds: [updatedEmbed] });
        
        // Update order status
        if (activeOrders.has(billNumber)) {
            const order = activeOrders.get(billNumber);
            order.status = 'cancelled';
            activeOrders.set(billNumber, order);
        }
    } else if (customId.startsWith('buy_rank_') || customId.startsWith('buy_coins_')) {
        const type = customId.startsWith('buy_rank_') ? 'rank' : 'coins';
        const itemName = customId.replace(`buy_${type}_`, '');
        
        // Create a purchase guide modal or message
        const purchaseGuideEmbed = new EmbedBuilder()
            .setColor(0xff9d00)
            .setTitle(`How to Purchase ${type === 'rank' ? 'a Rank' : 'Coins'}`)
            .setDescription(
                `To purchase ${type === 'rank' ? `the ${itemName} rank` : `${itemName} coins`}, follow these steps:`
            )
            .addFields(
                { name: '1. Make Payment', value: 'Use the UPI QR code on our website: https://melon-mc.fun/payment' },
                { name: '2. Join Our Discord', value: 'Make sure you\'re in our Discord server: https://discord.gg/7fKJwXnQrB' },
                { name: '3. Submit Proof', value: `Create a ticket and send your payment screenshot along with your Minecraft username` },
                { name: '4. Wait for Verification', value: 'An admin will verify your payment and deliver your purchase' }
            )
            .setFooter({ text: 'Your purchase will be delivered within 20 minutes of verification' });
        
        await interaction.reply({ embeds: [purchaseGuideEmbed], ephemeral: true });
    } else if (customId === 'confirm_purchase') {
        // Check if user has admin role
        if (!checkAdminRole(interaction)) {
            await interaction.reply({ 
                content: "You don't have permission to confirm purchases.", 
                ephemeral: true 
            });
            return;
        }

        await interaction.deferUpdate();

        // Get information from the embed
        const embed = interaction.message.embeds[0];
        
        // Parse user and product info from embed fields
        const userField = embed.fields.find(field => field.name === 'Discord User');
        const usernameField = embed.fields.find(field => field.name === 'Minecraft Username');
        const productField = embed.fields.find(field => field.name === 'Product');
        const orderIdField = embed.fields.find(field => field.name === 'Order ID');
        
        if (!userField || !usernameField || !productField || !orderIdField) {
            await interaction.followUp({ 
                content: "Error: Could not find required information in the purchase embed.", 
                ephemeral: true 
            });
            return;
        }
        
        const discordUserId = userField.value.replace(/[<@!>]/g, '');
        const username = usernameField.value;
        const product = productField.value;
        const orderId = orderIdField.value;
        
        try {
            // Disable the buttons
            const disabledRow = new ActionRowBuilder()
                .addComponents(
                    new ButtonBuilder()
                        .setCustomId('confirm_purchase')
                        .setLabel('Confirm')
                        .setStyle(ButtonStyle.Success)
                        .setDisabled(true),
                    new ButtonBuilder()
                        .setCustomId('cancel_purchase')
                        .setLabel('Cancel')
                        .setStyle(ButtonStyle.Danger)
                        .setDisabled(true)
                );
            
            // Update the original message to show processing
            const processingEmbed = EmbedBuilder.from(embed)
                .setColor('#FFA500')
                .setTitle('Processing Purchase...');
            
            await interaction.message.edit({ 
                embeds: [processingEmbed], 
                components: [disabledRow] 
            });
            
            // Process the payment
            const result = await paymentHandler.processVerifiedPayment(
                orderId,
                username,
                product,
                interaction,
                client
            );
            
            // Update the embed based on the result
            const resultEmbed = EmbedBuilder.from(embed)
                .setColor(result ? '#00FF00' : '#FF0000')
                .setTitle(result ? '✅ Purchase Confirmed' : '❌ Purchase Failed')
                .setDescription(result 
                    ? `The purchase has been confirmed and ${product} has been delivered to ${username}.`
                    : `The purchase has failed. Please check logs for details.`);
            
            await interaction.message.edit({ 
                embeds: [resultEmbed], 
                components: [disabledRow] 
            });
            
            // Notify the customer
            try {
                const member = await interaction.guild.members.fetch(discordUserId);
                if (member) {
                    await member.send({
                        embeds: [new EmbedBuilder()
                            .setColor(result ? '#00FF00' : '#FF0000')
                            .setTitle(result ? '✅ Your purchase was successful!' : '❌ There was an issue with your purchase')
                            .setDescription(result 
                                ? `Your purchase of ${product} has been processed and delivered to ${username}.` 
                                : `There was an issue processing your purchase of ${product}. Please contact server staff for assistance.`)
                            .setTimestamp()
                        ]
                    });
                }
            } catch (error) {
                console.error(`Could not DM user ${discordUserId}:`, error);
            }
            
            // Log the result
            const logChannel = client.channels.cache.get(process.env.LOG_CHANNEL_ID);
            if (logChannel) {
                await logChannel.send({
                    embeds: [new EmbedBuilder()
                        .setColor(result ? '#00FF00' : '#FF0000')
                        .setTitle(`${result ? '✅ Purchase Confirmed' : '❌ Purchase Failed'} | Order #${orderId}`)
                        .addFields(
                            { name: 'Discord User', value: `<@${discordUserId}>`, inline: true },
                            { name: 'Minecraft Username', value: username, inline: true },
                            { name: 'Product', value: product, inline: true },
                            { name: 'Processed By', value: interaction.user.tag, inline: true },
                            { name: 'Status', value: result ? 'Success' : 'Failed', inline: true },
                            { name: 'Time', value: new Date().toLocaleString(), inline: true }
                        )
                        .setTimestamp()
                    ]
                });
            }
            
            // Update the order status in our tracking system
            if (activeOrders.has(orderId)) {
                const order = activeOrders.get(orderId);
                order.status = result ? 'completed' : 'failed';
                order.processedBy = interaction.user.id;
                order.processingTime = new Date();
                activeOrders.set(orderId, order);
            }
            
        } catch (error) {
            console.error('Error processing purchase:', error);
            await interaction.followUp({ 
                content: `An error occurred while processing the purchase: ${error.message}`, 
                ephemeral: true 
            });
        }
    } else if (customId === 'cancel_purchase') {
        // Handle cancel purchase button (if needed)
        await interaction.reply({
            content: 'Purchase has been cancelled.',
            ephemeral: true
        });
    }
});

// Register commands when bot is ready
client.once('ready', async () => {
    console.log(`Logged in as ${client.user.tag}`);
    
    try {
        // Register commands
        const rest = new REST({ version: '10' }).setToken(TOKEN);
        
        console.log('Started refreshing application (/) commands.');
        
        await rest.put(
            Routes.applicationCommands(client.user.id),
            { body: commands }
        );
        
        console.log('Successfully reloaded application (/) commands.');
    } catch (error) {
        console.error('Error registering commands:', error);
    }
});

// Helper functions
function determineGamemode(productString) {
    productString = productString.toLowerCase();
    
    if (productString.includes('lifesteal')) {
        return 'lifesteal';
    } else if (productString.includes('classic')) {
        return 'classic';
    } else {
        // Default gamemode if not specified
        return 'player';
    }
}

function isRankName(productString) {
    const rankNames = ['eternal', 'immortal', 'knight', 'lord', 'lady', 'duke', 'duchess', 'vip+', 'vip plus'];
    return rankNames.some(rank => productString.toLowerCase().includes(rank));
}

function extractRankName(productString) {
    const rankNames = {
        'eternal': 'eternal',
        'immortal': 'immortal',
        'knight': 'knight',
        'lord': 'lord',
        'lady': 'lady',
        'duke': 'duke',
        'duchess': 'duchess',
        'vip+': 'vip_plus',
        'vip plus': 'vip_plus'
    };
    
    for (const [key, value] of Object.entries(rankNames)) {
        if (productString.toLowerCase().includes(key)) {
            return value;
        }
    }
    
    // If no specific rank name is found, extract the first word
    const firstWord = productString.split(' ')[0];
    return firstWord.toLowerCase();
}

function extractCoinAmount(productString) {
    // Try to find a number in the string
    const match = productString.match(/\d+/);
    
    if (match) {
        return match[0];
    }
    
    // Default amount if no number is found
    return '1000';
}

// Check if the user has admin role
function checkAdminRole(interaction) {
    if (!interaction.member) return false;
    return interaction.member.roles.cache.has(ADMIN_ROLE_ID);
}

function generateOrderId() {
    // Generate a random order ID with format MC-XXXXXX
    return `MC-${Math.floor(100000 + Math.random() * 900000)}`;
}

// Start the bot
// If you're having token issues, replace 'TOKEN' with your actual token in quotes
client.login(TOKEN).catch(error => {
    console.error('Failed to log in with environment token:', error);
    console.log('Attempting alternative login method...');
    
    // Uncomment and replace with your token if .env isn't working
    // client.login("paste_your_token_here").catch(err => {
    //     console.error('Alternative login also failed:', err);
    // });
}); 
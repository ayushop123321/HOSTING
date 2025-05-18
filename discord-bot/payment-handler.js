// Payment handling functions for MelonMC Discord bot
const { EmbedBuilder, ButtonBuilder, ButtonStyle, ActionRowBuilder } = require('discord.js');
const minecraftRcon = require('./minecraft-rcon');
const { MessageEmbed } = require('discord.js');
const { getRankInfo, getCoinPackages } = require('./commands');

// Store pending payments
const pendingPayments = new Map();

/**
 * Create a new payment request
 * @param {Object} paymentData Payment details
 * @param {string} paymentData.username Minecraft username
 * @param {string} paymentData.discordId Discord user ID
 * @param {string} paymentData.product Product name
 * @param {string} paymentData.gamemode Game mode
 * @param {number} paymentData.amount Payment amount
 * @returns {string} Payment ID
 */
function createPaymentRequest(paymentData) {
    const paymentId = generatePaymentId();
    
    pendingPayments.set(paymentId, {
        ...paymentData,
        status: 'pending',
        createdAt: new Date(),
        expiresAt: new Date(Date.now() + 3600000) // Expire in 1 hour
    });
    
    return paymentId;
}

/**
 * Get payment status
 * @param {string} paymentId Payment ID
 * @returns {Object|null} Payment data or null if not found
 */
function getPaymentStatus(paymentId) {
    return pendingPayments.get(paymentId) || null;
}

/**
 * Update payment status
 * @param {string} paymentId Payment ID
 * @param {string} status New status ('pending', 'verified', 'completed', 'rejected')
 * @param {Object} additionalData Additional data to add to the payment record
 * @returns {boolean} Success status
 */
function updatePaymentStatus(paymentId, status, additionalData = {}) {
    if (!pendingPayments.has(paymentId)) return false;
    
    const payment = pendingPayments.get(paymentId);
    payment.status = status;
    payment.updatedAt = new Date();
    
    // Add any additional data
    Object.assign(payment, additionalData);
    
    pendingPayments.set(paymentId, payment);
    return true;
}

/**
 * Get all pending payments for a user
 * @param {string} discordId Discord user ID
 * @returns {Array} Array of pending payments
 */
function getUserPendingPayments(discordId) {
    return Array.from(pendingPayments.values())
        .filter(payment => payment.discordId === discordId && payment.status === 'pending');
}

/**
 * Create payment verification embed for admins
 * @param {Object} paymentData Payment details
 * @param {string} paymentId Payment ID
 * @returns {Object} Embed and components
 */
function createVerificationEmbed(paymentData, paymentId) {
    const embed = new EmbedBuilder()
        .setColor(0xffa500)
        .setTitle('Payment Verification Request')
        .setDescription(`A new payment needs verification. Please check the details and verify or reject this payment.`)
        .addFields(
            { name: 'Minecraft Username', value: paymentData.username, inline: true },
            { name: 'Product', value: paymentData.product, inline: true },
            { name: 'Amount', value: `₹${paymentData.amount} INR`, inline: true },
            { name: 'Game Mode', value: paymentData.gamemode || 'Not specified', inline: true },
            { name: 'Discord User', value: `<@${paymentData.discordId}>`, inline: true },
            { name: 'Payment ID', value: paymentId, inline: true }
        )
        .setTimestamp();
    
    // Create verification buttons
    const verifyButton = new ButtonBuilder()
        .setCustomId(`verify_payment_${paymentId}`)
        .setLabel('Verify Payment')
        .setStyle(ButtonStyle.Success);
    
    const rejectButton = new ButtonBuilder()
        .setCustomId(`reject_payment_${paymentId}`)
        .setLabel('Reject Payment')
        .setStyle(ButtonStyle.Danger);
    
    const actionRow = new ActionRowBuilder().addComponents(verifyButton, rejectButton);
    
    return { embed, actionRow };
}

/**
 * Create payment receipt embed for users
 * @param {Object} paymentData Payment details
 * @param {string} paymentId Payment ID
 * @returns {Object} Embed
 */
function createPaymentReceiptEmbed(paymentData, paymentId) {
    const embed = new EmbedBuilder()
        .setColor(0x00ff00)
        .setTitle('Payment Verified!')
        .setDescription(`Your payment has been verified and your purchase has been delivered!`)
        .addFields(
            { name: 'Product', value: paymentData.product, inline: true },
            { name: 'Minecraft Username', value: paymentData.username, inline: true },
            { name: 'Payment ID', value: paymentId, inline: false },
            { name: 'Next Steps', value: 'Your purchase should be available in-game. If you encounter any issues, please contact our support team.', inline: false }
        )
        .setFooter({ text: 'Thank you for supporting MelonMC!' })
        .setTimestamp();
    
    return embed;
}

/**
 * Create payment rejection embed for users
 * @param {Object} paymentData Payment details
 * @param {string} paymentId Payment ID
 * @param {string} reason Rejection reason
 * @returns {Object} Embed
 */
function createPaymentRejectionEmbed(paymentData, paymentId, reason) {
    const embed = new EmbedBuilder()
        .setColor(0xff0000)
        .setTitle('Payment Rejected')
        .setDescription(`Your payment for ${paymentData.product} has been rejected.`)
        .addFields(
            { name: 'Reason', value: reason || 'No reason provided', inline: false },
            { name: 'Payment ID', value: paymentId, inline: true },
            { name: 'Next Steps', value: 'Please contact our support team if you believe this is a mistake.', inline: false }
        )
        .setTimestamp();
    
    return embed;
}

/**
 * Clean up expired payment requests
 * @returns {number} Number of expired payments removed
 */
function cleanupExpiredPayments() {
    const now = new Date();
    let count = 0;
    
    for (const [id, payment] of pendingPayments.entries()) {
        if (payment.expiresAt && payment.expiresAt < now && payment.status === 'pending') {
            pendingPayments.delete(id);
            count++;
        }
    }
    
    return count;
}

/**
 * Generate a unique payment ID
 * @returns {string} Payment ID
 */
function generatePaymentId() {
    return `PAY-${Date.now().toString(36)}-${Math.random().toString(36).substr(2, 5)}`.toUpperCase();
}

// Set up a cleanup job to run every hour
setInterval(cleanupExpiredPayments, 3600000);

/**
 * Process a verified payment by giving the player the purchased product
 * @param {string} orderId Order identifier
 * @param {string} username Minecraft username
 * @param {string} product Product string (e.g., "rank:vip:skyblock" or "coins:small:skyblock")
 * @param {object} interaction Discord interaction for responding
 * @param {object} client Discord client
 * @returns {Promise<boolean>} Success of delivery
 */
async function processVerifiedPayment(orderId, username, product, interaction, client) {
    try {
        if (!username || !product) {
            await interaction.followUp('Missing username or product information.');
            return false;
        }

        // Check if player exists on the server
        const playerExists = await minecraftRcon.checkPlayerExists(username);
        if (!playerExists) {
            await interaction.followUp(`Player "${username}" does not exist on our server. Please check the username.`);
            return false;
        }

        // Parse product string
        const [type, productId, gamemode] = product.split(':');
        
        if (!type || !productId || !gamemode) {
            await interaction.followUp('Invalid product format. Expected format: "type:productId:gamemode"');
            return false;
        }

        // Process based on product type
        let result;
        if (type.toLowerCase() === 'rank') {
            // Get rank info for confirmation message
            const rankInfo = getRankInfo(productId, gamemode);
            
            // Process rank purchase
            result = await minecraftRcon.giveRank(username, productId, gamemode);
            
            if (result.success) {
                await sendSuccessMessage(client, orderId, username, `rank ${productId} (${gamemode})`, result.message);
            } else {
                await sendFailureMessage(client, orderId, username, `rank ${productId} (${gamemode})`, result.message);
            }
        } 
        else if (type.toLowerCase() === 'coins') {
            // Get coin package info
            const coinPackages = getCoinPackages(gamemode);
            const packageInfo = coinPackages.find(pkg => pkg.id === productId);
            
            if (!packageInfo) {
                await interaction.followUp(`Coin package "${productId}" not found for gamemode "${gamemode}"`);
                return false;
            }
            
            // Process coin purchase
            result = await minecraftRcon.giveCoins(username, packageInfo.amount, gamemode);
            
            if (result.success) {
                await sendSuccessMessage(client, orderId, username, `${packageInfo.amount} coins (${gamemode})`, result.message);
            } else {
                await sendFailureMessage(client, orderId, username, `${packageInfo.amount} coins (${gamemode})`, result.message);
            }
        } 
        else {
            await interaction.followUp(`Unknown product type: ${type}`);
            return false;
        }

        // Respond to the interaction
        if (result.success) {
            await interaction.followUp({
                content: `Successfully delivered ${type} to ${username}! They have been notified in-game.`,
                ephemeral: true
            });
            
            // Log the successful delivery to a channel if configured
            logDelivery(client, orderId, username, product, result.success, result.message);
            
            return true;
        } else {
            await interaction.followUp({
                content: `Failed to deliver ${type} to ${username}: ${result.message}`,
                ephemeral: true
            });
            
            // Log the failed delivery
            logDelivery(client, orderId, username, product, result.success, result.message);
            
            return false;
        }
    } catch (error) {
        console.error('Error processing payment:', error);
        await interaction.followUp({
            content: `An error occurred while processing the payment: ${error.message}`,
            ephemeral: true
        });
        return false;
    }
}

/**
 * Send a success message to the user on Discord
 * @param {object} client Discord client
 * @param {string} orderId Order identifier
 * @param {string} username Minecraft username
 * @param {string} product Product description
 * @param {string} message Additional details
 */
async function sendSuccessMessage(client, orderId, username, product, message) {
    try {
        // Send a direct message to the user if we have their Discord ID
        // This would require storing the Discord ID with the order
        // For now, we'll just log it
        console.log(`Success message for order ${orderId}: ${message}`);
    } catch (error) {
        console.error('Error sending success message:', error);
    }
}

/**
 * Send a failure message to the user on Discord
 * @param {object} client Discord client
 * @param {string} orderId Order identifier
 * @param {string} username Minecraft username
 * @param {string} product Product description
 * @param {string} error Error message
 */
async function sendFailureMessage(client, orderId, username, product, error) {
    try {
        // Send a direct message to the user if we have their Discord ID
        // This would require storing the Discord ID with the order
        // For now, we'll just log it
        console.log(`Failure message for order ${orderId}: ${error}`);
    } catch (error) {
        console.error('Error sending failure message:', error);
    }
}

/**
 * Log delivery status to a Discord channel
 * @param {object} client Discord client
 * @param {string} orderId Order identifier
 * @param {string} username Minecraft username
 * @param {string} product Product string
 * @param {boolean} success Whether delivery was successful
 * @param {string} message Additional details or error message
 */
async function logDelivery(client, orderId, username, product, success, message) {
    try {
        const logChannelId = process.env.LOG_CHANNEL_ID;
        if (!logChannelId) return;
        
        const logChannel = client.channels.cache.get(logChannelId);
        if (!logChannel) return;
        
        const embed = new MessageEmbed()
            .setTitle(`${success ? '✅ Delivery Success' : '❌ Delivery Failed'} | Order #${orderId}`)
            .setColor(success ? '#00FF00' : '#FF0000')
            .addField('Minecraft Username', username, true)
            .addField('Product', product, true)
            .addField('Time', new Date().toLocaleString(), true)
            .addField('Details', message || 'No additional details')
            .setTimestamp();
        
        await logChannel.send({ embeds: [embed] });
    } catch (error) {
        console.error('Error logging delivery:', error);
    }
}

module.exports = {
    createPaymentRequest,
    getPaymentStatus,
    updatePaymentStatus,
    getUserPendingPayments,
    createVerificationEmbed,
    createPaymentReceiptEmbed,
    createPaymentRejectionEmbed,
    pendingPayments,
    processVerifiedPayment
}; 
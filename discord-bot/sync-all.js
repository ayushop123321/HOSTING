// Standalone script to sync all Discord announcements
require('dotenv').config();
const { Client, GatewayIntentBits } = require('discord.js');
const fs = require('fs').promises;
const path = require('path');

// Initialize Discord client with necessary intents
const client = new Client({ 
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent
    ] 
});

// File paths for saving announcements
const DATA_DIR = path.join(__dirname, 'data');
const WEBSITE_ANNOUNCEMENTS_DIR = path.join(__dirname, '..', 'Website', 'announcements');
const WEBSITE_ADMIN_DIR = path.join(__dirname, '..', 'Website', 'admin');
const filePaths = {
    local: path.join(DATA_DIR, 'announcements.json'),
    website: path.join(WEBSITE_ANNOUNCEMENTS_DIR, 'announcements.json'),
    localStorage: path.join(WEBSITE_ADMIN_DIR, 'localStorage_backup.json')
};

// Generate a unique ID based on timestamp and username
function generateUniqueId(message) {
    const timestamp = message.createdTimestamp;
    const authorId = message.author.id;
    return `${timestamp}-${authorId.substring(0, 6)}`;
}

// Extract title from message content (first line or first sentence)
function extractTitle(content) {
    // Try to get first line, if it's not too long
    const firstLine = content.split('\n')[0].trim();
    if (firstLine.length <= 100) {
        return firstLine;
    }
    
    // Otherwise try to get first sentence
    const firstSentence = content.split(/[.!?]/, 1)[0].trim();
    if (firstSentence.length <= 100) {
        return firstSentence + '...';
    }
    
    // If all else fails, truncate
    return content.substring(0, 97) + '...';
}

// Determine announcement type based on content keywords
function determineAnnouncementType(content) {
    const lowercaseContent = content.toLowerCase();
    
    if (lowercaseContent.includes('critical') || 
        lowercaseContent.includes('emergency') || 
        lowercaseContent.includes('urgent')) {
        return 'critical';
    }
    
    if (lowercaseContent.includes('warning') || 
        lowercaseContent.includes('caution') || 
        lowercaseContent.includes('attention')) {
        return 'warning';
    }
    
    if (lowercaseContent.includes('update') || 
        lowercaseContent.includes('version') || 
        lowercaseContent.includes('new feature')) {
        return 'update';
    }
    
    return 'info'; // Default type
}

// Process a Discord message into an announcement object
function processMessage(message) {
    const content = message.content.trim();
    
    // Skip empty messages or ones that are too short
    if (!content || content.length < 5) {
        console.log(`Skipping message ${message.id}: Too short`);
        return null;
    }
    
    // Skip messages that look like commands
    if (content.startsWith('!') || content.startsWith('/') || content.startsWith('.')) {
        console.log(`Skipping message ${message.id}: Looks like a command`);
        return null;
    }
    
    const announcement = {
        id: generateUniqueId(message),
        title: extractTitle(content),
        content: content,
        date: message.createdAt.toISOString(),
        author: message.author.username,
        authorId: message.author.id,
        type: determineAnnouncementType(content),
        messageId: message.id,
        timestamp: message.createdTimestamp
    };
    
    return announcement;
}

// Ensure directories exist
async function ensureDirectoriesExist() {
    try {
        await fs.mkdir(DATA_DIR, { recursive: true });
        await fs.mkdir(WEBSITE_ANNOUNCEMENTS_DIR, { recursive: true });
        await fs.mkdir(WEBSITE_ADMIN_DIR, { recursive: true });
        console.log('✅ All directories checked/created');
    } catch (error) {
        console.error('❌ Error creating directories:', error);
        throw error;
    }
}

// Save announcements to a file
async function saveAnnouncementsToFile(announcements, filePath) {
    try {
        // Pretty print JSON with 2-space indentation
        await fs.writeFile(filePath, JSON.stringify(announcements, null, 2));
        console.log(`✅ Saved ${announcements.length} announcements to ${filePath}`);
        return true;
    } catch (error) {
        console.error(`❌ Error saving to ${filePath}:`, error);
        return false;
    }
}

// Save announcements to all locations
async function saveAnnouncementsToAllLocations(announcements) {
    await ensureDirectoriesExist();
    
    let successCount = 0;
    
    // Save to local data directory
    if (await saveAnnouncementsToFile(announcements, filePaths.local)) {
        successCount++;
    }
    
    // Save to website announcements directory
    if (await saveAnnouncementsToFile(announcements, filePaths.website)) {
        successCount++;
    }
    
    // Save to localStorage backup (to simulate localStorage for the admin panel)
    const localStorageObj = {
        "announcements": JSON.stringify(announcements)
    };
    
    try {
        await fs.writeFile(filePaths.localStorage, JSON.stringify(localStorageObj, null, 2));
        console.log(`✅ Saved localStorage backup to ${filePaths.localStorage}`);
        successCount++;
    } catch (error) {
        console.error(`❌ Error saving localStorage backup:`, error);
    }
    
    return successCount;
}

// Main function to sync all announcements
async function syncAllAnnouncements(limit = 100) {
    console.log(`\n🔄 Starting announcement sync process (limit: ${limit})`);
    console.log('----------------------------------------');
    
    try {
        // Parse and validate limit
        limit = parseInt(limit);
        if (isNaN(limit) || limit <= 0) {
            limit = 100; // Default to 100 if invalid
        }
        
        // Check for token
        if (!process.env.DISCORD_TOKEN) {
            throw new Error('DISCORD_TOKEN not found in environment variables');
        }
        
        // Check for announcement channel ID
        const announcementChannelId = process.env.ANNOUNCEMENT_CHANNEL_ID;
        if (!announcementChannelId) {
            throw new Error('ANNOUNCEMENT_CHANNEL_ID not found in environment variables');
        }
        
        console.log(`🔑 Logging in to Discord...`);
        await client.login(process.env.DISCORD_TOKEN);
        console.log('✅ Logged in successfully');
        
        console.log(`🔍 Fetching announcement channel ${announcementChannelId}...`);
        const channel = await client.channels.fetch(announcementChannelId);
        
        if (!channel) {
            throw new Error(`Channel with ID ${announcementChannelId} not found`);
        }
        
        console.log(`✅ Found channel: ${channel.name}`);
        
        console.log(`📥 Fetching last ${limit} messages...`);
        
        // Fetch messages (up to the limit)
        const messages = await channel.messages.fetch({ limit: Math.min(limit, 100) });
        console.log(`✅ Fetched ${messages.size} messages`);
        
        // Process messages into announcements
        console.log(`🔄 Processing messages into announcements...`);
        const announcements = [];
        messages.forEach(message => {
            const announcement = processMessage(message);
            if (announcement) {
                announcements.push(announcement);
                console.log(`  ✅ Processed: ${announcement.title.substring(0, 40)}...`);
            }
        });
        
        if (announcements.length === 0) {
            console.log('❌ No valid announcements found in messages');
            return;
        }
        
        console.log(`✅ Processed ${announcements.length} announcements`);
        
        // Sort announcements by date (newest first)
        announcements.sort((a, b) => new Date(b.date) - new Date(a.date));
        
        // Save to all locations
        console.log(`💾 Saving announcements to files...`);
        const saveResults = await saveAnnouncementsToAllLocations(announcements);
        
        console.log('\n----------------------------------------');
        console.log(`🎉 Sync process completed!`);
        console.log(`   ✅ ${announcements.length} announcements processed`);
        console.log(`   💾 Saved to ${saveResults} of 3 locations`);
        
    } catch (error) {
        console.error('❌ ERROR:', error.message);
        console.error('Stack trace:', error.stack);
    } finally {
        // Always destroy the client when done
        client.destroy();
        console.log('👋 Discord client disconnected');
    }
}

// Check if this script is being run directly
if (require.main === module) {
    // Get the limit from command line arguments or use default
    const limit = process.argv[2] || 100;
    syncAllAnnouncements(limit)
        .then(() => {
            console.log('Process complete. Exiting.');
            process.exit(0);
        })
        .catch(error => {
            console.error('Fatal error:', error);
            process.exit(1);
        });
}

module.exports = {
    syncAllAnnouncements,
    processMessage,
    saveAnnouncementsToAllLocations
}; 
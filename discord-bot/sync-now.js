require('dotenv').config();
const firebase = require('firebase/app');
require('firebase/firestore');
const { Client, GatewayIntentBits, Partials } = require('discord.js');
const fs = require('fs').promises;
const path = require('path');

// Initialize Discord client
const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent
  ],
  partials: [Partials.Message, Partials.Channel]
});

// Firebase config - Use the config from announcement-sync.js
const firebaseConfig = {
  apiKey: "AIzaSyBwxuW2cdXbwGAkx91kQD9Nk4GhF1vReHQ",
  authDomain: "melonmc-admin.firebaseapp.com",
  projectId: "melonmc-admin",
  storageBucket: "melonmc-admin.appspot.com",
  messagingSenderId: "123456789012",
  appId: "1:123456789012:web:abcdef1234567890abcdef"
};

let db;
try {
  if (!firebase.apps.length) {
    firebase.initializeApp(firebaseConfig);
  }
  db = firebase.firestore();
  console.log("Firebase initialized");
} catch (error) {
  console.error("Firebase initialization error:", error);
}

// Generate unique ID
function generateUniqueId() {
  return 'ann_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

// Process message content to determine announcement type
function determineAnnouncementType(content) {
  const lowerContent = content.toLowerCase();
  
  if (lowerContent.includes('update') || lowerContent.includes('new')) {
    return 'update';
  } else if (lowerContent.includes('warning') || lowerContent.includes('caution') || lowerContent.includes('attention')) {
    return 'warning';
  } else if (lowerContent.includes('critical') || lowerContent.includes('urgent') || lowerContent.includes('emergency')) {
    return 'critical';
  } else {
    return 'info';
  }
}

// Process announcement message
async function processAnnouncementMessage(message) {
  try {
    if (message.author.bot) return;
    
    const announcementId = generateUniqueId();
    const content = message.content.trim();
    
    // Skip empty messages
    if (!content) {
      console.log("Skipping empty message");
      return;
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
    
    const type = determineAnnouncementType(content);
    const timestamp = new Date().toISOString();
    
    const announcement = {
      id: announcementId,
      title: title,
      content: body,
      type: type,
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
    
    console.log(`Processing announcement: ${title} (${type})`);
    
    // Save to Firebase
    let firebaseSuccess = false;
    if (db) {
      try {
        const firestoreAnnouncement = {
          ...announcement,
          createdAt: firebase.firestore.FieldValue.serverTimestamp()
        };
        await db.collection('announcements').add(firestoreAnnouncement);
        console.log("Announcement saved to Firebase");
        firebaseSuccess = true;
      } catch (error) {
        console.error("Error saving to Firebase:", error);
      }
    }
    
    // Save to local file for website to read
    try {
      // First save to discord-bot/data directory
      const discordDataDir = path.join(__dirname, 'data');
      const discordAnnouncementsFile = path.join(discordDataDir, 'announcements.json');
      
      // Ensure discord data directory exists
      await fs.mkdir(discordDataDir, { recursive: true });
      
      // Read existing announcements or create empty array
      let discordAnnouncements = [];
      try {
        const discordData = await fs.readFile(discordAnnouncementsFile, 'utf8');
        discordAnnouncements = JSON.parse(discordData);
      } catch (error) {
        console.log("No existing discord announcements file, creating new one");
      }
      
      // Add new announcement and save
      discordAnnouncements.unshift(announcement);
      await fs.writeFile(discordAnnouncementsFile, JSON.stringify(discordAnnouncements, null, 2));
      console.log("Announcement saved to discord-bot/data/announcements.json");
      
      // Now also save to the website directory
      const websiteDataDir = path.join(__dirname, '..', 'Website', 'announcements');
      const websiteAnnouncementsFile = path.join(websiteDataDir, 'announcements.json');
      
      // Ensure website data directory exists
      await fs.mkdir(websiteDataDir, { recursive: true });
      
      // Read existing announcements or create empty array
      let websiteAnnouncements = [];
      try {
        const websiteData = await fs.readFile(websiteAnnouncementsFile, 'utf8');
        websiteAnnouncements = JSON.parse(websiteData);
      } catch (error) {
        console.log("No existing website announcements file, creating new one");
      }
      
      // Add new announcement and save
      websiteAnnouncements.unshift(announcement);
      await fs.writeFile(websiteAnnouncementsFile, JSON.stringify(websiteAnnouncements, null, 2));
      console.log("Announcement saved to Website/announcements/announcements.json");
      
      // Try to add reaction to the message (may fail if reading from older messages)
      try {
        await message.react('✅');
      } catch (error) {
        console.log("Could not add reaction to message (likely an older message)");
      }
      
      return true;
    } catch (error) {
      console.error("Error saving to local file:", error);
      try {
        await message.react('❌');
      } catch (reactionError) {
        console.log("Could not add error reaction to message");
      }
      return false;
    }
  } catch (error) {
    console.error("Error processing announcement:", error);
    return false;
  }
}

// Main function to sync announcements
async function syncAnnouncements() {
  try {
    console.log("Starting announcement sync...");
    
    // Login to Discord
    console.log("Logging in to Discord...");
    await client.login(process.env.DISCORD_TOKEN);
    console.log("Discord client logged in");
    
    // Wait for client to be ready
    await new Promise(resolve => {
      if (client.isReady()) {
        console.log("Client is ready");
        resolve();
      } else {
        console.log("Waiting for client to be ready...");
        client.once('ready', () => {
          console.log("Client is now ready");
          resolve();
        });
      }
    });
    
    console.log("Environment variables:");
    console.log("ANNOUNCEMENT_CHANNEL_ID:", process.env.ANNOUNCEMENT_CHANNEL_ID);
    
    const channelId = process.env.ANNOUNCEMENT_CHANNEL_ID;
    if (!channelId) {
      console.error("ANNOUNCEMENT_CHANNEL_ID not set in .env file");
      process.exit(1);
    }
    
    console.log(`Fetching channel ${channelId}...`);
    const channel = await client.channels.fetch(channelId);
    if (!channel) {
      console.error("Could not find announcement channel");
      process.exit(1);
    }
    
    console.log(`Fetching messages from ${channel.name}...`);
    const messages = await channel.messages.fetch({ limit: 30 });
    console.log(`Found ${messages.size} messages`);
    
    let processed = 0;
    let successful = 0;
    
    // Process messages from oldest to newest
    const sortedMessages = Array.from(messages.values()).sort((a, b) => a.createdTimestamp - b.createdTimestamp);
    
    for (const message of sortedMessages) {
      if (!message.author.bot) {
        console.log(`Processing message from ${message.author.username}: ${message.content.substring(0, 30)}...`);
        processed++;
        const success = await processAnnouncementMessage(message);
        if (success) successful++;
      }
    }
    
    console.log(`Sync completed. Processed ${processed} messages, ${successful} successful.`);
    setTimeout(() => {
      process.exit(0);
    }, 3000); // Give time for reactions to be added
    
  } catch (error) {
    console.error("Error syncing announcements:", error);
    process.exit(1);
  }
}

// Run sync
syncAnnouncements(); 
// Manual announcement sync script
// This script creates test announcements and saves them to local files

const fs = require('fs').promises;
const path = require('path');

// Generate unique ID
function generateUniqueId() {
  return 'ann_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

// Create test announcements
const testAnnouncements = [
  {
    title: "Server Update: New Features Added",
    content: "We've added new features to our server. Enjoy new game modes, improved performance, and bug fixes.",
    type: "update"
  },
  {
    title: "Warning: Scheduled Maintenance",
    content: "The server will be down for maintenance on Saturday from 2-4 PM EST. Please plan accordingly.",
    type: "warning"
  },
  {
    title: "Critical: Security Update Required",
    content: "All players must update their client to the latest version to address a security vulnerability.",
    type: "critical"
  },
  {
    title: "New Season Starting",
    content: "The new season starts tomorrow! Get ready for new challenges and rewards.",
    type: "info"
  },
  {
    title: "Weekend Event: Double XP",
    content: "Join us this weekend for double XP on all game modes. Start earning more rewards today!",
    type: "update"
  }
];

// Main function
async function createTestAnnouncements() {
  console.log("Starting manual announcement creation...");

  try {
    for (let i = 0; i < testAnnouncements.length; i++) {
      const testAnn = testAnnouncements[i];
      const announcementId = generateUniqueId();
      const timestamp = new Date().toISOString();
      
      // Create announcement object
      const announcement = {
        id: announcementId,
        title: testAnn.title,
        content: testAnn.content,
        type: testAnn.type,
        timestamp: timestamp,
        createdBy: "Manual Sync Script",
        source: {
          platform: 'manual',
          createdAt: new Date().getTime()
        }
      };
      
      console.log(`Creating announcement ${i+1}/${testAnnouncements.length}: ${testAnn.title}`);
      
      // Save to discord-bot/data directory
      try {
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
          console.log(`- No existing discord announcements file, creating new one`);
        }
        
        // Add new announcement and save
        discordAnnouncements.unshift(announcement);
        await fs.writeFile(discordAnnouncementsFile, JSON.stringify(discordAnnouncements, null, 2));
        console.log(`- Saved to discord-bot/data/announcements.json`);
      } catch (error) {
        console.error(`- Error saving to discord data directory:`, error);
      }
      
      // Save to website announcements directory
      try {
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
          console.log(`- No existing website announcements file, creating new one`);
        }
        
        // Add new announcement and save
        websiteAnnouncements.unshift(announcement);
        await fs.writeFile(websiteAnnouncementsFile, JSON.stringify(websiteAnnouncements, null, 2));
        console.log(`- Saved to Website/announcements/announcements.json`);
      } catch (error) {
        console.error(`- Error saving to website directory:`, error);
      }
      
      // Also save directly to the localStorage file in admin directory for admin panel
      try {
        const adminDir = path.join(__dirname, '..', 'Website', 'admin');
        const localStorageFile = path.join(adminDir, 'localStorage_backup.json');
        
        // Read existing localStorage backup or create new object
        let localStorageData = {};
        try {
          const localStorageContent = await fs.readFile(localStorageFile, 'utf8');
          localStorageData = JSON.parse(localStorageContent);
        } catch (error) {
          console.log(`- No existing localStorage backup, creating new one`);
        }
        
        // Get existing announcements or create empty array
        let announcements = [];
        if (localStorageData.announcements) {
          announcements = JSON.parse(localStorageData.announcements);
        }
        
        // Add new announcement and save
        announcements.unshift(announcement);
        localStorageData.announcements = JSON.stringify(announcements);
        
        await fs.writeFile(localStorageFile, JSON.stringify(localStorageData, null, 2));
        console.log(`- Saved to Website/admin/localStorage_backup.json`);
      } catch (error) {
        console.error(`- Error saving to localStorage backup:`, error);
      }
      
      console.log(`Announcement ${i+1} created successfully`);
    }
    
    console.log(`All ${testAnnouncements.length} announcements created and saved!`);
    
  } catch (error) {
    console.error("Error creating announcements:", error);
  }
}

// Run the function
createTestAnnouncements(); 
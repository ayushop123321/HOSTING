const fs = require('fs').promises;
const path = require('path');

// File paths for saving announcements
const DATA_DIR = path.join(__dirname, 'data');
const WEBSITE_ANNOUNCEMENTS_DIR = path.join(__dirname, '..', 'Website', 'announcements');
const WEBSITE_ADMIN_DIR = path.join(__dirname, '..', 'Website', 'admin');

// Generate test announcements
async function generateTestAnnouncements() {
    console.log('Generating test announcements...');
    
    // Create test announcements
    const announcements = [
        {
            id: `test-${Date.now()}-1`,
            title: '🎮 Server Update: New Minigames Added!',
            content: '🎮 Server Update: New Minigames Added!\n\nWe\'re excited to announce that we\'ve added 3 new minigames to the server! Check out Skyblock, BedWars, and SkyWars in the hub. Have fun playing!',
            date: new Date().toISOString(),
            author: 'MelonMC Admin',
            authorId: 'admin1',
            type: 'update',
            messageId: 'test1',
            timestamp: Date.now()
        },
        {
            id: `test-${Date.now()}-2`,
            title: '⚠️ Warning: Server Maintenance',
            content: '⚠️ Warning: Server Maintenance\n\nWe will be performing server maintenance tomorrow at 2 PM EST. The server will be down for approximately 2 hours. We apologize for any inconvenience.',
            date: new Date(Date.now() - 86400000).toISOString(), // 1 day ago
            author: 'MelonMC Admin',
            authorId: 'admin1',
            type: 'warning',
            messageId: 'test2',
            timestamp: Date.now() - 86400000
        },
        {
            id: `test-${Date.now()}-3`,
            title: '🎁 Weekend Special: Double XP!',
            content: '🎁 Weekend Special: Double XP!\n\nThis weekend (Friday-Sunday), all players will receive double XP for all activities! Don\'t miss this opportunity to level up faster!',
            date: new Date(Date.now() - 172800000).toISOString(), // 2 days ago
            author: 'MelonMC Admin',
            authorId: 'admin1',
            type: 'info',
            messageId: 'test3',
            timestamp: Date.now() - 172800000
        },
        {
            id: `test-${Date.now()}-4`,
            title: '🔴 CRITICAL: Security Update',
            content: '🔴 CRITICAL: Security Update\n\nWe have detected a security vulnerability that requires all users to change their passwords immediately. Please log in to your account and update your password as soon as possible.',
            date: new Date(Date.now() - 259200000).toISOString(), // 3 days ago
            author: 'MelonMC Admin',
            authorId: 'admin1',
            type: 'critical',
            messageId: 'test4',
            timestamp: Date.now() - 259200000
        },
        {
            id: `test-${Date.now()}-5`,
            title: '🏆 Tournament Results',
            content: '🏆 Tournament Results\n\nCongratulations to the winners of our monthly PvP tournament!\n\n🥇 First Place: PlayerOne\n🥈 Second Place: PlayerTwo\n🥉 Third Place: PlayerThree\n\nThanks to everyone who participated!',
            date: new Date(Date.now() - 345600000).toISOString(), // 4 days ago
            author: 'MelonMC Admin',
            authorId: 'admin1',
            type: 'info',
            messageId: 'test5',
            timestamp: Date.now() - 345600000
        }
    ];
    
    // Ensure directories exist
    await ensureDirectoriesExist();
    
    // Save announcements to all locations
    await saveAnnouncementsToAllLocations(announcements);
    
    console.log('\nTest announcements have been generated and saved!');
    console.log('These announcements should now appear on your website.');
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
    // File paths
    const filePaths = {
        local: path.join(DATA_DIR, 'announcements.json'),
        website: path.join(WEBSITE_ANNOUNCEMENTS_DIR, 'announcements.json'),
        localStorage: path.join(WEBSITE_ADMIN_DIR, 'localStorage_backup.json')
    };
    
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

// Run the function
generateTestAnnouncements()
    .then(() => {
        console.log('Done!');
    })
    .catch(error => {
        console.error('Error:', error);
    }); 
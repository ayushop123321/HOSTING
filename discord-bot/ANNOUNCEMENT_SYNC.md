# Discord Announcement Sync for MelonMC Website

This feature allows automatically syncing announcements from a Discord channel to the MelonMC website's announcement section.

## Setup Instructions

1. **Add the Announcement Channel ID to your .env file**:
   ```
   ANNOUNCEMENT_CHANNEL_ID=your_announcement_channel_id_here
   ```

2. **Install the required Firebase dependency**:
   ```bash
   npm install firebase@8.10.1
   ```

3. **Restart your bot**:
   ```bash
   npm start
   ```

## How It Works

1. The bot monitors a specific announcement channel in your Discord server
2. When a new message is posted in that channel, it's automatically converted to a website announcement
3. The announcement is stored in both Firebase (same database used by the website) and a local JSON file as backup
4. The website will display these announcements on both the homepage and announcements page

## Post Types

The system automatically detects the type of announcement based on keywords in the message:

- **Update** (default): Regular announcements without special keywords
- **Warning**: Messages containing "warning", "caution", or "attention"
- **Critical**: Messages containing "critical", "urgent", or "emergency"

## Post Format

For best results, format your Discord announcements like this:

```
Title of the Announcement

This is the main content of the announcement. The first line will be used as the title,
and the rest will be treated as the content.

You can include multiple paragraphs and formatting.
```

## Manual Sync Command

Administrators can manually sync announcements using the `/syncannouncements` command:

```
/syncannouncements [count:5]
```

- `count` is an optional parameter specifying how many recent announcements to process (default: 5)

## Troubleshooting

- **Reactions**: The bot adds reactions to processed messages:
  - ✅ = Successfully processed and saved
  - ❌ = Failed to process

- **Logs**: Check your console for detailed logs with the `[AnnouncementSync]` prefix

- **Backup**: All announcements are saved to `data/announcements.json` as a backup

- **Firebase Connection**: If Firebase fails to connect, announcements will still be saved locally and the bot will continue to function

## Limitations

- Images and attachments are not synced, only text content
- Formatting such as bold, italic, etc. will not be preserved
- The bot must have permission to read messages and add reactions in the announcement channel
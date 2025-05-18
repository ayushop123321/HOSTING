package com.melonmc.MelonMCShop.managers;

import com.melonmc.MelonMCShop.MelonMCShop;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages player ranks for the MelonMCShop plugin
 */
public class RankManager {
    private final MelonMCShop plugin;
    private final Logger logger;
    private final Permission permissions;
    private final Map<String, Map<String, String>> rankPermissions;
    private final Map<UUID, Map<String, String>> pendingRanks = new HashMap<>();
    private boolean operational = true;
    private final File rankDataFile;
    private FileConfiguration rankData;
    private final Map<UUID, String> playerRanks;
    private final Map<String, UUID> playerNameToUUID;
    private final Map<String, Long> pendingRankAssignments;
    
    /**
     * Constructor for RankManager
     * @param plugin MelonMCShop instance
     */
    public RankManager(MelonMCShop plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.permissions = plugin.getPermissions();
        this.rankPermissions = new HashMap<>();
        this.playerRanks = new ConcurrentHashMap<>();
        this.playerNameToUUID = new ConcurrentHashMap<>();
        this.pendingRankAssignments = new ConcurrentHashMap<>();
        this.rankDataFile = new File(plugin.getDataFolder(), "rank_data.yml");
        
        // Load rank permissions from config
        loadRankPermissions();
        loadPendingRanks();
        
        // Load data
        loadData();
        
        // Start periodic save task
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 20L * 300, 20L * 300); // Every 5 minutes
    }
    
    /**
     * Load rank permissions from config
     */
    private void loadRankPermissions() {
        ConfigurationSection ranksSection = plugin.getConfig().getConfigurationSection("ranks");
        
        if (ranksSection == null) {
            logger.warning("No ranks defined in config.yml!");
            return;
        }
        
        // Loop through all ranks
        for (String rankName : ranksSection.getKeys(false)) {
            ConfigurationSection rankSection = ranksSection.getConfigurationSection(rankName);
            
            if (rankSection == null) continue;
            
            Map<String, String> gamemodeGroups = new HashMap<>();
            
            // Get group for each gamemode
            ConfigurationSection gamemodesSection = rankSection.getConfigurationSection("gamemodes");
            if (gamemodesSection != null) {
                for (String gamemode : gamemodesSection.getKeys(false)) {
                    String groupName = gamemodesSection.getString(gamemode);
                    if (groupName != null && !groupName.isEmpty()) {
                        gamemodeGroups.put(gamemode.toLowerCase(), groupName);
                    }
                }
            }
            
            // Default gamemode group
            String defaultGroup = rankSection.getString("default_group");
            if (defaultGroup != null && !defaultGroup.isEmpty()) {
                gamemodeGroups.put("default", defaultGroup);
            }
            
            rankPermissions.put(rankName.toLowerCase(), gamemodeGroups);
        }
        
        logger.info("Loaded " + rankPermissions.size() + " ranks from config.yml");
    }
    
    /**
     * Give a player a rank
     * @param player The player
     * @param gamemode The gamemode
     * @param rank The rank to give
     * @return Whether the rank was given successfully
     */
    public boolean giveRank(Player player, String gamemode, String rank) {
        try {
            if (player == null || !player.isOnline()) {
                // Store rank to give later
                storePendingRank(player.getUniqueId(), gamemode, rank);
                return false;
            }

            String perm = plugin.getConfig().getString("rank-permissions." + gamemode + "." + rank);
            if (perm == null || perm.isEmpty()) {
                plugin.getLogger().warning("No permission found for rank: " + rank + " in gamemode: " + gamemode);
                return false;
            }

            plugin.getPermissions().playerAdd(null, player, perm);
            player.sendMessage(ChatColor.GREEN + "You have been given the rank " + rank + " for " + gamemode + "!");
            
            // Notify any watchers (like the Discord bot)
            if (plugin.getDiscordBridge() != null && plugin.getDiscordBridge().isConnected()) {
                plugin.getDiscordBridge().notifyPurchaseComplete(
                    player, "rank", rank, gamemode, UUID.randomUUID().toString().substring(0, 8)
                );
            }
            
            return true;
        } catch (Exception e) {
            plugin.logError("Failed to give rank " + rank + " to player " + player.getName(), e);
            operational = false;
            return false;
        }
    }
    
    /**
     * Store a pending rank for a player
     * @param uuid The player UUID
     * @param gamemode The gamemode
     * @param rank The rank
     */
    public void storePendingRank(UUID uuid, String gamemode, String rank) {
        if (!pendingRanks.containsKey(uuid)) {
            pendingRanks.put(uuid, new HashMap<>());
        }
        pendingRanks.get(uuid).put(gamemode, rank);
        saveData();
    }
    
    /**
     * Check if a player has pending ranks and give them if they're online
     * @param player The player to check
     */
    public void checkPendingRanks(Player player) {
        UUID uuid = player.getUniqueId();
        if (pendingRanks.containsKey(uuid)) {
            Map<String, String> ranks = pendingRanks.get(uuid);
            for (Map.Entry<String, String> entry : ranks.entrySet()) {
                giveRank(player, entry.getKey(), entry.getValue());
            }
            pendingRanks.remove(uuid);
            saveData();
        }
    }
    
    /**
     * Load pending ranks from config
     */
    public void loadPendingRanks() {
        try {
            pendingRanks.clear();
            
            if (plugin.getConfig().contains("pending-ranks")) {
                for (String uuidStr : plugin.getConfig().getConfigurationSection("pending-ranks").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        Map<String, String> ranks = new HashMap<>();
                        
                        for (String gamemode : plugin.getConfig().getConfigurationSection("pending-ranks." + uuidStr).getKeys(false)) {
                            String rank = plugin.getConfig().getString("pending-ranks." + uuidStr + "." + gamemode);
                            ranks.put(gamemode, rank);
                        }
                        
                        pendingRanks.put(uuid, ranks);
                    } catch (IllegalArgumentException e) {
                        plugin.logError("Invalid UUID in pending ranks: " + uuidStr, e);
                    }
                }
            }
            operational = true;
        } catch (Exception e) {
            plugin.logError("Failed to load pending ranks", e);
            operational = false;
        }
    }
    
    /**
     * Save pending ranks to config
     */
    public void saveData() {
        try {
            if (rankData == null) {
                rankData = new YamlConfiguration();
            }
            
            // Save player UUIDs
            rankData.set("player-uuids", null);
            for (Map.Entry<String, UUID> entry : playerNameToUUID.entrySet()) {
                rankData.set("player-uuids." + entry.getKey(), entry.getValue().toString());
            }
            
            // Save player ranks
            rankData.set("player-ranks", null);
            for (Map.Entry<UUID, String> entry : playerRanks.entrySet()) {
                rankData.set("player-ranks." + entry.getKey().toString(), entry.getValue());
            }
            
            // Save pending rank assignments
            rankData.set("pending-ranks", null);
            for (Map.Entry<String, Long> entry : pendingRankAssignments.entrySet()) {
                rankData.set("pending-ranks." + entry.getKey() + ".timestamp", entry.getValue());
                rankData.set("pending-ranks." + entry.getKey() + ".rank", playerRanks.get(getPlayerUUID(entry.getKey())));
            }
            
            // Save to file
            rankData.save(rankDataFile);
            
            operational = true;
        } catch (IOException e) {
            plugin.logError("Error saving rank data", e);
            operational = false;
        }
    }
    
    /**
     * Load data (alias for loadPendingRanks for consistency)
     */
    public void loadData() {
        try {
            if (!rankDataFile.exists()) {
                rankDataFile.getParentFile().mkdirs();
                plugin.saveResource("rank_data.yml", false);
            }

            rankData = YamlConfiguration.loadConfiguration(rankDataFile);

            // Load player UUIDs
            if (rankData.contains("player-uuids")) {
                for (String playerName : rankData.getConfigurationSection("player-uuids").getKeys(false)) {
                    UUID uuid = UUID.fromString(rankData.getString("player-uuids." + playerName));
                    playerNameToUUID.put(playerName.toLowerCase(), uuid);
                }
            }

            // Load player ranks
            if (rankData.contains("player-ranks")) {
                for (String uuidStr : rankData.getConfigurationSection("player-ranks").getKeys(false)) {
                    UUID uuid = UUID.fromString(uuidStr);
                    String rank = rankData.getString("player-ranks." + uuidStr);
                    playerRanks.put(uuid, rank);
                }
            }
            
            // Load pending rank assignments
            if (rankData.contains("pending-ranks")) {
                for (String playerName : rankData.getConfigurationSection("pending-ranks").getKeys(false)) {
                    long timestamp = rankData.getLong("pending-ranks." + playerName + ".timestamp");
                    pendingRankAssignments.put(playerName.toLowerCase(), timestamp);
                }
            }
            
            plugin.getLogger().info("Loaded rank data: " + playerRanks.size() + " ranks, " + 
                                   pendingRankAssignments.size() + " pending assignments");
                                   
        } catch (Exception e) {
            plugin.logError("Error loading rank data", e);
        }
    }
    
    /**
     * Check if the manager is operational
     * @return true if operational, false if errors were encountered
     */
    public boolean isOperational() {
        return operational;
    }

    /**
     * Get a player's UUID by name
     * @param playerName Player name
     * @return UUID or null if not found
     */
    public UUID getPlayerUUID(String playerName) {
        if (playerName == null) {
            return null;
        }
        
        // Check cache first
        playerName = playerName.toLowerCase();
        if (playerNameToUUID.containsKey(playerName)) {
            return playerNameToUUID.get(playerName);
        }
        
        // Check online players
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            UUID uuid = player.getUniqueId();
            playerNameToUUID.put(playerName, uuid);
            return uuid;
        }
        
        // Try to load from Bukkit's offline player data
        try {
            @SuppressWarnings("deprecation")
            UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
            if (uuid != null) {
                playerNameToUUID.put(playerName, uuid);
                return uuid;
            }
        } catch (Exception e) {
            plugin.logError("Error getting UUID for player " + playerName, e);
        }
        
        return null;
    }

    /**
     * Set a player's rank
     * @param playerUUID Player UUID
     * @param playerName Player name (for offline players)
     * @param rankName Rank name
     * @return true if successful
     */
    public boolean setRank(UUID playerUUID, String playerName, String rankName) {
        try {
            // Make sure we have the UUID
            if (playerUUID == null) {
                plugin.getLogger().warning("Cannot set rank: Player UUID is null");
                return false;
            }
            
            // Update name-UUID mapping
            if (playerName != null) {
                playerNameToUUID.put(playerName.toLowerCase(), playerUUID);
            }
            
            // Store the rank
            playerRanks.put(playerUUID, rankName);
            
            // Get online player if available
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                // Player is online, apply directly
                try {
                    // Remove all rank groups first
                    for (String group : plugin.getConfig().getStringList("rank-groups")) {
                        if (plugin.getPermissions().playerInGroup(player, group)) {
                            plugin.getPermissions().playerRemoveGroup(player, group);
                        }
                    }
                    
                    // Add the new rank group
                    plugin.getPermissions().playerAddGroup(player, rankName);
                    
                    // Remove from pending assignments
                    pendingRankAssignments.remove(playerName.toLowerCase());
                    
                    return true;
                } catch (Exception e) {
                    plugin.logError("Error applying rank to online player", e);
                    return false;
                }
            } else {
                // Player is offline, add to pending assignments
                if (playerName != null) {
                    pendingRankAssignments.put(playerName.toLowerCase(), System.currentTimeMillis());
                }
                return true;
            }
        } catch (Exception e) {
            plugin.logError("Error setting player rank", e);
            return false;
        }
    }
    
    /**
     * Check and apply any pending ranks for a player who just logged in
     * @param player The player who joined
     */
    public void checkPendingRank(Player player) {
        try {
            String playerName = player.getName().toLowerCase();
            UUID playerUUID = player.getUniqueId();
            
            // Update our name-UUID mapping
            playerNameToUUID.put(playerName, playerUUID);
            
            // Check if player has a pending rank
            if (pendingRankAssignments.containsKey(playerName)) {
                // Get the stored rank
                String rankName = playerRanks.get(playerUUID);
                if (rankName != null) {
                    // Apply the rank
                    plugin.getLogger().info("Applying pending rank " + rankName + " to player " + player.getName());
                    
                    // Remove all rank groups first
                    for (String group : plugin.getConfig().getStringList("rank-groups")) {
                        if (plugin.getPermissions().playerInGroup(player, group)) {
                            plugin.getPermissions().playerRemoveGroup(player, group);
                        }
                    }
                    
                    // Add the new rank group
                    plugin.getPermissions().playerAddGroup(player, rankName);
                    
                    // Notify the player
                    player.sendMessage(plugin.formatMessage("&aYour rank has been set to &6" + rankName + "&a!"));
                    
                    // Remove from pending assignments
                    pendingRankAssignments.remove(playerName);
                }
            }
        } catch (Exception e) {
            plugin.logError("Error checking pending rank for player " + player.getName(), e);
        }
    }
    
    /**
     * Get a player's current rank
     * @param player The player
     * @return The rank name, or "default" if none
     */
    public String getPlayerRank(Player player) {
        if (player == null) return "default";
        
        UUID playerUUID = player.getUniqueId();
        
        // Check stored ranks
        if (playerRanks.containsKey(playerUUID)) {
            return playerRanks.get(playerUUID);
        }
        
        // Check permissions system
        for (String rankName : plugin.getConfig().getStringList("rank-groups")) {
            if (plugin.getPermissions().playerInGroup(player, rankName)) {
                // Update our cache
                playerRanks.put(playerUUID, rankName);
                return rankName;
            }
        }
        
        return "default";
    }
    
    /**
     * Get all stored ranks for players
     * @return Map of UUID to rank name
     */
    public Map<UUID, String> getAllPlayerRanks() {
        return new HashMap<>(playerRanks);
    }
    
    /**
     * Clear expired pending rank assignments
     * @param maxAgeDays Maximum age in days to keep pending assignments
     * @return Number of expired assignments removed
     */
    public int cleanupExpiredAssignments(int maxAgeDays) {
        long cutoff = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L);
        int removed = 0;
        
        for (Map.Entry<String, Long> entry : pendingRankAssignments.entrySet()) {
            if (entry.getValue() < cutoff) {
                pendingRankAssignments.remove(entry.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " expired rank assignments");
            saveData();
        }
        
        return removed;
    }
} 
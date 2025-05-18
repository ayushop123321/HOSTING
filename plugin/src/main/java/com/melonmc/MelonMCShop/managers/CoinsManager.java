package com.melonmc.MelonMCShop.managers;

import com.melonmc.MelonMCShop.MelonMCShop;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages player coins for the MelonMCShop plugin
 */
public class CoinsManager {
    private final MelonMCShop plugin;
    private final Logger logger;
    private final Economy economy;
    private final Map<String, Economy> gamemodeEconomies;
    
    // Storage for offline players' pending coins if economy is null
    private final Map<UUID, Map<String, Integer>> pendingCoins;
    
    private boolean operational = true;
    
    /**
     * Constructor for CoinsManager
     * @param plugin MelonMCShop instance
     */
    public CoinsManager(MelonMCShop plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.economy = plugin.getEconomy();
        this.gamemodeEconomies = new HashMap<>();
        this.pendingCoins = new HashMap<>();
        
        // Schedule periodic saving of pending coins
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 6000L, 6000L); // Every 5 minutes
        loadData();
    }
    
    /**
     * Give coins to a player
     * @param player Player to give coins to
     * @param amount Amount of coins to give
     * @param gamemode Gamemode to give coins for
     * @return true if successful, false otherwise
     */
    public boolean giveCoins(OfflinePlayer player, int amount, String gamemode) {
        if (player == null) {
            logger.warning("Cannot give coins to null player");
            return false;
        }
        
        if (amount <= 0) {
            logger.warning("Cannot give non-positive amount of coins: " + amount);
            return false;
        }
        
        String gamemodeLower = gamemode.toLowerCase();
        
        // Handle giving coins if player is online and economy is available
        if (player.isOnline() && economy != null) {
            try {
                // Use the economy provider to give coins
                economy.depositPlayer(player, amount);
                logger.info("Gave " + amount + " coins to " + player.getName() + " in gamemode " + gamemode);
                return true;
            } catch (Exception e) {
                logger.severe("Error giving coins to player " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            // Store coins for offline player to be given when they log in
            UUID playerUUID = player.getUniqueId();
            
            // Get or create pending coins map for this player
            Map<String, Integer> playerPendingCoins = pendingCoins.computeIfAbsent(playerUUID, k -> new HashMap<>());
            
            // Add coins to pending
            int existingAmount = playerPendingCoins.getOrDefault(gamemodeLower, 0);
            playerPendingCoins.put(gamemodeLower, existingAmount + amount);
            
            logger.info("Added " + amount + " coins to pending for offline player " + player.getName() + 
                        " in gamemode " + gamemode + " (total: " + (existingAmount + amount) + ")");
            
            // Save pending coins data
            saveData();
            
            return true;
        }
    }
    
    /**
     * Process pending coins for a player who has just joined
     * @param player Player who joined
     * @return true if any coins were processed, false otherwise
     */
    public boolean processPendingCoins(Player player) {
        if (player == null || economy == null) return false;
        
        UUID playerUUID = player.getUniqueId();
        Map<String, Integer> playerPendingCoins = pendingCoins.get(playerUUID);
        
        if (playerPendingCoins == null || playerPendingCoins.isEmpty()) return false;
        
        boolean processedAny = false;
        
        // Process each gamemode's pending coins
        for (Map.Entry<String, Integer> entry : playerPendingCoins.entrySet()) {
            String gamemode = entry.getKey();
            int amount = entry.getValue();
            
            try {
                // Give the coins
                economy.depositPlayer(player, amount);
                
                // Send message to player
                player.sendMessage("§a§lPENDING COINS! §fYou received §e" + amount + " coins §ffor §6" + 
                                  gamemode + " §fgamemode that were pending while you were offline!");
                
                logger.info("Processed " + amount + " pending coins for player " + player.getName() + 
                            " in gamemode " + gamemode);
                            
                processedAny = true;
            } catch (Exception e) {
                logger.severe("Error processing pending coins for player " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                // Keep coins in pending if there was an error
                continue;
            }
        }
        
        // Clear pending coins for this player
        pendingCoins.remove(playerUUID);
        
        // Save pending coins data
        saveData();
        
        return processedAny;
    }
    
    /**
     * Save pending coins to config
     */
    public void saveData() {
        try {
            // Clear existing pending coins
            plugin.getConfig().set("pending-coins", null);
            
            // Save current pending coins
            for (Map.Entry<UUID, Map<String, Integer>> entry : pendingCoins.entrySet()) {
                String uuidStr = entry.getKey().toString();
                Map<String, Integer> gamemodeCounts = entry.getValue();
                
                for (Map.Entry<String, Integer> gamemodeEntry : gamemodeCounts.entrySet()) {
                    String gamemode = gamemodeEntry.getKey();
                    int amount = gamemodeEntry.getValue();
                    
                    plugin.getConfig().set("pending-coins." + uuidStr + "." + gamemode, amount);
                }
            }
            
            plugin.saveConfig();
            operational = true;
        } catch (Exception e) {
            logger.severe("Failed to save pending coins: " + e.getMessage());
            e.printStackTrace();
            operational = false;
        }
    }
    
    /**
     * Load pending coins from config
     */
    public void loadData() {
        try {
            pendingCoins.clear();
            
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("pending-coins");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID playerUUID = UUID.fromString(uuidStr);
                        Map<String, Integer> gamemodeCounts = new HashMap<>();
                        
                        ConfigurationSection playerSection = section.getConfigurationSection(uuidStr);
                        if (playerSection != null) {
                            for (String gamemode : playerSection.getKeys(false)) {
                                int amount = playerSection.getInt(gamemode);
                                if (amount > 0) {
                                    gamemodeCounts.put(gamemode, amount);
                                }
                            }
                        }
                        
                        if (!gamemodeCounts.isEmpty()) {
                            pendingCoins.put(playerUUID, gamemodeCounts);
                        }
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid UUID in pending coins: " + uuidStr);
                    }
                }
            }
            operational = true;
        } catch (Exception e) {
            logger.severe("Failed to load pending coins: " + e.getMessage());
            e.printStackTrace();
            operational = false;
        }
    }
    
    /**
     * Get player's coins for a gamemode
     * @param player Player to check
     * @param gamemode Gamemode to check coins for
     * @return Amount of coins, or 0 if economy is not available
     */
    public double getCoins(OfflinePlayer player, String gamemode) {
        if (player == null || economy == null) return 0;
        
        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            logger.severe("Error getting coins for player " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Check if the coins manager is operational
     * @return true if operational, false otherwise
     */
    public boolean isOperational() {
        return operational;
    }
} 
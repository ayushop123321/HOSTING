package com.melonmc.MelonMCShop.listeners;

import com.melonmc.MelonMCShop.MelonMCShop;
import com.melonmc.MelonMCShop.managers.CoinsManager;
import com.melonmc.MelonMCShop.managers.RankManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player events for MelonMCShop
 */
public class PlayerListener implements Listener {
    private final MelonMCShop plugin;
    private final RankManager rankManager;
    private final CoinsManager coinsManager;
    
    /**
     * Constructor for PlayerListener
     * @param plugin The plugin instance
     */
    public PlayerListener(MelonMCShop plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
        this.coinsManager = plugin.getCoinsManager();
    }
    
    /**
     * Handle player join event
     * @param event PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        
        // Run slightly delayed to ensure all player data is loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                // Check and apply any pending rank from Discord bill form
                plugin.getRankManager().checkPendingRank(player);
                
                // Welcome back message for returning players with ranks
                String currentRank = plugin.getRankManager().getPlayerRank(player);
                if (!"default".equals(currentRank)) {
                    player.sendMessage(
                        ChatColor.GREEN + "Welcome back to MelonMC! " +
                        ChatColor.GOLD + "Your current rank: " + 
                        ChatColor.YELLOW + currentRank
                    );
                }
            } catch (Exception e) {
                plugin.logError("Error processing player join", e);
            }
        }, 20L); // 1 second delay
    }
    
    /**
     * Handle player quit event
     * @param event PlayerQuitEvent
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save player data if necessary
        try {
            // Nothing specific to do here yet
            // Could add tracking of online time, etc.
        } catch (Exception e) {
            plugin.logError("Error processing player quit", e);
        }
    }
} 
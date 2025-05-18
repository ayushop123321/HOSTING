package com.melonmc.MelonMCShop.listeners;

import com.melonmc.MelonMCShop.MelonMCShop;
import com.melonmc.MelonMCShop.gui.ShopGUI;
import com.melonmc.MelonMCShop.rewards.DailyRewards;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles inventory interactions for plugin GUIs
 */
public class GUIListener implements Listener {
    private final MelonMCShop plugin;
    private final ShopGUI shopGUI;
    private final DailyRewards dailyRewards;
    
    public GUIListener(MelonMCShop plugin, ShopGUI shopGUI, DailyRewards dailyRewards) {
        this.plugin = plugin;
        this.shopGUI = shopGUI;
        this.dailyRewards = dailyRewards;
    }
    
    /**
     * Handle inventory clicks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        // Handle daily rewards GUI
        if (title.equals("§6§lDaily Rewards")) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null) return;
            
            dailyRewards.handleRewardClick(player, event.getRawSlot());
        }
        
        // Shop GUI is already handled in ShopGUI class
    }
    
    /**
     * Clean up when a player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        shopGUI.removePlayer(player);
    }
    
    /**
     * Handle inventory close events
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        // Potentially add animation effects when closing GUIs
    }
} 
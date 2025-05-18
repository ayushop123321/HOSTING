package com.melonmc.MelonMCShop.commands;

import com.melonmc.MelonMCShop.MelonMCShop;
import com.melonmc.MelonMCShop.gui.ShopGUI;
import com.melonmc.MelonMCShop.managers.CoinsManager;
import com.melonmc.MelonMCShop.managers.RankManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Handles player-facing commands for MelonMCShop
 */
public class PlayerCommands implements CommandExecutor {
    private final MelonMCShop plugin;
    private final ShopGUI shopGUI;
    private final RankManager rankManager;
    private final CoinsManager coinsManager;

    /**
     * Constructor
     * @param plugin MelonMCShop instance
     */
    public PlayerCommands(MelonMCShop plugin) {
        this.plugin = plugin;
        this.shopGUI = plugin.getShopGUI();
        this.rankManager = plugin.getRankManager();
        this.coinsManager = plugin.getCoinsManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "shopgui":
                return handleShopGUI(player, args);
            case "redeem":
                return handleRedeem(player, args);
            case "ranks":
                return handleRanks(player, args);
            default:
                return false;
        }
    }

    /**
     * Handle the shopgui command
     * @param player Player using the command
     * @param args Command arguments
     * @return true if successful, false otherwise
     */
    private boolean handleShopGUI(Player player, String[] args) {
        // Check permission
        if (!player.hasPermission("melonmc.user")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // Open shop GUI
        shopGUI.openMainMenu(player);
        return true;
    }

    /**
     * Handle the redeem command
     * @param player Player using the command
     * @param args Command arguments
     * @return true if successful, false otherwise
     */
    private boolean handleRedeem(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /redeem <code>");
            return true;
        }

        // Get redeem code
        String code = args[0];

        // Check if code is valid
        if (!plugin.getConfig().contains("redeem-codes." + code)) {
            player.sendMessage(ChatColor.RED + "Invalid redeem code!");
            return true;
        }

        // Check if code has already been redeemed
        if (plugin.getConfig().contains("redeemed-codes." + player.getUniqueId() + "." + code)) {
            player.sendMessage(ChatColor.RED + "You have already redeemed this code!");
            return true;
        }

        // Get reward type and value
        String rewardType = plugin.getConfig().getString("redeem-codes." + code + ".type", "coins");
        String gamemode = plugin.getConfig().getString("redeem-codes." + code + ".gamemode", "survival");

        // Process reward
        boolean success = false;
        switch (rewardType.toLowerCase()) {
            case "coins":
                int amount = plugin.getConfig().getInt("redeem-codes." + code + ".amount", 100);
                success = plugin.getCoinsManager().giveCoins(player, amount, gamemode);
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "You have been given " + amount + " coins for " + gamemode + "!");
                }
                break;
                
            case "rank":
                String rank = plugin.getConfig().getString("redeem-codes." + code + ".rank", "vip");
                success = plugin.getRankManager().giveRank(player, gamemode, rank);
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "You have been given the rank " + rank + " for " + gamemode + "!");
                }
                break;
                
            default:
                player.sendMessage(ChatColor.RED + "Unknown reward type!");
                return true;
        }

        if (success) {
            // Mark code as redeemed
            plugin.getConfig().set("redeemed-codes." + player.getUniqueId() + "." + code, true);
            plugin.saveConfig();
        } else {
            player.sendMessage(ChatColor.RED + "Failed to give reward. Please try again later.");
        }

        return true;
    }

    /**
     * Handle the ranks command
     * @param player Player using the command
     * @param args Command arguments
     * @return true if successful, false otherwise
     */
    private boolean handleRanks(Player player, String[] args) {
        // Display available ranks
        player.sendMessage(ChatColor.GREEN + "=== Available Ranks ===");
        
        if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
            // Check player's ranks
            player.sendMessage(ChatColor.YELLOW + "Your Ranks:");
            
            // Display ranks for each gamemode
            for (String gamemode : plugin.getConfig().getConfigurationSection("rank-permissions").getKeys(false)) {
                boolean hasRank = false;
                
                for (String rank : plugin.getConfig().getConfigurationSection("rank-permissions." + gamemode).getKeys(false)) {
                    String permission = plugin.getConfig().getString("rank-permissions." + gamemode + "." + rank);
                    if (permission != null && player.hasPermission(permission)) {
                        player.sendMessage(ChatColor.GOLD + gamemode + ": " + ChatColor.GREEN + rank);
                        hasRank = true;
                        break;  // Only show highest rank if multiple
                    }
                }
                
                if (!hasRank) {
                    player.sendMessage(ChatColor.GOLD + gamemode + ": " + ChatColor.GRAY + "None");
                }
            }
            
            return true;
        }
        
        // Display available ranks from config
        if (plugin.getConfig().contains("ranks")) {
            for (String rankKey : plugin.getConfig().getConfigurationSection("ranks").getKeys(false)) {
                String displayName = plugin.getConfig().getString("ranks." + rankKey + ".display-name", rankKey);
                String price = plugin.getConfig().getString("ranks." + rankKey + ".price", "N/A");
                
                player.sendMessage(ChatColor.GOLD + displayName + ": " + ChatColor.WHITE + price);
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "No ranks available.");
        }

        player.sendMessage(ChatColor.GREEN + "Use /shopgui to purchase ranks!");
        return true;
    }
} 
package com.melonmc.MelonMCShop.commands;

import com.melonmc.MelonMCShop.MelonMCShop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles admin commands for the plugin
 */
public class CommandHandler implements CommandExecutor {

    private final MelonMCShop plugin;

    public CommandHandler(MelonMCShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Get the command name
        String cmdName = cmd.getName().toLowerCase();

        switch (cmdName) {
            case "givecoin":
                return handleGiveCoin(sender, args);
            case "giverank":
                return handleGiveRank(sender, args);
            case "is_player_exists":
                return handlePlayerExists(sender, args);
            case "daily":
                if (sender instanceof Player) {
                    return plugin.getDailyRewards().executeCommand((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
            case "lpset":
                return handleLpSet(sender, args);
            default:
                return false;
        }
    }

    /**
     * Handle the /lpset command
     * Sets a player's LuckPerms parent group
     */
    private boolean handleLpSet(CommandSender sender, String[] args) {
        // Check permission
        if (!hasPermission(sender, "melonmc.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // Check arguments
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lpset <player> <kit name>");
            return true;
        }

        // Get arguments
        String playerName = args[0];
        String kitName = args[1];

        // Execute the LuckPerms command via console
        String lpCommand = "lp user " + playerName + " parent set " + kitName;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lpCommand);
        
        // Send confirmation message
        sender.sendMessage(ChatColor.GREEN + "Set " + playerName + "'s permission group to " + kitName);
        
        return true;
    }

    /**
     * Handle the /givecoin command
     * Gives coins to a player
     */
    private boolean handleGiveCoin(CommandSender sender, String[] args) {
        // Check permission
        if (!hasPermission(sender, "melonmc.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // Check arguments
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /givecoin <player> <amount> <gamemode>");
            return true;
        }

        // Get arguments
        String playerName = args[0];
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "Amount must be positive!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Amount must be a number!");
            return true;
        }
        
        String gamemode = args[2];

        // Find player
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        // If player is online, give coins directly
        if (target.isOnline() && target.getPlayer() != null) {
            boolean success = plugin.getCoinsManager().giveCoins(target.getPlayer(), amount, gamemode);
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Given " + amount + " coins to " + playerName + " for " + gamemode);
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to give coins to " + playerName);
            }
            return true;
        } 
        
        // For offline players, we'll have to handle this differently
        // Store the info in config for when they log in
        if (target.hasPlayedBefore()) {
            // Create the pending coins structure manually using config
            String path = "pending-coins." + target.getUniqueId() + "." + gamemode;
            int existingCoins = plugin.getConfig().getInt(path, 0);
            plugin.getConfig().set(path, existingCoins + amount);
            plugin.saveConfig();
            
            sender.sendMessage(ChatColor.GREEN + "Stored " + amount + " coins for offline player " + playerName + " (gamemode: " + gamemode + ")");
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " not found!");
            return true;
        }
    }

    /**
     * Handle the /giverank command
     * Gives a rank to a player
     */
    private boolean handleGiveRank(CommandSender sender, String[] args) {
        // Check permission
        if (!hasPermission(sender, "melonmc.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // Check arguments
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /giverank <player> <rank> <gamemode>");
            return true;
        }

        // Get arguments
        String playerName = args[0];
        String rank = args[1];
        String gamemode = args[2];

        // Find player
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        
        // If player is online, give rank directly
        if (target.isOnline() && target.getPlayer() != null) {
            boolean success = plugin.getRankManager().giveRank(target.getPlayer(), gamemode, rank);
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Given rank " + rank + " to " + playerName + " for " + gamemode);
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to give rank to " + playerName);
            }
            return true;
        }
        
        // If player is offline, store pending rank
        if (target.hasPlayedBefore()) {
            plugin.getRankManager().storePendingRank(target.getUniqueId(), gamemode, rank);
            sender.sendMessage(ChatColor.GREEN + "Stored rank " + rank + " for offline player " + playerName + " (gamemode: " + gamemode + ")");
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " not found!");
            return true;
        }
    }

    /**
     * Handle the /is_player_exists command
     * Checks if a player exists on the server
     */
    private boolean handlePlayerExists(CommandSender sender, String[] args) {
        // Check permission
        if (!hasPermission(sender, "melonmc.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // Check arguments
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /is_player_exists <player>");
            return true;
        }

        // Get player name
        String playerName = args[0];

        // Check if player exists
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        boolean exists = player.hasPlayedBefore() || player.isOnline();

        if (exists) {
            sender.sendMessage(ChatColor.GREEN + "Player " + playerName + " exists! UUID: " + player.getUniqueId());
        } else {
            sender.sendMessage(ChatColor.RED + "Player " + playerName + " does not exist!");
        }

        return true;
    }

    /**
     * Check if a sender has a permission
     * If sender is console, permission is granted
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            return true; // Console has all permissions
        }
        
        return sender.hasPermission(permission) || sender.isOp();
    }
} 
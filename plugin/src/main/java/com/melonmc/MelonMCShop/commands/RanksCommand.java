package com.melonmc.MelonMCShop.commands;

import com.melonmc.MelonMCShop.MelonMCShop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Handler for rank management commands
 */
public class RanksCommand implements CommandExecutor, TabCompleter {

    private final MelonMCShop plugin;
    private final List<String> validRanks = Arrays.asList(
            "eternal", "immortal", "knight", "lord", "lady", "duke", "duchess", "vip_plus", "vip+"
    );

    /**
     * Constructor for RanksCommand
     * @param plugin MelonMCShop instance
     */
    public RanksCommand(MelonMCShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            if (cmd.getName().equalsIgnoreCase("rankinfo")) {
                return handleRankInfo(sender, args);
            } else if (cmd.getName().equalsIgnoreCase("setrank")) {
                return handleSetRank(sender, args);
            } else if (cmd.getName().equalsIgnoreCase("checkrank")) {
                return handleCheckRank(sender, args);
            }
            return false;
        } catch (Exception e) {
            plugin.logError("Error executing ranks command", e);
            sender.sendMessage(ChatColor.RED + "An error occurred while processing the command.");
            return true;
        }
    }

    /**
     * Handle the checkrank command
     * @param sender Command sender
     * @param args Command arguments
     * @return true if successful, false otherwise
     */
    private boolean handleCheckRank(CommandSender sender, String[] args) {
        if (!sender.hasPermission("melonmc.ranks.check")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /checkrank <player>");
            return true;
        }

        String playerName = args[0];
        Player player = Bukkit.getPlayerExact(playerName);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online.");
            return true;
        }

        // Get player's rank using permissions
        String rank = "default";
        for (String rankName : validRanks) {
            if (plugin.getPermissions().playerHas(player, "group." + rankName)) {
                rank = rankName;
                break;
            }
        }

        sender.sendMessage(ChatColor.GREEN + player.getName() + "'s current rank: " + ChatColor.GOLD + rank);
        return true;
    }

    /**
     * Handle the rankinfo command
     * @param sender Command sender
     * @param args Command arguments
     * @return true if successful, false otherwise
     */
    private boolean handleRankInfo(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GREEN + "=== MelonMC Ranks ===");
        sender.sendMessage(ChatColor.GOLD + "• Eternal Rank: " + ChatColor.WHITE + "749rs");
        sender.sendMessage(ChatColor.GOLD + "• Immortal Rank: " + ChatColor.WHITE + "699rs");
        sender.sendMessage(ChatColor.GOLD + "• Knight Rank: " + ChatColor.WHITE + "599rs");
        sender.sendMessage(ChatColor.GOLD + "• Lord Rank: " + ChatColor.WHITE + "499rs");
        sender.sendMessage(ChatColor.GOLD + "• Lady Rank: " + ChatColor.WHITE + "399rs");
        sender.sendMessage(ChatColor.GOLD + "• Duke Rank: " + ChatColor.WHITE + "199rs");
        sender.sendMessage(ChatColor.GOLD + "• Duchess Rank: " + ChatColor.WHITE + "99rs");
        sender.sendMessage(ChatColor.GOLD + "• VIP+ Rank: " + ChatColor.WHITE + "59rs");
        sender.sendMessage(ChatColor.GREEN + "Visit our website or Discord for purchase information.");
        return true;
    }

    /**
     * Handle the setrank command
     * @param sender Command sender
     * @param args Command arguments
     * @return true if successful, false otherwise
     */
    private boolean handleSetRank(CommandSender sender, String[] args) {
        if (!sender.hasPermission("melonmc.ranks.set")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /setrank <player> <rank> [auth_token]");
            return true;
        }

        String playerName = args[0];
        String rankName = args[1].toLowerCase();
        String authToken = args.length > 2 ? args[2] : null;

        // Verify auth token if security is enabled
        if (plugin.getConfig().getBoolean("security.require-auth-token", false)) {
            String configToken = plugin.getConfig().getString("security.auth-token", "");
            if (authToken == null || !authToken.equals(configToken)) {
                sender.sendMessage(ChatColor.RED + "Invalid authentication token.");
                plugin.getLogger().warning("Unauthorized rank set attempt by " + sender.getName());
                return true;
            }
        }

        // Normalize rank name (vip+ -> vip_plus)
        if (rankName.equals("vip+")) {
            rankName = "vip_plus";
        }

        // Validate rank
        if (!validRanks.contains(rankName)) {
            sender.sendMessage(ChatColor.RED + "Invalid rank! Valid ranks: " + String.join(", ", validRanks));
            return true;
        }

        // Find player (online or offline)
        Player player = Bukkit.getPlayerExact(playerName);
        UUID playerUUID = null;
        
        if (player != null) {
            playerUUID = player.getUniqueId();
        } else {
            // Try to get player from rank manager storage
            playerUUID = plugin.getRankManager().getPlayerUUID(playerName);
            
            if (playerUUID == null) {
                // If player has never joined before, we can't set their rank
                sender.sendMessage(ChatColor.RED + "Player not found. They must join the server at least once.");
                return true;
            }
        }
        
        // Set the rank
        try {
            boolean success;
            if (player != null) {
                // Player is online
                success = applyRankToPlayer(player, rankName);
            } else {
                // Player is offline
                success = plugin.getRankManager().setRank(playerUUID, playerName, rankName);
            }

            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Successfully set " + playerName + "'s rank to " + rankName);
                
                // Notify online player
                if (player != null) {
                    player.sendMessage(ChatColor.GREEN + "Your rank has been set to " + ChatColor.GOLD + rankName + ChatColor.GREEN + "!");
                    player.sendMessage(ChatColor.GREEN + "Enjoy your new privileges!");
                }
                
                // Log the rank change
                plugin.getLogger().info("Rank change: " + playerName + " -> " + rankName + " by " + sender.getName());
                
                // Notify Discord
                notifyDiscordRankChange(playerName, rankName, playerUUID);
                
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to set the rank. Check console for details.");
                return true;
            }
        } catch (Exception e) {
            plugin.logError("Error setting rank for player " + playerName, e);
            sender.sendMessage(ChatColor.RED + "An error occurred while setting the rank.");
            return true;
        }
    }

    /**
     * Apply rank directly to an online player
     * @param player The player to apply the rank to
     * @param rankName The rank name
     * @return true if successful, false otherwise
     */
    private boolean applyRankToPlayer(Player player, String rankName) {
        try {
            // Remove existing rank groups first
            for (String group : validRanks) {
                if (plugin.getPermissions().playerInGroup(player, group)) {
                    plugin.getPermissions().playerRemoveGroup(player, group);
                }
            }
            
            // Add new rank
            return plugin.getPermissions().playerAddGroup(player, rankName);
        } catch (Exception e) {
            plugin.logError("Error applying rank to player", e);
            return false;
        }
    }

    /**
     * Notify Discord about a rank change
     * @param playerName Player name
     * @param rankName Rank name
     * @param playerUUID Player UUID
     */
    private void notifyDiscordRankChange(String playerName, String rankName, UUID playerUUID) {
        // Check if Discord bridge is available and connected
        if (plugin.getDiscordBridge() != null && plugin.getDiscordBridge().isConnected()) {
            // Schedule as async task
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Send notification
                    plugin.getDiscordBridge().notifyPurchaseComplete(
                            playerName, 
                            playerUUID.toString(), 
                            "rank", 
                            rankName, 
                            "player", 
                            "RANK_" + System.currentTimeMillis()
                    );
                } catch (Exception e) {
                    plugin.logError("Error notifying Discord about rank change", e);
                }
            });
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (cmd.getName().equalsIgnoreCase("setrank")) {
            if (args.length == 1) {
                // Suggest online players
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args.length == 2) {
                // Suggest ranks
                for (String rank : validRanks) {
                    if (rank.startsWith(args[1].toLowerCase())) {
                        completions.add(rank);
                    }
                }
            }
        } else if (cmd.getName().equalsIgnoreCase("checkrank")) {
            if (args.length == 1) {
                // Suggest online players
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        }
        
        return completions;
    }
} 
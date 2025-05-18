package com.melonmc.MelonMCShop.commands;

import com.melonmc.MelonMCShop.MelonMCShop;
import com.melonmc.MelonMCShop.managers.DailyRewards;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command handler for rewards-related commands
 */
public class RewardsCommand implements CommandExecutor {

    private final MelonMCShop plugin;
    private final DailyRewards dailyRewards;

    /**
     * Constructor
     * @param plugin MelonMCShop plugin instance
     */
    public RewardsCommand(MelonMCShop plugin) {
        this.plugin = plugin;
        this.dailyRewards = plugin.getDailyRewards();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        switch (cmd.getName().toLowerCase()) {
            case "rewards":
            case "daily":
                return handleDailyRewards(player);
            default:
                return false;
        }
    }

    /**
     * Handle daily rewards command
     * @param player Player who issued the command
     * @return true if handled successfully
     */
    private boolean handleDailyRewards(Player player) {
        if (dailyRewards != null) {
            return dailyRewards.executeCommand(player);
        } else {
            player.sendMessage(ChatColor.RED + "Daily rewards are not available!");
            return true;
        }
    }
} 
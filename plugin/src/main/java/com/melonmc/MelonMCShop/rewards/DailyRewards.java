package com.melonmc.MelonMCShop.rewards;

import com.melonmc.MelonMCShop.MelonMCShop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles daily rewards for players
 */
public class DailyRewards {
    private final MelonMCShop plugin;
    private final Logger logger;
    private final List<Reward> dailyRewards = new ArrayList<>();
    private final Map<UUID, PlayerRewardData> playerRewardData = new HashMap<>();
    private final File dataFile;
    private FileConfiguration dataConfig;
    
    // GUI constants
    private static final String GUI_TITLE = "§6§lDaily Rewards";
    private static final int GUI_SIZE = 36;
    
    /**
     * Constructor for Daily Rewards
     * @param plugin MelonMCShop instance
     */
    public DailyRewards(MelonMCShop plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFile = new File(plugin.getDataFolder(), "rewards.yml");
        
        // Load rewards from config
        loadRewards();
        
        // Load player data
        loadData();
        
        // Schedule auto-save
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 12000L, 12000L); // Save every 10 minutes
    }
    
    /**
     * Load rewards from config
     */
    private void loadRewards() {
        ConfigurationSection rewardsSection = plugin.getConfig().getConfigurationSection("daily_rewards");
        
        if (rewardsSection == null) {
            logger.warning("No daily rewards defined in config! Creating defaults...");
            createDefaultRewards();
            return;
        }
        
        dailyRewards.clear();
        
        for (String key : rewardsSection.getKeys(false)) {
            try {
                ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(key);
                
                if (rewardSection == null) continue;
                
                int day = Integer.parseInt(key);
                String type = rewardSection.getString("type", "coins");
                int amount = rewardSection.getInt("amount", 100);
                String description = rewardSection.getString("description", "Daily Reward");
                Material icon = Material.matchMaterial(rewardSection.getString("icon", "GOLD_INGOT"));
                
                if (icon == null) icon = Material.GOLD_INGOT;
                
                Reward reward = new Reward(day, type, amount, description, icon);
                dailyRewards.add(reward);
            } catch (NumberFormatException e) {
                logger.warning("Invalid day number in daily rewards config: " + key);
            }
        }
        
        // Sort rewards by day
        dailyRewards.sort(Comparator.comparingInt(r -> r.day));
        
        logger.info("Loaded " + dailyRewards.size() + " daily rewards");
    }
    
    /**
     * Create default rewards if none are defined
     */
    private void createDefaultRewards() {
        ConfigurationSection rewardsSection = plugin.getConfig().createSection("daily_rewards");
        
        // Day 1: 100 coins
        ConfigurationSection day1 = rewardsSection.createSection("1");
        day1.set("type", "coins");
        day1.set("amount", 100);
        day1.set("description", "100 Coins");
        day1.set("icon", "GOLD_NUGGET");
        
        // Day 2: 200 coins
        ConfigurationSection day2 = rewardsSection.createSection("2");
        day2.set("type", "coins");
        day2.set("amount", 200);
        day2.set("description", "200 Coins");
        day2.set("icon", "GOLD_INGOT");
        
        // Day 3: 300 coins
        ConfigurationSection day3 = rewardsSection.createSection("3");
        day3.set("type", "coins");
        day3.set("amount", 300);
        day3.set("description", "300 Coins");
        day3.set("icon", "GOLD_BLOCK");
        
        // Day 4: 400 coins
        ConfigurationSection day4 = rewardsSection.createSection("4");
        day4.set("type", "coins");
        day4.set("amount", 400);
        day4.set("description", "400 Coins");
        day4.set("icon", "GOLD_BLOCK");
        
        // Day 5: 500 coins
        ConfigurationSection day5 = rewardsSection.createSection("5");
        day5.set("type", "coins");
        day5.set("amount", 500);
        day5.set("description", "500 Coins");
        day5.set("icon", "EMERALD");
        
        // Day 6: 600 coins
        ConfigurationSection day6 = rewardsSection.createSection("6");
        day6.set("type", "coins");
        day6.set("amount", 600);
        day6.set("description", "600 Coins");
        day6.set("icon", "EMERALD");
        
        // Day 7: 1000 coins
        ConfigurationSection day7 = rewardsSection.createSection("7");
        day7.set("type", "coins");
        day7.set("amount", 1000);
        day7.set("description", "1000 Coins - Weekly Bonus!");
        day7.set("icon", "DIAMOND");
        
        plugin.saveConfig();
        
        // Load the rewards we just created
        loadRewards();
    }
    
    /**
     * Load player data from file
     */
    private void loadData() {
        // Create data file if it doesn't exist
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                logger.severe("Could not create rewards data file: " + e.getMessage());
                return;
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
        
        if (playersSection == null) return;
        
        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);
                
                if (playerSection == null) continue;
                
                String lastRewardStr = playerSection.getString("last_reward");
                LocalDate lastReward = lastRewardStr != null ? 
                        LocalDate.parse(lastRewardStr, DateTimeFormatter.ISO_LOCAL_DATE) : null;
                
                int streak = playerSection.getInt("streak", 0);
                
                PlayerRewardData data = new PlayerRewardData(lastReward, streak);
                playerRewardData.put(uuid, data);
            } catch (Exception e) {
                logger.warning("Error loading reward data for player " + uuidStr + ": " + e.getMessage());
            }
        }
        
        logger.info("Loaded daily reward data for " + playerRewardData.size() + " players");
    }
    
    /**
     * Save player data to file
     */
    public void saveData() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        
        ConfigurationSection playersSection = dataConfig.createSection("players");
        
        for (Map.Entry<UUID, PlayerRewardData> entry : playerRewardData.entrySet()) {
            ConfigurationSection playerSection = playersSection.createSection(entry.getKey().toString());
            
            if (entry.getValue().lastReward != null) {
                playerSection.set("last_reward", entry.getValue().lastReward.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            
            playerSection.set("streak", entry.getValue().streak);
        }
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            logger.severe("Could not save rewards data: " + e.getMessage());
        }
    }
    
    /**
     * Check if a player can claim a reward today
     * @param player Player to check
     * @return true if they can claim, false otherwise
     */
    public boolean canClaimToday(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerRewardData data = playerRewardData.get(uuid);
        
        if (data == null) return true; // New player, can claim
        
        LocalDate today = LocalDate.now();
        
        // If they haven't claimed today, they can claim
        return data.lastReward == null || !data.lastReward.equals(today);
    }
    
    /**
     * Get the current streak for a player
     * @param player Player to check
     * @return Streak count
     */
    public int getStreak(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerRewardData data = playerRewardData.get(uuid);
        
        if (data == null) return 0;
        
        return data.streak;
    }
    
    /**
     * Claim today's reward for a player
     * @param player Player claiming the reward
     * @return true if successful, false otherwise
     */
    public boolean claimDailyReward(Player player) {
        if (!canClaimToday(player)) {
            player.sendMessage(ChatColor.RED + "You have already claimed your daily reward today!");
            return false;
        }
        
        UUID uuid = player.getUniqueId();
        PlayerRewardData data = playerRewardData.get(uuid);
        
        // Calculate streak
        int newStreak;
        if (data == null) {
            // First time claiming
            newStreak = 1;
        } else {
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            
            // If they claimed yesterday, increment streak
            if (data.lastReward != null && data.lastReward.equals(yesterday)) {
                newStreak = data.streak + 1;
                
                // If they've completed a full week, reset to day 1
                if (newStreak > 7) {
                    newStreak = 1;
                }
            } else {
                // Streak broken, start over
                newStreak = 1;
            }
        }
        
        // Get the reward for this streak day
        Reward reward = getRewardForDay(newStreak);
        
        if (reward == null) {
            player.sendMessage(ChatColor.RED + "Error: No reward found for day " + newStreak);
            return false;
        }
        
        // Apply the reward
        boolean success = giveReward(player, reward);
        
        if (success) {
            // Update player data
            PlayerRewardData newData = new PlayerRewardData(LocalDate.now(), newStreak);
            playerRewardData.put(uuid, newData);
            
            // Notify the player
            player.sendMessage(ChatColor.GREEN + "You claimed your daily reward: " + ChatColor.GOLD + reward.description);
            player.sendMessage(ChatColor.GREEN + "Current streak: " + ChatColor.GOLD + newStreak + " day(s)");
            
            // Play a sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // Save data
            saveData();
            
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Failed to give reward. Please contact an administrator.");
            return false;
        }
    }
    
    /**
     * Get the reward for a specific day
     * @param day Day number
     * @return Reward for that day, or null if not found
     */
    private Reward getRewardForDay(int day) {
        for (Reward reward : dailyRewards) {
            if (reward.day == day) {
                return reward;
            }
        }
        
        // If no specific reward for this day, return the last one
        if (!dailyRewards.isEmpty()) {
            return dailyRewards.get(dailyRewards.size() - 1);
        }
        
        return null;
    }
    
    /**
     * Give a reward to a player
     * @param player Player to give reward to
     * @param reward Reward to give
     * @return true if successful, false otherwise
     */
    private boolean giveReward(Player player, Reward reward) {
        switch (reward.type.toLowerCase()) {
            case "coins":
                return giveCoins(player, reward.amount);
            case "item":
                return giveItem(player, reward);
            default:
                logger.warning("Unknown reward type: " + reward.type);
                return false;
        }
    }
    
    /**
     * Give coins to a player
     * @param player Player to give coins to
     * @param amount Amount of coins to give
     * @return true if successful, false otherwise
     */
    private boolean giveCoins(Player player, int amount) {
        String gamemode = plugin.getConfig().getString("settings.default-gamemode", "survival");
        return plugin.getCoinsManager().giveCoins(player, amount, gamemode);
    }
    
    /**
     * Give an item to a player
     * @param player Player to give the item to
     * @param reward Reward containing the item details
     * @return true if successful, false otherwise
     */
    private boolean giveItem(Player player, Reward reward) {
        // For now, just create a simple item with the reward icon
        ItemStack item = new ItemStack(reward.icon);
        item.setAmount(reward.amount);
        
        // Add the reward description as the item name
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + reward.description);
            item.setItemMeta(meta);
        }
        
        // Check if player has inventory space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Your inventory is full! The reward will be dropped at your feet.");
            player.getWorld().dropItem(player.getLocation(), item);
        } else {
            player.getInventory().addItem(item);
        }
        
        return true;
    }
    
    /**
     * Open the daily rewards GUI for a player
     * @param player Player to show the GUI to
     */
    public void openRewardsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        
        // Add each day's reward
        int slot = 10;
        for (Reward reward : dailyRewards) {
            ItemStack icon = new ItemStack(reward.icon);
            ItemMeta meta = icon.getItemMeta();
            
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Day " + reward.day);
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + reward.description);
                lore.add("");
                
                int streak = getStreak(player);
                
                if (reward.day < streak) {
                    // Past reward
                    lore.add(ChatColor.GREEN + "✓ Claimed");
                } else if (reward.day == streak && canClaimToday(player)) {
                    // Current reward, available
                    lore.add(ChatColor.YELLOW + "➤ Available today!");
                    lore.add(ChatColor.YELLOW + "Click to claim!");
                } else if (reward.day == streak && !canClaimToday(player)) {
                    // Current reward, already claimed
                    lore.add(ChatColor.GREEN + "✓ Claimed today");
                    lore.add(ChatColor.GRAY + "Come back tomorrow!");
                } else {
                    // Future reward
                    lore.add(ChatColor.RED + "✗ Locked");
                    lore.add(ChatColor.GRAY + "Unlock in " + (reward.day - streak) + " days");
                }
                
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            
            gui.setItem(slot, icon);
            
            // Increment slot
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
        
        // Add streak info
        ItemStack streakItem = new ItemStack(Material.CLOCK);
        ItemMeta streakMeta = streakItem.getItemMeta();
        
        if (streakMeta != null) {
            int streak = getStreak(player);
            streakMeta.setDisplayName(ChatColor.GOLD + "Your Streak: " + streak + " day(s)");
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Login daily to keep your streak!");
            
            if (canClaimToday(player)) {
                lore.add("");
                lore.add(ChatColor.YELLOW + "You can claim your reward today!");
            } else {
                lore.add("");
                lore.add(ChatColor.GRAY + "You have already claimed your reward today.");
                lore.add(ChatColor.GRAY + "Come back tomorrow!");
            }
            
            streakMeta.setLore(lore);
            streakItem.setItemMeta(streakMeta);
        }
        
        gui.setItem(31, streakItem);
        
        // Fill empty slots with glass panes
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
    }
    
    /**
     * Handle clicking in the rewards GUI
     * @param player Player who clicked
     * @param slot Slot that was clicked
     * @return true if the click was handled, false otherwise
     */
    public boolean handleRewardClick(Player player, int slot) {
        // Check if the slot contains a reward
        Reward clickedReward = null;
        int index = 0;
        
        // Map slot to reward index
        if (slot >= 10 && slot <= 16) {
            index = slot - 10;
        } else if (slot >= 19 && slot <= 25) {
            index = slot - 19 + 7;
        }
        
        // Get the reward at this index if it exists
        if (index < dailyRewards.size()) {
            clickedReward = dailyRewards.get(index);
        }
        
        if (clickedReward == null) {
            return false;
        }
        
        // Check if they can claim this reward
        int streak = getStreak(player);
        
        if (clickedReward.day == streak && canClaimToday(player)) {
            // Claim the reward
            player.closeInventory();
            return claimDailyReward(player);
        } else {
            // Notify the player why they can't claim
            if (clickedReward.day < streak) {
                player.sendMessage(ChatColor.RED + "You have already claimed this reward!");
            } else if (clickedReward.day == streak && !canClaimToday(player)) {
                player.sendMessage(ChatColor.RED + "You have already claimed your reward today! Come back tomorrow.");
            } else {
                player.sendMessage(ChatColor.RED + "You can't claim this reward yet! Keep your daily streak going.");
            }
            
            // Play a sound to indicate they can't claim
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            
            return true;
        }
    }
    
    /**
     * Save data on plugin disable
     */
    public void onDisable() {
        saveData();
    }
    
    /**
     * Static class to hold reward data
     */
    private static class Reward {
        public final int day;
        public final String type;
        public final int amount;
        public final String description;
        public final Material icon;
        
        public Reward(int day, String type, int amount, String description, Material icon) {
            this.day = day;
            this.type = type;
            this.amount = amount;
            this.description = description;
            this.icon = icon;
        }
    }
    
    /**
     * Static class to hold player reward data
     */
    private static class PlayerRewardData {
        public final LocalDate lastReward;
        public final int streak;
        
        public PlayerRewardData(LocalDate lastReward, int streak) {
            this.lastReward = lastReward;
            this.streak = streak;
        }
    }
} 
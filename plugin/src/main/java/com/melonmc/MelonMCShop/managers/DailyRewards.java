package com.melonmc.MelonMCShop.managers;

import com.melonmc.MelonMCShop.MelonMCShop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages daily rewards for players
 */
public class DailyRewards {
    private final MelonMCShop plugin;
    private final Map<UUID, LocalDate> lastRewardClaims = new ConcurrentHashMap<>();
    private final Map<Integer, DailyReward> dayRewards = new HashMap<>();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private File dataFile;
    private FileConfiguration dataConfig;
    private boolean operational = true;
    
    /**
     * Constructor
     * @param plugin MelonMCShop plugin instance
     */
    public DailyRewards(MelonMCShop plugin) {
        this.plugin = plugin;
        
        // Create default rewards if they don't exist
        setupDefaultRewards();
        
        // Initialize data storage
        setupStorage();
        
        // Load data
        loadData();
    }
    
    /**
     * Setup default rewards in config if they don't exist
     */
    private void setupDefaultRewards() {
        try {
            ConfigurationSection rewardsSection = plugin.getConfig().getConfigurationSection("daily-rewards");
            if (rewardsSection == null) {
                rewardsSection = plugin.getConfig().createSection("daily-rewards");
                
                // Day 1: 100 coins
                ConfigurationSection day1 = rewardsSection.createSection("day1");
                day1.set("type", "coins");
                day1.set("amount", 100);
                day1.set("display-name", "&aDay 1: &f100 Coins");
                day1.set("description", "&7Log in daily to earn rewards!");
                day1.set("material", "GOLD_INGOT");
                
                // Day 2: 150 coins
                ConfigurationSection day2 = rewardsSection.createSection("day2");
                day2.set("type", "coins");
                day2.set("amount", 150);
                day2.set("display-name", "&aDay 2: &f150 Coins");
                day2.set("description", "&7Keep the streak going!");
                day2.set("material", "GOLD_INGOT");
                
                // Day 3: 200 coins
                ConfigurationSection day3 = rewardsSection.createSection("day3");
                day3.set("type", "coins");
                day3.set("amount", 200);
                day3.set("display-name", "&aDay 3: &f200 Coins");
                day3.set("description", "&7You're on a roll!");
                day3.set("material", "GOLD_INGOT");
                
                // Day 4: 250 coins
                ConfigurationSection day4 = rewardsSection.createSection("day4");
                day4.set("type", "coins");
                day4.set("amount", 250);
                day4.set("display-name", "&aDay 4: &f250 Coins");
                day4.set("description", "&7Almost there!");
                day4.set("material", "GOLD_INGOT");
                
                // Day 5: Small rank
                ConfigurationSection day5 = rewardsSection.createSection("day5");
                day5.set("type", "rank");
                day5.set("rank", "vip");
                day5.set("gamemode", "survival");
                day5.set("display-name", "&aDay 5: &fVIP Rank");
                day5.set("description", "&7Thanks for your loyalty!");
                day5.set("material", "DIAMOND");
                
                // Day 6: 300 coins
                ConfigurationSection day6 = rewardsSection.createSection("day6");
                day6.set("type", "coins");
                day6.set("amount", 300);
                day6.set("display-name", "&aDay 6: &f300 Coins");
                day6.set("description", "&7Almost a week!");
                day6.set("material", "GOLD_INGOT");
                
                // Day 7: Bigger rank
                ConfigurationSection day7 = rewardsSection.createSection("day7");
                day7.set("type", "rank");
                day7.set("rank", "vip+");
                day7.set("gamemode", "survival");
                day7.set("display-name", "&aDay 7: &fVIP+ Rank");
                day7.set("description", "&7Full week completed!");
                day7.set("material", "EMERALD");
                
                plugin.saveConfig();
            }
            
            // Load rewards from config
            loadRewards();
        } catch (Exception e) {
            plugin.logError("Failed to setup default rewards", e);
            operational = false;
        }
    }
    
    /**
     * Load rewards from config
     */
    private void loadRewards() {
        try {
            dayRewards.clear();
            
            ConfigurationSection rewardsSection = plugin.getConfig().getConfigurationSection("daily-rewards");
            if (rewardsSection == null) return;
            
            for (String key : rewardsSection.getKeys(false)) {
                try {
                    ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(key);
                    if (rewardSection == null) continue;
                    
                    // Parse day number from key
                    int day;
                    if (key.startsWith("day")) {
                        try {
                            day = Integer.parseInt(key.substring(3));
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid day format in rewards: " + key);
                            continue;
                        }
                    } else {
                        try {
                            day = Integer.parseInt(key);
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid day format in rewards: " + key);
                            continue;
                        }
                    }
                    
                    // Read reward data
                    String type = rewardSection.getString("type", "coins");
                    String displayName = rewardSection.getString("display-name", "Day " + day);
                    List<String> description = rewardSection.getStringList("description");
                    if (description.isEmpty() && rewardSection.contains("description")) {
                        description = Collections.singletonList(rewardSection.getString("description"));
                    }
                    
                    Material material;
                    try {
                        material = Material.valueOf(rewardSection.getString("material", "PAPER").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = Material.PAPER;
                    }
                    
                    DailyReward reward = new DailyReward(day, type);
                    reward.setDisplayName(displayName);
                    reward.setDescription(description);
                    reward.setMaterial(material);
                    
                    if ("coins".equalsIgnoreCase(type)) {
                        reward.setAmount(rewardSection.getInt("amount", 100));
                    } else if ("rank".equalsIgnoreCase(type)) {
                        reward.setRank(rewardSection.getString("rank", "default"));
                        reward.setGamemode(rewardSection.getString("gamemode", "survival"));
                    } else if ("item".equalsIgnoreCase(type)) {
                        reward.setItemType(Material.valueOf(rewardSection.getString("item-type", "DIAMOND").toUpperCase()));
                        reward.setAmount(rewardSection.getInt("amount", 1));
                    } else if ("command".equalsIgnoreCase(type)) {
                        reward.setCommand(rewardSection.getString("command", "say %player% claimed a daily reward"));
                    }
                    
                    dayRewards.put(day, reward);
                } catch (Exception e) {
                    plugin.logError("Error loading reward: " + key, e);
                }
            }
        } catch (Exception e) {
            plugin.logError("Failed to load rewards", e);
            operational = false;
        }
    }
    
    /**
     * Setup data storage
     */
    private void setupStorage() {
        try {
            dataFile = new File(plugin.getDataFolder(), "daily_rewards.yml");
            if (!dataFile.exists()) {
                dataFile.getParentFile().mkdirs();
                plugin.saveResource("daily_rewards.yml", false);
            }
            
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        } catch (Exception e) {
            plugin.logError("Failed to setup daily rewards storage", e);
            operational = false;
        }
    }
    
    /**
     * Open the reward menu for a player
     * @param player The player
     */
    public void openRewardMenu(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, 27, ChatColor.GREEN + "Daily Rewards");
            UUID playerUUID = player.getUniqueId();
            int streak = calculateStreak(playerUUID);
            
            // Current day's reward
            int currentDay = (streak % 7) + 1;
            if (currentDay <= 0) currentDay = 1;
            
            // Check if already claimed today
            boolean canClaim = canClaimReward(playerUUID);
            
            // Add all rewards to GUI
            for (int day = 1; day <= 7; day++) {
                DailyReward reward = dayRewards.get(day);
                if (reward == null) continue;
                
                ItemStack item = new ItemStack(reward.getMaterial());
                ItemMeta meta = item.getItemMeta();
                
                if (meta != null) {
                    // Format display name
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', reward.getDisplayName()));
                    
                    // Create lore
                    List<String> lore = new ArrayList<>();
                    
                    // Add description
                    for (String line : reward.getDescription()) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', line));
                    }
                    
                    // Add reward info based on type
                    if ("coins".equalsIgnoreCase(reward.getType())) {
                        lore.add(ChatColor.YELLOW + "Reward: " + ChatColor.WHITE + reward.getAmount() + " coins");
                    } else if ("rank".equalsIgnoreCase(reward.getType())) {
                        lore.add(ChatColor.YELLOW + "Reward: " + ChatColor.WHITE + reward.getRank() + " rank");
                        lore.add(ChatColor.YELLOW + "Gamemode: " + ChatColor.WHITE + reward.getGamemode());
                    } else if ("item".equalsIgnoreCase(reward.getType())) {
                        lore.add(ChatColor.YELLOW + "Reward: " + ChatColor.WHITE + reward.getAmount() + "x " + reward.getItemType().toString().toLowerCase().replace("_", " "));
                    } else if ("command".equalsIgnoreCase(reward.getType())) {
                        lore.add(ChatColor.YELLOW + "Reward: " + ChatColor.WHITE + "Special reward");
                    }
                    
                    // Show status
                    if (day < currentDay) {
                        lore.add(ChatColor.GRAY + "Status: " + ChatColor.GREEN + "Claimed");
                    } else if (day == currentDay) {
                        if (canClaim) {
                            lore.add(ChatColor.GRAY + "Status: " + ChatColor.GOLD + "Available");
                            lore.add(ChatColor.GREEN + "Click to claim!");
                        } else {
                            lore.add(ChatColor.GRAY + "Status: " + ChatColor.GREEN + "Claimed Today");
                            lore.add(ChatColor.GRAY + "Come back tomorrow!");
                        }
                    } else {
                        lore.add(ChatColor.GRAY + "Status: " + ChatColor.RED + "Locked");
                        lore.add(ChatColor.GRAY + "Keep your streak going!");
                    }
                    
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                
                // Place in GUI based on day
                int slot;
                switch (day) {
                    case 1: slot = 10; break;
                    case 2: slot = 11; break;
                    case 3: slot = 12; break;
                    case 4: slot = 13; break;
                    case 5: slot = 14; break;
                    case 6: slot = 15; break;
                    case 7: slot = 16; break;
                    default: slot = day - 1; break;
                }
                
                gui.setItem(slot, item);
            }
            
            // Add info item
            ItemStack infoItem = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = infoItem.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName(ChatColor.YELLOW + "Your Daily Rewards");
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Current streak: " + ChatColor.GREEN + streak + " day(s)");
                
                if (canClaim) {
                    lore.add(ChatColor.GOLD + "You can claim today's reward!");
                } else {
                    LocalDate nextClaim = lastRewardClaims.get(playerUUID).plusDays(1);
                    lore.add(ChatColor.GRAY + "Next reward: " + ChatColor.WHITE + nextClaim.format(dateFormatter));
                }
                
                infoMeta.setLore(lore);
                infoItem.setItemMeta(infoMeta);
            }
            
            gui.setItem(4, infoItem);
            
            player.openInventory(gui);
        } catch (Exception e) {
            plugin.logError("Failed to open reward menu for player " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "Failed to open rewards menu. Please try again later.");
        }
    }
    
    /**
     * Open rewards GUI (alias for openRewardMenu for compatibility)
     * @param player The player
     */
    public void openRewardsGUI(Player player) {
        openRewardMenu(player);
    }
    
    /**
     * Handle player clicking a reward
     * @param player The player
     * @param slot The slot clicked
     * @return true if reward was claimed, false otherwise
     */
    public boolean handleRewardClick(Player player, int slot) {
        try {
            UUID playerUUID = player.getUniqueId();
            
            // Check if player can claim reward
            if (!canClaimReward(playerUUID)) {
                player.sendMessage(ChatColor.RED + "You have already claimed your daily reward today!");
                return false;
            }
            
            // Calculate current day in 7-day cycle
            int streak = calculateStreak(playerUUID);
            int currentDay = (streak % 7) + 1;
            if (currentDay <= 0) currentDay = 1;
            
            // Check if clicked slot matches current day's reward slot
            int expectedSlot;
            switch (currentDay) {
                case 1: expectedSlot = 10; break;
                case 2: expectedSlot = 11; break;
                case 3: expectedSlot = 12; break;
                case 4: expectedSlot = 13; break;
                case 5: expectedSlot = 14; break;
                case 6: expectedSlot = 15; break;
                case 7: expectedSlot = 16; break;
                default: expectedSlot = currentDay - 1; break;
            }
            
            if (slot != expectedSlot) {
                return false;
            }
            
            // Get the reward for current day
            DailyReward reward = dayRewards.get(currentDay);
            if (reward == null) {
                player.sendMessage(ChatColor.RED + "No reward found for day " + currentDay);
                return false;
            }
            
            // Give the reward based on type
            boolean success = false;
            
            switch (reward.getType().toLowerCase()) {
                case "coins":
                    success = plugin.getCoinsManager().giveCoins(player, reward.getAmount(), "daily");
                    break;
                    
                case "rank":
                    success = plugin.getRankManager().giveRank(player, reward.getGamemode(), reward.getRank());
                    break;
                    
                case "item":
                    ItemStack item = new ItemStack(reward.getItemType(), reward.getAmount());
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    
                    if (leftover.isEmpty()) {
                        success = true;
                        player.sendMessage(ChatColor.GREEN + "You received " + reward.getAmount() + "x " + 
                                reward.getItemType().toString().toLowerCase().replace("_", " ") + "!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Your inventory is full! Please make space and try again.");
                    }
                    break;
                    
                case "command":
                    String command = reward.getCommand()
                            .replace("%player%", player.getName())
                            .replace("%uuid%", player.getUniqueId().toString());
                    
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    success = true;
                    player.sendMessage(ChatColor.GREEN + "You received a special reward!");
                    break;
            }
            
            if (success) {
                // Update last claim
                lastRewardClaims.put(playerUUID, LocalDate.now());
                saveData();
                
                player.sendMessage(ChatColor.GREEN + "You claimed your day " + currentDay + " reward!");
                player.closeInventory();
                return true;
            }
            
            return false;
        } catch (Exception e) {
            plugin.logError("Failed to handle reward click for player " + player.getName(), e);
            return false;
        }
    }
    
    /**
     * Check if a player can claim their daily reward
     * @param playerUUID The player's UUID
     * @return true if they can claim, false otherwise
     */
    public boolean canClaimReward(UUID playerUUID) {
        LocalDate lastClaim = lastRewardClaims.get(playerUUID);
        if (lastClaim == null) {
            return true;
        }
        
        LocalDate today = LocalDate.now();
        return !today.equals(lastClaim);
    }
    
    /**
     * Calculate a player's current streak
     * @param playerUUID The player's UUID
     * @return The player's streak (days in a row)
     */
    public int calculateStreak(UUID playerUUID) {
        LocalDate lastClaim = lastRewardClaims.get(playerUUID);
        if (lastClaim == null) {
            return 0;
        }
        
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        
        // If they claimed yesterday, their streak continues
        if (lastClaim.equals(yesterday) || lastClaim.equals(today)) {
            String streakStr = dataConfig.getString("players." + playerUUID + ".streak");
            if (streakStr != null) {
                try {
                    return Integer.parseInt(streakStr);
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
            return 1;
        }
        
        // If they missed a day, streak resets
        return 0;
    }
    
    /**
     * Load data from storage
     */
    public void loadData() {
        try {
            lastRewardClaims.clear();
            
            // Reload data file
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            
            // Load player data
            ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
            if (playersSection != null) {
                for (String uuidStr : playersSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        ConfigurationSection playerData = playersSection.getConfigurationSection(uuidStr);
                        
                        if (playerData != null && playerData.contains("last-claim")) {
                            String dateStr = playerData.getString("last-claim");
                            if (dateStr != null) {
                                LocalDate date = LocalDate.parse(dateStr, dateFormatter);
                                lastRewardClaims.put(uuid, date);
                            }
                        }
                    } catch (Exception e) {
                        plugin.logError("Failed to load reward data for player " + uuidStr, e);
                    }
                }
            }
            
            operational = true;
        } catch (Exception e) {
            plugin.logError("Failed to load daily rewards data", e);
            operational = false;
        }
    }
    
    /**
     * Save data to storage
     */
    public void saveData() {
        try {
            // Clear existing data
            dataConfig.set("players", null);
            
            // Save each player's data
            for (Map.Entry<UUID, LocalDate> entry : lastRewardClaims.entrySet()) {
                UUID uuid = entry.getKey();
                LocalDate lastClaim = entry.getValue();
                
                dataConfig.set("players." + uuid + ".last-claim", lastClaim.format(dateFormatter));
                
                // Save streak data
                int streak = calculateStreak(uuid);
                if (streak > 0) {
                    dataConfig.set("players." + uuid + ".streak", streak);
                }
            }
            
            // Save to file
            dataConfig.save(dataFile);
            operational = true;
        } catch (Exception e) {
            plugin.logError("Failed to save daily rewards data", e);
            operational = false;
        }
    }
    
    /**
     * Execute daily command
     * @param sender The command sender
     * @param args The command arguments
     * @return true if command was successful, false otherwise
     */
    public boolean executeCommand(Player player) {
        openRewardMenu(player);
        return true;
    }
    
    /**
     * Check if the manager is operational
     * @return true if operational, false if errors were encountered
     */
    public boolean isOperational() {
        return operational;
    }
    
    /**
     * Daily reward data class
     */
    private static class DailyReward {
        private final int day;
        private final String type;
        private String displayName;
        private List<String> description = new ArrayList<>();
        private Material material = Material.PAPER;
        private int amount = 100;
        private String rank = "default";
        private String gamemode = "survival";
        private Material itemType = Material.DIAMOND;
        private String command = "";
        
        public DailyReward(int day, String type) {
            this.day = day;
            this.type = type;
            this.displayName = "Day " + day;
        }
        
        public int getDay() {
            return day;
        }
        
        public String getType() {
            return type;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
        
        public List<String> getDescription() {
            return description;
        }
        
        public void setDescription(List<String> description) {
            this.description = description;
        }
        
        public Material getMaterial() {
            return material;
        }
        
        public void setMaterial(Material material) {
            this.material = material;
        }
        
        public int getAmount() {
            return amount;
        }
        
        public void setAmount(int amount) {
            this.amount = amount;
        }
        
        public String getRank() {
            return rank;
        }
        
        public void setRank(String rank) {
            this.rank = rank;
        }
        
        public String getGamemode() {
            return gamemode;
        }
        
        public void setGamemode(String gamemode) {
            this.gamemode = gamemode;
        }
        
        public Material getItemType() {
            return itemType;
        }
        
        public void setItemType(Material itemType) {
            this.itemType = itemType;
        }
        
        public String getCommand() {
            return command;
        }
        
        public void setCommand(String command) {
            this.command = command;
        }
    }
} 
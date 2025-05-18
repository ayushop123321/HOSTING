package com.melonmc.MelonMCShop;

import com.melonmc.MelonMCShop.commands.CommandHandler;
import com.melonmc.MelonMCShop.commands.PlayerCommands;
import com.melonmc.MelonMCShop.commands.RanksCommand;
import com.melonmc.MelonMCShop.commands.RewardsCommand;
import com.melonmc.MelonMCShop.gui.ShopGUI;
import com.melonmc.MelonMCShop.listeners.PlayerListener;
import com.melonmc.MelonMCShop.managers.*;
import com.melonmc.MelonMCShop.utils.DiscordBridge;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The main class of the MelonMCShop plugin.
 */
public class MelonMCShop extends JavaPlugin {

    private Permission permissions;
    private Economy economy;
    private RankManager rankManager;
    private CoinsManager coinsManager;
    private ShopGUI shopGUI;
    private PlayerListener playerListener;
    private DailyRewards dailyRewards;
    private AuthManager authManager;
    private DiscordBridge discordBridge;
    
    // Error handling and auto-recovery
    private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastErrorTimes = new ConcurrentHashMap<>();
    private BukkitTask healthCheckTask;
    private BukkitTask autoRecoveryTask;
    private boolean recoveryInProgress = false;
    private int consecutiveRecoveries = 0;
    private final int MAX_RECOVERY_ATTEMPTS = 3;
    private final long RECOVERY_COOLDOWN = 300000; // 5 minutes
    private long lastRecoveryAttempt = 0;
    private File errorLogFile;

    @Override
    public void onEnable() {
        // Initialize error log
        initErrorLog();
        
        getLogger().info(ChatColor.GREEN + "Starting MelonMCShop v" + this.getDescription().getVersion());
        
        try {
            // Ensure config is created
            saveDefaultConfig();
            
            // Set up Vault permissions
            setupPermissions();
            
            // Set up Vault economy
            setupEconomy();
            
            // Initialize managers
            initializeManagers();
            
            // Register listeners
            registerListeners();
            
            // Register commands
            registerCommands();
            
            // Initialize Discord bridge
            initializeDiscordBridge();
            
            // Start health check task
            startHealthCheck();
            
            getLogger().info(ChatColor.GREEN + "MelonMCShop has been enabled!");
        } catch (Exception e) {
            logError("Error during plugin startup", e);
            getLogger().severe("MelonMCShop failed to start properly. Check the error logs.");
            
            // Try to recover
            if (!recoveryInProgress) {
                Bukkit.getScheduler().runTaskLater(this, this::tryRecovery, 100L);
            }
        }
    }

    @Override
    public void onDisable() {
        try {
            // Cancel scheduled tasks
            if (healthCheckTask != null) {
                healthCheckTask.cancel();
            }
            
            if (autoRecoveryTask != null) {
                autoRecoveryTask.cancel();
            }
            
            // Shut down Discord bridge
            if (discordBridge != null) {
                discordBridge.shutdown();
            }
            
            // Save any pending data
            if (rankManager != null) {
                rankManager.saveData();
            }
            
            if (coinsManager != null) {
                coinsManager.saveData();
            }
            
            if (dailyRewards != null) {
                dailyRewards.saveData();
            }
            
            getLogger().info(ChatColor.RED + "MelonMCShop has been disabled!");
        } catch (Exception e) {
            logError("Error during plugin shutdown", e);
        }
    }

    /**
     * Initialize the error log file
     */
    private void initErrorLog() {
        try {
            File logsDir = new File(getDataFolder(), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String fileName = "errors-" + dateFormat.format(new Date()) + ".log";
            errorLogFile = new File(logsDir, fileName);
            
            if (!errorLogFile.exists()) {
                errorLogFile.createNewFile();
            }
        } catch (IOException e) {
            getLogger().severe("Failed to create error log file: " + e.getMessage());
        }
    }

    /**
     * Log an error to both console and error log file
     * @param message Error message
     * @param e Exception
     */
    public void logError(String message, Throwable e) {
        // Log to console
        getLogger().log(Level.SEVERE, message, e);
        
        // Log to file
        if (errorLogFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(errorLogFile, true))) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                writer.println("[" + timeFormat.format(new Date()) + "] " + message);
                if (e != null) {
                    e.printStackTrace(writer);
                    writer.println();
                }
            } catch (IOException ex) {
                getLogger().severe("Failed to write to error log: " + ex.getMessage());
            }
        }
        
        // Track error frequency
        String errorType = e != null ? e.getClass().getSimpleName() : "Unknown";
        errorCounts.put(errorType, errorCounts.getOrDefault(errorType, 0) + 1);
        lastErrorTimes.put(errorType, System.currentTimeMillis());
        
        // Check if recovery is needed
        if (errorCounts.getOrDefault(errorType, 0) >= 5 && 
                System.currentTimeMillis() - lastRecoveryAttempt > RECOVERY_COOLDOWN) {
            tryRecovery();
        }
    }
    
    /**
     * Start health check task
     */
    private void startHealthCheck() {
        healthCheckTask = Bukkit.getScheduler().runTaskTimer(this, this::performHealthCheck, 20L * 60, 20L * 60 * 5);
        autoRecoveryTask = Bukkit.getScheduler().runTaskTimer(this, this::performAutoMaintenance, 20L * 60 * 15, 20L * 60 * 120);
    }
    
    /**
     * Perform a health check of all plugin systems
     */
    private void performHealthCheck() {
        try {
            getLogger().info("Performing plugin health check...");
            Map<String, Boolean> healthStatus = new HashMap<>();
            
            // Check managers
            healthStatus.put("RankManager", rankManager != null && rankManager.isOperational());
            healthStatus.put("CoinsManager", coinsManager != null && coinsManager.isOperational());
            healthStatus.put("ShopGUI", shopGUI != null);
            healthStatus.put("DailyRewards", dailyRewards != null && dailyRewards.isOperational());
            healthStatus.put("AuthManager", authManager != null && authManager.isOperational());
            
            // Check Discord connection
            healthStatus.put("DiscordBridge", discordBridge != null && discordBridge.isConnected());
            
            // Check Vault integration
            healthStatus.put("Permissions", permissions != null);
            healthStatus.put("Economy", economy != null);
            
            // Check data files
            File configFile = new File(getDataFolder(), "config.yml");
            healthStatus.put("ConfigFile", configFile.exists() && configFile.length() > 0);
            
            // Log any issues
            boolean healthIssues = false;
            StringBuilder healthReport = new StringBuilder();
            for (Map.Entry<String, Boolean> entry : healthStatus.entrySet()) {
                if (!entry.getValue()) {
                    healthIssues = true;
                    healthReport.append(entry.getKey()).append(" is unhealthy! ");
                }
            }
            
            if (healthIssues) {
                getLogger().warning("Health check found issues: " + healthReport.toString());
                
                // Try recovery if there are issues
                if (!recoveryInProgress) {
                    Bukkit.getScheduler().runTaskLater(this, this::tryRecovery, 20L);
                }
            } else {
                getLogger().info("Health check completed. All systems operational.");
                resetErrorState(); // Reset error counters when everything is healthy
            }
        } catch (Exception e) {
            logError("Error during health check", e);
        }
    }
    
    /**
     * Reset error state when everything is working well
     */
    private void resetErrorState() {
        errorCounts.clear();
        consecutiveRecoveries = 0;
    }
    
    /**
     * Try to recover from a bad state
     */
    private void tryRecovery() {
        if (recoveryInProgress || System.currentTimeMillis() - lastRecoveryAttempt < RECOVERY_COOLDOWN) {
            return;
        }
        
        try {
            recoveryInProgress = true;
            lastRecoveryAttempt = System.currentTimeMillis();
            
            if (consecutiveRecoveries >= MAX_RECOVERY_ATTEMPTS) {
                getLogger().severe("Too many consecutive recovery attempts. Manual intervention required.");
                return;
            }
            
            consecutiveRecoveries++;
            getLogger().warning("Attempting auto-recovery of plugin systems... (Attempt " + consecutiveRecoveries + ")");
            
            // Reload configuration
            reloadConfig();
            
            // Reinitialize managers that have issues
            if (rankManager == null || !rankManager.isOperational()) {
                rankManager = new RankManager(this);
                rankManager.loadData();
            }
            
            if (coinsManager == null || !coinsManager.isOperational()) {
                coinsManager = new CoinsManager(this);
                coinsManager.loadData();
            }
            
            if (shopGUI == null) {
                shopGUI = new ShopGUI(this);
            }
            
            if (dailyRewards == null || !dailyRewards.isOperational()) {
                dailyRewards = new DailyRewards(this);
                dailyRewards.loadData();
            }
            
            if (authManager == null || !authManager.isOperational()) {
                authManager = new AuthManager(this);
            }
            
            // Check Discord bridge
            if (discordBridge == null || !discordBridge.isConnected()) {
                initializeDiscordBridge();
            }
            
            getLogger().info("Recovery completed. Systems restored to operational state.");
            
        } catch (Exception e) {
            logError("Error during recovery attempt", e);
            
            // Schedule another attempt with increasing delay
            int delay = Math.min(60 * consecutiveRecoveries, 300);
            getLogger().info("Will attempt recovery again in " + delay + " seconds");
            Bukkit.getScheduler().runTaskLater(this, this::tryRecovery, 20L * delay);
        } finally {
            recoveryInProgress = false;
        }
    }
    
    /**
     * Perform automatic maintenance tasks
     */
    private void performAutoMaintenance() {
        try {
            getLogger().info("Performing automatic maintenance...");
            
            // Save all data to prevent data loss
            if (rankManager != null) rankManager.saveData();
            if (coinsManager != null) coinsManager.saveData();
            if (dailyRewards != null) dailyRewards.saveData();
            
            // Cleanup old error logs - keep last 7 days
            cleanupOldLogs();
            
            // Notify Discord bot of server status
            if (discordBridge != null && discordBridge.isConnected()) {
                // Use ping method to update status
                try {
                    discordBridge.connect();
                } catch (Exception e) {
                    logError("Error updating Discord status during maintenance", e);
                }
            }
            
            getLogger().info("Automatic maintenance completed");
        } catch (Exception e) {
            logError("Error during automatic maintenance", e);
        }
    }
    
    /**
     * Clean up old error logs
     */
    private void cleanupOldLogs() {
        try {
            File logsDir = new File(getDataFolder(), "logs");
            if (!logsDir.exists()) return;
            
            File[] logFiles = logsDir.listFiles((dir, name) -> name.startsWith("errors-") && name.endsWith(".log"));
            if (logFiles == null) return;
            
            // Keep logs from the last 7 days
            long cutoffTime = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 7);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            
            for (File logFile : logFiles) {
                String datePart = logFile.getName().substring(7, 17); // Extract date from "errors-yyyy-MM-dd.log"
                try {
                    Date logDate = dateFormat.parse(datePart);
                    if (logDate.getTime() < cutoffTime) {
                        if (logFile.delete()) {
                            getLogger().info("Deleted old log file: " + logFile.getName());
                        }
                    }
                } catch (Exception e) {
                    // Just ignore files with invalid format
                }
            }
        } catch (Exception e) {
            logError("Error cleaning up log files", e);
        }
    }

    /**
     * Initialize Discord Bridge
     */
    private void initializeDiscordBridge() {
        if (getConfig().getBoolean("discord.enabled", true)) {
            discordBridge = new DiscordBridge(this);
            getLogger().info("Discord bridge initialized");
        } else {
            getLogger().info("Discord bridge is disabled in config");
        }
    }
    
    /**
     * Formats the given message with the plugin's prefix
     *
     * @param message The message to be formatted
     * @return The formatted message
     */
    public String formatMessage(String message) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", "&8[&6MelonMCShop&8] &7") + message);
    }

    /**
     * Set up Vault permissions
     */
    private boolean setupPermissions() {
        try {
            RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
            if (rsp != null) {
                permissions = rsp.getProvider();
                return true;
            } else {
                logError("No Vault permissions provider found", null);
                return false;
            }
        } catch (Exception e) {
            logError("Failed to setup Vault permissions", e);
            return false;
        }
    }

    /**
     * Set up Vault economy
     */
    private boolean setupEconomy() {
        try {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                return true;
            } else {
                logError("No Vault economy provider found", null);
                return false;
            }
        } catch (Exception e) {
            logError("Failed to setup Vault economy", e);
            return false;
        }
    }

    /**
     * Initialize all managers
     */
    private void initializeManagers() {
        try {
            rankManager = new RankManager(this);
            coinsManager = new CoinsManager(this);
            shopGUI = new ShopGUI(this);
            dailyRewards = new DailyRewards(this);
            authManager = new AuthManager(this);
        } catch (Exception e) {
            logError("Failed to initialize managers", e);
            throw e;  // Re-throw to indicate fatal error
        }
    }

    /**
     * Register listeners
     */
    private void registerListeners() {
        try {
            playerListener = new PlayerListener(this);
            getServer().getPluginManager().registerEvents(playerListener, this);
        } catch (Exception e) {
            logError("Failed to register listeners", e);
            throw e;  // Re-throw to indicate fatal error
        }
    }

    /**
     * Register commands
     */
    private void registerCommands() {
        try {
            // Register built-in commands
            getCommand("givecoin").setExecutor(new CommandHandler(this));
            getCommand("giverank").setExecutor(new CommandHandler(this));
            getCommand("is_player_exists").setExecutor(new CommandHandler(this));
            
            // Register rank commands
            RanksCommand ranksCommand = new RanksCommand(this);
            getCommand("rankinfo").setExecutor(ranksCommand);
            getCommand("setrank").setExecutor(ranksCommand);
            getCommand("checkrank").setExecutor(ranksCommand);
            
            // Set tab completers
            getCommand("rankinfo").setTabCompleter(ranksCommand);
            getCommand("setrank").setTabCompleter(ranksCommand);
            getCommand("checkrank").setTabCompleter(ranksCommand);
            
            // Register GUI commands
            getCommand("shopgui").setExecutor(new CommandHandler(this));
            
            // Register rewards commands
            RewardsCommand rewardsCommand = new RewardsCommand(this);
            getCommand("redeem").setExecutor(rewardsCommand);
            getCommand("rewards").setExecutor(rewardsCommand);
            getCommand("daily").setExecutor(rewardsCommand);
            
            // Register player commands
            PlayerCommands playerCommands = new PlayerCommands(this);
            // Add any player commands here
            
            getLogger().info("Commands registered successfully");
        } catch (Exception e) {
            logError("Error registering commands", e);
        }
    }

    /**
     * @return The plugin's permissions integration
     */
    public Permission getPermissions() {
        return permissions;
    }

    /**
     * @return The plugin's economy integration
     */
    public Economy getEconomy() {
        return economy;
    }

    /**
     * @return The plugin's rank manager
     */
    public RankManager getRankManager() {
        return rankManager;
    }

    /**
     * @return The plugin's coins manager
     */
    public CoinsManager getCoinsManager() {
        return coinsManager;
    }

    /**
     * @return The plugin's shop GUI
     */
    public ShopGUI getShopGUI() {
        return shopGUI;
    }

    /**
     * @return The plugin's daily rewards manager
     */
    public DailyRewards getDailyRewards() {
        return dailyRewards;
    }

    /**
     * @return The plugin's authentication manager
     */
    public AuthManager getAuthManager() {
        return authManager;
    }
    
    /**
     * Get the Discord bridge instance
     * @return The Discord bridge
     */
    public DiscordBridge getDiscordBridge() {
        return discordBridge;
    }
} 
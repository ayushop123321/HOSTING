package com.melonmc.MelonMCShop.security;

import com.melonmc.MelonMCShop.MelonMCShop;
import org.bukkit.configuration.ConfigurationSection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages security aspects of the plugin including authentication and rate limiting
 */
public class AuthManager {
    private final MelonMCShop plugin;
    private final Logger logger;
    private final String rconToken;
    private final Map<String, Long> lastCommandTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> commandCount = new ConcurrentHashMap<>();
    private final int RATE_LIMIT_WINDOW = 60000; // 1 minute
    private final int RATE_LIMIT_MAX = 30; // max 30 commands per minute
    private final Map<String, CommandLog> commandLogs = new ConcurrentHashMap<>();

    public AuthManager(MelonMCShop plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        // Generate a random token if not configured
        String configToken = plugin.getConfig().getString("security.rcon-token");
        if (configToken == null || configToken.isEmpty()) {
            this.rconToken = generateRandomToken();
            plugin.getConfig().set("security.rcon-token", this.rconToken);
            plugin.saveConfig();
            logger.info("Generated new RCON authentication token");
        } else {
            this.rconToken = configToken;
        }
        
        // Start cleanup thread
        new Thread(this::cleanupOldEntries).start();
    }
    
    /**
     * Validate a command with authentication and rate limiting
     * @param command Command to validate
     * @param token Authentication token
     * @param source Source of the command
     * @return true if the command is authorized, false otherwise
     */
    public boolean validateCommand(String command, String token, String source) {
        // Check authentication token
        if (!validateToken(token)) {
            logger.warning("Invalid authentication token from " + source);
            return false;
        }
        
        // Check rate limiting
        if (isRateLimited(source)) {
            logger.warning("Rate limit exceeded for " + source);
            return false;
        }
        
        // Log the command
        logCommand(command, source);
        
        return true;
    }
    
    /**
     * Check if a source is rate limited
     * @param source Source to check
     * @return true if rate limited, false otherwise
     */
    private boolean isRateLimited(String source) {
        long currentTime = System.currentTimeMillis();
        
        // Update last command time
        lastCommandTime.put(source, currentTime);
        
        // Check and update command count
        int count = commandCount.getOrDefault(source, 0);
        count++;
        commandCount.put(source, count);
        
        // If over limit, deny the request
        return count > RATE_LIMIT_MAX;
    }
    
    /**
     * Log a command for audit purposes
     * @param command Command to log
     * @param source Source of the command
     */
    private void logCommand(String command, String source) {
        CommandLog log = new CommandLog(command, source, System.currentTimeMillis());
        commandLogs.put(UUID.randomUUID().toString(), log);
        
        // Also log to console if debug is enabled
        if (plugin.getConfig().getBoolean("settings.debug", false)) {
            logger.info("Command executed: " + command + " from " + source);
        }
    }
    
    /**
     * Validate an authentication token
     * @param token Token to validate
     * @return true if valid, false otherwise
     */
    private boolean validateToken(String token) {
        if (token == null) return false;
        return token.equals(rconToken);
    }
    
    /**
     * Get the RCON authentication token
     * @return The token
     */
    public String getRconToken() {
        return rconToken;
    }
    
    /**
     * Generate a random token
     * @return A random token
     */
    private String generateRandomToken() {
        try {
            // Create a unique token based on current time and a random UUID
            String base = System.currentTimeMillis() + "_" + UUID.randomUUID().toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes());
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 32); // Use first 32 chars
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple UUID if SHA-256 is not available
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
    
    /**
     * Clean up old entries in the rate limiting and command logs
     */
    private void cleanupOldEntries() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(60000); // Cleanup every minute
                
                long currentTime = System.currentTimeMillis();
                
                // Clear old rate limit entries
                lastCommandTime.entrySet().removeIf(entry -> 
                    currentTime - entry.getValue() > RATE_LIMIT_WINDOW);
                commandCount.entrySet().removeIf(entry -> 
                    !lastCommandTime.containsKey(entry.getKey()));
                
                // Clean up old command logs (keep for 24 hours)
                commandLogs.entrySet().removeIf(entry -> 
                    currentTime - entry.getValue().timestamp > 86400000);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Get a summary of command activity
     * @return A summary of command activity
     */
    public Map<String, Object> getActivitySummary() {
        Map<String, Object> summary = new HashMap<>();
        
        Map<String, Integer> sourceCount = new HashMap<>();
        Map<String, Integer> commandTypeCount = new HashMap<>();
        
        for (CommandLog log : commandLogs.values()) {
            // Count by source
            sourceCount.put(log.source, sourceCount.getOrDefault(log.source, 0) + 1);
            
            // Count by command type
            String commandType = log.command.split(" ")[0];
            commandTypeCount.put(commandType, commandTypeCount.getOrDefault(commandType, 0) + 1);
        }
        
        summary.put("total_commands", commandLogs.size());
        summary.put("sources", sourceCount);
        summary.put("command_types", commandTypeCount);
        
        return summary;
    }
    
    /**
     * Simple class to store command logs
     */
    private static class CommandLog {
        public final String command;
        public final String source;
        public final long timestamp;
        
        public CommandLog(String command, String source, long timestamp) {
            this.command = command;
            this.source = source;
            this.timestamp = timestamp;
        }
    }
} 
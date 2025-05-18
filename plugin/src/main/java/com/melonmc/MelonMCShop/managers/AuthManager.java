package com.melonmc.MelonMCShop.managers;

import com.melonmc.MelonMCShop.MelonMCShop;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages authentication and security for API interactions
 */
public class AuthManager {
    private final MelonMCShop plugin;
    private final Map<UUID, Boolean> authenticatedUsers = new ConcurrentHashMap<>();
    private final Map<String, String> apiTokens = new HashMap<>();
    private File authFile;
    private FileConfiguration authConfig;
    private final SecureRandom random = new SecureRandom();
    private boolean operational = true;

    /**
     * Constructor
     * @param plugin MelonMCShop instance
     */
    public AuthManager(MelonMCShop plugin) {
        this.plugin = plugin;
        setupStorage();
        loadData();
        validateConfig();
    }

    /**
     * Setup storage files
     */
    private void setupStorage() {
        try {
            authFile = new File(plugin.getDataFolder(), "auth.yml");
            if (!authFile.exists()) {
                authFile.getParentFile().mkdirs();
                plugin.saveResource("auth.yml", false);
            }
            
            authConfig = YamlConfiguration.loadConfiguration(authFile);
        } catch (Exception e) {
            plugin.logError("Failed to setup auth storage", e);
            operational = false;
        }
    }

    /**
     * Validate and create default configuration
     */
    private void validateConfig() {
        try {
            // Ensure admin tokens exists
            ConfigurationSection tokenSection = authConfig.getConfigurationSection("api-tokens");
            if (tokenSection == null) {
                tokenSection = authConfig.createSection("api-tokens");
                String defaultToken = generateToken();
                tokenSection.set("default", hashPassword(defaultToken));
                
                plugin.getLogger().warning("Generated default API token: " + defaultToken);
                plugin.getLogger().warning("Please change this token in auth.yml for security reasons.");
                
                saveData();
            }
            
            // Load tokens into memory
            loadTokens();
            operational = true;
        } catch (Exception e) {
            plugin.logError("Failed to validate auth config", e);
            operational = false;
        }
    }

    /**
     * Load tokens into memory
     */
    private void loadTokens() {
        apiTokens.clear();
        
        ConfigurationSection tokenSection = authConfig.getConfigurationSection("api-tokens");
        if (tokenSection != null) {
            for (String key : tokenSection.getKeys(false)) {
                String hash = tokenSection.getString(key);
                if (hash != null && !hash.isEmpty()) {
                    apiTokens.put(key, hash);
                }
            }
        }
    }

    /**
     * Authenticate a player for admin operations
     * @param player Player to authenticate
     * @param password Admin password
     * @return true if authentication was successful, false otherwise
     */
    public boolean authenticatePlayer(Player player, String password) {
        if (player == null || password == null || password.isEmpty()) {
            return false;
        }
        
        // Check if password is correct
        String storedHash = authConfig.getString("admin-password");
        if (storedHash == null || storedHash.isEmpty()) {
            // No admin password set, use default from config.yml
            String defaultPass = plugin.getConfig().getString("security.default-admin-password", "admin");
            if (password.equals(defaultPass)) {
                authenticatedUsers.put(player.getUniqueId(), true);
                player.sendMessage(ChatColor.GREEN + "Authentication successful! However, you should set a custom admin password.");
                return true;
            }
            return false;
        }
        
        // Validate stored password hash
        if (validatePassword(password, storedHash)) {
            authenticatedUsers.put(player.getUniqueId(), true);
            player.sendMessage(ChatColor.GREEN + "Authentication successful!");
            return true;
        }
        
        return false;
    }

    /**
     * Check if a player is authenticated
     * @param player Player to check
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated(Player player) {
        if (player == null) {
            return false;
        }
        
        if (player.hasPermission("melonmc.admin")) {
            return true;  // Admins are always authenticated
        }
        
        return authenticatedUsers.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Revoke authentication for a player
     * @param player Player to log out
     */
    public void logout(Player player) {
        if (player != null) {
            authenticatedUsers.remove(player.getUniqueId());
        }
    }

    /**
     * Set a new admin password
     * @param player Player making the change (must be authenticated)
     * @param newPassword New password
     * @return true if password was changed, false otherwise
     */
    public boolean setAdminPassword(Player player, String newPassword) {
        if (!isAuthenticated(player)) {
            player.sendMessage(ChatColor.RED + "You must be authenticated to change the admin password!");
            return false;
        }
        
        if (newPassword == null || newPassword.length() < 6) {
            player.sendMessage(ChatColor.RED + "Password must be at least 6 characters long!");
            return false;
        }
        
        try {
            String hash = hashPassword(newPassword);
            authConfig.set("admin-password", hash);
            saveData();
            
            player.sendMessage(ChatColor.GREEN + "Admin password has been updated!");
            return true;
        } catch (Exception e) {
            plugin.logError("Failed to set admin password", e);
            player.sendMessage(ChatColor.RED + "Failed to update admin password!");
            return false;
        }
    }

    /**
     * Verify an API token
     * @param tokenName Token name
     * @param token Token value
     * @return true if valid, false otherwise
     */
    public boolean verifyApiToken(String tokenName, String token) {
        if (tokenName == null || token == null) {
            return false;
        }
        
        String storedHash = apiTokens.get(tokenName);
        if (storedHash == null) {
            return false;
        }
        
        return validatePassword(token, storedHash);
    }

    /**
     * Create a new API token
     * @param tokenName Name for the token
     * @return The generated token, null if failed
     */
    public String createApiToken(String tokenName) {
        if (tokenName == null || tokenName.isEmpty()) {
            return null;
        }
        
        try {
            String token = generateToken();
            String hash = hashPassword(token);
            
            ConfigurationSection tokenSection = authConfig.getConfigurationSection("api-tokens");
            if (tokenSection == null) {
                tokenSection = authConfig.createSection("api-tokens");
            }
            
            tokenSection.set(tokenName, hash);
            apiTokens.put(tokenName, hash);
            saveData();
            
            return token;
        } catch (Exception e) {
            plugin.logError("Failed to create API token: " + tokenName, e);
            return null;
        }
    }

    /**
     * Revoke an API token
     * @param tokenName Name of token to revoke
     * @return true if revoked, false otherwise
     */
    public boolean revokeApiToken(String tokenName) {
        if (tokenName == null || tokenName.isEmpty()) {
            return false;
        }
        
        try {
            ConfigurationSection tokenSection = authConfig.getConfigurationSection("api-tokens");
            if (tokenSection != null && tokenSection.contains(tokenName)) {
                tokenSection.set(tokenName, null);
                apiTokens.remove(tokenName);
                saveData();
                return true;
            }
            return false;
        } catch (Exception e) {
            plugin.logError("Failed to revoke API token: " + tokenName, e);
            return false;
        }
    }

    /**
     * Generate a secure random token
     * @return A random token
     */
    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return new BigInteger(1, bytes).toString(16);
    }

    /**
     * Hash a password using PBKDF2
     * @param password Password to hash
     * @return Hashed password
     */
    private String hashPassword(String password) {
        try {
            int iterations = 1000;
            char[] chars = password.toCharArray();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            
            PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 64 * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            
            return iterations + ":" + toHex(salt) + ":" + toHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /**
     * Validate a password against a stored hash
     * @param password Password to check
     * @param storedHash Stored hash
     * @return true if valid, false otherwise
     */
    private boolean validatePassword(String password, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = fromHex(parts[1]);
            byte[] hash = fromHex(parts[2]);
            
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, hash.length * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] testHash = skf.generateSecret(spec).getEncoded();
            
            int diff = hash.length ^ testHash.length;
            for (int i = 0; i < hash.length && i < testHash.length; i++) {
                diff |= hash[i] ^ testHash[i];
            }
            
            return diff == 0;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            plugin.logError("Failed to validate password", e);
            return false;
        }
    }

    /**
     * Convert bytes to hex string
     */
    private String toHex(byte[] array) {
        BigInteger bi = new BigInteger(1, array);
        String hex = bi.toString(16);
        int paddingLength = (array.length * 2) - hex.length();
        if (paddingLength > 0) {
            return String.format("%0" + paddingLength + "d", 0) + hex;
        } else {
            return hex;
        }
    }

    /**
     * Convert hex string to bytes
     */
    private byte[] fromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    /**
     * Load authentication data
     */
    public void loadData() {
        try {
            authenticatedUsers.clear();
            authConfig = YamlConfiguration.loadConfiguration(authFile);
            operational = true;
        } catch (Exception e) {
            plugin.logError("Failed to load auth data", e);
            operational = false;
        }
    }

    /**
     * Save authentication data
     */
    public void saveData() {
        try {
            authConfig.save(authFile);
            operational = true;
        } catch (Exception e) {
            plugin.logError("Failed to save auth data", e);
            operational = false;
        }
    }
    
    /**
     * Check if manager is operational
     * @return true if operational, false otherwise
     */
    public boolean isOperational() {
        return operational;
    }
} 
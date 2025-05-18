package com.melonmc.MelonMCShop.utils;

import com.melonmc.MelonMCShop.MelonMCShop;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Handles communication with the Discord bot
 */
public class DiscordBridge {
    private final MelonMCShop plugin;
    private final String apiUrl;
    private final String apiToken;
    private boolean connected = false;
    private int failedAttempts = 0;
    private long lastPing = 0;
    private BukkitTask pingTask;
    
    /**
     * Constructor for DiscordBridge
     * @param plugin MelonMCShop instance
     */
    public DiscordBridge(MelonMCShop plugin) {
        this.plugin = plugin;
        
        // Load config values
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("discord");
        
        if (config == null) {
            // Create default config
            config = plugin.getConfig().createSection("discord");
            config.set("api-url", "http://localhost:3000/api");
            config.set("api-token", "change-this-to-your-secure-token");
            config.set("enabled", true);
            config.set("ping-interval", 60);
            plugin.saveConfig();
            
            plugin.getLogger().warning("Created default Discord bridge configuration. Please update your config.yml with correct values.");
            this.apiUrl = "http://localhost:3000/api";
            this.apiToken = "change-this-to-your-secure-token";
            connected = false;
        } else {
            this.apiUrl = config.getString("api-url", "http://localhost:3000/api");
            this.apiToken = config.getString("api-token", "");
            
            // Only attempt connection if enabled
            if (config.getBoolean("enabled", true)) {
                // Run initial connection async
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::connect);
                
                // Start ping task
                int pingInterval = config.getInt("ping-interval", 60);
                startPingTask(pingInterval);
            } else {
                plugin.getLogger().info("Discord bridge is disabled in config.");
            }
        }
    }
    
    /**
     * Connect to the Discord bot API
     * @return true if successful, false otherwise
     */
    public boolean connect() {
        try {
            // Don't attempt to connect if token is default
            if ("change-this-to-your-secure-token".equals(apiToken)) {
                plugin.getLogger().warning("Discord bridge token is still default. Please update your config!");
                connected = false;
                return false;
            }
            
            // Send ping request to API
            Map<String, Object> data = new HashMap<>();
            data.put("action", "ping");
            data.put("server_id", plugin.getServer().getPort());
            
            CompletableFuture<Map<String, Object>> response = sendRequest("ping", data);
            
            // Wait for response with timeout
            Map<String, Object> result = response.get(5, TimeUnit.SECONDS);
            
            if (result != null && "pong".equals(result.get("status"))) {
                plugin.getLogger().info("Successfully connected to Discord bot!");
                connected = true;
                failedAttempts = 0;
                lastPing = System.currentTimeMillis();
                return true;
            } else {
                plugin.getLogger().warning("Failed to connect to Discord bot: Invalid response");
                connected = false;
                failedAttempts++;
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to connect to Discord bot: " + e.getMessage());
            connected = false;
            failedAttempts++;
            return false;
        }
    }
    
    /**
     * Check if the bridge is connected
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected && (System.currentTimeMillis() - lastPing < 300000); // 5 minutes timeout
    }
    
    /**
     * Try to reconnect to the Discord bot
     */
    public void reconnect() {
        // Exponential backoff: wait longer after each failed attempt
        // Max backoff of 5 minutes (after 5 failed attempts)
        int backoffSeconds = Math.min(30 * (int)Math.pow(2, failedAttempts), 300);
        
        plugin.getLogger().info("Scheduling reconnect attempt to Discord bot in " + backoffSeconds + " seconds");
        
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::connect, backoffSeconds * 20L);
    }
    
    /**
     * Start the ping task
     * @param intervalSeconds Interval in seconds
     */
    private void startPingTask(int intervalSeconds) {
        if (pingTask != null) {
            pingTask.cancel();
        }
        
        pingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                if (!isConnected()) {
                    connect();
                } else {
                    ping();
                }
            } catch (Exception e) {
                plugin.logError("Error in Discord ping task", e);
            }
        }, 20L * 60, 20L * intervalSeconds);
    }
    
    /**
     * Send a simple ping to keep the connection alive
     */
    private void ping() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("action", "ping");
            data.put("server_id", plugin.getServer().getPort());
            data.put("online_players", Bukkit.getOnlinePlayers().size());
            
            sendRequest("ping", data).thenAccept(response -> {
                if (response != null && "pong".equals(response.get("status"))) {
                    lastPing = System.currentTimeMillis();
                    connected = true;
                } else {
                    connected = false;
                }
            }).exceptionally(e -> {
                connected = false;
                return null;
            });
        } catch (Exception e) {
            plugin.logError("Error pinging Discord bot", e);
            connected = false;
        }
    }
    
    /**
     * Notifies the Discord bot about a purchase completion
     * @param playerName The player name who received the purchase
     * @param playerUUID The player UUID
     * @param itemType Type of item (rank, coins)
     * @param itemName Name of the item or amount
     * @param gamemode Gamemode the purchase is for
     * @param transactionId Transaction ID
     * @return Future with response data
     */
    public CompletableFuture<Map<String, Object>> notifyPurchaseComplete(String playerName, String playerUUID, 
                                                        String itemType, String itemName, 
                                                        String gamemode, String transactionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "purchase_complete");
        data.put("player_uuid", playerUUID);
        data.put("player_name", playerName);
        data.put("item_type", itemType);
        data.put("item_name", itemName);
        data.put("gamemode", gamemode);
        data.put("transaction_id", transactionId);
        data.put("timestamp", System.currentTimeMillis());
        
        return sendRequest("purchase", data);
    }
    
    /**
     * Notifies the Discord bot about a purchase completion
     * @param player The player who received the purchase
     * @param itemType Type of item (rank, coins)
     * @param itemName Name of the item or amount
     * @param gamemode Gamemode the purchase is for
     * @param transactionId Transaction ID
     * @return Future with response data
     */
    public CompletableFuture<Map<String, Object>> notifyPurchaseComplete(Player player, String itemType, 
                                                         String itemName, String gamemode, 
                                                         String transactionId) {
        return notifyPurchaseComplete(
            player.getName(),
            player.getUniqueId().toString(),
            itemType,
            itemName,
            gamemode,
            transactionId
        );
    }
    
    /**
     * Verify a player by their Discord ID
     * @param playerUUID Player's UUID
     * @param discordId Discord ID
     * @return Future with verification result
     */
    public CompletableFuture<Boolean> verifyPlayer(UUID playerUUID, String discordId) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "verify");
        data.put("player_uuid", playerUUID.toString());
        data.put("discord_id", discordId);
        
        return sendRequest("verify", data).thenApply(response -> {
            if (response != null && "success".equals(response.get("status"))) {
                return true;
            }
            return false;
        }).exceptionally(e -> {
            plugin.logError("Error verifying player with Discord", e);
            return false;
        });
    }
    
    /**
     * Send a request to the Discord bot API
     * @param endpoint API endpoint
     * @param data Request data
     * @return Future with response data
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<Map<String, Object>> sendRequest(String endpoint, Map<String, Object> data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Prepare connection
                URL url = new URL(apiUrl + "/" + endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiToken);
                conn.setRequestProperty("User-Agent", "MelonMCShop/" + plugin.getDescription().getVersion());
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                // Convert data to JSON and send
                String jsonData = convertToJson(data);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonData.getBytes(StandardCharsets.UTF_8));
                }
                
                // Read response
                int responseCode = conn.getResponseCode();
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        return parseJsonResponse(response.toString());
                    }
                } else {
                    plugin.getLogger().warning("Discord API request failed with code " + responseCode);
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        plugin.getLogger().warning("Error response: " + errorResponse.toString());
                    } catch (Exception e) {
                        // Ignore errors reading the error
                    }
                    return null;
                }
            } catch (Exception e) {
                plugin.logError("Failed to send request to Discord bot", e);
                return null;
            }
        });
    }
    
    /**
     * Simple method to convert a Map to a JSON string
     * (In a real implementation, you'd use Jackson, Gson, or another JSON library)
     */
    private String convertToJson(Map<String, Object> data) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Simple method to parse a JSON string to a Map
     * (In a real implementation, you'd use Jackson, Gson, or another JSON library)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String json) {
        // This is a very simplified JSON parser for demonstration
        // In a real plugin, you should use a proper JSON library
        Map<String, Object> result = new HashMap<>();
        
        // Extract key-value pairs (extremely basic, doesn't handle nested objects properly)
        String innerJson = json.trim();
        if (innerJson.startsWith("{") && innerJson.endsWith("}")) {
            innerJson = innerJson.substring(1, innerJson.length() - 1);
            
            String[] pairs = innerJson.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    if (key.startsWith("\"") && key.endsWith("\"")) {
                        key = key.substring(1, key.length() - 1);
                    }
                    
                    String valueStr = keyValue[1].trim();
                    Object value;
                    
                    if (valueStr.equals("null")) {
                        value = null;
                    } else if (valueStr.equals("true")) {
                        value = true;
                    } else if (valueStr.equals("false")) {
                        value = false;
                    } else if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
                        value = valueStr.substring(1, valueStr.length() - 1);
                    } else {
                        try {
                            value = Integer.parseInt(valueStr);
                        } catch (NumberFormatException e1) {
                            try {
                                value = Double.parseDouble(valueStr);
                            } catch (NumberFormatException e2) {
                                value = valueStr;
                            }
                        }
                    }
                    
                    result.put(key, value);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Shut down the Discord bridge
     */
    public void shutdown() {
        if (pingTask != null) {
            pingTask.cancel();
        }
        
        // Notify Discord that we're shutting down
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("action", "shutdown");
            data.put("server_id", plugin.getServer().getPort());
            
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl + "/status").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
            conn.setDoOutput(true);
            conn.setConnectTimeout(2000); // Short timeout for shutdown
            
            String jsonData = convertToJson(data);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonData.getBytes(StandardCharsets.UTF_8));
            }
            
            // Don't care about the response, just send it
            conn.getResponseCode();
            
        } catch (Exception e) {
            // Ignore exceptions during shutdown
        }
        
        connected = false;
    }
    
    /**
     * Check if a player has a pending purchase and process it
     * @param player Player to check
     * @return CompletableFuture with true if purchase was processed
     */
    public CompletableFuture<Boolean> checkPendingPurchase(Player player) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "check_pending");
        data.put("player_uuid", player.getUniqueId().toString());
        data.put("player_name", player.getName());
        
        return sendRequest("pending", data).thenApply(response -> {
            if (response != null && "pending_found".equals(response.get("status"))) {
                // Process the pending purchase
                String type = (String) response.get("type");
                String item = (String) response.get("item");
                String gamemode = (String) response.get("gamemode");
                String transactionId = (String) response.get("transaction_id");
                
                switch (type) {
                    case "rank":
                        return plugin.getRankManager().giveRank(player, gamemode, item);
                    case "coins":
                        try {
                            int amount = Integer.parseInt(item);
                            return plugin.getCoinsManager().giveCoins(player, amount, gamemode);
                        } catch (NumberFormatException e) {
                            plugin.logError("Invalid coin amount in pending purchase", e);
                            return false;
                        }
                    default:
                        plugin.getLogger().warning("Unknown purchase type: " + type);
                        return false;
                }
            }
            return false;
        }).exceptionally(e -> {
            plugin.logError("Error checking pending purchases", e);
            return false;
        });
    }
} 
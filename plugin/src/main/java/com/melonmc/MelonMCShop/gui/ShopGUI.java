package com.melonmc.MelonMCShop.gui;

import com.melonmc.MelonMCShop.MelonMCShop;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Interactive GUI shop for players to view ranks and coin packages
 */
public class ShopGUI implements Listener {
    private final MelonMCShop plugin;
    private static final String MAIN_MENU_TITLE = "§d§lMelonMC Shop";
    private static final String RANKS_MENU_TITLE = "§b§lRanks Shop";
    private static final String COINS_MENU_TITLE = "§e§lCoins Shop";
    
    // Cache of open inventories and their types
    private final Map<Player, String> openInventories = new HashMap<>();
    
    // Item positions
    private static final int RANKS_POSITION = 11;
    private static final int COINS_POSITION = 15;
    private static final int BACK_POSITION = 26;
    
    private final Map<String, Map<Integer, ShopItem>> shopItems = new HashMap<>();
    private final Map<UUID, String> currentCategory = new HashMap<>();
    
    public ShopGUI(MelonMCShop plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadShopItems();
    }
    
    /**
     * Load shop items from config
     */
    private void loadShopItems() {
        shopItems.clear();
        ConfigurationSection shopSection = plugin.getConfig().getConfigurationSection("shop");
        
        if (shopSection == null) {
            // Create default shop if none exists
            createDefaultShop();
            shopSection = plugin.getConfig().getConfigurationSection("shop");
        }
        
        for (String category : shopSection.getKeys(false)) {
            ConfigurationSection categorySection = shopSection.getConfigurationSection(category);
            if (categorySection == null) continue;
            
            Map<Integer, ShopItem> itemMap = new HashMap<>();
            for (String key : categorySection.getKeys(false)) {
                ConfigurationSection itemSection = categorySection.getConfigurationSection(key);
                if (itemSection == null) continue;
                
                try {
                    String name = ChatColor.translateAlternateColorCodes('&', itemSection.getString("name", "Unknown"));
                    String type = itemSection.getString("type", "rank");
                    Material material;
                    try {
                        material = Material.valueOf(itemSection.getString("material", "PAPER").toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = Material.PAPER;
                    }
                    
                    int price = itemSection.getInt("price", 1000);
                    int slot = itemSection.getInt("slot", -1);
                    
                    List<String> description = new ArrayList<>();
                    if (itemSection.isList("description")) {
                        description = itemSection.getStringList("description");
                    } else if (itemSection.isString("description")) {
                        description.add(itemSection.getString("description", ""));
                    }
                    
                    ShopItem item = new ShopItem(key, name, type, material, price);
                    item.setDescription(description);
                    
                    // Additional properties based on type
                    if ("rank".equals(type)) {
                        item.setRankName(itemSection.getString("rank", "default"));
                        item.setGamemode(itemSection.getString("gamemode", "survival"));
                    } else if ("item".equals(type)) {
                        Material itemType = Material.valueOf(itemSection.getString("item-type", "DIAMOND").toUpperCase());
                        item.setItemType(itemType);
                        item.setAmount(itemSection.getInt("amount", 1));
                    } else if ("command".equals(type)) {
                        item.setCommand(itemSection.getString("command", ""));
                    } else if ("permission".equals(type)) {
                        item.setPermission(itemSection.getString("permission", ""));
                    }
                    
                    if (slot >= 0) {
                        itemMap.put(slot, item);
                    }
                } catch (Exception e) {
                    plugin.logError("Failed to load shop item: " + key, e);
                }
            }
            
            shopItems.put(category.toLowerCase(), itemMap);
        }
    }
    
    /**
     * Create a default shop configuration
     */
    private void createDefaultShop() {
        plugin.getLogger().info("Creating default shop configuration...");
        
        ConfigurationSection shopSection = plugin.getConfig().createSection("shop");
        
        // Ranks category
        ConfigurationSection ranksSection = shopSection.createSection("ranks");
        
        // VIP rank
        ConfigurationSection vipSection = ranksSection.createSection("vip");
        vipSection.set("name", "&aVIP &fRank");
        vipSection.set("type", "rank");
        vipSection.set("material", "EMERALD");
        vipSection.set("price", 1000);
        vipSection.set("slot", 10);
        vipSection.set("description", Arrays.asList("&7Purchase the &aVIP &7rank!", "&7Benefits:", "&7- Custom chat prefix", "&7- Access to /fly"));
        vipSection.set("rank", "vip");
        vipSection.set("gamemode", "survival");
        
        // VIP+ rank
        ConfigurationSection vipPlusSection = ranksSection.createSection("vip_plus");
        vipPlusSection.set("name", "&bVIP+ &fRank");
        vipPlusSection.set("type", "rank");
        vipPlusSection.set("material", "DIAMOND");
        vipPlusSection.set("price", 2500);
        vipPlusSection.set("slot", 12);
        vipPlusSection.set("description", Arrays.asList("&7Purchase the &bVIP+ &7rank!", "&7Benefits:", "&7- All VIP benefits", "&7- 5 player warps", "&7- Colored chat messages"));
        vipPlusSection.set("rank", "vip_plus");
        vipPlusSection.set("gamemode", "survival");
        
        // MVP rank
        ConfigurationSection mvpSection = ranksSection.createSection("mvp");
        mvpSection.set("name", "&6MVP &fRank");
        mvpSection.set("type", "rank");
        mvpSection.set("material", "GOLD_BLOCK");
        mvpSection.set("price", 5000);
        mvpSection.set("slot", 14);
        mvpSection.set("description", Arrays.asList("&7Purchase the &6MVP &7rank!", "&7Benefits:", "&7- All VIP+ benefits", "&7- /nick command", "&7- Special particles"));
        mvpSection.set("rank", "mvp");
        mvpSection.set("gamemode", "survival");
        
        // MVP+ rank
        ConfigurationSection mvpPlusSection = ranksSection.createSection("mvp_plus");
        mvpPlusSection.set("name", "&cMVP+ &fRank");
        mvpPlusSection.set("type", "rank");
        mvpPlusSection.set("material", "NETHERITE_INGOT");
        mvpPlusSection.set("price", 10000);
        mvpPlusSection.set("slot", 16);
        mvpPlusSection.set("description", Arrays.asList("&7Purchase the &cMVP+ &7rank!", "&7Benefits:", "&7- All MVP benefits", "&7- Custom join messages", "&7- Special cosmetics"));
        mvpPlusSection.set("rank", "mvp_plus");
        mvpPlusSection.set("gamemode", "survival");
        
        // Items category
        ConfigurationSection itemsSection = shopSection.createSection("items");
        
        // Diamond set
        ConfigurationSection diamondSetSection = itemsSection.createSection("diamond_set");
        diamondSetSection.set("name", "&bDiamond Set");
        diamondSetSection.set("type", "command");
        diamondSetSection.set("material", "DIAMOND_CHESTPLATE");
        diamondSetSection.set("price", 500);
        diamondSetSection.set("slot", 10);
        diamondSetSection.set("description", "&7Get a full diamond armor set!");
        diamondSetSection.set("command", "give %player% diamond_helmet 1\ngive %player% diamond_chestplate 1\ngive %player% diamond_leggings 1\ngive %player% diamond_boots 1");
        
        // Diamond sword
        ConfigurationSection diamondSwordSection = itemsSection.createSection("diamond_sword");
        diamondSwordSection.set("name", "&bDiamond Sword");
        diamondSwordSection.set("type", "item");
        diamondSwordSection.set("material", "DIAMOND_SWORD");
        diamondSwordSection.set("price", 200);
        diamondSwordSection.set("slot", 12);
        diamondSwordSection.set("description", "&7A powerful diamond sword!");
        diamondSwordSection.set("item-type", "DIAMOND_SWORD");
        diamondSwordSection.set("amount", 1);
        
        // Save config
        plugin.saveConfig();
    }
    
    /**
     * Open the main shop menu for a player
     * @param player The player to open the menu for
     */
    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);
        
        // Create category items
        for (String category : shopItems.keySet()) {
            ItemStack item = createCategoryItem(category);
            
            // Place based on order
            int slot;
            switch (category.toLowerCase()) {
                case "ranks": slot = 11; break;
                case "items": slot = 13; break;
                case "permissions": slot = 15; break;
                default: slot = shopItems.keySet().size() % 9;
            }
            
            inventory.setItem(slot, item);
        }
        
        // Help item
        ItemStack helpItem = new ItemStack(Material.BOOK);
        ItemMeta helpMeta = helpItem.getItemMeta();
        if (helpMeta != null) {
            helpMeta.setDisplayName(ChatColor.YELLOW + "Help");
            List<String> lore = Arrays.asList(
                ChatColor.GRAY + "Click on a category to",
                ChatColor.GRAY + "view available items.",
                "",
                ChatColor.GRAY + "Your Balance: " + ChatColor.GREEN + 
                    (plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0) + " coins"
            );
            helpMeta.setLore(lore);
            helpItem.setItemMeta(helpMeta);
        }
        inventory.setItem(26, helpItem);
        
        player.openInventory(inventory);
        openInventories.put(player, "main");
    }
    
    /**
     * Create a category item for the main menu
     * @param category Category name
     * @return ItemStack for the category
     */
    private ItemStack createCategoryItem(String category) {
        Material material;
        String displayName;
        List<String> lore = new ArrayList<>();
        
        switch (category.toLowerCase()) {
            case "ranks":
                material = Material.DIAMOND;
                displayName = ChatColor.AQUA + "Ranks";
                lore.add(ChatColor.GRAY + "Click to view available ranks");
                break;
            case "items":
                material = Material.CHEST;
                displayName = ChatColor.GOLD + "Items";
                lore.add(ChatColor.GRAY + "Click to view purchasable items");
                break;
            case "permissions":
                material = Material.PAPER;
                displayName = ChatColor.YELLOW + "Permissions";
                lore.add(ChatColor.GRAY + "Click to view special permissions");
                break;
            default:
                material = Material.STONE;
                displayName = ChatColor.WHITE + category;
                lore.add(ChatColor.GRAY + "Click to view options");
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Open a category page
     * @param player The player
     * @param category The category to open
     */
    public void openCategoryPage(Player player, String category) {
        String categoryLower = category.toLowerCase();
        Map<Integer, ShopItem> items = shopItems.get(categoryLower);
        
        if (items == null || items.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This shop category is empty!");
            return;
        }
        
        String title;
        switch (categoryLower) {
            case "ranks": title = ChatColor.AQUA + "Ranks Shop"; break;
            case "items": title = ChatColor.GOLD + "Items Shop"; break;
            case "permissions": title = ChatColor.YELLOW + "Permissions Shop"; break;
            default: title = ChatColor.GREEN + category + " Shop";
        }
        
        Inventory gui = Bukkit.createInventory(null, 36, title);
        
        // Add items to gui
        for (Map.Entry<Integer, ShopItem> entry : items.entrySet()) {
            ShopItem shopItem = entry.getValue();
            ItemStack item = createShopItemStack(shopItem);
            gui.setItem(entry.getKey(), item);
        }
        
        // Add back button
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back to Main Menu");
            backButton.setItemMeta(backMeta);
        }
        gui.setItem(31, backButton);
        
        // Add balance display
        ItemStack balanceItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta balanceMeta = balanceItem.getItemMeta();
        if (balanceMeta != null) {
            balanceMeta.setDisplayName(ChatColor.YELLOW + "Your Balance");
            List<String> lore = Collections.singletonList(
                ChatColor.GREEN + "" + (plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0) + " coins"
            );
            balanceMeta.setLore(lore);
            balanceItem.setItemMeta(balanceMeta);
        }
        gui.setItem(35, balanceItem);
        
        player.openInventory(gui);
        currentCategory.put(player.getUniqueId(), categoryLower);
    }
    
    /**
     * Create an ItemStack for a shop item
     * @param shopItem The shop item
     * @return The ItemStack
     */
    private ItemStack createShopItemStack(ShopItem shopItem) {
        ItemStack item = new ItemStack(shopItem.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', shopItem.getName()));
            
            List<String> lore = new ArrayList<>();
            for (String line : shopItem.getDescription()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            
            lore.add("");
            lore.add(ChatColor.YELLOW + "Price: " + ChatColor.GREEN + shopItem.getPrice() + " coins");
            lore.add(ChatColor.GRAY + "Click to purchase!");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Handle inventory clicks in the shop GUI
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String inventoryType = openInventories.get(player);
        
        if (inventoryType == null) return;
        
        String title = event.getView().getTitle();
        
        if (title.startsWith(MAIN_MENU_TITLE) || 
            title.startsWith(RANKS_MENU_TITLE) || 
            title.startsWith(COINS_MENU_TITLE)) {
            
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null) return;
            
            // Handle main menu clicks
            if (inventoryType.equals("main")) {
                if (event.getSlot() == RANKS_POSITION) {
                    // Default to the first gamemode in config
                    String defaultGamemode = plugin.getConfig().getString("settings.default-gamemode", "survival");
                    openCategoryPage(player, "ranks");
                } else if (event.getSlot() == COINS_POSITION) {
                    String defaultGamemode = plugin.getConfig().getString("settings.default-gamemode", "survival");
                    openCategoryPage(player, "items");
                }
            } 
            // Handle back button in submenus
            else if (event.getSlot() == BACK_POSITION) {
                openMainMenu(player);
            }
            // Handle rank/coin clicks - just show info message
            else if (event.getCurrentItem().getItemMeta() != null && 
                     event.getCurrentItem().getItemMeta().hasDisplayName()) {
                player.sendMessage("§a§lMelonMC §8» §fTo purchase this item, go to our Discord server and use the §e/bill §fcommand!");
                player.closeInventory();
            }
        }
    }
    
    /**
     * Handle a click on the shop GUI
     * @param player The player who clicked
     * @param slot The slot that was clicked
     * @param inventory The inventory title
     * @return true if the click was handled, false otherwise
     */
    public boolean handleClick(Player player, int slot, String inventory) {
        if (inventory.equals(MAIN_MENU_TITLE) || 
            inventory.equals(RANKS_MENU_TITLE) || 
            inventory.equals(COINS_MENU_TITLE)) {
            // Main menu
            if (slot == RANKS_POSITION) {
                openCategoryPage(player, "ranks");
                return true;
            } else if (slot == COINS_POSITION) {
                openCategoryPage(player, "items");
                return true;
            }
        } else if (inventory.contains("Shop")) {
            // Category page
            if (slot == BACK_POSITION) {
                // Back button
                openMainMenu(player);
                return true;
            } else if (slot == 35) {
                // Balance display - do nothing
                return true;
            } else {
                // Try to purchase the item
                return handlePurchase(player, slot);
            }
        }
        
        return false;
    }
    
    /**
     * Handle a purchase attempt
     * @param player The player
     * @param slot The slot that was clicked
     * @return true if the purchase was handled, false otherwise
     */
    private boolean handlePurchase(Player player, int slot) {
        String category = currentCategory.get(player.getUniqueId());
        if (category == null) return false;
        
        Map<Integer, ShopItem> items = shopItems.get(category);
        if (items == null) return false;
        
        ShopItem item = items.get(slot);
        if (item == null) return false;
        
        // Check if player has enough money
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Economy is not available!");
            return true;
        }
        
        int price = item.getPrice();
        if (economy.getBalance(player) < price) {
            player.sendMessage(ChatColor.RED + "You don't have enough coins! You need " + price + " coins.");
            return true;
        }
        
        // Process the purchase
        boolean success = economy.withdrawPlayer(player, price).transactionSuccess();
        if (!success) {
            player.sendMessage(ChatColor.RED + "Failed to process payment. Please try again.");
            return true;
        }
        
        // Give the item based on its type
        boolean itemGiven = false;
        switch (item.getType().toLowerCase()) {
            case "rank":
                itemGiven = plugin.getRankManager().giveRank(player, item.getGamemode(), item.getRankName());
                break;
                
            case "item":
                ItemStack itemStack = new ItemStack(item.getItemType(), item.getAmount());
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
                itemGiven = leftover.isEmpty();
                if (!itemGiven) {
                    // Refund if inventory is full
                    economy.depositPlayer(player, price);
                    player.sendMessage(ChatColor.RED + "Your inventory is full! Purchase refunded.");
                }
                break;
                
            case "command":
                String command = item.getCommand()
                        .replace("%player%", player.getName())
                        .replace("%uuid%", player.getUniqueId().toString());
                
                try {
                    for (String cmd : command.split("\n")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.trim());
                    }
                    itemGiven = true;
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Error executing command. Please contact an admin.");
                    economy.depositPlayer(player, price);
                    plugin.logError("Failed to execute command for shop purchase", e);
                }
                break;
                
            case "permission":
                if (plugin.getPermissions() != null) {
                    try {
                        plugin.getPermissions().playerAdd(player, item.getPermission());
                        itemGiven = true;
                    } catch (Exception e) {
                        economy.depositPlayer(player, price);
                        plugin.logError("Failed to give permission for shop purchase", e);
                    }
                }
                break;
        }
        
        if (itemGiven) {
            player.sendMessage(ChatColor.GREEN + "You purchased " + ChatColor.translateAlternateColorCodes('&', item.getName()) + 
                    ChatColor.GREEN + " for " + price + " coins!");
            player.closeInventory();
            
            // Notify Discord if enabled
            if (plugin.getDiscordBridge() != null && plugin.getDiscordBridge().isConnected()) {
                plugin.getDiscordBridge().notifyPurchaseComplete(
                    player, item.getType(), item.getId(), category, 
                    UUID.randomUUID().toString().substring(0, 8)
                );
            }
        }
        
        return true;
    }
    
    /**
     * Clean up when a player quits
     * @param player The player who quit
     */
    public void removePlayer(Player player) {
        openInventories.remove(player);
    }
    
    /**
     * ShopItem class to store item data
     */
    private static class ShopItem {
        private final String id;
        private final String name;
        private final String type;
        private final Material material;
        private final int price;
        private List<String> description = new ArrayList<>();
        private String rankName = "";
        private String gamemode = "survival";
        private Material itemType = Material.DIAMOND;
        private int amount = 1;
        private String command = "";
        private String permission = "";
        
        public ShopItem(String id, String name, String type, Material material, int price) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.material = material;
            this.price = price;
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public String getType() {
            return type;
        }
        
        public Material getMaterial() {
            return material;
        }
        
        public int getPrice() {
            return price;
        }
        
        public List<String> getDescription() {
            return description;
        }
        
        public void setDescription(List<String> description) {
            this.description = description;
        }
        
        public String getRankName() {
            return rankName;
        }
        
        public void setRankName(String rankName) {
            this.rankName = rankName;
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
        
        public int getAmount() {
            return amount;
        }
        
        public void setAmount(int amount) {
            this.amount = amount;
        }
        
        public String getCommand() {
            return command;
        }
        
        public void setCommand(String command) {
            this.command = command;
        }
        
        public String getPermission() {
            return permission;
        }
        
        public void setPermission(String permission) {
            this.permission = permission;
        }
    }
} 
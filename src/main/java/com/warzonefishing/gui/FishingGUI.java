package com.warzonefishing.gui;

import com.warzonefishing.WarzoneFishing;
import com.warzonefishing.hooks.HeadHuntingHook;
import com.warzonefishing.models.FishingReward;
import com.warzonefishing.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Fish Encyclopedia GUI
 * Shows all catchable fish organized by rarity with prices and chances
 */
public class FishingGUI implements Listener {
    
    private final WarzoneFishing plugin;
    private final String MAIN_TITLE = MessageUtils.color("&b&lWarzone Fishing");
    private final String RARITY_TITLE_PREFIX = MessageUtils.color("&b&l");
    
    // Track which GUI players have open
    private final Map<UUID, String> openGUIs = new HashMap<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    
    // Rarity order for display
    private final List<String> RARITY_ORDER = Arrays.asList(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"
    );
    
    // Glass pane colors by rarity
    private final Map<String, Short> RARITY_GLASS = new HashMap<String, Short>() {{
        put("COMMON", (short) 7);      // Gray
        put("UNCOMMON", (short) 5);    // Lime
        put("RARE", (short) 9);        // Cyan
        put("EPIC", (short) 10);       // Purple
        put("LEGENDARY", (short) 1);   // Orange
    }};
    
    public FishingGUI(WarzoneFishing plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Open the main encyclopedia menu
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MAIN_TITLE);
        
        // Fill with cyan glass
        ItemStack glass = createGlass((short) 9, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }
        
        // Title item (center top)
        ItemStack titleItem = new ItemStack(Material.FISHING_ROD);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.setDisplayName(MessageUtils.color("&b&lWarzone Fishing Encyclopedia"));
        titleMeta.setLore(Arrays.asList(
                MessageUtils.color("&7Browse all catchable fish and rewards!"),
                "",
                MessageUtils.color("&7Click a rarity category to view items.")
        ));
        titleItem.setItemMeta(titleMeta);
        inv.setItem(4, titleItem);
        
        // Calculate total weight for percentage display
        double totalWeight = plugin.getRewardManager().getTotalWeight();
        
        // Rarity category slots (row 2-3, spaced out)
        int[] raritySlots = {20, 21, 22, 23, 24};
        
        for (int i = 0; i < RARITY_ORDER.size(); i++) {
            String rarity = RARITY_ORDER.get(i);
            List<FishingReward> rewards = plugin.getRewardManager().getRewardsByRarity(rarity);
            
            // Calculate total chance for this rarity
            double rarityChance = 0;
            for (FishingReward r : rewards) {
                rarityChance += r.getChance();
            }
            double percentage = (rarityChance / totalWeight) * 100;
            
            ItemStack categoryItem = createRarityItem(rarity, rewards.size(), percentage);
            inv.setItem(raritySlots[i], categoryItem);
        }
        
        // Stats/Player info item (bottom left)
        HeadHuntingHook hook = plugin.getHeadHuntingHook();
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(MessageUtils.color("&e&lYour Stats"));
        
        List<String> statsLore = new ArrayList<>();
        statsLore.add(MessageUtils.color("&7Total Rewards: &b" + plugin.getRewardManager().getRewardCount()));
        
        if (hook.isEnabled()) {
            int playerLevel = hook.getPlayerLevel(player);
            statsLore.add(MessageUtils.color("&7Your Level: &a" + playerLevel));
            
            if (hook.hasGuardianMask(player)) {
                int luckBonus = hook.getFishingLuckBonus(player);
                statsLore.add("");
                statsLore.add(MessageUtils.color("&d⚡ Guardian Mask Equipped!"));
                statsLore.add(MessageUtils.color("&7Rare+ Luck Bonus: &a+" + luckBonus + "%"));
            }
            
            statsLore.add("");
            statsLore.add(MessageUtils.color("&7&oCatch rates personalized to your level"));
        } else {
            statsLore.add("");
            statsLore.add(MessageUtils.color("&7&oInstall HeadHunting for level bonuses!"));
        }
        
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        inv.setItem(48, statsItem);
        
        // All fish button (bottom right)
        ItemStack allItem = new ItemStack(Material.CHEST);
        ItemMeta allMeta = allItem.getItemMeta();
        allMeta.setDisplayName(MessageUtils.color("&f&lView All"));
        allMeta.setLore(Arrays.asList(
                MessageUtils.color("&7Click to see all rewards"),
                MessageUtils.color("&7in one list.")
        ));
        allItem.setItemMeta(allMeta);
        inv.setItem(50, allItem);
        
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(MessageUtils.color("&c&lClose"));
        closeItem.setItemMeta(closeMeta);
        inv.setItem(49, closeItem);
        
        player.openInventory(inv);
        openGUIs.put(player.getUniqueId(), "main");
    }
    
    /**
     * Open a rarity-specific page
     */
    public void openRarityPage(Player player, String rarity, int page) {
        List<FishingReward> rewards = plugin.getRewardManager().getRewardsByRarity(rarity);
        
        if (rewards.isEmpty()) {
            player.sendMessage(MessageUtils.color(MessageUtils.PREFIX + "&7No " + rarity.toLowerCase() + " rewards found."));
            return;
        }
        
        // 45 slots for items (5 rows), bottom row for navigation
        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) rewards.size() / itemsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        String title = MessageUtils.color(MessageUtils.getRarityColor(rarity) + "&l" + rarity + " Fish");
        if (totalPages > 1) {
            title += MessageUtils.color(" &7(" + (page + 1) + "/" + totalPages + ")");
        }
        
        Inventory inv = Bukkit.createInventory(null, 54, title);
        
        // Fill bottom row with glass
        short glassColor = RARITY_GLASS.getOrDefault(rarity, (short) 7);
        ItemStack glass = createGlass(glassColor, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, glass);
        }
        
        // Add rewards - calculate personalized weights
        List<FishingReward> allRewards = plugin.getRewardManager().getAllRewards();
        double totalWeight = calculatePlayerTotalWeight(player, allRewards);
        if (totalWeight <= 0) totalWeight = plugin.getRewardManager().getTotalWeight();
        
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, rewards.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            FishingReward reward = rewards.get(i);
            ItemStack displayItem = createRewardDisplayItem(reward, totalWeight, player);
            inv.setItem(i - startIndex, displayItem);
        }
        
        // Navigation
        // Back button (bottom left)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(MessageUtils.color("&7« Back to Menu"));
        backItem.setItemMeta(backMeta);
        inv.setItem(45, backItem);
        
        // Previous page
        if (page > 0) {
            ItemStack prevItem = new ItemStack(Material.PAPER);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName(MessageUtils.color("&e« Previous Page"));
            prevItem.setItemMeta(prevMeta);
            inv.setItem(48, prevItem);
        }
        
        // Page indicator
        ItemStack pageItem = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageItem.getItemMeta();
        pageMeta.setDisplayName(MessageUtils.color("&fPage " + (page + 1) + "/" + totalPages));
        pageMeta.setLore(Arrays.asList(
                MessageUtils.color("&7" + rewards.size() + " " + rarity.toLowerCase() + " rewards")
        ));
        pageItem.setItemMeta(pageMeta);
        inv.setItem(49, pageItem);
        
        // Next page
        if (page < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.PAPER);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName(MessageUtils.color("&eNext Page »"));
            nextItem.setItemMeta(nextMeta);
            inv.setItem(50, nextItem);
        }
        
        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(MessageUtils.color("&c&lClose"));
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);
        
        player.openInventory(inv);
        openGUIs.put(player.getUniqueId(), "rarity:" + rarity);
        playerPages.put(player.getUniqueId(), page);
    }
    
    /**
     * Open the "all rewards" view
     */
    public void openAllRewards(Player player, int page) {
        List<FishingReward> rewards = plugin.getRewardManager().getAllRewards();
        
        // Sort by rarity order, then by chance
        rewards.sort((a, b) -> {
            int rarityCompare = RARITY_ORDER.indexOf(a.getRarity()) - RARITY_ORDER.indexOf(b.getRarity());
            if (rarityCompare != 0) return rarityCompare;
            return Double.compare(b.getChance(), a.getChance());
        });
        
        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) rewards.size() / itemsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        String title = MessageUtils.color("&b&lAll Rewards");
        if (totalPages > 1) {
            title += MessageUtils.color(" &7(" + (page + 1) + "/" + totalPages + ")");
        }
        
        Inventory inv = Bukkit.createInventory(null, 54, title);
        
        // Fill bottom row
        ItemStack glass = createGlass((short) 9, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, glass);
        }
        
        // Add rewards - calculate personalized weights
        double totalWeight = calculatePlayerTotalWeight(player, rewards);
        if (totalWeight <= 0) totalWeight = plugin.getRewardManager().getTotalWeight();
        
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, rewards.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            FishingReward reward = rewards.get(i);
            ItemStack displayItem = createRewardDisplayItem(reward, totalWeight, player);
            inv.setItem(i - startIndex, displayItem);
        }
        
        // Navigation (same as rarity page)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(MessageUtils.color("&7« Back to Menu"));
        backItem.setItemMeta(backMeta);
        inv.setItem(45, backItem);
        
        if (page > 0) {
            ItemStack prevItem = new ItemStack(Material.PAPER);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName(MessageUtils.color("&e« Previous Page"));
            prevItem.setItemMeta(prevMeta);
            inv.setItem(48, prevItem);
        }
        
        ItemStack pageItem = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageItem.getItemMeta();
        pageMeta.setDisplayName(MessageUtils.color("&fPage " + (page + 1) + "/" + totalPages));
        pageMeta.setLore(Arrays.asList(
                MessageUtils.color("&7" + rewards.size() + " total rewards")
        ));
        pageItem.setItemMeta(pageMeta);
        inv.setItem(49, pageItem);
        
        if (page < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.PAPER);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName(MessageUtils.color("&eNext Page »"));
            nextItem.setItemMeta(nextMeta);
            inv.setItem(50, nextItem);
        }
        
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(MessageUtils.color("&c&lClose"));
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);
        
        player.openInventory(inv);
        openGUIs.put(player.getUniqueId(), "all");
        playerPages.put(player.getUniqueId(), page);
    }
    
    /**
     * Calculate total effective weight for a player (considering level requirements)
     */
    private double calculatePlayerTotalWeight(Player player, List<FishingReward> rewards) {
        HeadHuntingHook hook = plugin.getHeadHuntingHook();
        double total = 0;
        
        for (FishingReward reward : rewards) {
            double weight = hook.calculateEffectiveWeight(
                reward.getChance(), 
                reward.getRarity(), 
                reward.getRequiredLevel(), 
                player
            );
            total += weight;
        }
        
        return total;
    }
    
    /**
     * Create a display item for a reward (with catch % and sell price in lore)
     * Shows personalized catch rates based on player's HeadHunting level
     */
    private ItemStack createRewardDisplayItem(FishingReward reward, double totalWeight, Player player) {
        ItemStack item;
        
        if (reward.hasItem()) {
            item = reward.createItemStack().clone();
        } else {
            // Command-only reward - show a paper
            item = new ItemStack(Material.PAPER);
        }
        
        ItemMeta meta = item.getItemMeta();
        
        // Set display name with rarity color
        String displayName = reward.getItemDisplayName();
        if (!displayName.startsWith("§")) {
            displayName = MessageUtils.getRarityColor(reward.getRarity()) + displayName;
        }
        meta.setDisplayName(displayName);
        
        // Build lore
        List<String> lore = new ArrayList<>();
        
        // Rarity tag
        lore.add(MessageUtils.color(MessageUtils.getRarityColor(reward.getRarity()) + reward.getRarity()));
        lore.add("");
        
        // Original lore if exists
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (!line.contains("Warzone Fishing") && !line.equals("")) {
                    lore.add(line);
                }
            }
            if (!lore.get(lore.size() - 1).isEmpty()) {
                lore.add("");
            }
        }
        
        // Calculate personalized catch chance
        HeadHuntingHook hook = plugin.getHeadHuntingHook();
        double effectiveWeight = hook.calculateEffectiveWeight(
            reward.getChance(),
            reward.getRarity(),
            reward.getRequiredLevel(),
            player
        );
        
        // Check if player can catch this
        if (effectiveWeight <= 0) {
            // Player doesn't meet requirements
            lore.add(MessageUtils.color("&c✖ Locked"));
            if (reward.getRequiredLevel() > 0) {
                int playerLevel = hook.isEnabled() ? hook.getPlayerLevel(player) : 1;
                lore.add(MessageUtils.color("&7Requires Level: &e" + reward.getRequiredLevel() + 
                    " &7(You: &" + (playerLevel >= reward.getRequiredLevel() ? "a" : "c") + playerLevel + "&7)"));
            }
            if (reward.requiresGuardianMask()) {
                lore.add(MessageUtils.color("&7Requires: &bGuardian Mask"));
            }
        } else {
            // Calculate percentage with effective weight
            double percentage = (effectiveWeight / totalWeight) * 100;
            String chanceColor = percentage >= 10 ? "&a" : percentage >= 1 ? "&e" : "&c";
            lore.add(MessageUtils.color("&7Catch Rate: " + chanceColor + String.format("%.2f%%", percentage)));
            
            // Show if boosted by guardian mask
            if (hook.isEnabled() && hook.hasGuardianMask(player)) {
                double basePercentage = (reward.getChance() / totalWeight) * 100;
                if (percentage > basePercentage) {
                    lore.add(MessageUtils.color("&d⚡ Guardian Boost Active!"));
                }
            }
            
            // Show level requirement if any
            if (reward.getRequiredLevel() > 0) {
                lore.add(MessageUtils.color("&7Level Req: &a" + reward.getRequiredLevel() + " ✓"));
            }
        }
        
        // Sell value (from NBT if available)
        int sellValue = reward.getSellValue();
        if (sellValue > 0) {
            lore.add(MessageUtils.color("&7Sell Price: &6$" + formatNumber(sellValue)));
        }
        
        // Type indicator
        if (!reward.getCommands().isEmpty()) {
            lore.add("");
            lore.add(MessageUtils.color("&d+ Command Reward"));
        }
        
        if (reward.isBroadcast()) {
            lore.add(MessageUtils.color("&e✦ Server Broadcast"));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create a rarity category item for the main menu
     */
    private ItemStack createRarityItem(String rarity, int count, double percentage) {
        Material material;
        short data = 0;
        
        switch (rarity) {
            case "COMMON":
                material = Material.RAW_FISH;
                break;
            case "UNCOMMON":
                material = Material.RAW_FISH;
                data = 1; // Salmon
                break;
            case "RARE":
                material = Material.RAW_FISH;
                data = 2; // Clownfish
                break;
            case "EPIC":
                material = Material.RAW_FISH;
                data = 3; // Pufferfish
                break;
            case "LEGENDARY":
                material = Material.GOLDEN_APPLE;
                break;
            default:
                material = Material.PAPER;
        }
        
        ItemStack item = new ItemStack(material, 1, data);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(MessageUtils.color(MessageUtils.getRarityColor(rarity) + "&l" + rarity));
        meta.setLore(Arrays.asList(
                "",
                MessageUtils.color("&7Items: &f" + count),
                MessageUtils.color("&7Catch Rate: &f" + String.format("%.1f%%", percentage)),
                "",
                MessageUtils.color("&eClick to browse!")
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Create a glass pane with custom name
     */
    private ItemStack createGlass(short color, String name) {
        ItemStack glass = new ItemStack(Material.STAINED_GLASS_PANE, 1, color);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(MessageUtils.color(name));
        glass.setItemMeta(meta);
        return glass;
    }
    
    /**
     * Format large numbers with commas
     */
    private String formatNumber(int number) {
        return String.format("%,d", number);
    }
    
    /**
     * Handle inventory clicks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        String guiType = openGUIs.get(player.getUniqueId());
        if (guiType == null) return;
        
        // Check if clicking in our GUI
        String title = event.getInventory().getTitle();
        if (!title.contains("Warzone Fishing") && 
            !title.contains("Fish") && 
            !title.contains("All Rewards") &&
            !RARITY_ORDER.stream().anyMatch(r -> title.toUpperCase().contains(r))) {
            return;
        }
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.STAINED_GLASS_PANE) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        
        String name = meta.getDisplayName();
        
        // Handle main menu clicks
        if (guiType.equals("main")) {
            // Check if clicking a rarity
            for (String rarity : RARITY_ORDER) {
                if (name.toUpperCase().contains(rarity)) {
                    openRarityPage(player, rarity, 0);
                    return;
                }
            }
            
            // View All
            if (name.contains("View All")) {
                openAllRewards(player, 0);
                return;
            }
            
            // Close
            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                return;
            }
        }
        
        // Handle rarity/all page clicks
        if (guiType.startsWith("rarity:") || guiType.equals("all")) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            
            // Back to menu
            if (clicked.getType() == Material.ARROW && name.contains("Back")) {
                openMainMenu(player);
                return;
            }
            
            // Previous page
            if (name.contains("Previous")) {
                if (guiType.equals("all")) {
                    openAllRewards(player, currentPage - 1);
                } else {
                    String rarity = guiType.replace("rarity:", "");
                    openRarityPage(player, rarity, currentPage - 1);
                }
                return;
            }
            
            // Next page
            if (name.contains("Next")) {
                if (guiType.equals("all")) {
                    openAllRewards(player, currentPage + 1);
                } else {
                    String rarity = guiType.replace("rarity:", "");
                    openRarityPage(player, rarity, currentPage + 1);
                }
                return;
            }
            
            // Close
            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                return;
            }
        }
    }
    
    /**
     * Clean up when inventory is closed
     */
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            UUID uuid = event.getPlayer().getUniqueId();
            openGUIs.remove(uuid);
            playerPages.remove(uuid);
        }
    }
}

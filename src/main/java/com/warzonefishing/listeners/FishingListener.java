package com.warzonefishing.listeners;

import com.warzonefishing.WarzoneFishing;
import com.warzonefishing.models.FishingReward;
import com.warzonefishing.utils.MessageUtils;
import com.warzonefishing.utils.TitleAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for fishing events in warzone areas
 * Handles reward generation, title display, commands, and broadcasts
 */
public class FishingListener implements Listener {
    
    private final WarzoneFishing plugin;
    private final Map<UUID, Long> cooldowns;
    
    public FishingListener(WarzoneFishing plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        // Only handle caught fish state
        if (event.getState() != State.CAUGHT_FISH) {
            return;
        }
        
        Player player = event.getPlayer();
        Location hookLocation = event.getHook().getLocation();
        
        // Check if player has permission
        if (!player.hasPermission("warzonefishing.fish")) {
            return;
        }
        
        // Check if fishing in warzone
        if (!isInWarzone(hookLocation)) {
            return;
        }
        
        // Check cooldown
        if (!checkCooldown(player)) {
            return;
        }
        
        // Get random reward
        FishingReward reward = plugin.getRewardManager().getRandomReward();
        if (reward == null) {
            plugin.getLogger().warning("No rewards configured! Using default catch.");
            return;
        }
        
        // Remove the original caught item
        if (event.getCaught() instanceof Item) {
            event.getCaught().remove();
        }
        
        // Give item reward if applicable
        ItemStack rewardItem = null;
        if (reward.hasItem()) {
            rewardItem = reward.createItemStack();
            giveItem(player, rewardItem, hookLocation);
        }
        
        // Send title
        sendTitle(player, reward, rewardItem);
        
        // Play sound
        if (reward.getSound() != null) {
            player.playSound(player.getLocation(), reward.getSound(), 
                    reward.getSoundVolume(), reward.getSoundPitch());
        }
        
        // Execute commands
        executeCommands(player, reward, rewardItem);
        
        // Broadcast if enabled
        if (reward.shouldBroadcast()) {
            broadcastMessage(player, reward, rewardItem);
        }
        
        // Send action bar if configured
        sendActionBar(player, reward, rewardItem);
    }
    
    /**
     * Check and update cooldown for a player
     * @return true if player can fish, false if on cooldown
     */
    private boolean checkCooldown(Player player) {
        if (player.hasPermission("warzonefishing.bypass.cooldown")) {
            return true;
        }
        
        int cooldownSeconds = plugin.getConfig().getInt("settings.cooldown", 0);
        if (cooldownSeconds <= 0) {
            return true;
        }
        
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastCatch = cooldowns.getOrDefault(uuid, 0L);
        
        if (now - lastCatch < cooldownSeconds * 1000L) {
            int remaining = (int) ((cooldownSeconds * 1000L - (now - lastCatch)) / 1000);
            MessageUtils.send(player, "&7Please wait &b" + remaining + "s &7before fishing again.");
            return false;
        }
        
        cooldowns.put(uuid, now);
        return true;
    }
    
    /**
     * Give an item to the player or drop it
     */
    private void giveItem(Player player, ItemStack item, Location hookLocation) {
        boolean dropAtHook = plugin.getConfig().getBoolean("settings.drop-at-hook", false);
        
        if (dropAtHook) {
            hookLocation.getWorld().dropItemNaturally(hookLocation, item);
        } else if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            // Inventory full - drop at player's feet
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            MessageUtils.send(player, "&cInventory full! Item dropped at your feet.");
        }
    }
    
    /**
     * Send title and subtitle to player
     */
    private void sendTitle(Player player, FishingReward reward, ItemStack item) {
        String title = replacePlaceholders(reward.getTitleMessage(), player, reward, item);
        String subtitle = replacePlaceholders(reward.getSubtitleMessage(), player, reward, item);
        
        int fadeIn = plugin.getConfig().getInt("settings.title-fade-in", 10);
        int stay = plugin.getConfig().getInt("settings.title-stay", 40);
        int fadeOut = plugin.getConfig().getInt("settings.title-fade-out", 10);
        
        TitleAPI.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
    }
    
    /**
     * Send action bar message if configured
     */
    private void sendActionBar(Player player, FishingReward reward, ItemStack item) {
        String actionBarMsg = plugin.getConfig().getString("settings.action-bar-message", "");
        if (actionBarMsg.isEmpty()) {
            return;
        }
        
        String message = replacePlaceholders(MessageUtils.color(actionBarMsg), player, reward, item);
        TitleAPI.sendActionBar(player, message);
    }
    
    /**
     * Execute reward commands
     */
    private void executeCommands(Player player, FishingReward reward, ItemStack item) {
        for (String command : reward.getCommands()) {
            String parsedCommand = replacePlaceholders(command, player, reward, item);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
        }
    }
    
    /**
     * Broadcast reward message to server
     */
    private void broadcastMessage(Player player, FishingReward reward, ItemStack item) {
        String message = replacePlaceholders(reward.getBroadcastMessage(), player, reward, item);
        Bukkit.broadcastMessage(message);
    }
    
    /**
     * Replace placeholders in a string
     */
    private String replacePlaceholders(String text, Player player, FishingReward reward, ItemStack item) {
        if (text == null) return "";
        
        String itemName = reward.getItemDisplayName();
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            itemName = item.getItemMeta().getDisplayName();
        }
        
        return text
                .replace("{player}", player.getName())
                .replace("{item}", itemName)
                .replace("{rarity}", reward.getRarity())
                .replace("{rarity_color}", MessageUtils.getRarityColor(reward.getRarity()))
                .replace("{id}", reward.getId());
    }
    
    /**
     * Check if a location is in the warzone
     */
    private boolean isInWarzone(Location location) {
        String claimPlugin = plugin.getConfig().getString("settings.claim-plugin", "factions").toLowerCase();
        
        switch (claimPlugin) {
            case "factions":
            case "factionsuuid":
                return isInFactionsWarzone(location);
            case "worldguard":
                return isInWorldGuardRegion(location);
            case "none":
                return isInConfiguredWorld(location);
            default:
                return isInFactionsWarzone(location);
        }
    }
    
    /**
     * Check Factions warzone
     */
    private boolean isInFactionsWarzone(Location location) {
        try {
            // Try FactionsUUID / SavageFactions / etc.
            Class<?> boardClass = Class.forName("com.massivecraft.factions.Board");
            Object board = boardClass.getMethod("getInstance").invoke(null);
            
            Class<?> fLocationClass = Class.forName("com.massivecraft.factions.FLocation");
            Object fLocation = fLocationClass.getConstructor(Location.class).newInstance(location);
            
            Object faction = boardClass.getMethod("getFactionAt", fLocationClass).invoke(board, fLocation);
            
            if (faction != null) {
                // Try isWarZone() method
                try {
                    Boolean isWarzone = (Boolean) faction.getClass().getMethod("isWarZone").invoke(faction);
                    if (isWarzone != null && isWarzone) {
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {}
                
                // Try checking tag/id
                try {
                    String tag = (String) faction.getClass().getMethod("getTag").invoke(faction);
                    if (tag != null && tag.equalsIgnoreCase("warzone")) {
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {}
                
                try {
                    String id = (String) faction.getClass().getMethod("getId").invoke(faction);
                    if (id != null && id.equalsIgnoreCase("warzone")) {
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (ClassNotFoundException e) {
            // Factions not loaded
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking Factions warzone: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check WorldGuard region
     */
    private boolean isInWorldGuardRegion(Location location) {
        String regionName = plugin.getConfig().getString("settings.worldguard-region", "warzone");
        
        try {
            Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            if (wgPlugin == null) {
                return false;
            }
            
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object regionManager = wgClass.getMethod("getRegionManager", World.class)
                    .invoke(wgPlugin, location.getWorld());
            
            if (regionManager == null) {
                return false;
            }
            
            // Create vector
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.Vector");
            Object vector = vectorClass.getConstructor(double.class, double.class, double.class)
                    .newInstance(location.getX(), location.getY(), location.getZ());
            
            // Get applicable regions
            Object applicableRegions = regionManager.getClass()
                    .getMethod("getApplicableRegions", vectorClass)
                    .invoke(regionManager, vector);
            
            // Check each region
            for (Object region : (Iterable<?>) applicableRegions) {
                String id = (String) region.getClass().getMethod("getId").invoke(region);
                if (id.equalsIgnoreCase(regionName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard region: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check if location is in configured allowed worlds
     */
    private boolean isInConfiguredWorld(Location location) {
        List<String> worlds = plugin.getConfig().getStringList("settings.allowed-worlds");
        
        // If no worlds configured, allow all
        if (worlds.isEmpty()) {
            return true;
        }
        
        return worlds.contains(location.getWorld().getName());
    }
}

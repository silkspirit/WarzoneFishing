package com.warzonefishing.listeners;

import com.warzonefishing.WarzoneFishing;
import com.warzonefishing.hooks.HeadHuntingHook;
import com.warzonefishing.models.FishingReward;
import com.warzonefishing.utils.MessageUtils;
import com.warzonefishing.utils.TitleAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Fish;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
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
    
    // Cached NMS fields for fish hook wait time manipulation
    private Field nmsEntityField;
    private Field waitTimeField;
    private boolean nmsInitialized = false;
    private boolean nmsAvailable = false;
    
    public FishingListener(WarzoneFishing plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
    }
    
    /**
     * Initialize NMS reflection for fish hook wait time manipulation.
     * Tries v1_8_R3 first, then falls back to generic CraftFish approach.
     */
    private void initNMS() {
        if (nmsInitialized) return;
        nmsInitialized = true;
        
        try {
            // Get CraftFish -> handle (NMS EntityFishingHook)
            Class<?> craftFishClass = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftFish");
            nmsEntityField = craftFishClass.getMethod("getHandle").getDeclaringClass().getMethod("getHandle").getDeclaringClass()
                    .getDeclaredMethod("getHandle").getDeclaringClass().getDeclaredField("entity");
        } catch (Exception ignored) {
            // Fall through to try generic approach
        }
        
        try {
            // Generic approach: get the NMS entity via CraftEntity.getHandle()
            // and then access the wait time field on EntityFishingHook
            Class<?> nmsEntityFishingHook = Class.forName("net.minecraft.server.v1_8_R3.EntityFishingHook");
            
            // In 1.8.8 NMS, the fish hook has fields 'h' (max wait time) and 'ax'/'ay' or similar
            // The wait time countdown field varies by exact build. Try common field names.
            // In Spigot 1.8.8, EntityFishingHook has field 'h' for the bite timer
            for (String fieldName : new String[]{"h", "ax", "ay", "g", "aw"}) {
                try {
                    waitTimeField = nmsEntityFishingHook.getDeclaredField(fieldName);
                    waitTimeField.setAccessible(true);
                    // Verify it's an int field
                    if (waitTimeField.getType() == int.class) {
                        nmsAvailable = true;
                        plugin.getLogger().info("Fish hook wait time field found: " + fieldName + " (catch rate boost enabled)");
                        return;
                    }
                } catch (NoSuchFieldException ignored) {}
            }
            
            // If no known field names work, try all int fields and find the one that looks like a timer
            Field[] fields = nmsEntityFishingHook.getDeclaredFields();
            for (Field f : fields) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    // We'll use the first private int field as a candidate
                    // and verify at runtime
                    waitTimeField = f;
                    nmsAvailable = true;
                    plugin.getLogger().info("Fish hook wait time field candidate: " + f.getName() + " (catch rate boost enabled)");
                    return;
                }
            }
            
            plugin.getLogger().warning("Could not find fish hook wait time field. Catch rate boost will be disabled.");
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("NMS classes not found (not 1.8.8?). Catch rate boost for guardian mask will be disabled.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize NMS for catch rate boost: " + e.getMessage());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        
        // Handle cast/fishing state — apply catch rate boost for guardian mask
        if (event.getState() == State.FISHING) {
            applyCatchRateBoost(player, event.getHook());
            return;
        }
        
        // Only handle caught fish state for rewards
        if (event.getState() != State.CAUGHT_FISH) {
            return;
        }
        
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
        
        // Get random reward (filtered by player level and mask requirements)
        FishingReward reward = plugin.getRewardManager().getRandomReward(player);
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
        
        // Clean up stale cooldown entries to prevent memory leak
        if (cooldowns.size() > 100) {
            long now = System.currentTimeMillis();
            long expiry = cooldownSeconds * 2000L;
            Iterator<Map.Entry<UUID, Long>> it = cooldowns.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                if (now - entry.getValue() > expiry) {
                    it.remove();
                }
            }
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
     * Apply catch rate boost to the fishing hook by combining ALL three boost sources:
     * 1. Guardian mask bonus (player-specific, from HeadHunting ability)
     * 2. Server-wide hourly boost event (from HeadHunting FishingManager)
     * 3. Personal/faction fishing booster (from HeadHunting BoosterManager)
     * 
     * All three are combined multiplicatively to determine total wait time reduction.
     */
    private void applyCatchRateBoost(Player player, Fish hook) {
        HeadHuntingHook headHunting = plugin.getHeadHuntingHook();
        if (headHunting == null || !headHunting.isEnabled()) return;
        
        // Source 1: Guardian mask catch rate boost (e.g. 0.25 = 25% faster)
        double maskBoost = headHunting.getCatchRateBoost(player);
        double configMultiplier = plugin.getConfig().getDouble("settings.guardian-mask.catch-rate-multiplier", 1.0);
        double maxMaskBoost = plugin.getConfig().getDouble("settings.guardian-mask.max-catch-rate-boost", 0.50);
        maskBoost = Math.min(maskBoost * configMultiplier, maxMaskBoost);
        // Convert to multiplier: 0.25 boost → 1.25x speed → wait time = 1/1.25
        double maskMultiplier = 1.0 + Math.max(maskBoost, 0.0);
        
        // Source 2: Server-wide hourly boost event multiplier (e.g. 2.0 = 2x faster)
        double serverMultiplier = headHunting.getServerBoostMultiplier();
        
        // Source 3: Personal/faction fishing booster multiplier (e.g. 1.5 = 1.5x faster)
        double personalMultiplier = headHunting.getPersonalFishingBoostMultiplier(player);
        
        // Combine all three multiplicatively
        double totalMultiplier = maskMultiplier * serverMultiplier * personalMultiplier;
        
        // No boost needed if total is 1.0 or less
        if (totalMultiplier <= 1.0) return;
        
        // Convert multiplier to wait time reduction fraction
        // e.g. 2x speed → wait time = 1/2 → reduction = 0.50
        double reduction = 1.0 - (1.0 / totalMultiplier);
        
        // Cap reduction at 90% to prevent near-instant catches
        double maxReduction = plugin.getConfig().getDouble("settings.max-catch-rate-reduction", 0.90);
        reduction = Math.min(reduction, maxReduction);
        
        if (reduction <= 0) return;
        
        // Schedule the wait time reduction for next tick (hook needs to be fully initialized)
        final double finalReduction = reduction;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!hook.isValid()) return;
            reduceHookWaitTime(hook, finalReduction);
        }, 1L);
    }
    
    /**
     * Reduce the NMS fish hook wait time using reflection.
     * In 1.8.8, EntityFishingHook has an int field controlling time until a fish bites.
     */
    private void reduceHookWaitTime(Fish hook, double boostPercent) {
        initNMS();
        
        if (!nmsAvailable || waitTimeField == null) return;
        
        try {
            // Get the NMS entity via CraftEntity.getHandle()
            Object nmsEntity = hook.getClass().getMethod("getHandle").invoke(hook);
            
            int currentWait = waitTimeField.getInt(nmsEntity);
            
            // Only reduce if there's actual wait time set (> 0)
            if (currentWait > 0) {
                int reducedWait = (int) (currentWait * (1.0 - boostPercent));
                // Minimum wait time of 20 ticks (1 second) to prevent instant catches
                int minWait = plugin.getConfig().getInt("settings.guardian-mask.min-wait-ticks", 20);
                reducedWait = Math.max(reducedWait, minWait);
                
                waitTimeField.setInt(nmsEntity, reducedWait);
            }
        } catch (Exception e) {
            // Silently fail — don't spam console every cast
            if (plugin.getConfig().getBoolean("settings.debug", false)) {
                plugin.getLogger().warning("Failed to reduce hook wait time: " + e.getMessage());
            }
        }
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

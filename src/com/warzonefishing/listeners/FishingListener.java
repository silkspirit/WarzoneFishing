package com.warzonefishing.listeners;

import com.warzonefishing.WarzoneFishing;
import com.warzonefishing.models.FishingReward;
import com.warzonefishing.utils.TitleAPI;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class FishingListener implements Listener {
    private final WarzoneFishing plugin;

    public FishingListener(WarzoneFishing plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        
        Player player = event.getPlayer();
        Location hookLocation = event.getHook().getLocation();
        
        if (!this.isInWarzone(hookLocation)) {
            return;
        }
        
        if (!player.hasPermission("warzonefishing.fish")) {
            return;
        }
        
        // Pass player for HeadHunting level bonus!
        FishingReward reward = this.plugin.getRewardManager().getRandomReward(player);
        if (reward == null) {
            return;
        }
        
        if (event.getCaught() instanceof Item) {
            event.getCaught().remove();
        }
        
        ItemStack rewardItem = reward.createItemStack();
        
        if (this.plugin.getConfig().getBoolean("settings.drop-at-hook", false)) {
            hookLocation.getWorld().dropItemNaturally(hookLocation, rewardItem);
        } else if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(rewardItem);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), rewardItem);
            player.sendMessage(ChatColor.YELLOW + "Your inventory was full! The item dropped at your feet.");
        }
        
        // Build display name
        String itemDisplayName = rewardItem.hasItemMeta() && rewardItem.getItemMeta().hasDisplayName() 
            ? rewardItem.getItemMeta().getDisplayName() 
            : rewardItem.getType().name();
        
        String title = reward.getTitleMessage()
            .replace("{player}", player.getName())
            .replace("{item}", itemDisplayName)
            .replace("{rarity}", reward.getRarity());
            
        String subtitle = reward.getSubtitleMessage()
            .replace("{player}", player.getName())
            .replace("{item}", itemDisplayName)
            .replace("{rarity}", reward.getRarity());
        
        int fadeIn = this.plugin.getConfig().getInt("settings.title-fade-in", 10);
        int stay = this.plugin.getConfig().getInt("settings.title-stay", 40);
        int fadeOut = this.plugin.getConfig().getInt("settings.title-fade-out", 10);
        
        TitleAPI.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
        
        if (reward.getSound() != null) {
            player.playSound(player.getLocation(), reward.getSound(), reward.getSoundVolume(), reward.getSoundPitch());
        }
        
        // Run commands
        for (String command : reward.getCommands()) {
            String parsedCommand = command
                .replace("{player}", player.getName())
                .replace("{item}", rewardItem.getType().name())
                .replace("{rarity}", reward.getRarity());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
        }
        
        // Broadcast if enabled
        if (reward.shouldBroadcast()) {
            String broadcastMsg = reward.getBroadcastText()
                .replace("{player}", player.getName())
                .replace("{item}", itemDisplayName)
                .replace("{rarity}", reward.getRarity());
            Bukkit.broadcastMessage(broadcastMsg);
        }
        
        // Action bar
        String actionBarMsg = this.plugin.getConfig().getString("settings.action-bar-message", "");
        if (!actionBarMsg.isEmpty()) {
            String parsedActionBar = ChatColor.translateAlternateColorCodes('&', actionBarMsg)
                .replace("{player}", player.getName())
                .replace("{item}", itemDisplayName)
                .replace("{rarity}", reward.getRarity());
            TitleAPI.sendActionBar(player, parsedActionBar);
        }
    }

    private boolean isInWarzone(Location location) {
        String claimPlugin = this.plugin.getConfig().getString("settings.claim-plugin", "factions").toLowerCase();
        
        switch (claimPlugin) {
            case "factions":
            case "factionsuuid":
                return this.isInFactionsWarzone(location);
            case "worldguard":
                return this.isInWorldGuardRegion(location);
            case "none":
                return this.isInConfiguredWorld(location);
            default:
                return this.isInFactionsWarzone(location);
        }
    }

    private boolean isInFactionsWarzone(Location location) {
        try {
            Class<?> boardClass = Class.forName("com.massivecraft.factions.Board");
            Object board = boardClass.getMethod("getInstance").invoke(null);
            Class<?> flocationClass = Class.forName("com.massivecraft.factions.FLocation");
            Object flocation = flocationClass.getConstructor(Location.class).newInstance(location);
            Object faction = boardClass.getMethod("getFactionAt", flocationClass).invoke(board, flocation);
            
            if (faction != null) {
                // Try isWarZone method
                try {
                    Boolean isWarzone = (Boolean) faction.getClass().getMethod("isWarZone").invoke(faction);
                    if (isWarzone != null && isWarzone) {
                        return true;
                    }
                } catch (Exception e) {}
                
                // Fallback: check tag/id
                try {
                    String tag = (String) faction.getClass().getMethod("getTag").invoke(faction);
                    String id = (String) faction.getClass().getMethod("getId").invoke(faction);
                    if (tag != null && (tag.equalsIgnoreCase("warzone") || tag.equalsIgnoreCase("WarZone"))) {
                        return true;
                    }
                    if (id != null && id.equalsIgnoreCase("warzone")) {
                        return true;
                    }
                } catch (Exception e) {}
            }
        } catch (ClassNotFoundException e) {
            // Factions not installed
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error checking Factions warzone: " + e.getMessage());
        }
        
        return false;
    }

    private boolean isInWorldGuardRegion(Location location) {
        String regionName = this.plugin.getConfig().getString("settings.worldguard-region", "warzone");
        
        try {
            Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            if (wgPlugin == null) {
                return false;
            }
            
            Class<?> wgClass = wgPlugin.getClass();
            Object regionManager = wgClass.getMethod("getRegionManager", World.class).invoke(wgPlugin, location.getWorld());
            if (regionManager == null) {
                return false;
            }
            
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.Vector");
            Object vector = vectorClass.getConstructor(double.class, double.class, double.class)
                .newInstance(location.getX(), location.getY(), location.getZ());
            
            Object applicableRegions = regionManager.getClass().getMethod("getApplicableRegions", vectorClass)
                .invoke(regionManager, vector);
            
            for (Object region : (Iterable<?>) applicableRegions) {
                String id = (String) region.getClass().getMethod("getId").invoke(region);
                if (id.equalsIgnoreCase(regionName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error checking WorldGuard region: " + e.getMessage());
        }
        
        return false;
    }

    private boolean isInConfiguredWorld(Location location) {
        List<String> worlds = this.plugin.getConfig().getStringList("settings.allowed-worlds");
        if (worlds.isEmpty()) {
            return true;
        }
        return worlds.contains(location.getWorld().getName());
    }
}

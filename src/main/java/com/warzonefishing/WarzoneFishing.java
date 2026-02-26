package com.warzonefishing;

import com.warzonefishing.commands.WarzoneFishingCommand;
import com.warzonefishing.gui.FishingGUI;
import com.warzonefishing.hooks.HeadHuntingHook;
import com.warzonefishing.listeners.FishingListener;
import com.warzonefishing.managers.RewardManager;
import com.warzonefishing.utils.MessageUtils;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WarzoneFishing - Custom fishing rewards in warzone areas
 * 
 * Features:
 * - NBT-based rarity fish system for ShopGUIPlus integration
 * - Custom skull textures for special items
 * - Crate key rewards via commands or direct items
 * - Teal/aqua themed messages
 * - Faction/WorldGuard warzone detection
 * 
 * @version 2.0.0
 */
public class WarzoneFishing extends JavaPlugin {
    
    private static WarzoneFishing instance;
    private RewardManager rewardManager;
    private FishingGUI fishingGUI;
    private HeadHuntingHook headHuntingHook;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize the reward manager and load rewards
        rewardManager = new RewardManager(this);
        rewardManager.loadRewards();
        
        // Initialize GUI manager
        fishingGUI = new FishingGUI(this);
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new FishingListener(this), this);
        
        // Register commands
        WarzoneFishingCommand commandExecutor = new WarzoneFishingCommand(this);
        getCommand("warzonefishing").setExecutor(commandExecutor);
        getCommand("warzonefishing").setTabCompleter(commandExecutor);
        
        // Startup message
        getLogger().info(MessageUtils.stripColor("&3═══════════════════════════════════════"));
        getLogger().info(MessageUtils.stripColor("&b WarzoneFishing &fv" + getDescription().getVersion()));
        getLogger().info(MessageUtils.stripColor("&3═══════════════════════════════════════"));
        getLogger().info("Loaded " + rewardManager.getRewardCount() + " fishing rewards.");
        
        // Check for soft dependencies
        checkDependencies();
        
        // Hook into HeadHunting for level-based rewards
        headHuntingHook = new HeadHuntingHook();
    }
    
    @Override
    public void onDisable() {
        getLogger().info("WarzoneFishing has been disabled!");
        instance = null;
    }
    
    /**
     * Check for optional plugin dependencies and log their status
     */
    private void checkDependencies() {
        String claimPlugin = getConfig().getString("settings.claim-plugin", "factions");
        
        if (claimPlugin.equalsIgnoreCase("factions") || claimPlugin.equalsIgnoreCase("factionsuuid")) {
            if (getServer().getPluginManager().getPlugin("Factions") != null) {
                getLogger().info("Hooked into Factions for warzone detection.");
            } else {
                getLogger().warning("Factions not found! Warzone detection may not work.");
            }
        } else if (claimPlugin.equalsIgnoreCase("worldguard")) {
            if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
                getLogger().info("Hooked into WorldGuard for region detection.");
            } else {
                getLogger().warning("WorldGuard not found! Region detection may not work.");
            }
        } else if (claimPlugin.equalsIgnoreCase("none")) {
            getLogger().info("Running in 'none' mode - fishing allowed in configured worlds.");
        }
    }
    
    /**
     * Get the plugin instance
     * @return Plugin instance
     */
    public static WarzoneFishing getInstance() {
        return instance;
    }
    
    /**
     * Get the reward manager
     * @return RewardManager instance
     */
    public RewardManager getRewardManager() {
        return rewardManager;
    }
    
    /**
     * Get the fishing GUI manager
     * @return FishingGUI instance
     */
    public FishingGUI getFishingGUI() {
        return fishingGUI;
    }
    
    /**
     * Get the HeadHunting hook
     * @return HeadHuntingHook instance
     */
    public HeadHuntingHook getHeadHuntingHook() {
        return headHuntingHook;
    }
    
    /**
     * Reload the plugin configuration and rewards
     */
    public void reload() {
        reloadConfig();
        rewardManager.loadRewards();
        getLogger().info("Configuration reloaded! Loaded " + rewardManager.getRewardCount() + " rewards.");
    }
}

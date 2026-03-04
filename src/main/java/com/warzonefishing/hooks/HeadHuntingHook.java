package com.warzonefishing.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Hook into HeadHunting plugin for level-based rewards
 */
public class HeadHuntingHook {
    
    private final boolean enabled;
    private Plugin headHunting;
    private Object dataManager;
    private Object abilityHandler;
    private Object fishingManager;
    private Object boosterManager;
    private Method getPlayerDataMethod;
    private Method getLevelMethod;
    private Method getEquippedMaskMethod;
    private Method getFishingLuckBonusMethod;
    private Method getBoostMultiplierMethod;
    private Method getFishingMultiplierMethod;
    
    public HeadHuntingHook() {
        this.enabled = setupHook();
    }
    
    private boolean setupHook() {
        headHunting = Bukkit.getPluginManager().getPlugin("HeadHunting");
        if (headHunting == null || !headHunting.isEnabled()) {
            return false;
        }
        
        try {
            // Get DataManager
            Method getDataManagerMethod = headHunting.getClass().getMethod("getDataManager");
            dataManager = getDataManagerMethod.invoke(headHunting);
            
            // Get AbilityHandler
            try {
                Method getAbilityHandlerMethod = headHunting.getClass().getMethod("getAbilityHandler");
                abilityHandler = getAbilityHandlerMethod.invoke(headHunting);
                getFishingLuckBonusMethod = abilityHandler.getClass().getMethod("getFishingLuckBonus", Player.class);
            } catch (Exception e) {
                // AbilityHandler might not exist, that's fine
            }
            
            // Get FishingManager for server-wide boost event multiplier
            try {
                Method getFishingManagerMethod = headHunting.getClass().getMethod("getFishingManager");
                fishingManager = getFishingManagerMethod.invoke(headHunting);
                getBoostMultiplierMethod = fishingManager.getClass().getMethod("getBoostMultiplier");
            } catch (Exception e) {
                // FishingManager might not exist
            }
            
            // Get BoosterManager for personal/faction fishing boosters
            try {
                Method getBoosterManagerMethod = headHunting.getClass().getMethod("getBoosterManager");
                boosterManager = getBoosterManagerMethod.invoke(headHunting);
                getFishingMultiplierMethod = boosterManager.getClass().getMethod("getFishingMultiplier", Player.class);
            } catch (Exception e) {
                // BoosterManager might not exist
            }
            
            // Get PlayerData methods
            getPlayerDataMethod = dataManager.getClass().getMethod("getPlayerData", Player.class);
            
            // Test with reflection to find PlayerData class methods
            Class<?> playerDataClass = Class.forName("com.headhunting.data.PlayerData");
            getLevelMethod = playerDataClass.getMethod("getLevel");
            getEquippedMaskMethod = playerDataClass.getMethod("getEquippedMask");
            
            Bukkit.getLogger().info("[WarzoneFishing] Hooked into HeadHunting for level-based rewards!");
            return true;
            
        } catch (Exception e) {
            Bukkit.getLogger().warning("[WarzoneFishing] Failed to hook into HeadHunting: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get player's HeadHunting level
     */
    public int getPlayerLevel(Player player) {
        if (!enabled) return 1;
        
        try {
            Object playerData = getPlayerDataMethod.invoke(dataManager, player);
            return (int) getLevelMethod.invoke(playerData);
        } catch (Exception e) {
            return 1;
        }
    }
    
    /**
     * Get player's equipped mask ID (or null if none)
     */
    public String getEquippedMask(Player player) {
        if (!enabled) return null;
        
        try {
            Object playerData = getPlayerDataMethod.invoke(dataManager, player);
            return (String) getEquippedMaskMethod.invoke(playerData);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check if player has guardian or elder guardian mask equipped
     */
    public boolean hasGuardianMask(Player player) {
        String mask = getEquippedMask(player);
        if (mask == null) return false;
        return mask.equalsIgnoreCase("guardian") || mask.equalsIgnoreCase("elder_guardian");
    }
    
    /**
     * Get fishing luck bonus from guardian mask (0-100%)
     */
    public int getFishingLuckBonus(Player player) {
        if (!enabled || abilityHandler == null || getFishingLuckBonusMethod == null) {
            return 0;
        }
        
        try {
            return (int) getFishingLuckBonusMethod.invoke(abilityHandler, player);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Calculate effective weight for a reward based on player level.
     * Guardian mask luck bonus no longer affects drop weights — it now
     * increases catch rate (fish bite faster) instead.
     * 
     * @param baseWeight The base chance weight
     * @param rarity The rarity of the reward (unused, kept for API compat)
     * @param requiredLevel Level required (0 = no requirement)
     * @param player The player to calculate for
     * @return The effective weight (0 if player can't get this reward)
     */
    public double calculateEffectiveWeight(double baseWeight, String rarity, int requiredLevel, Player player) {
        if (!enabled) return baseWeight;
        
        int playerLevel = getPlayerLevel(player);
        
        // Can't get reward if level too low
        if (requiredLevel > 0 && playerLevel < requiredLevel) {
            return 0;
        }
        
        // Return base weight — no luck bonus applied to drop rates anymore
        // Guardian mask bonus now speeds up catch rate via FishingListener
        return baseWeight;
    }
    
    /**
     * Get the catch rate boost multiplier for a player with guardian mask.
     * This reduces the fishing hook wait time, making fish bite faster.
     * 
     * @param player The player to check
     * @return Catch rate boost as a decimal (e.g. 0.25 = 25% faster catches), 0 if no boost
     */
    public double getCatchRateBoost(Player player) {
        if (!enabled || !hasGuardianMask(player)) {
            return 0.0;
        }
        
        int luckBonus = getFishingLuckBonus(player);
        return luckBonus / 100.0;
    }
    
    /**
     * Get the server-wide fishing boost event catch rate multiplier.
     * When the hourly boost event is active, this returns the configured multiplier (e.g. 2.0).
     * When inactive, returns 1.0 (no boost).
     * 
     * @return Catch rate multiplier from server boost event (1.0 = no boost)
     */
    public double getServerBoostMultiplier() {
        if (!enabled || fishingManager == null || getBoostMultiplierMethod == null) {
            return 1.0;
        }
        
        try {
            return (double) getBoostMultiplierMethod.invoke(fishingManager);
        } catch (Exception e) {
            return 1.0;
        }
    }
    
    /**
     * Get the personal/faction fishing booster multiplier for a player.
     * This combines personal + faction FISHING type boosters from HeadHunting's BoosterManager.
     * 
     * @param player The player to check
     * @return Combined fishing booster multiplier (1.0 = no boost)
     */
    public double getPersonalFishingBoostMultiplier(Player player) {
        if (!enabled || boosterManager == null || getFishingMultiplierMethod == null) {
            return 1.0;
        }
        
        try {
            return (double) getFishingMultiplierMethod.invoke(boosterManager, player);
        } catch (Exception e) {
            return 1.0;
        }
    }
}

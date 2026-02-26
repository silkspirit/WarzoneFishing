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
    private Method getPlayerDataMethod;
    private Method getLevelMethod;
    private Method getEquippedMaskMethod;
    private Method getFishingLuckBonusMethod;
    
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
     * Calculate effective weight for a reward based on player level and mask
     * 
     * @param baseWeight The base chance weight
     * @param rarity The rarity of the reward
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
        
        double weight = baseWeight;
        
        // Apply luck bonus for guardian mask on rare+ items
        if (hasGuardianMask(player)) {
            int luckBonus = getFishingLuckBonus(player);
            if (rarity != null && (rarity.equalsIgnoreCase("RARE") || 
                rarity.equalsIgnoreCase("EPIC") || 
                rarity.equalsIgnoreCase("LEGENDARY"))) {
                weight *= (1.0 + luckBonus / 100.0);
            }
        }
        
        return weight;
    }
}

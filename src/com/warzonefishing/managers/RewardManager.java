package com.warzonefishing.managers;

import com.warzonefishing.WarzoneFishing;
import com.warzonefishing.models.FishingReward;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class RewardManager {
    private final WarzoneFishing plugin;
    private final List<FishingReward> rewards;
    private final Random random;
    private double totalWeight;
    
    // HeadHunting integration
    private boolean headHuntingEnabled = false;
    private Object headHuntingPlugin = null;

    public RewardManager(WarzoneFishing plugin) {
        this.plugin = plugin;
        this.rewards = new ArrayList<FishingReward>();
        this.random = new Random();
        this.totalWeight = 0.0;
        
        // Try to hook into HeadHunting
        setupHeadHuntingHook();
    }
    
    private void setupHeadHuntingHook() {
        try {
            Plugin hh = Bukkit.getPluginManager().getPlugin("HeadHunting");
            if (hh != null && hh.isEnabled()) {
                headHuntingPlugin = hh;
                headHuntingEnabled = true;
                plugin.getLogger().info("HeadHunting integration enabled! Higher levels = better fishing luck!");
            }
        } catch (Exception e) {
            plugin.getLogger().info("HeadHunting not found - fishing luck bonus disabled.");
        }
    }
    
    /**
     * Get player's HeadHunting level (1-14 for rankup masks)
     * Returns 0 if HeadHunting not installed or player has no level
     */
    public int getHeadHuntingLevel(Player player) {
        if (!headHuntingEnabled || headHuntingPlugin == null) {
            return 0;
        }
        
        try {
            // Try to get the player's level via HeadHunting API
            // HeadHunting stores player level - we need to call the appropriate method
            Class<?> hhClass = headHuntingPlugin.getClass();
            
            // Try getting PlayerDataManager
            Object dataManager = hhClass.getMethod("getPlayerDataManager").invoke(headHuntingPlugin);
            if (dataManager != null) {
                // Get player data
                Object playerData = dataManager.getClass().getMethod("getPlayerData", Player.class).invoke(dataManager, player);
                if (playerData != null) {
                    // Get level
                    Object level = playerData.getClass().getMethod("getLevel").invoke(playerData);
                    if (level instanceof Integer) {
                        return (Integer) level;
                    }
                }
            }
        } catch (Exception e) {
            // Silent fail - HeadHunting API might be different
        }
        
        return 0;
    }
    
    /**
     * Calculate luck multiplier based on HeadHunting level
     * Level 1: 1.0x (no bonus)
     * Level 7: 1.15x
     * Level 14: 1.35x (max 35% better odds for rare items)
     */
    public double getLuckMultiplier(int level) {
        if (level <= 0) return 1.0;
        // 2.5% bonus per level, capped at level 14
        return 1.0 + (Math.min(level, 14) * 0.025);
    }

    public void loadRewards() {
        this.rewards.clear();
        this.totalWeight = 0.0;
        ConfigurationSection rewardsSection = this.plugin.getConfig().getConfigurationSection("rewards");
        if (rewardsSection == null) {
            this.plugin.getLogger().warning("No rewards section found in config!");
            return;
        }
        for (String key : rewardsSection.getKeys(false)) {
            ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(key);
            if (rewardSection == null) continue;
            try {
                FishingReward reward = this.loadReward(key, rewardSection);
                if (reward == null) continue;
                this.rewards.add(reward);
                this.totalWeight += reward.getChance();
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("Failed to load reward: " + key + " - " + e.getMessage());
            }
        }
        this.rewards.sort((a, b) -> Double.compare(a.getChance(), b.getChance()));
    }

    private FishingReward loadReward(String id, ConfigurationSection section) {
        Sound sound;
        String materialName = section.getString("material", "DIAMOND");
        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null) {
            this.plugin.getLogger().warning("Invalid material for reward " + id + ": " + materialName);
            return null;
        }
        String displayName = section.getString("display-name", "");
        int amount = section.getInt("amount", 1);
        short data = (short)section.getInt("data", 0);
        List<String> lore = section.getStringList("lore");
        double chance = section.getDouble("chance", 1.0);
        String rarity = section.getString("rarity", "COMMON");
        HashMap<Enchantment, Integer> enchantments = new HashMap<Enchantment, Integer>();
        ConfigurationSection enchSection = section.getConfigurationSection("enchantments");
        if (enchSection != null) {
            for (String enchName : enchSection.getKeys(false)) {
                Enchantment ench = Enchantment.getByName(enchName.toUpperCase());
                if (ench == null) continue;
                enchantments.put(ench, enchSection.getInt(enchName));
            }
        }
        String titleMessage = section.getString("title-message", "&6You caught something!");
        String subtitleMessage = section.getString("subtitle-message", "&e" + displayName);
        String soundName = section.getString("sound", "NOTE_PLING");
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            sound = Sound.NOTE_PLING;
        }
        float soundPitch = (float)section.getDouble("sound-pitch", 1.0);
        float soundVolume = (float)section.getDouble("sound-volume", 1.0);
        List<String> commands = section.getStringList("commands");
        boolean broadcastMessage = section.getBoolean("broadcast", false);
        String broadcastText = section.getString("broadcast-message", "&6{player} &ecaught a &6{item}&e!");
        return new FishingReward(id, displayName, material, amount, data, lore, enchantments, chance, rarity, titleMessage, subtitleMessage, sound, soundPitch, soundVolume, commands, broadcastMessage, broadcastText);
    }

    /**
     * Get random reward without HeadHunting bonus (legacy method)
     */
    public FishingReward getRandomReward() {
        return getRandomReward(null);
    }
    
    /**
     * Get random reward with HeadHunting level bonus
     * Higher levels boost chances for RARE, EPIC, and LEGENDARY items
     * Guardian mask with MASK_FISHING_REWARDS ability unlocks special masked rewards
     */
    public FishingReward getRandomReward(Player player) {
        if (this.rewards.isEmpty() || this.totalWeight <= 0.0) {
            return null;
        }
        
        // Get HeadHunting level bonus
        int hhLevel = (player != null) ? getHeadHuntingLevel(player) : 0;
        double luckMultiplier = getLuckMultiplier(hhLevel);
        
        // Check if player has mask fishing rewards ability (Guardian mask level 5)
        boolean hasMaskFishingRewards = (player != null) && hasMaskFishingRewardsAbility(player);
        
        // Calculate adjusted weights
        double adjustedTotalWeight = 0.0;
        List<Double> adjustedWeights = new ArrayList<>();
        
        for (FishingReward reward : this.rewards) {
            double weight = reward.getChance();
            
            // Check if this is a mask-only reward
            String rarity = reward.getRarity().toUpperCase();
            boolean isMaskedReward = "MASKED".equalsIgnoreCase(rarity) || 
                                     reward.getId().toLowerCase().contains("masked");
            
            // Skip masked rewards if player doesn't have the ability
            if (isMaskedReward && !hasMaskFishingRewards) {
                adjustedWeights.add(0.0);
                continue;
            }
            
            // Apply luck bonus to rare+ items
            if (luckMultiplier > 1.0) {
                switch (rarity) {
                    case "RARE":
                        weight *= luckMultiplier;
                        break;
                    case "EPIC":
                        weight *= (luckMultiplier * 1.25); // Extra bonus for epic
                        break;
                    case "LEGENDARY":
                    case "MASKED":
                        weight *= (luckMultiplier * 1.5); // Even more for legendary/masked
                        break;
                }
            }
            
            adjustedWeights.add(weight);
            adjustedTotalWeight += weight;
        }
        
        if (adjustedTotalWeight <= 0) {
            return this.rewards.get(0); // Fallback
        }
        
        // Roll with adjusted weights
        double roll = this.random.nextDouble() * adjustedTotalWeight;
        double cumulative = 0.0;
        
        for (int i = 0; i < this.rewards.size(); i++) {
            cumulative += adjustedWeights.get(i);
            if (roll < cumulative) {
                return this.rewards.get(i);
            }
        }
        
        return this.rewards.get(this.rewards.size() - 1);
    }
    
    /**
     * Check if player has MASK_FISHING_REWARDS ability (Guardian mask level 5)
     */
    private boolean hasMaskFishingRewardsAbility(Player player) {
        if (!headHuntingEnabled || headHuntingPlugin == null) {
            return false;
        }
        
        try {
            // Try to call AbilityHandler.hasMaskFishingRewards
            Class<?> hhClass = headHuntingPlugin.getClass();
            Object abilityHandler = hhClass.getMethod("getAbilityHandler").invoke(headHuntingPlugin);
            if (abilityHandler != null) {
                Boolean hasAbility = (Boolean) abilityHandler.getClass()
                    .getMethod("hasMaskFishingRewards", Player.class)
                    .invoke(abilityHandler, player);
                return hasAbility != null && hasAbility;
            }
        } catch (Exception e) {
            // Silent fail
        }
        
        return false;
    }

    public int getRewardCount() {
        return this.rewards.size();
    }

    public List<FishingReward> getAllRewards() {
        return new ArrayList<FishingReward>(this.rewards);
    }

    public FishingReward getRewardById(String id) {
        for (FishingReward reward : this.rewards) {
            if (!reward.getId().equalsIgnoreCase(id)) continue;
            return reward;
        }
        return null;
    }
    
    public boolean isHeadHuntingEnabled() {
        return headHuntingEnabled;
    }
}

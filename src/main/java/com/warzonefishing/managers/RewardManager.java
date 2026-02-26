package com.warzonefishing.managers;

import com.warzonefishing.WarzoneFishing;
import com.warzonefishing.models.FishingReward;
import com.warzonefishing.models.FishingReward.RewardType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;

import java.util.*;

/**
 * Manages loading and selecting fishing rewards from configuration
 * Supports weighted random selection and various reward types
 */
public class RewardManager {
    
    private final WarzoneFishing plugin;
    private final List<FishingReward> rewards;
    private final Random random;
    private double totalWeight;
    
    public RewardManager(WarzoneFishing plugin) {
        this.plugin = plugin;
        this.rewards = new ArrayList<>();
        this.random = new Random();
        this.totalWeight = 0.0;
    }
    
    /**
     * Load all rewards from the configuration
     */
    public void loadRewards() {
        rewards.clear();
        totalWeight = 0.0;
        
        ConfigurationSection rewardsSection = plugin.getConfig().getConfigurationSection("rewards");
        if (rewardsSection == null) {
            plugin.getLogger().warning("No 'rewards' section found in config.yml!");
            return;
        }
        
        int loaded = 0;
        int failed = 0;
        
        for (String key : rewardsSection.getKeys(false)) {
            ConfigurationSection section = rewardsSection.getConfigurationSection(key);
            if (section == null) continue;
            
            try {
                FishingReward reward = loadReward(key, section);
                if (reward != null) {
                    rewards.add(reward);
                    totalWeight += reward.getChance();
                    loaded++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load reward '" + key + "': " + e.getMessage());
                failed++;
            }
        }
        
        // Sort by chance (ascending) for weighted selection optimization
        rewards.sort(Comparator.comparingDouble(FishingReward::getChance));
        
        if (failed > 0) {
            plugin.getLogger().warning("Failed to load " + failed + " reward(s). Check your config!");
        }
        
        plugin.getLogger().info("Loaded " + loaded + " rewards with total weight " + String.format("%.2f", totalWeight));
    }
    
    /**
     * Load a single reward from a configuration section
     */
    private FishingReward loadReward(String id, ConfigurationSection section) {
        // Get reward type
        String typeStr = section.getString("type", "ITEM").toUpperCase();
        RewardType type;
        try {
            type = RewardType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            type = RewardType.ITEM;
        }
        
        // Get material (not required for COMMAND type)
        Material material = null;
        if (type != RewardType.COMMAND) {
            String materialName = section.getString("material", "STONE");
            material = Material.getMaterial(materialName.toUpperCase());
            
            if (material == null) {
                plugin.getLogger().warning("Invalid material for reward '" + id + "': " + materialName);
                if (type != RewardType.CUSTOM) {
                    return null;
                }
                // For CUSTOM type with skull-texture, use SKULL_ITEM
                if (section.contains("skull-texture") || section.contains("skull-owner")) {
                    material = Material.SKULL_ITEM;
                } else {
                    return null;
                }
            }
        }
        
        // Basic properties
        String displayName = section.getString("display-name", "");
        int amount = section.getInt("amount", 1);
        short data = (short) section.getInt("data", 0);
        List<String> lore = section.getStringList("lore");
        double chance = section.getDouble("chance", 1.0);
        String rarity = section.getString("rarity", "COMMON");
        
        // Enchantments
        Map<Enchantment, Integer> enchantments = loadEnchantments(section);
        
        // NBT data
        Map<String, Object> nbtData = loadNBTData(section);
        
        // Skull properties
        String skullTexture = section.getString("skull-texture", null);
        String skullOwner = section.getString("skull-owner", null);
        
        // Display settings
        String titleMessage = section.getString("title-message", "&3Fish Caught!");
        String subtitleMessage = section.getString("subtitle-message", "&b" + displayName);
        Sound sound = loadSound(section.getString("sound", "NOTE_PLING"));
        float soundPitch = (float) section.getDouble("sound-pitch", 1.0);
        float soundVolume = (float) section.getDouble("sound-volume", 1.0);
        
        // Actions
        List<String> commands = section.getStringList("commands");
        boolean broadcast = section.getBoolean("broadcast", false);
        String broadcastMessage = section.getString("broadcast-message", 
                "&3&l[FISHING] &b{player} &fcaught a " + displayName + "&f!");
        
        // Flags
        boolean hideFlags = section.getBoolean("hide-flags", false);
        boolean unbreakable = section.getBoolean("unbreakable", false);
        boolean glow = section.getBoolean("glow", false);
        
        // Level requirements (for HeadHunting integration)
        int requiredLevel = section.getInt("required-level", 0);
        boolean requiresGuardianMask = section.getBoolean("requires-guardian-mask", false);
        
        // Validate chance
        if (chance <= 0) {
            plugin.getLogger().warning("Reward '" + id + "' has invalid chance " + chance + ", skipping.");
            return null;
        }
        
        return new FishingReward(
                id, type, displayName, material, amount, data, lore, enchantments,
                chance, rarity, nbtData, skullTexture, skullOwner,
                titleMessage, subtitleMessage, sound, soundPitch, soundVolume,
                commands, broadcast, broadcastMessage,
                hideFlags, unbreakable, glow,
                requiredLevel, requiresGuardianMask
        );
    }
    
    /**
     * Load enchantments from a configuration section
     */
    private Map<Enchantment, Integer> loadEnchantments(ConfigurationSection section) {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        ConfigurationSection enchSection = section.getConfigurationSection("enchantments");
        
        if (enchSection == null) return enchantments;
        
        for (String enchName : enchSection.getKeys(false)) {
            Enchantment ench = Enchantment.getByName(enchName.toUpperCase());
            if (ench != null) {
                enchantments.put(ench, enchSection.getInt(enchName));
            } else {
                plugin.getLogger().warning("Unknown enchantment: " + enchName);
            }
        }
        
        return enchantments;
    }
    
    /**
     * Load NBT data from a configuration section
     */
    private Map<String, Object> loadNBTData(ConfigurationSection section) {
        Map<String, Object> nbtData = new HashMap<>();
        ConfigurationSection nbtSection = section.getConfigurationSection("nbt");
        
        if (nbtSection == null) return nbtData;
        
        for (String key : nbtSection.getKeys(false)) {
            Object value = nbtSection.get(key);
            nbtData.put(key, value);
        }
        
        return nbtData;
    }
    
    /**
     * Load a sound from string name (1.8.8 compatible)
     */
    private Sound loadSound(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return Sound.NOTE_PLING;
        }
        
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try common mappings
            switch (soundName.toUpperCase()) {
                case "LEVEL_UP":
                    return Sound.LEVEL_UP;
                case "ORB_PICKUP":
                    return Sound.ORB_PICKUP;
                case "SPLASH":
                    return Sound.SPLASH;
                case "CLICK":
                    return Sound.CLICK;
                default:
                    plugin.getLogger().warning("Unknown sound: " + soundName + ", using NOTE_PLING");
                    return Sound.NOTE_PLING;
            }
        }
    }
    
    /**
     * Get a random reward based on weights
     * @return A randomly selected reward, or null if no rewards
     */
    public FishingReward getRandomReward() {
        if (rewards.isEmpty() || totalWeight <= 0) {
            return null;
        }
        
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        
        for (FishingReward reward : rewards) {
            cumulative += reward.getChance();
            if (roll < cumulative) {
                return reward;
            }
        }
        
        // Fallback to last reward (shouldn't happen)
        return rewards.get(rewards.size() - 1);
    }
    
    /**
     * Get the total number of loaded rewards
     */
    public int getRewardCount() {
        return rewards.size();
    }
    
    /**
     * Get all loaded rewards
     */
    public List<FishingReward> getAllRewards() {
        return new ArrayList<>(rewards);
    }
    
    /**
     * Get rewards filtered by rarity
     */
    public List<FishingReward> getRewardsByRarity(String rarity) {
        List<FishingReward> filtered = new ArrayList<>();
        for (FishingReward reward : rewards) {
            if (reward.getRarity().equalsIgnoreCase(rarity)) {
                filtered.add(reward);
            }
        }
        return filtered;
    }
    
    /**
     * Get a reward by its ID
     */
    public FishingReward getRewardById(String id) {
        for (FishingReward reward : rewards) {
            if (reward.getId().equalsIgnoreCase(id)) {
                return reward;
            }
        }
        return null;
    }
    
    /**
     * Get the total weight of all rewards
     */
    public double getTotalWeight() {
        return totalWeight;
    }
}

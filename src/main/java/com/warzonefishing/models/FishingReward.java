package com.warzonefishing.models;

import com.warzonefishing.nbt.NBTHelper;
import com.warzonefishing.utils.MessageUtils;
import com.warzonefishing.utils.SkullUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a fishing reward with all its properties
 * Supports custom NBT tags, skull textures, and various reward types
 */
public class FishingReward {
    
    // Reward type enum
    public enum RewardType {
        ITEM,       // Normal item
        CUSTOM,     // Custom item with skull texture or special properties
        COMMAND     // Command-only reward (no item given)
    }
    
    // Basic properties
    private final String id;
    private final RewardType type;
    private final String displayName;
    private final Material material;
    private final int amount;
    private final short data;
    private final List<String> lore;
    private final Map<Enchantment, Integer> enchantments;
    private final double chance;
    private final String rarity;
    
    // NBT properties
    private final Map<String, Object> nbtData;
    
    // Skull properties
    private final String skullTexture;
    private final String skullOwner;
    
    // Display settings
    private final String titleMessage;
    private final String subtitleMessage;
    private final Sound sound;
    private final float soundPitch;
    private final float soundVolume;
    
    // Actions
    private final List<String> commands;
    private final boolean broadcast;
    private final String broadcastMessage;
    
    // Flags
    private final boolean hideFlags;
    private final boolean unbreakable;
    private final boolean glow;
    
    /**
     * Constructor with all properties
     */
    public FishingReward(
            String id,
            RewardType type,
            String displayName,
            Material material,
            int amount,
            short data,
            List<String> lore,
            Map<Enchantment, Integer> enchantments,
            double chance,
            String rarity,
            Map<String, Object> nbtData,
            String skullTexture,
            String skullOwner,
            String titleMessage,
            String subtitleMessage,
            Sound sound,
            float soundPitch,
            float soundVolume,
            List<String> commands,
            boolean broadcast,
            String broadcastMessage,
            boolean hideFlags,
            boolean unbreakable,
            boolean glow
    ) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.material = material;
        this.amount = amount;
        this.data = data;
        this.lore = lore != null ? lore : new ArrayList<>();
        this.enchantments = enchantments;
        this.chance = chance;
        this.rarity = rarity != null ? rarity.toUpperCase() : "COMMON";
        this.nbtData = nbtData;
        this.skullTexture = skullTexture;
        this.skullOwner = skullOwner;
        this.titleMessage = titleMessage;
        this.subtitleMessage = subtitleMessage;
        this.sound = sound;
        this.soundPitch = soundPitch;
        this.soundVolume = soundVolume;
        this.commands = commands != null ? commands : new ArrayList<>();
        this.broadcast = broadcast;
        this.broadcastMessage = broadcastMessage;
        this.hideFlags = hideFlags;
        this.unbreakable = unbreakable;
        this.glow = glow;
    }
    
    // ============ Getters ============
    
    public String getId() {
        return id;
    }
    
    public RewardType getType() {
        return type;
    }
    
    public double getChance() {
        return chance;
    }
    
    public String getRarity() {
        return rarity;
    }
    
    public String getRarityColor() {
        return MessageUtils.getRarityColor(rarity);
    }
    
    public String getTitleMessage() {
        return MessageUtils.color(titleMessage);
    }
    
    public String getSubtitleMessage() {
        return MessageUtils.color(subtitleMessage);
    }
    
    public Sound getSound() {
        return sound;
    }
    
    public float getSoundPitch() {
        return soundPitch;
    }
    
    public float getSoundVolume() {
        return soundVolume;
    }
    
    public List<String> getCommands() {
        return commands;
    }
    
    public boolean shouldBroadcast() {
        return broadcast;
    }
    
    public String getBroadcastMessage() {
        return MessageUtils.color(broadcastMessage);
    }
    
    public boolean hasItem() {
        return type != RewardType.COMMAND;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Create the item stack for this reward
     * Handles normal items, skulls with textures, and NBT tags
     * 
     * @return The created ItemStack or null if COMMAND type
     */
    public ItemStack createItemStack() {
        // Command-only rewards don't create items
        if (type == RewardType.COMMAND) {
            return null;
        }
        
        ItemStack item;
        
        // Handle skull with custom texture
        if (skullTexture != null && !skullTexture.isEmpty()) {
            item = SkullUtils.createSkull(skullTexture, amount);
        }
        // Handle skull with owner name
        else if (skullOwner != null && !skullOwner.isEmpty() && material == Material.SKULL_ITEM && data == 3) {
            item = SkullUtils.createPlayerSkull(skullOwner, amount);
        }
        // Normal item
        else {
            item = new ItemStack(material, amount, data);
        }
        
        // Apply item meta
        ItemMeta meta = item.getItemMeta();
        
        // Set display name
        if (displayName != null && !displayName.isEmpty()) {
            meta.setDisplayName(MessageUtils.color(displayName));
        }
        
        // Set lore
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(MessageUtils.color(line));
            }
            meta.setLore(coloredLore);
        }
        
        // Apply hide flags
        if (hideFlags) {
            meta.addItemFlags(
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_POTION_EFFECTS
            );
        }
        
        // Apply unbreakable
        if (unbreakable) {
            meta.spigot().setUnbreakable(true);
        }
        
        item.setItemMeta(meta);
        
        // Apply enchantments
        if (enchantments != null && !enchantments.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
        }
        
        // Apply glow effect (enchant + hide flags)
        if (glow && (enchantments == null || enchantments.isEmpty())) {
            item.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
            ItemMeta glowMeta = item.getItemMeta();
            glowMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(glowMeta);
        }
        
        // Apply custom NBT tags
        if (nbtData != null && !nbtData.isEmpty()) {
            item = NBTHelper.applyNBT(item, nbtData);
        }
        
        return item;
    }
    
    /**
     * Get the display item name for messages
     * @return The colored display name or material name
     */
    public String getItemDisplayName() {
        if (displayName != null && !displayName.isEmpty()) {
            return MessageUtils.color(displayName);
        }
        
        if (material != null) {
            // Convert MATERIAL_NAME to Material Name
            String name = material.name().replace('_', ' ').toLowerCase();
            StringBuilder result = new StringBuilder();
            boolean capitalizeNext = true;
            for (char c : name.toCharArray()) {
                if (c == ' ') {
                    result.append(c);
                    capitalizeNext = true;
                } else if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        }
        
        return "Unknown Item";
    }
    
    /**
     * Get a formatted string representation for the list command
     * @return Formatted reward info
     */
    public String getListEntry() {
        String rarityColored = MessageUtils.color(getRarityColor() + rarity);
        String chanceStr = String.format("%.2f", chance);
        return MessageUtils.color(
                getRarityColor() + "• &f" + id + 
                " &7- Chance: &b" + chanceStr + 
                " &7- Rarity: " + rarityColored
        );
    }
    
    @Override
    public String toString() {
        return "FishingReward{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", material=" + material +
                ", rarity='" + rarity + '\'' +
                ", chance=" + chance +
                '}';
    }
}

package com.warzonefishing.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Utility class for creating custom skull items with Base64 textures
 * Compatible with 1.8.8
 * 
 * Example texture (from HeadHunting):
 * "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJjMTIzIn19fQ=="
 */
public class SkullUtils {
    
    private static final String VERSION;
    
    static {
        VERSION = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    }
    
    /**
     * Create a player skull with a custom texture
     * @param texture Base64 encoded texture string
     * @return ItemStack skull with the texture applied
     */
    public static ItemStack createSkull(String texture) {
        return createSkull(texture, 1);
    }
    
    /**
     * Create a player skull with a custom texture and amount
     * @param texture Base64 encoded texture string
     * @param amount Stack size
     * @return ItemStack skull with the texture applied
     */
    public static ItemStack createSkull(String texture, int amount) {
        // SKULL_ITEM with data 3 = player head
        ItemStack skull = new ItemStack(Material.SKULL_ITEM, amount, (short) 3);
        
        if (texture == null || texture.isEmpty()) {
            return skull;
        }
        
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        try {
            // Create GameProfile with unique UUID
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfileClass.getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), null);
            
            // Get properties from profile
            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object propertyMap = getProperties.invoke(profile);
            
            // Create Property with texture
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", texture);
            
            // Add property to map using Multimap.put
            propertyMap.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(propertyMap, "textures", property);
            
            // Set profile on SkullMeta using reflection
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            
            skull.setItemMeta(meta);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[WarzoneFishing] Failed to apply skull texture: " + e.getMessage());
        }
        
        return skull;
    }
    
    /**
     * Create a skull from a player's name (will fetch their actual skin)
     * @param playerName The player's name
     * @return ItemStack skull with the player's skin
     */
    public static ItemStack createPlayerSkull(String playerName) {
        return createPlayerSkull(playerName, 1);
    }
    
    /**
     * Create a skull from a player's name with amount
     * @param playerName The player's name
     * @param amount Stack size
     * @return ItemStack skull with the player's skin
     */
    public static ItemStack createPlayerSkull(String playerName, int amount) {
        ItemStack skull = new ItemStack(Material.SKULL_ITEM, amount, (short) 3);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwner(playerName);
        skull.setItemMeta(meta);
        return skull;
    }
    
    /**
     * Apply a custom texture to an existing skull
     * @param skull The skull ItemStack (must be SKULL_ITEM with data 3)
     * @param texture Base64 encoded texture string
     * @return The modified skull
     */
    public static ItemStack applyTexture(ItemStack skull, String texture) {
        if (skull == null || skull.getType() != Material.SKULL_ITEM || skull.getDurability() != 3) {
            return skull;
        }
        
        if (texture == null || texture.isEmpty()) {
            return skull;
        }
        
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfileClass.getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), null);
            
            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object propertyMap = getProperties.invoke(profile);
            
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", texture);
            
            propertyMap.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(propertyMap, "textures", property);
            
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            
            skull.setItemMeta(meta);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[WarzoneFishing] Failed to apply skull texture: " + e.getMessage());
        }
        
        return skull;
    }
    
    /**
     * Check if an item is a player skull
     * @param item The item to check
     * @return true if it's a player skull
     */
    public static boolean isPlayerSkull(ItemStack item) {
        return item != null && item.getType() == Material.SKULL_ITEM && item.getDurability() == 3;
    }
}

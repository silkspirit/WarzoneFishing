package com.warzonefishing.nbt;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * NBT Helper for 1.8.8 using NMS reflection
 * Allows adding custom NBT tags to items for ShopGUIPlus integration
 * 
 * Example usage:
 *   ItemStack fish = new ItemStack(Material.RAW_FISH);
 *   fish = NBTHelper.setString(fish, "warzone_rarity", "rare");
 *   fish = NBTHelper.setInt(fish, "sell_value", 50);
 */
public class NBTHelper {
    
    private static final String VERSION;
    private static boolean nmsAvailable = true;
    
    static {
        VERSION = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        
        // Test if NMS is available
        try {
            Class.forName("net.minecraft.server." + VERSION + ".NBTTagCompound");
        } catch (ClassNotFoundException e) {
            nmsAvailable = false;
            Bukkit.getLogger().warning("[WarzoneFishing] NMS not available - NBT features disabled");
        }
    }
    
    /**
     * Check if NBT functionality is available
     * @return true if NMS is accessible
     */
    public static boolean isAvailable() {
        return nmsAvailable;
    }
    
    /**
     * Set a string NBT tag on an item
     * @param item The item to modify
     * @param key The NBT key
     * @param value The string value
     * @return The modified item
     */
    public static ItemStack setString(ItemStack item, String key, String value) {
        if (!nmsAvailable || item == null) return item;
        
        try {
            Object nmsItem = asNMSCopy(item);
            Object compound = getOrCreateTag(nmsItem);
            
            compound.getClass().getMethod("setString", String.class, String.class)
                    .invoke(compound, key, value);
            
            setTag(nmsItem, compound);
            return asBukkitCopy(nmsItem);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[WarzoneFishing] Failed to set NBT string: " + e.getMessage());
            return item;
        }
    }
    
    /**
     * Set an integer NBT tag on an item
     * @param item The item to modify
     * @param key The NBT key
     * @param value The integer value
     * @return The modified item
     */
    public static ItemStack setInt(ItemStack item, String key, int value) {
        if (!nmsAvailable || item == null) return item;
        
        try {
            Object nmsItem = asNMSCopy(item);
            Object compound = getOrCreateTag(nmsItem);
            
            compound.getClass().getMethod("setInt", String.class, int.class)
                    .invoke(compound, key, value);
            
            setTag(nmsItem, compound);
            return asBukkitCopy(nmsItem);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[WarzoneFishing] Failed to set NBT int: " + e.getMessage());
            return item;
        }
    }
    
    /**
     * Set a double NBT tag on an item
     * @param item The item to modify
     * @param key The NBT key
     * @param value The double value
     * @return The modified item
     */
    public static ItemStack setDouble(ItemStack item, String key, double value) {
        if (!nmsAvailable || item == null) return item;
        
        try {
            Object nmsItem = asNMSCopy(item);
            Object compound = getOrCreateTag(nmsItem);
            
            compound.getClass().getMethod("setDouble", String.class, double.class)
                    .invoke(compound, key, value);
            
            setTag(nmsItem, compound);
            return asBukkitCopy(nmsItem);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[WarzoneFishing] Failed to set NBT double: " + e.getMessage());
            return item;
        }
    }
    
    /**
     * Set a boolean NBT tag on an item
     * @param item The item to modify
     * @param key The NBT key
     * @param value The boolean value
     * @return The modified item
     */
    public static ItemStack setBoolean(ItemStack item, String key, boolean value) {
        if (!nmsAvailable || item == null) return item;
        
        try {
            Object nmsItem = asNMSCopy(item);
            Object compound = getOrCreateTag(nmsItem);
            
            compound.getClass().getMethod("setBoolean", String.class, boolean.class)
                    .invoke(compound, key, value);
            
            setTag(nmsItem, compound);
            return asBukkitCopy(nmsItem);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[WarzoneFishing] Failed to set NBT boolean: " + e.getMessage());
            return item;
        }
    }
    
    /**
     * Apply multiple NBT tags from a map
     * @param item The item to modify
     * @param nbtData Map of key-value pairs
     * @return The modified item
     */
    public static ItemStack applyNBT(ItemStack item, Map<String, Object> nbtData) {
        if (!nmsAvailable || item == null || nbtData == null || nbtData.isEmpty()) {
            return item;
        }
        
        ItemStack result = item;
        for (Map.Entry<String, Object> entry : nbtData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String) {
                result = setString(result, key, (String) value);
            } else if (value instanceof Integer) {
                result = setInt(result, key, (Integer) value);
            } else if (value instanceof Double) {
                result = setDouble(result, key, (Double) value);
            } else if (value instanceof Boolean) {
                result = setBoolean(result, key, (Boolean) value);
            } else if (value instanceof Number) {
                // Handle other number types
                result = setDouble(result, key, ((Number) value).doubleValue());
            } else {
                // Convert to string
                result = setString(result, key, String.valueOf(value));
            }
        }
        
        return result;
    }
    
    /**
     * Get a string NBT tag from an item
     * @param item The item to read
     * @param key The NBT key
     * @return The string value or null if not found
     */
    public static String getString(ItemStack item, String key) {
        if (!nmsAvailable || item == null) return null;
        
        try {
            Object nmsItem = asNMSCopy(item);
            Object compound = getTag(nmsItem);
            
            if (compound == null) return null;
            
            Boolean hasKey = (Boolean) compound.getClass().getMethod("hasKey", String.class)
                    .invoke(compound, key);
            
            if (!hasKey) return null;
            
            return (String) compound.getClass().getMethod("getString", String.class)
                    .invoke(compound, key);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get an integer NBT tag from an item
     * @param item The item to read
     * @param key The NBT key
     * @return The integer value or 0 if not found
     */
    public static int getInt(ItemStack item, String key) {
        if (!nmsAvailable || item == null) return 0;
        
        try {
            Object nmsItem = asNMSCopy(item);
            Object compound = getTag(nmsItem);
            
            if (compound == null) return 0;
            
            Boolean hasKey = (Boolean) compound.getClass().getMethod("hasKey", String.class)
                    .invoke(compound, key);
            
            if (!hasKey) return 0;
            
            return (Integer) compound.getClass().getMethod("getInt", String.class)
                    .invoke(compound, key);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Check if an item has a specific NBT key
     * @param item The item to check
     * @param key The NBT key
     * @return true if the key exists
     */
    public static boolean hasKey(ItemStack item, String key) {
        if (!nmsAvailable || item == null) return false;
        
        try {
            Object nmsItem = asNMSCopy(item);
            Object compound = getTag(nmsItem);
            
            if (compound == null) return false;
            
            return (Boolean) compound.getClass().getMethod("hasKey", String.class)
                    .invoke(compound, key);
        } catch (Exception e) {
            return false;
        }
    }
    
    // ============ Private Helper Methods ============
    
    private static Object asNMSCopy(ItemStack item) throws Exception {
        Class<?> craftItemStack = getCraftBukkitClass("inventory.CraftItemStack");
        Method asNMSCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
        return asNMSCopy.invoke(null, item);
    }
    
    private static ItemStack asBukkitCopy(Object nmsItem) throws Exception {
        Class<?> craftItemStack = getCraftBukkitClass("inventory.CraftItemStack");
        Class<?> nmsItemStack = getNMSClass("ItemStack");
        Method asBukkitCopy = craftItemStack.getMethod("asBukkitCopy", nmsItemStack);
        return (ItemStack) asBukkitCopy.invoke(null, nmsItem);
    }
    
    private static Object getOrCreateTag(Object nmsItem) throws Exception {
        Object tag = getTag(nmsItem);
        if (tag == null) {
            tag = getNMSClass("NBTTagCompound").newInstance();
        }
        return tag;
    }
    
    private static Object getTag(Object nmsItem) throws Exception {
        return nmsItem.getClass().getMethod("getTag").invoke(nmsItem);
    }
    
    private static void setTag(Object nmsItem, Object compound) throws Exception {
        Class<?> nbtClass = getNMSClass("NBTTagCompound");
        nmsItem.getClass().getMethod("setTag", nbtClass).invoke(nmsItem, compound);
    }
    
    private static Class<?> getNMSClass(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + VERSION + "." + name);
    }
    
    private static Class<?> getCraftBukkitClass(String name) throws ClassNotFoundException {
        return Class.forName("org.bukkit.craftbukkit." + VERSION + "." + name);
    }
}

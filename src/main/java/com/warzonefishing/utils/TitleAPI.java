package com.warzonefishing.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;

/**
 * Title API for 1.8.8 using NMS reflection
 * Sends title, subtitle, and action bar messages to players
 */
public class TitleAPI {
    
    private static final String VERSION;
    
    static {
        // Get the server version (e.g., v1_8_R3)
        VERSION = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    }
    
    /**
     * Send a title and subtitle to a player
     * @param player The player to send to
     * @param title The title text (can be null)
     * @param subtitle The subtitle text (can be null)
     * @param fadeIn Fade in time in ticks
     * @param stay Display time in ticks
     * @param fadeOut Fade out time in ticks
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            // Send timing packet first
            sendTimings(player, fadeIn, stay, fadeOut);
            
            // Send title if present
            if (title != null && !title.isEmpty()) {
                Object titlePacket = createTitlePacket(title, TitleAction.TITLE);
                sendPacket(player, titlePacket);
            }
            
            // Send subtitle if present
            if (subtitle != null && !subtitle.isEmpty()) {
                Object subtitlePacket = createTitlePacket(subtitle, TitleAction.SUBTITLE);
                sendPacket(player, subtitlePacket);
            }
        } catch (Exception e) {
            // Fallback to chat message if title fails
            if (title != null) player.sendMessage(MessageUtils.color(title));
            if (subtitle != null) player.sendMessage(MessageUtils.color(subtitle));
        }
    }
    
    /**
     * Send an action bar message to a player
     * @param player The player to send to
     * @param message The message to display
     */
    public static void sendActionBar(Player player, String message) {
        try {
            Object chatComponent = getNMSClass("IChatBaseComponent").getDeclaredClasses()[0]
                    .getMethod("a", String.class)
                    .invoke(null, "{\"text\":\"" + message + "\"}");
            
            Object packet = getNMSClass("PacketPlayOutChat")
                    .getConstructor(getNMSClass("IChatBaseComponent"), byte.class)
                    .newInstance(chatComponent, (byte) 2);
            
            sendPacket(player, packet);
        } catch (Exception e) {
            // Fallback to regular message
            player.sendMessage(MessageUtils.color(message));
        }
    }
    
    /**
     * Clear the title from a player's screen
     * @param player The player to clear
     */
    public static void clearTitle(Player player) {
        try {
            Class<?> packetClass = getNMSClass("PacketPlayOutTitle");
            Class<?> actionClass = getNMSClass("PacketPlayOutTitle$EnumTitleAction");
            
            Object clearAction = actionClass.getField("CLEAR").get(null);
            Constructor<?> constructor = packetClass.getConstructor(actionClass, getNMSClass("IChatBaseComponent"));
            Object packet = constructor.newInstance(clearAction, null);
            
            sendPacket(player, packet);
        } catch (Exception ignored) {
            // Silently fail - title will disappear on its own
        }
    }
    
    /**
     * Reset title settings for a player
     * @param player The player to reset
     */
    public static void resetTitle(Player player) {
        try {
            Class<?> packetClass = getNMSClass("PacketPlayOutTitle");
            Class<?> actionClass = getNMSClass("PacketPlayOutTitle$EnumTitleAction");
            
            Object resetAction = actionClass.getField("RESET").get(null);
            Constructor<?> constructor = packetClass.getConstructor(actionClass, getNMSClass("IChatBaseComponent"));
            Object packet = constructor.newInstance(resetAction, null);
            
            sendPacket(player, packet);
        } catch (Exception ignored) {
            // Silently fail
        }
    }
    
    /**
     * Send title timing packet
     */
    private static void sendTimings(Player player, int fadeIn, int stay, int fadeOut) throws Exception {
        Class<?> packetClass = getNMSClass("PacketPlayOutTitle");
        Class<?> actionClass = getNMSClass("PacketPlayOutTitle$EnumTitleAction");
        
        Object timingAction = actionClass.getField("TIMES").get(null);
        Constructor<?> constructor = packetClass.getConstructor(
                actionClass, 
                getNMSClass("IChatBaseComponent"), 
                int.class, int.class, int.class
        );
        Object packet = constructor.newInstance(timingAction, null, fadeIn, stay, fadeOut);
        
        sendPacket(player, packet);
    }
    
    /**
     * Create a title packet with the given text and action
     */
    private static Object createTitlePacket(String text, TitleAction action) throws Exception {
        Class<?> packetClass = getNMSClass("PacketPlayOutTitle");
        Class<?> actionClass = getNMSClass("PacketPlayOutTitle$EnumTitleAction");
        Class<?> chatClass = getNMSClass("IChatBaseComponent");
        
        Object actionEnum = actionClass.getField(action.name()).get(null);
        
        // Create chat component from JSON
        Object chatComponent = chatClass.getDeclaredClasses()[0]
                .getMethod("a", String.class)
                .invoke(null, "{\"text\":\"" + escapeJson(text) + "\"}");
        
        Constructor<?> constructor = packetClass.getConstructor(actionClass, chatClass);
        return constructor.newInstance(actionEnum, chatComponent);
    }
    
    /**
     * Send a packet to a player
     */
    private static void sendPacket(Player player, Object packet) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
        playerConnection.getClass()
                .getMethod("sendPacket", getNMSClass("Packet"))
                .invoke(playerConnection, packet);
    }
    
    /**
     * Get an NMS class by name
     */
    private static Class<?> getNMSClass(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + VERSION + "." + name);
    }
    
    /**
     * Escape special characters for JSON
     */
    private static String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Title action types
     */
    private enum TitleAction {
        TITLE,
        SUBTITLE,
        TIMES,
        CLEAR,
        RESET
    }
}

package com.warzonefishing.utils;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Utility class for message formatting with teal/aqua theme
 * 
 * Theme Colors:
 * - Primary: &3 (Dark Aqua)
 * - Secondary: &b (Aqua)
 * - Accent: &f (White)
 * - Error: &c (Red)
 * - Success: &a (Green)
 * 
 * Rarity Colors:
 * - COMMON: &7 (Gray)
 * - UNCOMMON: &a (Green)
 * - RARE: &3 (Dark Aqua)
 * - EPIC: &5 (Dark Purple)
 * - LEGENDARY: &6 (Gold)
 */
public class MessageUtils {
    
    // Theme prefix
    public static final String PREFIX = "&3&l「&b&lWZ&3&l」&r ";
    
    // Rarity color codes
    public static final String COMMON_COLOR = "&7";
    public static final String UNCOMMON_COLOR = "&a";
    public static final String RARE_COLOR = "&3";
    public static final String EPIC_COLOR = "&5";
    public static final String LEGENDARY_COLOR = "&6";
    
    /**
     * Translate color codes in a string
     * @param message The message with & color codes
     * @return Translated message with actual color codes
     */
    public static String color(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Strip color codes from a string
     * @param message The message with color codes
     * @return Plain text without colors
     */
    public static String stripColor(String message) {
        return ChatColor.stripColor(color(message));
    }
    
    /**
     * Send a prefixed message to a player
     * @param player The player to send to
     * @param message The message to send
     */
    public static void send(Player player, String message) {
        player.sendMessage(color(PREFIX + message));
    }
    
    /**
     * Send a message without prefix
     * @param player The player to send to
     * @param message The message to send
     */
    public static void sendRaw(Player player, String message) {
        player.sendMessage(color(message));
    }
    
    /**
     * Send an error message
     * @param player The player to send to
     * @param message The error message
     */
    public static void sendError(Player player, String message) {
        player.sendMessage(color(PREFIX + "&c" + message));
    }
    
    /**
     * Send a success message
     * @param player The player to send to
     * @param message The success message
     */
    public static void sendSuccess(Player player, String message) {
        player.sendMessage(color(PREFIX + "&a" + message));
    }
    
    /**
     * Get the color code for a rarity
     * @param rarity The rarity string
     * @return The color code for that rarity
     */
    public static String getRarityColor(String rarity) {
        if (rarity == null) return COMMON_COLOR;
        
        switch (rarity.toUpperCase()) {
            case "LEGENDARY":
                return LEGENDARY_COLOR;
            case "EPIC":
                return EPIC_COLOR;
            case "RARE":
                return RARE_COLOR;
            case "UNCOMMON":
                return UNCOMMON_COLOR;
            case "COMMON":
            default:
                return COMMON_COLOR;
        }
    }
    
    /**
     * Get the ChatColor for a rarity
     * @param rarity The rarity string
     * @return The ChatColor for that rarity
     */
    public static ChatColor getRarityChatColor(String rarity) {
        if (rarity == null) return ChatColor.GRAY;
        
        switch (rarity.toUpperCase()) {
            case "LEGENDARY":
                return ChatColor.GOLD;
            case "EPIC":
                return ChatColor.DARK_PURPLE;
            case "RARE":
                return ChatColor.DARK_AQUA;
            case "UNCOMMON":
                return ChatColor.GREEN;
            case "COMMON":
            default:
                return ChatColor.GRAY;
        }
    }
    
    /**
     * Format a rarity string with its color
     * @param rarity The rarity to format
     * @return Colored rarity string
     */
    public static String formatRarity(String rarity) {
        return color(getRarityColor(rarity) + rarity);
    }
    
    /**
     * Create a header line for menus
     * @param title The title to display
     * @return Formatted header
     */
    public static String createHeader(String title) {
        return color("&3═══════ &b" + title + " &3═══════");
    }
    
    /**
     * Create a footer line for menus
     * @return Formatted footer
     */
    public static String createFooter() {
        return color("&3═══════════════════════════════");
    }
}

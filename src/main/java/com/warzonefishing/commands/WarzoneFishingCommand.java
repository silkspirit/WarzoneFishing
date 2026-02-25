package com.warzonefishing.commands;

import com.warzonefishing.WarzoneFishing;
import com.warzonefishing.models.FishingReward;
import com.warzonefishing.utils.MessageUtils;
import com.warzonefishing.utils.TitleAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main command handler for WarzoneFishing
 * 
 * Commands:
 * /wf reload - Reload configuration
 * /wf list [rarity] - List all rewards
 * /wf give <player> <reward> [amount] - Give a reward to a player
 * /wf test - Test a random reward
 * /wf preview <reward> - Preview a specific reward
 * /wf info - Show plugin information
 */
public class WarzoneFishingCommand implements CommandExecutor, TabCompleter {
    
    private final WarzoneFishing plugin;
    private final List<String> subCommands = Arrays.asList(
            "reload", "list", "give", "test", "preview", "info"
    );
    private final List<String> rarities = Arrays.asList(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"
    );
    
    public WarzoneFishingCommand(WarzoneFishing plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
            case "list":
                handleList(sender, args);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "test":
                handleTest(sender);
                break;
            case "preview":
                handlePreview(sender, args);
                break;
            case "info":
                handleInfo(sender);
                break;
            default:
                sendHelp(sender, label);
                break;
        }
        
        return true;
    }
    
    /**
     * Send help message
     */
    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(MessageUtils.createHeader("WarzoneFishing"));
        sender.sendMessage(MessageUtils.color("&b/" + label + " reload &7- Reload configuration"));
        sender.sendMessage(MessageUtils.color("&b/" + label + " list [rarity] &7- List rewards"));
        sender.sendMessage(MessageUtils.color("&b/" + label + " give <player> <reward> &7- Give reward"));
        sender.sendMessage(MessageUtils.color("&b/" + label + " test &7- Test random reward"));
        sender.sendMessage(MessageUtils.color("&b/" + label + " preview <reward> &7- Preview reward"));
        sender.sendMessage(MessageUtils.color("&b/" + label + " info &7- Plugin information"));
        sender.sendMessage(MessageUtils.createFooter());
    }
    
    /**
     * Handle reload command
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("warzonefishing.admin")) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + "&cNo permission!"));
            return;
        }
        
        plugin.reload();
        sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                "&aReloaded! &fLoaded &b" + plugin.getRewardManager().getRewardCount() + " &frewards."));
    }
    
    /**
     * Handle list command
     */
    private void handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("warzonefishing.admin")) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + "&cNo permission!"));
            return;
        }
        
        List<FishingReward> rewards;
        String title;
        
        // Filter by rarity if specified
        if (args.length > 1) {
            String rarity = args[1].toUpperCase();
            rewards = plugin.getRewardManager().getRewardsByRarity(rarity);
            title = MessageUtils.getRarityColor(rarity) + rarity + " &fRewards";
        } else {
            rewards = plugin.getRewardManager().getAllRewards();
            title = "All Rewards";
        }
        
        sender.sendMessage(MessageUtils.createHeader(title));
        
        if (rewards.isEmpty()) {
            sender.sendMessage(MessageUtils.color("&7No rewards found."));
        } else {
            for (FishingReward reward : rewards) {
                sender.sendMessage(reward.getListEntry());
            }
            sender.sendMessage(MessageUtils.color("&7Total: &b" + rewards.size() + " &7rewards"));
        }
        
        sender.sendMessage(MessageUtils.createFooter());
    }
    
    /**
     * Handle give command
     */
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("warzonefishing.admin")) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + "&cNo permission!"));
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&cUsage: /wf give <player> <reward> [amount]"));
            return;
        }
        
        // Find player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&cPlayer not found: &f" + args[1]));
            return;
        }
        
        // Find reward
        FishingReward reward = plugin.getRewardManager().getRewardById(args[2]);
        if (reward == null) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&cReward not found: &f" + args[2]));
            return;
        }
        
        // Get amount
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                        "&cInvalid amount: &f" + args[3]));
                return;
            }
        }
        
        // Give reward(s)
        if (reward.hasItem()) {
            for (int i = 0; i < amount; i++) {
                ItemStack item = reward.createItemStack();
                target.getInventory().addItem(item);
            }
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&aGave &f" + amount + "x " + reward.getItemDisplayName() + 
                    " &ato &b" + target.getName()));
        }
        
        // Execute commands
        for (String cmd : reward.getCommands()) {
            String parsed = cmd.replace("{player}", target.getName());
            for (int i = 0; i < amount; i++) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
        
        // Notify target
        target.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                "&aYou received a fishing reward!"));
    }
    
    /**
     * Handle test command (gives a random reward)
     */
    private void handleTest(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&cThis command requires a player!"));
            return;
        }
        
        if (!sender.hasPermission("warzonefishing.admin")) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + "&cNo permission!"));
            return;
        }
        
        Player player = (Player) sender;
        FishingReward reward = plugin.getRewardManager().getRandomReward();
        
        if (reward == null) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&cNo rewards configured!"));
            return;
        }
        
        // Give item
        ItemStack item = null;
        if (reward.hasItem()) {
            item = reward.createItemStack();
            player.getInventory().addItem(item);
        }
        
        // Send title
        String title = reward.getTitleMessage()
                .replace("{player}", player.getName())
                .replace("{rarity}", reward.getRarity());
        String subtitle = reward.getSubtitleMessage()
                .replace("{item}", item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
                        ? item.getItemMeta().getDisplayName() : reward.getItemDisplayName())
                .replace("{rarity}", reward.getRarity());
        
        TitleAPI.sendTitle(player, title, subtitle, 10, 40, 10);
        
        // Play sound
        if (reward.getSound() != null) {
            player.playSound(player.getLocation(), reward.getSound(), 
                    reward.getSoundVolume(), reward.getSoundPitch());
        }
        
        // Execute commands
        for (String cmd : reward.getCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                    cmd.replace("{player}", player.getName()));
        }
        
        sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                "&fTested: " + MessageUtils.getRarityColor(reward.getRarity()) + 
                reward.getId() + " &7(Rarity: " + reward.getRarity() + ")"));
    }
    
    /**
     * Handle preview command
     */
    private void handlePreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&cThis command requires a player!"));
            return;
        }
        
        if (!sender.hasPermission("warzonefishing.admin")) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + "&cNo permission!"));
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&cUsage: /wf preview <reward>"));
            return;
        }
        
        FishingReward reward = plugin.getRewardManager().getRewardById(args[1]);
        if (reward == null) {
            sender.sendMessage(MessageUtils.color(MessageUtils.PREFIX + 
                    "&cReward not found: &f" + args[1]));
            return;
        }
        
        Player player = (Player) sender;
        
        // Show info
        sender.sendMessage(MessageUtils.createHeader("Reward Preview"));
        sender.sendMessage(MessageUtils.color("&bID: &f" + reward.getId()));
        sender.sendMessage(MessageUtils.color("&bType: &f" + reward.getType()));
        sender.sendMessage(MessageUtils.color("&bRarity: " + 
                MessageUtils.getRarityColor(reward.getRarity()) + reward.getRarity()));
        sender.sendMessage(MessageUtils.color("&bChance: &f" + reward.getChance()));
        sender.sendMessage(MessageUtils.color("&bDisplay: &f" + reward.getItemDisplayName()));
        
        if (!reward.getCommands().isEmpty()) {
            sender.sendMessage(MessageUtils.color("&bCommands: &f" + reward.getCommands().size()));
        }
        
        // Show title preview
        String title = reward.getTitleMessage().replace("{rarity}", reward.getRarity());
        String subtitle = reward.getSubtitleMessage()
                .replace("{item}", reward.getItemDisplayName())
                .replace("{rarity}", reward.getRarity());
        TitleAPI.sendTitle(player, title, subtitle, 10, 60, 10);
        
        // Play sound
        if (reward.getSound() != null) {
            player.playSound(player.getLocation(), reward.getSound(), 
                    reward.getSoundVolume(), reward.getSoundPitch());
        }
        
        sender.sendMessage(MessageUtils.createFooter());
    }
    
    /**
     * Handle info command
     */
    private void handleInfo(CommandSender sender) {
        sender.sendMessage(MessageUtils.createHeader("WarzoneFishing Info"));
        sender.sendMessage(MessageUtils.color("&bVersion: &f" + plugin.getDescription().getVersion()));
        sender.sendMessage(MessageUtils.color("&bRewards: &f" + plugin.getRewardManager().getRewardCount()));
        sender.sendMessage(MessageUtils.color("&bTotal Weight: &f" + 
                String.format("%.2f", plugin.getRewardManager().getTotalWeight())));
        sender.sendMessage(MessageUtils.color("&bClaim Plugin: &f" + 
                plugin.getConfig().getString("settings.claim-plugin", "factions")));
        
        // Show reward breakdown by rarity
        sender.sendMessage(MessageUtils.color("&7--- Rewards by Rarity ---"));
        for (String rarity : rarities) {
            int count = plugin.getRewardManager().getRewardsByRarity(rarity).size();
            if (count > 0) {
                sender.sendMessage(MessageUtils.color(MessageUtils.getRarityColor(rarity) + 
                        rarity + ": &f" + count));
            }
        }
        
        sender.sendMessage(MessageUtils.createFooter());
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Sub commands
            String partial = args[0].toLowerCase();
            completions = subCommands.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String partial = args[1].toLowerCase();
            
            if (subCommand.equals("list")) {
                // Rarity filter
                completions = rarities.stream()
                        .filter(s -> s.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("give")) {
                // Online players
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("preview")) {
                // Reward IDs
                completions = plugin.getRewardManager().getAllRewards().stream()
                        .map(FishingReward::getId)
                        .filter(s -> s.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Reward IDs for give command
            String partial = args[2].toLowerCase();
            completions = plugin.getRewardManager().getAllRewards().stream()
                    .map(FishingReward::getId)
                    .filter(s -> s.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        
        return completions;
    }
}

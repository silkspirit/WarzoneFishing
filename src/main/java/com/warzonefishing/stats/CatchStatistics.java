package com.warzonefishing.stats;

import com.warzonefishing.WarzoneFishing;
import com.warzonefishing.models.FishingReward;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * SQLite-backed catch statistics tracker.
 * Records per-player fishing data including catch counts, rarity breakdowns,
 * unique discoveries, and first-catch timestamps.
 */
public class CatchStatistics {

    private final WarzoneFishing plugin;
    private Connection connection;

    /**
     * Represents a leaderboard entry
     */
    public static class LeaderboardEntry {
        private final String uuid;
        private final String name;
        private final int totalCatches;

        public LeaderboardEntry(String uuid, String name, int totalCatches) {
            this.uuid = uuid;
            this.name = name;
            this.totalCatches = totalCatches;
        }

        public String getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public int getTotalCatches() {
            return totalCatches;
        }
    }

    public CatchStatistics(WarzoneFishing plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    /**
     * Initialize the SQLite database and create tables if needed
     */
    private void initDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "catch-stats.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            // Create tables
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS catch_stats (" +
                    "    uuid TEXT NOT NULL," +
                    "    reward_id TEXT NOT NULL," +
                    "    rarity TEXT NOT NULL DEFAULT 'COMMON'," +
                    "    count INTEGER DEFAULT 0," +
                    "    first_caught INTEGER DEFAULT 0," +
                    "    PRIMARY KEY (uuid, reward_id)" +
                    ")"
                );
                stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_catch_stats_uuid ON catch_stats(uuid)"
                );
            }

            // Add rarity column if it doesn't exist (migration for existing DBs)
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("ALTER TABLE catch_stats ADD COLUMN rarity TEXT NOT NULL DEFAULT 'COMMON'");
            } catch (SQLException ignored) {
                // Column already exists — expected
            }

            plugin.getLogger().info("Catch statistics database initialized.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize catch statistics database!", e);
        }
    }

    /**
     * Record a catch for a player. Increments the counter and sets first_caught if this is
     * the first time they've caught this reward.
     *
     * @param player   The player's UUID
     * @param rewardId The reward ID
     * @param rarity   The reward rarity (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
     * @return true if this was a NEW discovery (first time catching this reward), false otherwise
     */
    public boolean recordCatch(UUID player, String rewardId, String rarity) {
        if (connection == null) return false;

        String uuid = player.toString();
        long now = System.currentTimeMillis();

        try {
            // Check if this is a first-time discovery
            boolean isNew = false;
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT count FROM catch_stats WHERE uuid = ? AND reward_id = ?")) {
                check.setString(1, uuid);
                check.setString(2, rewardId);
                ResultSet rs = check.executeQuery();
                if (!rs.next()) {
                    isNew = true;
                }
            }

            // INSERT OR UPDATE
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO catch_stats (uuid, reward_id, rarity, count, first_caught) VALUES (" +
                    "    ?, ?, ?, " +
                    "    COALESCE((SELECT count FROM catch_stats WHERE uuid = ? AND reward_id = ?), 0) + 1, " +
                    "    CASE WHEN (SELECT first_caught FROM catch_stats WHERE uuid = ? AND reward_id = ?) IS NULL " +
                    "         OR (SELECT first_caught FROM catch_stats WHERE uuid = ? AND reward_id = ?) = 0 " +
                    "         THEN ? ELSE (SELECT first_caught FROM catch_stats WHERE uuid = ? AND reward_id = ?) END" +
                    ")")) {
                stmt.setString(1, uuid);
                stmt.setString(2, rewardId);
                stmt.setString(3, rarity != null ? rarity.toUpperCase() : "COMMON");
                stmt.setString(4, uuid);
                stmt.setString(5, rewardId);
                stmt.setString(6, uuid);
                stmt.setString(7, rewardId);
                stmt.setString(8, uuid);
                stmt.setString(9, rewardId);
                stmt.setLong(10, now);
                stmt.setString(11, uuid);
                stmt.setString(12, rewardId);
                stmt.executeUpdate();
            }

            return isNew;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record catch for " + uuid, e);
            return false;
        }
    }

    /**
     * Get full player stats
     *
     * @param player The player's UUID
     * @return PlayerCatchStats with all data
     */
    public PlayerCatchStats getPlayerStats(UUID player) {
        if (connection == null) {
            return new PlayerCatchStats(0, 0, getTotalRewardCount(), 
                    new HashMap<String, Integer>(), new HashMap<String, Integer>());
        }

        String uuid = player.toString();
        int totalCatches = 0;
        int uniqueDiscovered = 0;
        Map<String, Integer> catchesByRarity = new HashMap<String, Integer>();
        Map<String, Integer> catchesByReward = new HashMap<String, Integer>();

        try {
            // Get all rows for this player
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT reward_id, rarity, count FROM catch_stats WHERE uuid = ?")) {
                stmt.setString(1, uuid);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String rewardId = rs.getString("reward_id");
                    String rarity = rs.getString("rarity");
                    int count = rs.getInt("count");

                    totalCatches += count;
                    uniqueDiscovered++;
                    catchesByReward.put(rewardId, count);

                    // Aggregate by rarity
                    int current = catchesByRarity.containsKey(rarity) ? catchesByRarity.get(rarity) : 0;
                    catchesByRarity.put(rarity, current + count);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get stats for " + uuid, e);
        }

        return new PlayerCatchStats(totalCatches, uniqueDiscovered, getTotalRewardCount(),
                catchesByRarity, catchesByReward);
    }

    /**
     * Get total catches for a player
     */
    public int getTotalCatches(UUID player) {
        if (connection == null) return 0;

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COALESCE(SUM(count), 0) AS total FROM catch_stats WHERE uuid = ?")) {
            stmt.setString(1, player.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get total catches", e);
        }
        return 0;
    }

    /**
     * Get count of unique reward IDs caught by a player
     */
    public int getUniqueCaught(UUID player) {
        if (connection == null) return 0;

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(DISTINCT reward_id) AS unique_count FROM catch_stats WHERE uuid = ?")) {
            stmt.setString(1, player.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("unique_count");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get unique caught count", e);
        }
        return 0;
    }

    /**
     * Get the top fishers leaderboard. This should be called asynchronously from commands.
     *
     * @param limit Maximum number of entries
     * @return List of LeaderboardEntry sorted by total catches descending
     */
    public List<LeaderboardEntry> getTopFishers(int limit) {
        List<LeaderboardEntry> entries = new ArrayList<LeaderboardEntry>();
        if (connection == null) return entries;

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT uuid, SUM(count) AS total FROM catch_stats GROUP BY uuid ORDER BY total DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                int total = rs.getInt("total");

                // Resolve player name from Bukkit
                String name = resolvePlayerName(uuid);
                entries.add(new LeaderboardEntry(uuid, name, total));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get top fishers", e);
        }
        return entries;
    }

    /**
     * Check if a player has discovered (caught at least once) a specific reward
     */
    public boolean hasDiscovered(UUID player, String rewardId) {
        if (connection == null) return false;

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT count FROM catch_stats WHERE uuid = ? AND reward_id = ? AND count > 0")) {
            stmt.setString(1, player.toString());
            stmt.setString(2, rewardId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check discovery", e);
        }
        return false;
    }

    /**
     * Get the catch count for a specific reward for a player
     */
    public int getCatchCount(UUID player, String rewardId) {
        if (connection == null) return 0;

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT count FROM catch_stats WHERE uuid = ? AND reward_id = ?")) {
            stmt.setString(1, player.toString());
            stmt.setString(2, rewardId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get catch count", e);
        }
        return 0;
    }

    /**
     * Get the total number of possible rewards from RewardManager
     */
    private int getTotalRewardCount() {
        return plugin.getRewardManager().getRewardCount();
    }

    /**
     * Resolve a player name from UUID string. Returns the UUID string if offline.
     */
    private String resolvePlayerName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null) {
                return op.getName();
            }
        } catch (Exception ignored) {
        }
        return uuidStr.substring(0, 8) + "...";
    }

    /**
     * Find the rarest catch for a player (highest rarity with lowest total catch count).
     * Returns the display name of that reward, or null if none.
     */
    public String getRarestCatch(UUID player) {
        if (connection == null) return null;

        // Rarity priority order (highest first)
        String[] rarityOrder = {"LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON"};

        try {
            for (String rarity : rarityOrder) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT reward_id FROM catch_stats WHERE uuid = ? AND rarity = ? AND count > 0 " +
                        "ORDER BY count ASC LIMIT 1")) {
                    stmt.setString(1, player.toString());
                    stmt.setString(2, rarity);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        String rewardId = rs.getString("reward_id");
                        // Try to get display name from RewardManager
                        FishingReward reward = plugin.getRewardManager().getRewardById(rewardId);
                        if (reward != null) {
                            return reward.getItemDisplayName();
                        }
                        return rewardId;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get rarest catch", e);
        }
        return null;
    }

    /**
     * Shutdown the database connection
     */
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Catch statistics database closed.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close catch statistics database", e);
            }
        }
    }
}

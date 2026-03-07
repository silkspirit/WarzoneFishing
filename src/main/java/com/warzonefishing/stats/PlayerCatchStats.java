package com.warzonefishing.stats;

import java.util.Map;

/**
 * Data class holding a player's catch statistics
 */
public class PlayerCatchStats {

    private final int totalCatches;
    private final int uniqueDiscovered;
    private final int totalRewards;
    private final Map<String, Integer> catchesByRarity;
    private final Map<String, Integer> catchesByReward;

    public PlayerCatchStats(int totalCatches, int uniqueDiscovered, int totalRewards,
                            Map<String, Integer> catchesByRarity, Map<String, Integer> catchesByReward) {
        this.totalCatches = totalCatches;
        this.uniqueDiscovered = uniqueDiscovered;
        this.totalRewards = totalRewards;
        this.catchesByRarity = catchesByRarity;
        this.catchesByReward = catchesByReward;
    }

    public int getTotalCatches() {
        return totalCatches;
    }

    public int getUniqueDiscovered() {
        return uniqueDiscovered;
    }

    public int getTotalRewards() {
        return totalRewards;
    }

    public Map<String, Integer> getCatchesByRarity() {
        return catchesByRarity;
    }

    public Map<String, Integer> getCatchesByReward() {
        return catchesByReward;
    }

    /**
     * Get the discovery percentage (unique discovered / total possible)
     */
    public double getDiscoveryPercentage() {
        if (totalRewards <= 0) return 0.0;
        return ((double) uniqueDiscovered / totalRewards) * 100.0;
    }
}

package com.coreshift.maden;

import java.util.List;
import org.bukkit.Material;

public final class RewardDefinition {
    private final double chance;
    private final int xp;
    private final List<RewardItem> items;
    private final List<String> commands;
    private final String message;

    public RewardDefinition(double chance, int xp, List<RewardItem> items, List<String> commands, String message) {
        this.chance = chance;
        this.xp = xp;
        this.items = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
        this.commands = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(commands));
        this.message = message == null ? "" : message;
    }

    public double chance() {
        return chance;
    }

    public int xp() {
        return xp;
    }

    public List<RewardItem> items() {
        return items;
    }

    public List<String> commands() {
        return commands;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof RewardDefinition)) return false;
        RewardDefinition other = (RewardDefinition) value;
        return Double.compare(chance, other.chance) == 0
                && xp == other.xp
                && java.util.Objects.equals(items, other.items)
                && java.util.Objects.equals(commands, other.commands)
                && java.util.Objects.equals(message, other.message);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(chance, xp, items, commands, message);
    }
    public static final class RewardItem {
        private final Material material;
        private final int min;
        private final int max;
        private final double chance;

        public RewardItem(Material material, int min, int max, double chance) {
            this.material = material;
            this.min = min;
            this.max = max;
            this.chance = chance;
        }

        public Material material() {
            return material;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public double chance() {
            return chance;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof RewardItem)) return false;
            RewardItem other = (RewardItem) value;
            return java.util.Objects.equals(material, other.material)
                    && min == other.min
                    && max == other.max
                    && Double.compare(chance, other.chance) == 0;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(material, min, max, chance);
        }

    }
}

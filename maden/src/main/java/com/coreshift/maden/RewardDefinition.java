package com.coreshift.maden;

import java.util.List;
import org.bukkit.Material;

public record RewardDefinition(double chance, int xp, List<RewardItem> items, List<String> commands, String message) {
    public RewardDefinition {
        items = List.copyOf(items);
        commands = List.copyOf(commands);
        message = message == null ? "" : message;
    }

    public record RewardItem(Material material, int min, int max, double chance) {
    }
}

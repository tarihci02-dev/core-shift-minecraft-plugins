package com.coreshift.dungeonmobs;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.meta.ItemMeta;

public final class RewardCompat {
    private RewardCompat() {
    }

    public static void applyLore(ItemMeta meta, ConfigurationSection section) {
        if (meta == null || section == null) return;
        java.util.List<String> lines = section.getStringList("lore");
        if (!lines.isEmpty()) {
            meta.setLore(lines.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(java.util.stream.Collectors.toList()));
        }
    }
}

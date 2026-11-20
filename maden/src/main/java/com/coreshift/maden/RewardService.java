package com.coreshift.maden;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RewardService {
    public void reward(Player player, Mine mine, Material material, int n, int n2, int n3) {
        RewardDefinition rewardDefinition = mine.getReward(material);
        if (rewardDefinition == null || !this.roll(rewardDefinition.chance())) {
            return;
        }
        if (rewardDefinition.xp() > 0) {
            player.giveExp(rewardDefinition.xp());
        }
        for (RewardDefinition.RewardItem object2 : rewardDefinition.items()) {
            if (!this.roll(object2.chance())) continue;
            int string = ThreadLocalRandom.current().nextInt(object2.min(), object2.max() + 1);
            ItemStack reward = new ItemStack(object2.material(), string);
            HashMap<Integer, ItemStack> hashMap = player.getInventory().addItem(reward);
            for (ItemStack itemStack : hashMap.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
            }
        }
        ConsoleCommandSender consoleCommandSender = Bukkit.getConsoleSender();
        for (String string : rewardDefinition.commands()) {
            String command = this.replace(string, player, mine, material, n, n2, n3);
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (command.trim().isEmpty()) continue;
            Bukkit.dispatchCommand(consoleCommandSender, command);
        }
        if (!rewardDefinition.message().trim().isEmpty()) {
            player.sendMessage(CoreShiftMaden.color(this.replace(rewardDefinition.message(), player, mine, material, n, n2, n3)));
        }
    }

    private String replace(String string, Player player, Mine mine, Material material, int n, int n2, int n3) {
        return string.replace("%player%", player.getName()).replace("%mine%", mine.getName()).replace("%block%", material.name()).replace("%x%", String.valueOf(n)).replace("%y%", String.valueOf(n2)).replace("%z%", String.valueOf(n3));
    }

    private boolean roll(double d) {
        return d >= 100.0 || ThreadLocalRandom.current().nextDouble(100.0) < d;
    }
}

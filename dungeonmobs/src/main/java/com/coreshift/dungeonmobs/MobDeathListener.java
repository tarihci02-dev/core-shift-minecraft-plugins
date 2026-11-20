package com.coreshift.dungeonmobs;

import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class MobDeathListener
implements Listener {
    private final MobManager manager;

    public MobDeathListener(MobManager mobManager) {
        this.manager = mobManager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent entityDeathEvent) {
        LivingEntity livingEntity = entityDeathEvent.getEntity();
        MobDefinition mobDefinition = this.manager.getDefinitionFromEntity(livingEntity);
        if (mobDefinition == null) {
            return;
        }
        this.manager.onMobDeath(livingEntity);
        if (mobDefinition.clearVanillaDrops) {
            entityDeathEvent.getDrops().clear();
        }
        Random random = this.manager.getRandom();
        Location location = livingEntity.getLocation();
        for (MobDefinition.DropReward dropReward : mobDefinition.dropRewards) {
            if (!this.roll(random, dropReward.chance)) continue;
            int n = dropReward.min + random.nextInt(dropReward.max - dropReward.min + 1);
            ItemStack itemStack = dropReward.item.clone();
            itemStack.setAmount(n);
            location.getWorld().dropItemNaturally(location, itemStack);
        }
        int n = Math.min(mobDefinition.expMin, mobDefinition.expMax);
        int n2 = Math.max(mobDefinition.expMin, mobDefinition.expMax);
        if (n2 > 0) {
            entityDeathEvent.setDroppedExp(n + random.nextInt(n2 - n + 1));
        } else {
            entityDeathEvent.setDroppedExp(0);
        }
        Player player = livingEntity.getKiller();
        for (MobDefinition.CommandReward commandReward : mobDefinition.commandRewards) {
            String string;
            if (!this.roll(random, commandReward.chance) || (string = commandReward.command).contains("%player%") && player == null) continue;
            string = string.replace("%player%", player == null ? "" : player.getName()).replace("%mob%", mobDefinition.id).replace("%world%", location.getWorld().getName()).replace("%x%", Integer.toString(location.getBlockX())).replace("%y%", Integer.toString(location.getBlockY())).replace("%z%", Integer.toString(location.getBlockZ()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), string);
        }
    }

    private boolean roll(Random random, double d) {
        return d >= 100.0 || d > 0.0 && random.nextDouble() * 100.0 < d;
    }
}

package com.coreshift.dungeonmobs;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class MobWorldListener
implements Listener {
    private final MobManager manager;

    public MobWorldListener(MobManager mobManager) {
        this.manager = mobManager;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent chunkLoadEvent) {
        this.manager.onChunkLoad(chunkLoadEvent.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent chunkUnloadEvent) {
        this.manager.onChunkUnload(chunkUnloadEvent.getChunk());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent worldLoadEvent) {
        this.manager.onWorldLoad(worldLoadEvent.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent worldUnloadEvent) {
        this.manager.onWorldUnload(worldUnloadEvent.getWorld());
    }

    @EventHandler
    public void onMobDamage(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        LivingEntity livingEntity;
        Entity entity = entityDamageByEntityEvent.getEntity();
        if (!(entity instanceof LivingEntity) || (livingEntity = (LivingEntity)entity) instanceof Player) {
            return;
        }
        LivingEntity livingEntity2 = this.resolveLivingAttacker(entityDamageByEntityEvent.getDamager());
        if (livingEntity2 == null || livingEntity2 instanceof Player) {
            return;
        }
        if (this.isDungeonMob(livingEntity2) || this.isDungeonMob(livingEntity)) {
            entityDamageByEntityEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent entityTargetLivingEntityEvent) {
        LivingEntity livingEntity;
        Entity entity = entityTargetLivingEntityEvent.getEntity();
        LivingEntity livingEntity2 = entityTargetLivingEntityEvent.getTarget();
        if (!(entity instanceof LivingEntity) || (livingEntity = (LivingEntity)entity) instanceof Player) {
            return;
        }
        if (livingEntity2 == null || livingEntity2 instanceof Player) {
            return;
        }
        if (this.isDungeonMob(livingEntity) || this.isDungeonMob(livingEntity2)) {
            entityTargetLivingEntityEvent.setCancelled(true);
        }
    }

    private LivingEntity resolveLivingAttacker(Entity entity) {
        Projectile projectile;
        ProjectileSource projectileSource;
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity)entity;
            return livingEntity;
        }
        if (entity instanceof Projectile && (projectileSource = (projectile = (Projectile)entity).getShooter()) instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity)projectileSource;
            return livingEntity;
        }
        return null;
    }

    private boolean isDungeonMob(LivingEntity livingEntity) {
        return this.manager.getDefinitionFromEntity(livingEntity) != null;
    }
}

package com.coreshift.maden;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class MineListener
implements Listener {
    private final CoreShiftMaden plugin;
    private final MineManager mineManager;
    private final PendingManager pendingManager;
    private final RewardService rewardService;

    public MineListener(CoreShiftMaden coreShiftMaden, MineManager mineManager, PendingManager pendingManager, RewardService rewardService) {
        this.plugin = coreShiftMaden;
        this.mineManager = mineManager;
        this.pendingManager = pendingManager;
        this.rewardService = rewardService;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onBreak(BlockBreakEvent blockBreakEvent) {
        Block block = blockBreakEvent.getBlock();
        Mine mine = this.mineManager.getByBlock(block);
        if (mine == null) {
            return;
        }
        Player player = blockBreakEvent.getPlayer();
        if (!player.hasPermission("coreshift.maden.use")) {
            blockBreakEvent.setCancelled(true);
            this.plugin.msg(player, "no-permission", new String[0]);
            return;
        }
        if (this.pendingManager.contains(block)) {
            blockBreakEvent.setCancelled(true);
            return;
        }
        Material material = block.getType();
        if (!mine.canMine(material)) {
            blockBreakEvent.setCancelled(true);
            return;
        }
        blockBreakEvent.setCancelled(false);
        BlockData blockData = block.getBlockData();
        long l = System.currentTimeMillis() + mine.getRespawnMillis();
        PendingBlock pendingBlock = new PendingBlock(mine.getName(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), blockData.getAsString(), l);
        this.pendingManager.add(pendingBlock, blockData);
        if (!mine.isNaturalDrops()) {
            blockBreakEvent.setDropItems(false);
            blockBreakEvent.setExpToDrop(0);
        }
        this.rewardService.reward(player, mine, material, block.getX(), block.getY(), block.getZ());
    }
}

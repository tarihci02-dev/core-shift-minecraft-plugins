package com.coreshift.maden;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionOwner;
import com.sk89q.worldedit.world.World;
import org.bukkit.entity.Player;

public final class WorldEditSelectionProvider
implements SelectionProvider {
    @Override
    public SelectionBounds getCuboid(Player player) throws SelectionException {
        try {
            BukkitPlayer bukkitPlayer = BukkitAdapter.adapt((Player)player);
            LocalSession localSession = WorldEdit.getInstance().getSessionManager().get((SessionOwner)bukkitPlayer);
            World world = BukkitAdapter.adapt((org.bukkit.World)player.getWorld());
            Region region = localSession.getSelection(world);
            if (!(region instanceof CuboidRegion)) {
                throw new SelectionException(SelectionException.Reason.NOT_CUBOID);
            }
            BlockVector3 blockVector3 = region.getMinimumPoint();
            BlockVector3 blockVector32 = region.getMaximumPoint();
            return new SelectionBounds(player.getWorld().getName(), blockVector3.getBlockX(), blockVector3.getBlockY(), blockVector3.getBlockZ(), blockVector32.getBlockX(), blockVector32.getBlockY(), blockVector32.getBlockZ());
        }
        catch (IncompleteRegionException incompleteRegionException) {
            throw new SelectionException(SelectionException.Reason.INCOMPLETE, (Throwable)incompleteRegionException);
        }
    }
}

package com.coreshift.maden;

import org.bukkit.entity.Player;

public interface SelectionProvider {
    public SelectionBounds getCuboid(Player var1) throws SelectionException;
}

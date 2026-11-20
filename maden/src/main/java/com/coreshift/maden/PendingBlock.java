package com.coreshift.maden;

public final class PendingBlock {
    private final String mine;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String blockData;
    private final long restoreAt;

    public PendingBlock(String mine, String world, int x, int y, int z, String blockData, long restoreAt) {
        this.mine = mine;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockData = blockData;
        this.restoreAt = restoreAt;
    }

    public String mine() {
        return mine;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public String blockData() {
        return blockData;
    }

    public long restoreAt() {
        return restoreAt;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof PendingBlock)) return false;
        PendingBlock other = (PendingBlock) value;
        return java.util.Objects.equals(mine, other.mine)
                && java.util.Objects.equals(world, other.world)
                && x == other.x
                && y == other.y
                && z == other.z
                && java.util.Objects.equals(blockData, other.blockData)
                && restoreAt == other.restoreAt;
    }

    @Override
    public int hashCode() {
        int hash = java.util.Objects.hashCode(mine);
        hash = 31 * hash + java.util.Objects.hashCode(world);
        hash = 31 * hash + x;
        hash = 31 * hash + y;
        hash = 31 * hash + z;
        hash = 31 * hash + java.util.Objects.hashCode(blockData);
        hash = 31 * hash + Long.hashCode(restoreAt);
        return hash;
    }

    public long positionKey() {
        return PendingBlock.packPosition(this.x, this.y, this.z);
    }

    public String debugKey() {
        return this.world + ":" + this.x + ":" + this.y + ":" + this.z;
    }

    public static long packPosition(int n, int n2, int n3) {
        return ((long)n & 0x3FFFFFFL) << 38 | ((long)n3 & 0x3FFFFFFL) << 12 | (long)n2 & 0xFFFL;
    }
}

package com.coreshift.maden;

public record PendingBlock(String mine, String world, int x, int y, int z, String blockData, long restoreAt) {
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

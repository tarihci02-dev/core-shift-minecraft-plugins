package com.coreshift.dungeonmobs;

public final class OptimizedCaps {
    private OptimizedCaps() {
    }

    public static int minSpawn(int n, int n2) {
        return Math.max(0, Math.min(4, Math.min(n, n2)));
    }

    public static long limitInterval(long l, long l2) {
        if (l == 0L) {
            return Math.max(5L, l2);
        }
        return Math.max(1L, l2);
    }
}

package com.coreshift.maden;

public final class OptimizedCaps {
    private OptimizedCaps() {
    }

    public static int limit(int n, int n2) {
        int n3 = Math.max(1, n2);
        if (n == 2) {
            return Math.min(n3, 100);
        }
        if (n == 3) {
            return Math.min(n3, 200);
        }
        return n3;
    }
}

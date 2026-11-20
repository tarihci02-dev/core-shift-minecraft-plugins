package com.coreshift.maden;

public final class SelectionBounds {
    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public SelectionBounds(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public String worldName() {
        return worldName;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof SelectionBounds)) return false;
        SelectionBounds other = (SelectionBounds) value;
        return java.util.Objects.equals(worldName, other.worldName)
                && minX == other.minX
                && minY == other.minY
                && minZ == other.minZ
                && maxX == other.maxX
                && maxY == other.maxY
                && maxZ == other.maxZ;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }

}

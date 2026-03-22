package com.sapphic.ssl.api;

/**
 * An axis-aligned bounding box for a structure selection.
 *
 * <p>Unlike the vanilla {@code BlockBox} / structure-block limit of 48 × 48 × 48,
 * this implementation uses full {@code int} coordinates and therefore supports
 * selections up to {@link Integer#MAX_VALUE} in every dimension (e.g. 64 × 64 × 64,
 * or much larger city-scale builds).
 */
public final class StructureBoundingBox {

    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public StructureBoundingBox(int minX, int minY, int minZ,
                                int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    /** Construct from two arbitrary corner positions. */
    public static StructureBoundingBox fromCorners(int x1, int y1, int z1,
                                                   int x2, int y2, int z2) {
        return new StructureBoundingBox(x1, y1, z1, x2, y2, z2);
    }

    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }

    /** Width along the X axis (inclusive). */
    public int sizeX() { return maxX - minX + 1; }

    /** Height along the Y axis (inclusive). */
    public int sizeY() { return maxY - minY + 1; }

    /** Depth along the Z axis (inclusive). */
    public int sizeZ() { return maxZ - minZ + 1; }

    /** Total block count inside this box. */
    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    /** {@code true} if this box is larger than vanilla's 48 × 48 × 48 limit. */
    public boolean exceedsVanillaLimit() {
        return sizeX() > 48 || sizeY() > 48 || sizeZ() > 48;
    }

    @Override
    public String toString() {
        return String.format("StructureBoundingBox[(%d,%d,%d)→(%d,%d,%d) = %dx%dx%d]",
                minX, minY, minZ, maxX, maxY, maxZ, sizeX(), sizeY(), sizeZ());
    }
}

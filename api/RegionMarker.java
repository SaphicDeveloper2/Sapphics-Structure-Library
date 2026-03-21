package com.sapphic.ssl.api;

/**
 * A "Region Marker" covering a {@link SaphStructFormat#REGION_STRIDE} ×
 * {@link SaphStructFormat#REGION_STRIDE} block column (512 × 512 blocks = 32 × 32 chunks).
 *
 * <p>The binary loader checks region markers first as a <em>fast-fail</em> gate:
 * if the region is empty (no block data), the loader exits immediately without
 * scanning per-block data, keeping server tick impact near zero.
 *
 * <p>Region coordinates are derived by integer-dividing world block coordinates
 * by {@link SaphStructFormat#REGION_STRIDE}.
 */
public final class RegionMarker {

    private final int  regionX;
    private final int  regionZ;

    /** Byte offset into the block-data section where this region's data begins. */
    private final long dataOffset;

    /** Byte length of this region's packed block data. */
    private final int  dataLength;

    public RegionMarker(int regionX, int regionZ, long dataOffset, int dataLength) {
        this.regionX    = regionX;
        this.regionZ    = regionZ;
        this.dataOffset = dataOffset;
        this.dataLength = dataLength;
    }

    /** Region X coordinate (blockX / {@link SaphStructFormat#REGION_STRIDE}). */
    public int  regionX()    { return regionX; }

    /** Region Z coordinate (blockZ / {@link SaphStructFormat#REGION_STRIDE}). */
    public int  regionZ()    { return regionZ; }

    /** Byte offset into the packed block-data section. */
    public long dataOffset() { return dataOffset; }

    /** Byte count for this region's packed block data. */
    public int  dataLength() { return dataLength; }

    /**
     * Fast-fail guard: {@code true} when this region contains no block data,
     * meaning the loader can skip it entirely.
     */
    public boolean isEmpty() { return dataLength == 0; }

    /**
     * Returns the region coordinates for a given world-space block position.
     *
     * @param blockX World-space X coordinate.
     * @param blockZ World-space Z coordinate.
     * @return {@code int[]{regionX, regionZ}}
     */
    public static int[] toRegionCoords(int blockX, int blockZ) {
        int s = SaphStructFormat.REGION_STRIDE;
        return new int[]{ Math.floorDiv(blockX, s), Math.floorDiv(blockZ, s) };
    }

    @Override
    public String toString() {
        return String.format("RegionMarker[(%d,%d) offset=%d len=%d]",
                regionX, regionZ, dataOffset, dataLength);
    }
}

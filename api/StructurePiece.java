package com.sapphic.ssl.api;

import com.sapphic.ssl.api.loot.LootTableRef;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An in-memory representation of a fully-decoded {@code .tsaphstruct} (v2) file.
 *
 * <p>Stores:
 * <ul>
 *   <li>Bounding box (can exceed vanilla 48³).</li>
 *   <li>Block-state palette.</li>
 *   <li>Region marker index for fast-fail spatial lookups.</li>
 *   <li>Packed palette indices, Y→Z→X order.</li>
 *   <li>Block-entity NBT bytes, keyed by linear block index (sparse).</li>
 *   <li>Loot table refs, keyed by linear block index — containers in the structure
 *       that should be filled with loot when placed.</li>
 * </ul>
 */
public final class StructurePiece {

    private final StructureBoundingBox  bounds;
    private final List<BlockEntry>      palette;
    private final List<RegionMarker>    regions;

    /**
     * Palette indices, Y→Z→X order.  Length == bounds.volume().
     */
    private final int[] blocks;

    /**
     * Sparse block-entity NBT map.
     * Key = linear index {@code (ry * sZ + rz) * sX + rx}.
     * Value = GZIP-compressed NBT bytes.
     */
    private final Map<Integer, byte[]>        blockEntityData;

    /**
     * Sparse loot table reference map.
     * Key = linear index (same convention as blockEntityData).
     * Value = loot table reference (vanilla or TsaphLoot).
     *
     * <p>A block index present here means its container should have loot
     * generated on first player-open rather than using raw exported NBT.
     */
    private final Map<Integer, LootTableRef>  lootRefs;

    public StructurePiece(StructureBoundingBox         bounds,
                          List<BlockEntry>              palette,
                          List<RegionMarker>            regions,
                          int[]                         blocks,
                          Map<Integer, byte[]>          blockEntityData,
                          Map<Integer, LootTableRef>    lootRefs) {
        this.bounds          = Objects.requireNonNull(bounds,          "bounds");
        this.palette         = Collections.unmodifiableList(Objects.requireNonNull(palette,          "palette"));
        this.regions         = Collections.unmodifiableList(Objects.requireNonNull(regions,          "regions"));
        this.blocks          = Objects.requireNonNull(blocks,          "blocks");
        this.blockEntityData = Collections.unmodifiableMap(Objects.requireNonNull(blockEntityData,   "blockEntityData"));
        this.lootRefs        = Collections.unmodifiableMap(Objects.requireNonNull(lootRefs,          "lootRefs"));

        long expected = bounds.volume();
        if (blocks.length != expected) {
            throw new IllegalArgumentException(
                "Block array length " + blocks.length + " ≠ bounding-box volume " + expected);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public StructureBoundingBox       bounds()          { return bounds; }
    public List<BlockEntry>           palette()         { return palette; }
    public List<RegionMarker>         regions()         { return regions; }
    public int[]                      blocks()          { return blocks; }
    public Map<Integer, byte[]>       blockEntityData() { return blockEntityData; }
    public Map<Integer, LootTableRef> lootRefs()        { return lootRefs; }

    /** Returns the palette index for block at (rx, ry, rz) in local space. */
    public int paletteIndexAt(int rx, int ry, int rz) {
        return blocks[(ry * bounds.sizeZ() + rz) * bounds.sizeX() + rx];
    }

    /** Returns the {@link BlockEntry} at (rx, ry, rz). */
    public BlockEntry blockEntryAt(int rx, int ry, int rz) {
        return palette.get(paletteIndexAt(rx, ry, rz));
    }

    /** Linear block index used as key in both sparse maps. */
    public int linearIndex(int rx, int ry, int rz) {
        return (ry * bounds.sizeZ() + rz) * bounds.sizeX() + rx;
    }

    /**
     * Raw compressed NBT bytes for the block entity at (rx,ry,rz), or {@code null}.
     */
    public byte[] blockEntityNbtAt(int rx, int ry, int rz) {
        return blockEntityData.get(linearIndex(rx, ry, rz));
    }

    /**
     * Loot table reference for the container at (rx,ry,rz), or {@code null} if none.
     */
    public LootTableRef lootRefAt(int rx, int ry, int rz) {
        return lootRefs.get(linearIndex(rx, ry, rz));
    }

    @Override
    public String toString() {
        return String.format(
            "StructurePiece[%s, palette=%d, regions=%d, blockEntities=%d, lootRefs=%d]",
            bounds, palette.size(), regions.size(), blockEntityData.size(), lootRefs.size());
    }
}

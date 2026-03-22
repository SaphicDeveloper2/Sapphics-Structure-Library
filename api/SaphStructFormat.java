package com.sapphic.ssl.api;

/**
 * Format constants for the proprietary {@code .tsaphstruct} binary format.
 *
 * <h2>Binary Layout — Version 2</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │  HEADER  (29 bytes fixed)                                    │
 * │    [4]  Magic          0x54534150  ("TSAP")                  │
 * │    [1]  Version        0x02                                  │
 * │    [4]  SizeX          int  (supports &gt; 48)                  │
 * │    [4]  SizeY          int                                   │
 * │    [4]  SizeZ          int                                   │
 * │    [4]  PaletteSize    int                                   │
 * │    [4]  RegionCount    int                                   │
 * │    [4]  EntityCount    int  (block-entity records)           │
 * │    [4]  LootRefCount   int  (NEW in v2)                      │
 * ├──────────────────────────────────────────────────────────────┤
 * │  PALETTE  (PaletteSize entries)                              │
 * │    [2]  String length  short (unsigned)                      │
 * │    [N]  BlockState id  UTF-8                                 │
 * ├──────────────────────────────────────────────────────────────┤
 * │  REGION INDEX  (RegionCount × 20 bytes)                      │
 * │    [4]  RegionX        int  (blockX / REGION_STRIDE)         │
 * │    [4]  RegionZ        int  (blockZ / REGION_STRIDE)         │
 * │    [8]  DataOffset     long (byte offset into block data)    │
 * │    [4]  DataLength     int  (bytes for this region)          │
 * ├──────────────────────────────────────────────────────────────┤
 * │  BLOCK DATA                                                  │
 * │    Packed palette indices (min bits-per-block encoding)      │
 * │    Internal header: [int bits][int longs][int blockCount]    │
 * ├──────────────────────────────────────────────────────────────┤
 * │  BLOCK-ENTITY DATA  (EntityCount entries)                    │
 * │    [4]  rx         int  (relative X in local space)          │
 * │    [4]  ry         int                                       │
 * │    [4]  rz         int                                       │
 * │    [2]  TypeLen    short (unsigned)                          │
 * │    [N]  TypeId     UTF-8 (block entity type registry id)     │
 * │    [4]  NbtLen     int                                       │
 * │    [N]  NbtBytes   GZIP-compressed NbtCompound               │
 * ├──────────────────────────────────────────────────────────────┤
 * │  LOOT REFS  (LootRefCount entries)  ← NEW in v2             │
 * │    [4]  LinearIndex  int  ((ry*sZ+rz)*sX+rx)                │
 * │    [1]  RefType      byte (0x00=VANILLA, 0x01=TSAPHLOOT)     │
 * │    [2]  IdLen        short (unsigned)                        │
 * │    [N]  Id           UTF-8 (vanilla key or TsaphLoot name)   │
 * └──────────────────────────────────────────────────────────────┘
 *
 * Version 1 readers: treat LootRefCount as 0 (no loot section).
 * </pre>
 */
public final class SaphStructFormat {

    /** ASCII "TSAP" — magic bytes identifying this format. */
    public static final int  MAGIC        = 0x54534150;

    /**
     * Current format version.
     * v1 = no loot refs.
     * v2 = adds LootRefCount + LOOT REFS section.
     */
    public static final byte VERSION      = 0x02;

    /** Minimum version this reader supports (older files are still loaded). */
    public static final byte MIN_VERSION  = 0x01;

    /** File extension for structure files. */
    public static final String EXTENSION  = ".tsaphstruct";

    /**
     * Region stride in blocks.  Each "Region Marker" covers a
     * {@value #REGION_STRIDE} × {@value #REGION_STRIDE} column.
     */
    public static final int REGION_STRIDE = 512;

    /** Maximum distinct block-states in a single structure's palette. */
    public static final int MAX_PALETTE   = 65_535;

    /** Minimum bits-per-block (matches vanilla PackedIntegerArray floor). */
    public static final int MIN_BITS      = 1;

    private SaphStructFormat() {}
}

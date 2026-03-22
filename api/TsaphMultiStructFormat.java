package com.sapphic.ssl.api;

/**
 * Format constants for the {@code .tsaphmultistruct} binary format.
 *
 * <h2>Binary Layout — Version 1</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │  HEADER                                                      │
 * │    [4]  Magic          0x54534D53  ("TSMS")                  │
 * │    [1]  Version        0x01                                  │
 * │    [2]  NameLen        short (unsigned)                      │
 * │    [N]  Name           UTF-8                                 │
 * │    [4]  PieceCount     int                                   │
 * ├──────────────────────────────────────────────────────────────┤
 * │  PIECE RECORDS  (PieceCount entries)                         │
 * │    [2]  IdLen          short (UUID string)                   │
 * │    [N]  Id             UTF-8                                 │
 * │    [2]  NameLen        short                                 │
 * │    [N]  Name           UTF-8                                 │
 * │    [1]  Role           byte (0=ROOM, 1=HALLWAY, 2=PATH)      │
 * │    [1]  ConnectionType byte (0–8, see ConnectionType enum)   │
 * │    [4]  Weight         int  (relative spawn probability)     │
 * │    [4]  MaxCount       int  (-1 = unlimited)                 │
 * │    [4]  ConnPtCount    int  (number of ConnectionPoint records) │
 * │    per ConnectionPoint:                                       │
 * │      [4]  rx / ry / rz  int                                  │
 * │      [1]  facing        byte (0=N 1=S 2=E 3=W)               │
 * │    [4]  DataLen        int  (byte length of embedded struct) │
 * │    [N]  Data           bytes (.tsaphstruct binary, full v2)  │
 * └──────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>The embedded {@code Data} section for each piece is a complete, self-contained
 * {@code .tsaphstruct} v2 binary using the same bit-packed palette encoding as a
 * standalone file.  This means the multi-struct format inherits full support for
 * large structures, block-entity NBT, and loot refs.
 *
 * <p>A companion {@code .json} file is generated alongside every saved
 * {@code .tsaphmultistruct} file, allowing modpack developers to view and edit
 * spawn weights and counts without opening the game.
 */
public final class TsaphMultiStructFormat {

    /** ASCII "TSMS" — magic bytes identifying this format. */
    public static final int  MAGIC      = 0x54534D53;

    /** Current format version. */
    public static final byte VERSION    = 0x02;

    /** Minimum version this reader supports. */
    public static final byte MIN_VERSION = 0x01;

    /** File extension for multi-structure bundles. */
    public static final String EXTENSION = ".tsaphmultistruct";

    /** Extension for the companion config file. */
    public static final String COMPANION_EXTENSION = ".tsaphmultistruct.json";

    private TsaphMultiStructFormat() {}
}

package com.sapphic.ssl.api;

/**
 * The junction shape of a {@link PieceRole#HALLWAY} or {@link PieceRole#PATH} piece.
 *
 * <p>Connection types define how many open ends a connector exposes and in which
 * cardinal directions, relative to the piece's primary facing.  The procedural
 * engine uses this to safely chain pieces together and determine which ends still
 * need to be capped with {@link PieceRole#ROOM} pieces.
 *
 * <p>All directions are relative to the piece's primary facing ({@code NORTH} by default
 * — the engine rotates pieces to match open endpoints).
 *
 * <h2>Shapes at a glance</h2>
 * <pre>
 *  STRAIGHT         T_SHAPE          T_INVERTED
 *    [ ]              [ ]
 *    | |            ──┼──             ──┼──
 *    | |              |                |  |
 *    [ ]              |              ──┘  └──
 *
 *  CORNER_LEFT     CORNER_RIGHT    CORNER_LEFT_INV   CORNER_RIGHT_INV
 *    ┐                 ┌                ┘                  └
 *    └──               ──┘
 *
 *  MIDSECTION_BRANCH
 *    [ ]
 *    |──
 *    |
 *    [ ]
 * </pre>
 *
 * <p>{@link PieceRole#ROOM} pieces always use {@link #NONE}.
 */
public enum ConnectionType {

    /** No connection shape — used for ROOM pieces. */
    NONE((byte) 0, 0),

    /**
     * Two open ends: one at the primary face (NORTH), one at the opposite face (SOUTH).
     * Standard corridor or road segment.
     */
    STRAIGHT((byte) 1, 2),

    /**
     * Three open ends: primary face (NORTH), left face (WEST), right face (EAST).
     * A T-intersection allowing a branch to the left and right while continuing forward.
     */
    T_SHAPE((byte) 2, 3),

    /**
     * Three open ends: primary face (NORTH), back-left (SOUTH-WEST), back-right (SOUTH-EAST).
     * An inverted T — the corridor continues and forks rearward.
     */
    T_INVERTED((byte) 3, 3),

    /**
     * Two open ends: primary face (NORTH), left face (WEST).
     * A 90-degree left turn.
     */
    CORNER_LEFT((byte) 4, 2),

    /**
     * Two open ends: primary face (NORTH), right face (EAST).
     * A 90-degree right turn.
     */
    CORNER_RIGHT((byte) 5, 2),

    /**
     * Two open ends: opposite face (SOUTH), left face (WEST).
     * An inverted left corner — enters from the south, exits west.
     */
    CORNER_LEFT_INVERTED((byte) 6, 2),

    /**
     * Two open ends: opposite face (SOUTH), right face (EAST).
     * An inverted right corner — enters from the south, exits east.
     */
    CORNER_RIGHT_INVERTED((byte) 7, 2),

    /**
     * Three open ends: primary face (NORTH), opposite face (SOUTH), one side (EAST).
     * A long straight segment with an asymmetric early detour branch — useful for
     * creating a main approach to a destination with a secondary exploratory route.
     */
    MIDSECTION_BRANCH((byte) 8, 3);

    private final byte wireValue;

    /** How many open ends this junction type exposes. */
    private final int openEndCount;

    ConnectionType(byte wireValue, int openEndCount) {
        this.wireValue    = wireValue;
        this.openEndCount = openEndCount;
    }

    public byte wireValue()    { return wireValue; }
    public int  openEndCount() { return openEndCount; }

    public static ConnectionType fromWire(byte b) {
        return switch (b) {
            case 0 -> NONE;
            case 1 -> STRAIGHT;
            case 2 -> T_SHAPE;
            case 3 -> T_INVERTED;
            case 4 -> CORNER_LEFT;
            case 5 -> CORNER_RIGHT;
            case 6 -> CORNER_LEFT_INVERTED;
            case 7 -> CORNER_RIGHT_INVERTED;
            case 8 -> MIDSECTION_BRANCH;
            default -> throw new IllegalArgumentException(
                    "Unknown ConnectionType wire byte: 0x" + Integer.toHexString(b & 0xFF));
        };
    }

    public static ConnectionType fromString(String s) {
        return switch (s.toUpperCase().replace("-", "_").replace(" ", "_")) {
            case "NONE"                  -> NONE;
            case "STRAIGHT"              -> STRAIGHT;
            case "T_SHAPE", "T"         -> T_SHAPE;
            case "T_INVERTED"            -> T_INVERTED;
            case "CORNER_LEFT"           -> CORNER_LEFT;
            case "CORNER_RIGHT"          -> CORNER_RIGHT;
            case "CORNER_LEFT_INVERTED"  -> CORNER_LEFT_INVERTED;
            case "CORNER_RIGHT_INVERTED" -> CORNER_RIGHT_INVERTED;
            case "MIDSECTION_BRANCH", "MIDSECTION" -> MIDSECTION_BRANCH;
            default -> throw new IllegalArgumentException(
                    "Unknown ConnectionType: '" + s + "'");
        };
    }
}

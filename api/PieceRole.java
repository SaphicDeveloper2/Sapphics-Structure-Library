package com.sapphic.ssl.api;

/**
 * The structural role of a piece inside a {@code .tsaphmultistruct} bundle.
 *
 * <p>Roles drive the procedural engine's chaining logic:
 * <ul>
 *   <li>{@link #ROOM} pieces cap open connector ends and act as destinations
 *       (boss chambers, houses, treasure vaults).</li>
 *   <li>{@link #HALLWAY} pieces are underground connectors with defined junction
 *       types (corridors, tunnels, cave passages).</li>
 *   <li>{@link #PATH} pieces are surface connectors with the same junction types
 *       as hallways (dirt roads, paved streets, bridges).</li>
 * </ul>
 *
 * <p>The engine mandates that every open connector end is closed with a ROOM piece,
 * preventing dead-end corridors and uncapped road segments.
 */
public enum PieceRole {

    /** A terminal node — houses, chambers, plazas. Has no connector shape. */
    ROOM((byte) 0),

    /** An underground connector piece — corridors, tunnels. Defines a {@link ConnectionType}. */
    HALLWAY((byte) 1),

    /** A surface connector piece — roads, bridges, paths. Defines a {@link ConnectionType}. */
    PATH((byte) 2);

    private final byte wireValue;

    PieceRole(byte wireValue) { this.wireValue = wireValue; }

    public byte wireValue() { return wireValue; }

    /** {@code true} if this role requires a {@link ConnectionType} (non-ROOM roles). */
    public boolean isConnector() { return this != ROOM; }

    public static PieceRole fromWire(byte b) {
        return switch (b) {
            case 0 -> ROOM;
            case 1 -> HALLWAY;
            case 2 -> PATH;
            default -> throw new IllegalArgumentException(
                    "Unknown PieceRole wire byte: 0x" + Integer.toHexString(b & 0xFF));
        };
    }

    public static PieceRole fromString(String s) {
        return switch (s.toUpperCase()) {
            case "ROOM"    -> ROOM;
            case "HALLWAY" -> HALLWAY;
            case "PATH"    -> PATH;
            default -> throw new IllegalArgumentException("Unknown PieceRole: '" + s + "'");
        };
    }
}

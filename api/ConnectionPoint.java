package com.sapphic.ssl.api;

import net.minecraft.util.math.Direction;

/**
 * A single connection point baked into a {@link MultiStructPiece}.
 *
 * <p>Connection points are recorded during multi-struct export by scanning the
 * selected region for {@link com.sapphic.ssl.items.ConnectorBlock} instances.
 * Each connector block's relative position and {@code HORIZONTAL_FACING} direction
 * are captured as a {@code ConnectionPoint} and stored in the piece record.
 *
 * <p>At generation time the {@link com.sapphic.ssl.internal.ProceduralEngine}
 * uses these points to snap adjacent pieces together precisely, then replaces
 * each connector block with the floor block directly below it in the world.
 *
 * <h2>Wire format (inside each piece record)</h2>
 * <pre>
 *   [4]  rx      int  (local X, 0-based)
 *   [4]  ry      int  (local Y, 0-based)
 *   [4]  rz      int  (local Z, 0-based)
 *   [1]  facing  byte (0=NORTH 1=SOUTH 2=EAST 3=WEST)
 * </pre>
 */
public record ConnectionPoint(int rx, int ry, int rz, Direction facing) {

    /** Wire encoding — ordinals match the four horizontal directions. */
    public byte facingWire() {
        return switch (facing) {
            case NORTH -> 0;
            case SOUTH -> 1;
            case EAST  -> 2;
            case WEST  -> 3;
            default    -> 0;
        };
    }

    public static Direction facingFromWire(byte b) {
        return switch (b) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.EAST;
            case 3 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }
}

package com.sapphic.ssl.api;

import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

/**
 * Rotation math for structure placement.
 *
 * <p>Translates between the four {@link BlockRotation} values and their effects on
 * block coordinates (X/Z only — Y is invariant) and {@link Direction} facing values.
 * Used by {@link IStructureLoader#place(net.minecraft.server.world.ServerWorld,
 * StructurePiece, net.minecraft.util.math.BlockPos, BlockRotation)} and
 * {@link com.sapphic.ssl.internal.ProceduralEngine} to orient pieces at placement
 * time without mutating the stored {@link StructurePiece} data.
 *
 * <h2>Coordinate convention</h2>
 * <p>Rotations pivot around the bounding-box origin {@code (0, y, 0)} in local space.
 * For a piece with original footprint {@code sX × sZ}:
 * <ul>
 *   <li>{@code NONE}               — identity,             new footprint {@code sX × sZ}.</li>
 *   <li>{@code CLOCKWISE_90}        — 90 ° clockwise,       new footprint {@code sZ × sX}.</li>
 *   <li>{@code CLOCKWISE_180}       — 180 °,                new footprint {@code sX × sZ}.</li>
 *   <li>{@code COUNTERCLOCKWISE_90} — 90 ° counter-clockwise, new footprint {@code sZ × sX}.</li>
 * </ul>
 */
public final class StructureRotation {

    private StructureRotation() {}

    // ── Coordinate rotation ───────────────────────────────────────────────

    /**
     * Rotate local block coordinate {@code (rx, rz)} for a piece whose original
     * footprint is {@code sX × sZ}.
     *
     * @return {@code int[2]} — {@code [newRx, newRz]}.
     */
    public static int[] rotateCoord(int rx, int rz, int sX, int sZ, BlockRotation rotation) {
        return switch (rotation) {
            case NONE                -> new int[]{ rx,           rz           };
            case CLOCKWISE_90        -> new int[]{ sZ - 1 - rz,  rx           };
            case CLOCKWISE_180       -> new int[]{ sX - 1 - rx,  sZ - 1 - rz  };
            case COUNTERCLOCKWISE_90 -> new int[]{ rz,            sX - 1 - rx  };
        };
    }

    // ── Direction rotation ────────────────────────────────────────────────

    /**
     * Rotate a horizontal {@link Direction} by {@code rotation}.
     * {@code UP} and {@code DOWN} are returned unchanged.
     */
    public static Direction rotateDir(Direction dir, BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> dir;
            case CLOCKWISE_90 -> switch (dir) {
                case NORTH -> Direction.EAST;
                case EAST  -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case WEST  -> Direction.NORTH;
                default    -> dir;
            };
            case CLOCKWISE_180 -> switch (dir) {
                case NORTH -> Direction.SOUTH;
                case SOUTH -> Direction.NORTH;
                case EAST  -> Direction.WEST;
                case WEST  -> Direction.EAST;
                default    -> dir;
            };
            case COUNTERCLOCKWISE_90 -> switch (dir) {
                case NORTH -> Direction.WEST;
                case WEST  -> Direction.SOUTH;
                case SOUTH -> Direction.EAST;
                case EAST  -> Direction.NORTH;
                default    -> dir;
            };
        };
    }

    /**
     * Returns the {@link BlockRotation} that, when applied to {@code from}, yields
     * {@code to}.  Both must be horizontal directions (NORTH / SOUTH / EAST / WEST).
     *
     * <p>Every pair of horizontal directions is reachable via exactly one of the four
     * 90 ° rotations, so this always succeeds for valid horizontal inputs.
     *
     * @throws IllegalArgumentException if no rotation maps {@code from} to {@code to}
     *         (only possible when UP or DOWN is passed).
     */
    public static BlockRotation rotationToAlign(Direction from, Direction to) {
        for (BlockRotation r : BlockRotation.values()) {
            if (rotateDir(from, r) == to) return r;
        }
        throw new IllegalArgumentException(
                "Cannot align " + from + " → " + to + " via 90° rotations");
    }

    // ── Rotated footprint dimensions ──────────────────────────────────────

    /**
     * X extent of the rotated piece footprint.
     * Equal to {@code sX} for {@code NONE}/{@code CLOCKWISE_180};
     * equal to {@code sZ} for {@code CLOCKWISE_90}/{@code COUNTERCLOCKWISE_90}.
     */
    public static int rotatedSizeX(int sX, int sZ, BlockRotation rotation) {
        return switch (rotation) {
            case NONE, CLOCKWISE_180             -> sX;
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> sZ;
        };
    }

    /**
     * Z extent of the rotated piece footprint.
     * Equal to {@code sZ} for {@code NONE}/{@code CLOCKWISE_180};
     * equal to {@code sX} for {@code CLOCKWISE_90}/{@code COUNTERCLOCKWISE_90}.
     */
    public static int rotatedSizeZ(int sX, int sZ, BlockRotation rotation) {
        return switch (rotation) {
            case NONE, CLOCKWISE_180             -> sZ;
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> sX;
        };
    }

    // ── Block-state rotation ──────────────────────────────────────────────

    /**
     * Rotate a {@link BlockState}'s directional properties (facing, axis, etc.)
     * using Minecraft's built-in {@link BlockState#rotate(BlockRotation)}.
     *
     * <p>Returns the state unchanged when {@code rotation} is {@code NONE}.
     */
    public static BlockState rotateState(BlockState state, BlockRotation rotation) {
        if (rotation == BlockRotation.NONE) return state;
        return state.rotate(rotation);
    }
}

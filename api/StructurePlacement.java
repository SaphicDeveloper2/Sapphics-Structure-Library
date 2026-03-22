package com.sapphic.ssl.api;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Fluent builder for loading and placing a {@code .tsaphstruct} structure.
 *
 * <p>Replaces the three-step raw API pattern:
 * <pre>
 *   // Old way — manual and error-prone
 *   StructurePiece piece = StructureLoaderBridge.getLoader().load(path);
 *   BlockPos origin = new BlockPos(x, groundY - piece.groundOffset(), z);
 *   StructureLoaderBridge.getLoader().place(world, piece, origin);
 * </pre>
 *
 * <p>New way:
 * <pre>
 *   // Load from a Path
 *   StructurePlacement.load(path)
 *       .at(x, z)
 *       .onSurface()
 *       .place(world);
 *
 *   // Or from an already-loaded piece
 *   StructurePlacement.of(piece)
 *       .at(x, z)
 *       .atY(64)
 *       .withYOffset(-2)   // bury 2 blocks underground
 *       .place(world);
 * </pre>
 *
 * <h2>Y placement</h2>
 * <ul>
 *   <li>{@link #onSurface()} — looks up the {@code WORLD_SURFACE} heightmap at
 *       the chosen X/Z and places flush with the surface.  The structure's
 *       {@link StructurePiece#groundOffset()} is applied automatically.</li>
 *   <li>{@link #onOceanFloor()} — same but uses the {@code OCEAN_FLOOR} heightmap.</li>
 *   <li>{@link #atY(int)} — use a literal world Y.  Ground offset is still applied
 *       unless you call {@link #withoutGroundOffset()}.</li>
 *   <li>{@link #withYOffset(int)} — additional offset added on top of any computed Y
 *       (positive = higher, negative = lower/buried).</li>
 * </ul>
 */
public final class StructurePlacement {

    // ── State ─────────────────────────────────────────────────────────────

    private final StructurePiece piece;

    private int x = 0;
    private int z = 0;

    private YStrategy yStrategy     = YStrategy.ABSOLUTE;
    private int       absoluteY     = 0;
    private int       yOffset       = 0;
    private boolean   groundOffset  = true;   // apply piece.groundOffset() by default

    // ── Y strategies ─────────────────────────────────────────────────────

    private enum YStrategy { ABSOLUTE, SURFACE, OCEAN_FLOOR }

    // ── Entry points ──────────────────────────────────────────────────────

    private StructurePlacement(StructurePiece piece) {
        this.piece = Objects.requireNonNull(piece, "piece");
    }

    /**
     * Begin building a placement from an already-loaded {@link StructurePiece}.
     */
    public static StructurePlacement of(StructurePiece piece) {
        return new StructurePlacement(piece);
    }

    /**
     * Load a {@code .tsaphstruct} file from {@code path} and begin building a placement.
     *
     * @throws IOException If the file is missing, corrupt, or version-mismatched.
     */
    public static StructurePlacement load(Path path) throws IOException {
        return new StructurePlacement(StructureLoaderBridge.getLoader().load(path));
    }

    // ── Position configuration ────────────────────────────────────────────

    /**
     * Set the horizontal anchor position.  The structure is centred on this X/Z
     * by placing its (minX, minZ) corner at {@code (x - sizeX/2, z - sizeZ/2)}.
     * If you want to control the exact corner instead, use {@link #atCorner(int, int)}.
     */
    public StructurePlacement at(int x, int z) {
        int halfX = piece.bounds().sizeX() / 2;
        int halfZ = piece.bounds().sizeZ() / 2;
        this.x = x - halfX;
        this.z = z - halfZ;
        return this;
    }

    /**
     * Set the exact (minX, minZ) corner of the placement bounding box.
     * Use this when you want precise control rather than centering.
     */
    public StructurePlacement atCorner(int x, int z) {
        this.x = x;
        this.z = z;
        return this;
    }

    /**
     * Use an absolute Y coordinate.  Ground offset is still applied on top
     * unless you call {@link #withoutGroundOffset()}.
     */
    public StructurePlacement atY(int y) {
        this.absoluteY = y;
        this.yStrategy = YStrategy.ABSOLUTE;
        return this;
    }

    /**
     * Look up the world surface ({@code WORLD_SURFACE} heightmap) at the
     * chosen X/Z and place flush with the terrain.
     * {@link StructurePiece#groundOffset()} is applied automatically.
     */
    public StructurePlacement onSurface() {
        this.yStrategy = YStrategy.SURFACE;
        return this;
    }

    /**
     * Look up the ocean floor ({@code OCEAN_FLOOR} heightmap) at the chosen X/Z.
     * Useful for underwater ruins and drowned structures.
     */
    public StructurePlacement onOceanFloor() {
        this.yStrategy = YStrategy.OCEAN_FLOOR;
        return this;
    }

    /**
     * Apply an additional vertical offset on top of whatever Y is computed.
     * Positive values move the structure up; negative values bury it.
     *
     * <p>Example — sink 3 blocks into the ground:
     * <pre>
     *   StructurePlacement.of(piece).at(x, z).onSurface().withYOffset(-3).place(world);
     * </pre>
     */
    public StructurePlacement withYOffset(int offset) {
        this.yOffset = offset;
        return this;
    }

    /**
     * Disable the automatic {@link StructurePiece#groundOffset()} correction.
     *
     * <p>By default, if your selection box captured empty air rows at the bottom
     * of the structure, the engine shifts the origin up so the first real block
     * lands at the target Y.  Call this to disable that behaviour and use the
     * raw Y instead.
     */
    public StructurePlacement withoutGroundOffset() {
        this.groundOffset = false;
        return this;
    }

    // ── Terminal ──────────────────────────────────────────────────────────

    /**
     * Execute the placement in {@code world}.
     *
     * <p>Blocks in unloaded chunks are automatically deferred to the world's
     * persistent {@link StructureQueue} — no extra handling needed.
     *
     * @param world Target server world.
     */
    public void place(ServerWorld world) {
        int finalY = switch (yStrategy) {
            case SURFACE    -> world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            case OCEAN_FLOOR -> world.getTopY(Heightmap.Type.OCEAN_FLOOR,  x, z);
            case ABSOLUTE   -> absoluteY;
        };

        int offset = groundOffset ? piece.groundOffset() : 0;
        BlockPos origin = new BlockPos(x, finalY - offset + yOffset, z);
        StructureLoaderBridge.getLoader().place(world, piece, origin);
    }

    // ── Accessors (for inspection / testing) ─────────────────────────────

    /** The {@link StructurePiece} this placement will place. */
    public StructurePiece piece() { return piece; }
}

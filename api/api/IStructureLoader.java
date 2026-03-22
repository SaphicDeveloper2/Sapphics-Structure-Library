package com.sapphic.ssl.api;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Public interface for loading and placing {@code .tsaphstruct} structures.
 *
 * <p>Obtain an instance via {@link StructureLoaderBridge#getLoader()}.
 *
 * <p>Implementations live in {@code com.sapphic.ssl.internal} and are
 * obfuscated at build time — only this interface is part of the public API.
 */
public interface IStructureLoader {

    /**
     * Parse a {@code .tsaphstruct} file from disk into memory.
     *
     * @param path  Absolute path to the {@code .tsaphstruct} file.
     * @return      Decoded {@link StructurePiece}.
     * @throws IOException If the file is missing, corrupt, or version-mismatched.
     */
    StructurePiece load(Path path) throws IOException;

    /**
     * Place a loaded structure piece into {@code world} with its bounding-box
     * minimum corner anchored at {@code origin}.
     *
     * <p>Blocks in loaded chunks are placed immediately.  Blocks whose chunk is
     * not yet loaded are enqueued in the world-persistent {@link StructureQueue}
     * and applied when the chunk is later loaded.
     *
     * @param world   Target server world.
     * @param piece   Structure to place.
     * @param origin  World-space position of the structure's (minX, minY, minZ) corner.
     */
    void place(ServerWorld world, StructurePiece piece, BlockPos origin);

    /**
     * Process all pending placements for the chunk at {@code chunkX, chunkZ}
     * in the given world.  Called automatically by the Mixin pipeline on chunk load.
     *
     * @param world  Server world whose queue should be flushed.
     * @param chunkX Chunk X coordinate.
     * @param chunkZ Chunk Z coordinate.
     */
    void processChunkQueue(ServerWorld world, int chunkX, int chunkZ);
}

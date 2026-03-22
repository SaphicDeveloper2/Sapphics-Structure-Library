package com.sapphic.ssl.api;

import net.minecraft.util.math.ChunkPos;

import java.util.List;

/**
 * Represents the per-world persistent queue of {@link PendingPlacement} entries
 * that could not be applied immediately because their target chunks were unloaded.
 *
 * <p>Entries survive server restarts via the world-specific cache directory:
 * {@code <world>/data/ssl_queue/}.
 *
 * <p>Obtain an instance via {@link StructureLoaderBridge#getQueue(net.minecraft.server.world.ServerWorld)}.
 */
public interface StructureQueue {

    /**
     * Enqueue a single pending placement.
     *
     * @param placement The placement to defer.
     */
    void enqueue(PendingPlacement placement);

    /**
     * Retrieve and remove all pending placements whose target block falls
     * within the given chunk.
     *
     * @param chunkX Chunk X coordinate.
     * @param chunkZ Chunk Z coordinate.
     * @return Mutable list of matching placements (may be empty, never {@code null}).
     */
    List<PendingPlacement> drain(int chunkX, int chunkZ);

    /**
     * Retrieve and remove all pending placements for the given chunk position.
     *
     * @param pos Chunk position.
     * @return Mutable list of matching placements (may be empty, never {@code null}).
     */
    default List<PendingPlacement> drain(ChunkPos pos) {
        return drain(pos.x, pos.z);
    }

    /** Total number of placements currently waiting in this queue. */
    int size();

    /** {@code true} if there are no placements waiting. */
    default boolean isEmpty() { return size() == 0; }

    /**
     * Persist the current queue state to disk.  Called automatically on
     * server-stop; can also be triggered manually for safety.
     */
    void save();

    /** Load queue state from disk, replacing any in-memory state. */
    void load();
}

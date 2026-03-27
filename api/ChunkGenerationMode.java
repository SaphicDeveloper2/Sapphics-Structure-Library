package com.sapphic.ssl.api;

/**
 * Controls how SSL handles unloaded chunks during structure placement.
 *
 * <p>{@link #QUEUE} is the default and preserves SSL's normal behaviour:
 * blocks targeting unloaded chunks are deferred into SSL's persistent queue and
 * applied when those chunks load naturally.
 *
 * <p>{@link #FORCE_GENERATE} is opt-in for mods that need an immediate result.
 * SSL will synchronously generate/load only the chunks intersecting the target
 * structure footprint, then place the structure without using the deferred
 * queue for those chunks.
 */
public enum ChunkGenerationMode {
    /**
     * Default mode. Unloaded target chunks are handled by SSL's deferred queue.
     */
    QUEUE,

    /**
     * Opt-in mode. Required chunks are generated/loaded immediately before
     * placement.
     */
    FORCE_GENERATE
}
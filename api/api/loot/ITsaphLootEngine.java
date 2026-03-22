package com.sapphic.ssl.api.loot;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Public interface for populating container block entities with loot.
 *
 * <p>Obtain an instance via {@link com.sapphic.ssl.api.StructureLoaderBridge#getLootEngine()}.
 *
 * <p>The implementation lives in {@code com.sapphic.ssl.internal.loot} and is obfuscated.
 */
public interface ITsaphLootEngine {

    /**
     * Resolve and fill a container at {@code pos} in {@code world} using the given
     * loot table reference.
     *
     * <ul>
     *   <li>If the ref is {@link LootTableRef.RefType#VANILLA}, the vanilla
     *       {@code LootTable} and {@code LootTableSeed} NBT tags are written to the
     *       container's block entity, and Minecraft handles population on first open.</li>
     *   <li>If the ref is {@link LootTableRef.RefType#TSAPHLOOT}, an
     *       {@code ssl:tsaphloot} tag is written.  The {@code LootableContainerMixin}
     *       resolves and fills the container the first time a player opens it.</li>
     * </ul>
     *
     * @param world  Target server world.
     * @param pos    Absolute position of the container block.
     * @param ref    Loot table reference to apply.
     * @param seed   Random seed for vanilla loot tables (ignored for TsaphLoot,
     *               which generates a fresh seed from the world's random source).
     * @return {@code true} if the tag was applied successfully.
     */
    boolean applyLootTag(ServerWorld world, BlockPos pos, LootTableRef ref, long seed);

    /**
     * Immediately populate a container at {@code pos} with generated loot —
     * without waiting for a player to open it.
     *
     * <p>For VANILLA refs, uses Minecraft's {@code LootTable.supplyInventory}.
     * For TSAPHLOOT refs, runs the pool-based generation engine directly.
     *
     * @param world  Target world.
     * @param pos    Container position.
     * @param ref    Loot table reference.
     * @return {@code true} if population succeeded.
     */
    boolean populate(ServerWorld world, BlockPos pos, LootTableRef ref);

    /**
     * Attempt to resolve a TsaphLoot table by name from the registry.
     * Returns empty if the table hasn't been loaded.
     *
     * @param name The loot table name (file stem without extension).
     */
    Optional<TsaphLootTable> resolve(String name);
}

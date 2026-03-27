package com.sapphic.ssl.api;

import com.sapphic.ssl.api.loot.ITsaphLootEngine;
import com.sapphic.ssl.internal.MultiStructRegistry;
import com.sapphic.ssl.internal.ProceduralEngine;
import com.sapphic.ssl.internal.DeferredGenerationQueue;
import com.sapphic.ssl.internal.ForcedChunkGenerator;
import com.sapphic.ssl.internal.StructureDefinitionRegistry;
import com.sapphic.ssl.internal.StructureLoaderImpl;
import com.sapphic.ssl.internal.StructureTracker;
import com.sapphic.ssl.internal.WandSession;
import com.sapphic.ssl.internal.WandSessionManager;
import com.sapphic.ssl.internal.TsaphMultiStructReader;
import com.sapphic.ssl.internal.TsaphMultiStructWriter;
import com.sapphic.ssl.internal.WorldQueueCache;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * <h2>The Bridge</h2>
 *
 * <p>The sole, clean, public gateway between Mixins / external developers and the
 * obfuscated {@code com.sapphic.ssl.internal} black box.
 *
 * <h3>Architecture contract</h3>
 * <ul>
 * <li>Mixins may <em>only</em> call methods on this class or other classes in
 * {@code com.sapphic.ssl.api}.  Direct imports of {@code internal.*} are forbidden.</li>
 * <li>This class is fully preserved by ProGuard ({@code -keep}).</li>
 * </ul>
 */
public final class StructureLoaderBridge {

    private static volatile StructureLoaderImpl implInstance;
    private static volatile IStructureLoader    loader;
    private static volatile IStructureExporter  exporter;
    private static volatile ITsaphLootEngine    lootEngine;

    private StructureLoaderBridge() {}

    private static StructureLoaderImpl impl() {
        if (implInstance == null) {
            synchronized (StructureLoaderBridge.class) {
                if (implInstance == null) {
                    implInstance = new StructureLoaderImpl();
                    loader       = implInstance;
                    exporter     = implInstance;
                    lootEngine   = implInstance.getLootEngine();
                }
            }
        }
        return implInstance;
    }

    public static IStructureLoader   getLoader()     { impl(); return loader; }
    public static IStructureExporter getExporter()   { impl(); return exporter; }
    public static ITsaphLootEngine   getLootEngine() { impl(); return lootEngine; }

    public static StructureQueue getQueue(ServerWorld world) {
        return WorldQueueCache.getOrCreate(world);
    }

    /**
     * Called by {@code ChunkGeneratorMixin} when chunk at {@code pos} completes
     * feature generation.
     *
     * <p>Drains the direct queue for {@code pos}, then additionally drains the queues
     * for all 8 surrounding chunks.  This second ring of drains is what resolves
     * deferred neighbour-update entries: when chunk (X,Z) loads, blocks on the border
     * of the previously-loaded chunk (X-1, Z) may now have all their face-adjacent
     * neighbours present and can fire their pending update.
     *
     * <p>Using 8-way neighbour drain (not just 4-way) handles diagonal adjacency —
     * a fence corner block that touches three chunks needs all three loaded before its
     * connection-state is correct.
     */
    public static void processChunkQueue(ServerWorld world, ChunkPos pos) {
        IStructureLoader l = getLoader();

        // Primary: drain placements and update-only entries for this chunk
        l.processChunkQueue(world, pos.x, pos.z);

        // Secondary: re-drain the 8 surrounding chunks' update-only entries.
        // A block in an adjacent chunk may have been waiting for this chunk to load
        // before its neighbour-update could fire safely.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                l.processChunkQueue(world, pos.x + dx, pos.z + dz);
            }
        }
    }

    /**
     * Apply any high-level queued structure-generation requests whose anchor chunk
     * is {@code pos}.  Unlike {@link #getQueue(ServerWorld)}, this API works for
     * requests that were registered before the target dimension was entered.
     */
    public static void processQueuedGenerations(ServerWorld world, ChunkPos pos) {
        DeferredGenerationQueue.process(world, pos.x, pos.z);
    }

    /** Persist all world queue caches on server stop. */
    public static void onServerStopping() {
        WorldQueueCache.saveAll();
    }

    /**
     * Run any datapack-registered {@link StructureDefinition} placement checks for
     * the chunk at {@code pos}.  Called alongside {@link #processChunkQueue} from
     * both {@code ChunkGeneratorMixin} and the {@code CHUNK_LOAD} event.
     *
     * <p>Each definition performs a deterministic seeded roll; most chunks produce
     * no placement and return immediately.
     */
    public static void processChunkDefinitions(ServerWorld world, ChunkPos pos) {
        StructureDefinitionRegistry.processChunk(world, pos);
    }

    /**
     * Process pending boss spawns for the loaded chunk at {@code pos}.
     * Called alongside {@link #processChunkQueue} to spawn bosses whose
     * target location is now loaded.
     */
    public static void processBossSpawns(ServerWorld world, ChunkPos pos) {
        StructureTracker.get(world).processPendingBosses(pos.x, pos.z);
    }

    /**
     * All currently registered datapack structure definitions.
     * Useful for tooling, commands, or debugging.
     */
    public static List<StructureDefinition> definitions() {
        return StructureDefinitionRegistry.definitions();
    }

    // ── Multi-struct ───────────────────────────────────────────────────────

    /**
     * Load a {@code .tsaphmultistruct} bundle from disk, applying any companion
     * JSON overrides automatically.
     */
    public static MultiStructBundle loadMultiStruct(Path path) throws IOException {
        return TsaphMultiStructReader.read(path);
    }

    /**
     * Save a {@link MultiStructBundle} to a {@code .tsaphmultistruct} binary file
     * and generate its companion JSON config alongside it.
     */
    public static void saveMultiStruct(MultiStructBundle bundle, Path destination)
            throws IOException {
        TsaphMultiStructWriter.write(bundle, destination);
    }

    /**
     * Procedurally generate a structure from {@code bundle} centred at {@code (x, z)}
     * in {@code world}, chaining pieces up to {@code maxDepth} levels deep.
     *
     * @param world    Target server world.
     * @param bundle   The piece bundle to draw from.
     * @param x        World X anchor.
     * @param z        World Z anchor.
     * @param maxDepth Maximum connector chain depth.
     * @param seed     Optional fixed seed for generation. If null, a random seed is generated.
     */
    public static void spawnMultiStruct(ServerWorld world, MultiStructBundle bundle,
                                        int x, int z, int maxDepth, Long seed) {
        ProceduralEngine.generate(world, bundle, x, z, maxDepth, seed);
    }

    /**
     * Procedurally generate a multi-structure bundle using the requested chunk
        * handling mode.
        *
        * <p>Use {@link ChunkGenerationMode#QUEUE} for normal SSL behaviour, or
        * {@link ChunkGenerationMode#FORCE_GENERATE} when another mod requires the
        * destination chunks to exist immediately.
     */
    public static void spawnMultiStruct(ServerWorld world, MultiStructBundle bundle,
                                        int x, int z, int maxDepth, Long seed,
                                        ChunkGenerationMode chunkMode) {
        ProceduralEngine.generate(world, bundle, x, z, maxDepth, seed, chunkMode);
    }

    /**
     * Procedurally generate a multi-structure bundle with the first piece anchored
     * at the exact world-space {@code origin}.
     */
    public static void spawnMultiStructAt(ServerWorld world, MultiStructBundle bundle,
                                          BlockPos origin, int maxDepth, Long seed) {
        ProceduralEngine.generateAt(world, bundle, origin, maxDepth, seed);
    }

    /**
     * Procedurally generate a multi-structure bundle from an exact origin using
        * the requested chunk handling mode.
        *
        * <p>This overload is useful for cross-dimension placement, portals, and
        * teleport destinations where the Y coordinate is already known.
     */
    public static void spawnMultiStructAt(ServerWorld world, MultiStructBundle bundle,
                                          BlockPos origin, int maxDepth, Long seed,
                                          ChunkGenerationMode chunkMode) {
        ProceduralEngine.generateAt(world, bundle, origin, maxDepth, seed, chunkMode);
    }

    /**
     * Place a standalone structure with explicit control over how unloaded chunks
        * are handled.
        *
        * <p>{@link ChunkGenerationMode#QUEUE} preserves SSL's default deferred
        * placement behaviour.
        *
        * <p>{@link ChunkGenerationMode#FORCE_GENERATE} first generates/loads only
        * the chunks touched by the rotated structure footprint, then places the
        * structure immediately.
     */
    public static void placeStructure(ServerWorld world,
                                      StructurePiece piece,
                                      BlockPos origin,
                                      BlockRotation rotation,
                                      ChunkGenerationMode chunkMode) {
        placeStructure(world, piece, origin, rotation, chunkMode, InteriorFillMode.SKIP_AIR);
    }

    /** Convenience overload using {@link BlockRotation#NONE}. */
    public static void placeStructure(ServerWorld world,
                                      StructurePiece piece,
                                      BlockPos origin,
                                      ChunkGenerationMode chunkMode) {
        placeStructure(world, piece, origin, BlockRotation.NONE, chunkMode, InteriorFillMode.SKIP_AIR);
    }

    /**
     * Place a standalone structure with explicit control over chunk handling and
     * interior air filling.
     *
     * <p>When {@code interiorMode} is {@link InteriorFillMode#FILL_AIR}, air blocks
     * in the structure definition are explicitly placed, clearing any terrain that
     * would otherwise bleed into the structure's interior.
     *
     * @param world        Target server world.
     * @param piece        Structure to place.
     * @param origin       World-space position of the bounding-box minimum corner.
     * @param rotation     Clockwise rotation to apply.
     * @param chunkMode    How to handle unloaded chunks.
     * @param interiorMode How to handle air blocks (interior clearing).
     */
    public static void placeStructure(ServerWorld world,
                                      StructurePiece piece,
                                      BlockPos origin,
                                      BlockRotation rotation,
                                      ChunkGenerationMode chunkMode,
                                      InteriorFillMode interiorMode) {
        ChunkGenerationMode cMode = chunkMode == null ? ChunkGenerationMode.QUEUE : chunkMode;
        InteriorFillMode iMode = interiorMode == null ? InteriorFillMode.SKIP_AIR : interiorMode;
        if (cMode == ChunkGenerationMode.FORCE_GENERATE) {
            ForcedChunkGenerator.ensureStructureArea(world, piece, origin, rotation);
        }
        getLoader().place(world, piece, origin, rotation, iMode);
    }

    /** Convenience overload with interior mode but default rotation. */
    public static void placeStructure(ServerWorld world,
                                      StructurePiece piece,
                                      BlockPos origin,
                                      ChunkGenerationMode chunkMode,
                                      InteriorFillMode interiorMode) {
        placeStructure(world, piece, origin, BlockRotation.NONE, chunkMode, interiorMode);
    }

    /**
        * Generate/load the exact chunk range covering the supplied block-space box.
        *
        * <p>Useful when a mod wants chunk preparation as a separate step before a
        * later teleport, placement, or custom post-processing pass.
     */
    public static void ensureChunksGenerated(ServerWorld world,
                                             BlockPos minInclusive,
                                             BlockPos maxInclusive) {
        ForcedChunkGenerator.ensureBlockArea(world, minInclusive, maxInclusive);
    }

    /**
        * Generate/load a square chunk radius around the given block position.
        *
        * <p>Prefer the bounds-based overload when you already know the exact
        * footprint, since it avoids preparing unnecessary chunks.
     */
    public static void ensureChunksGenerated(ServerWorld world,
                                             BlockPos center,
                                             int chunkRadius) {
        ForcedChunkGenerator.ensureChunkRadius(world, new ChunkPos(center), chunkRadius);
    }

    /**
     * Queue a standalone structure for exact-coordinate placement the next time
     * the target anchor chunk becomes available in {@code dimensionKey}.
     */
    public static void queueStructure(String dimensionKey,
                                      StructurePiece piece,
                                      BlockPos origin,
                                      BlockRotation rotation) {
        DeferredGenerationQueue.enqueueStructure(dimensionKey, piece, origin, rotation);
    }

    /** Convenience overload using {@link BlockRotation#NONE}. */
    public static void queueStructure(String dimensionKey,
                                      StructurePiece piece,
                                      BlockPos origin) {
        queueStructure(dimensionKey, piece, origin, BlockRotation.NONE);
    }

    /** Registry-key overload for queued standalone structures. */
    public static void queueStructure(RegistryKey<World> dimension,
                                      StructurePiece piece,
                                      BlockPos origin,
                                      BlockRotation rotation) {
        queueStructure(dimension.getValue().toString(), piece, origin, rotation);
    }

    /** Registry-key overload using {@link BlockRotation#NONE}. */
    public static void queueStructure(RegistryKey<World> dimension,
                                      StructurePiece piece,
                                      BlockPos origin) {
        queueStructure(dimension.getValue().toString(), piece, origin, BlockRotation.NONE);
    }

    /**
     * Queue a multi-structure bundle for generation at exact world coordinates the
     * next time the target anchor chunk becomes available in {@code dimensionKey}.
     */
    public static void queueMultiStruct(String dimensionKey,
                                        MultiStructBundle bundle,
                                        BlockPos origin,
                                        int maxDepth,
                                        Long seed) {
        DeferredGenerationQueue.enqueueMultiStruct(dimensionKey, bundle, origin, maxDepth, seed);
    }

    /** Registry-key overload for queued multi-structures. */
    public static void queueMultiStruct(RegistryKey<World> dimension,
                                        MultiStructBundle bundle,
                                        BlockPos origin,
                                        int maxDepth,
                                        Long seed) {
        queueMultiStruct(dimension.getValue().toString(), bundle, origin, maxDepth, seed);
    }

    /** Reload all bundles from the given directory into the registry. */
    public static void reloadMultiStructRegistry(Path sslDir) {
        MultiStructRegistry.reload(sslDir);
    }

    /** All registered bundle names. */
    public static Set<String> multiStructNames() {
        return MultiStructRegistry.names();
    }

    /** Retrieve a registered bundle by name. */
    public static Optional<MultiStructBundle> getMultiStruct(String name) {
        return MultiStructRegistry.get(name);
    }

    // ── Wand sessions ──────────────────────────────────────────────────────

    /** Start a new multi-struct building session for the given player. */
    public static WandSession beginSession(UUID playerUuid) {
        return WandSessionManager.begin(playerUuid);
    }

    /** Return the active session for the given player, or empty if none. */
    public static Optional<WandSession> getSession(UUID playerUuid) {
        return WandSessionManager.get(playerUuid);
    }

    /** {@code true} if the given player has an active multi-struct session. */
    public static boolean hasSession(UUID playerUuid) {
        return WandSessionManager.hasSession(playerUuid);
    }

    /** End the session for the given player. */
    public static void endSession(UUID playerUuid) {
        WandSessionManager.end(playerUuid);
    }

    /** End all sessions — call on server stop. */
    public static void endAllSessions() {
        WandSessionManager.endAll();
    }
}
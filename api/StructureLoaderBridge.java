package com.sapphic.ssl.api;

import com.sapphic.ssl.api.loot.ITsaphLootEngine;
import com.sapphic.ssl.internal.MultiStructRegistry;
import com.sapphic.ssl.internal.ProceduralEngine;
import com.sapphic.ssl.internal.StructureDefinitionRegistry;
import com.sapphic.ssl.internal.StructureLoaderImpl;
import com.sapphic.ssl.internal.WandSession;
import com.sapphic.ssl.internal.WandSessionManager;
import com.sapphic.ssl.internal.TsaphMultiStructReader;
import com.sapphic.ssl.internal.TsaphMultiStructWriter;
import com.sapphic.ssl.internal.WorldQueueCache;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

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
 *   <li>Mixins may <em>only</em> call methods on this class or other classes in
 *       {@code com.sapphic.ssl.api}.  Direct imports of {@code internal.*} are forbidden.</li>
 *   <li>This class is fully preserved by ProGuard ({@code -keep}).</li>
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
     */
    public static void spawnMultiStruct(ServerWorld world, MultiStructBundle bundle,
                                        int x, int z, int maxDepth) {
        ProceduralEngine.generate(world, bundle, x, z, maxDepth);
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

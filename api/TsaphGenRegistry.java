package com.sapphic.ssl.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public Java API for registering {@link TsaphGenConfig} worldgen entries.
 *
 * <p>Mods that want to define structure spawn rules in code (rather than through
 * a {@code .tsaphgen} datapack file) call {@link #register} during
 * {@code ModInitializer.onInitialize()}, before the server starts.
 *
 * <p>Registrations from code and registrations from datapack files coexist in
 * the same list — the internal {@code TsaphGenLoader} populates this registry
 * from disk after all code registrations have been made.
 *
 * <h3>Example</h3>
 * <pre>
 * // In your ModInitializer:
 * TsaphGenRegistry.register(
 *     new TsaphGenConfig.Builder("mymod:my_village")
 *         .structure("mymod:my_village")
 *         .weight(0.004f)
 *         .dimensions("minecraft:overworld")
 *         .biomes("minecraft:plains", "minecraft:sunflower_plains")
 *         .salt(7391L)
 *         .build()
 * );
 * </pre>
 *
 * <p><strong>Thread safety:</strong> Registrations use a {@link CopyOnWriteArrayList}
 * so that {@link #all()} can be iterated safely from any thread (e.g. chunk
 * generation) while registrations happen on the main thread during init.
 */
public final class TsaphGenRegistry {

    private TsaphGenRegistry() {}

    /** Live registry — code entries first, then datapack entries appended at reload. */
    private static final CopyOnWriteArrayList<TsaphGenConfig> ENTRIES =
            new CopyOnWriteArrayList<>();

    /**
     * Entries registered by mod code (not from datapacks).
     * Preserved across datapack reloads so they aren't wiped.
     */
    private static final CopyOnWriteArrayList<TsaphGenConfig> CODE_ENTRIES =
            new CopyOnWriteArrayList<>();

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Register a {@link TsaphGenConfig} from Java code.
     *
     * <p>Call during {@code ModInitializer.onInitialize()}.  Code registrations
     * survive datapack {@code /reload} cycles — they are re-added on top of the
     * freshly-scanned datapack entries automatically.
     *
     * @param config The worldgen config to register.
     */
    public static void register(TsaphGenConfig config) {
        if (config == null) throw new NullPointerException("config");
        CODE_ENTRIES.add(config);
        ENTRIES.add(config);
    }

    /**
     * All currently registered configs (code + datapack, in registration order).
     *
     * <p>Returns an unmodifiable snapshot — safe to iterate from any thread.
     */
    public static List<TsaphGenConfig> all() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES));
    }

    /** Number of registered configs. */
    public static int size() { return ENTRIES.size(); }

    // ── Internal API (used by TsaphGenLoader on datapack reload) ──────────

    /**
     * Clear all datapack-sourced entries and re-populate from the provided
     * collection, then re-add code entries on top.
     *
     * <p>Called by {@code TsaphGenLoader} at the end of each datapack reload
     * pass.  Code entries registered via {@link #register} are always preserved.
     *
     * @param datapackEntries Freshly-loaded configs from disk/datapacks.
     */
    public static void reloadDatapack(Collection<TsaphGenConfig> datapackEntries) {
        ENTRIES.clear();
        ENTRIES.addAll(datapackEntries);
        ENTRIES.addAll(CODE_ENTRIES);   // code entries always win / follow
    }

    /**
     * Number of configs registered from Java code (not from datapacks).
     * Intended for diagnostics/logging only.
     */
    public static int codeRegisteredCount() { return CODE_ENTRIES.size(); }
}
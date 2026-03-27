package com.sapphic.ssl.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects the running Minecraft version once at class-load time and exposes it
 * as an enum constant, consumed by {@link SslCompat} and {@link SslMixinPlugin}.
 *
 * <h2>Supported range: 1.21.1 – 1.21.3</h2>
 * <p>All SSL-relevant vanilla APIs are stable across this range:
 * {@code ItemPlacementContext}, {@code DirectionProperty},
 * {@code LootableInventory}, {@code LootContextParameterSet},
 * {@code BlockPosArgumentType}, {@code ClickEvent}, {@code WorldSavePath},
 * {@code StructureWorldAccess}, {@code StructureAccessor}, {@code Chunk}
 * — all present and byte-for-byte identical.</p>
 *
 * <h2>Why 1.21.4+ is not supported</h2>
 * <p>Between 1.21.3 and 1.21.4, Mojang removed or restructured several vanilla
 * classes that SSL depends on at the bytecode level ({@code WorldSavePath},
 * {@code ItemPlacementContext}, {@code DirectionProperty}, {@code LootableInventory},
 * {@code LootContextParameterSet}, {@code BlockPosArgumentType}, {@code ClickEvent}).
 * Their intermediary class IDs do not exist in 1.21.4+ and cause
 * {@link NoClassDefFoundError} at mod initialisation.
 * Fabric Loader enforces the version range via the {@code minecraft} dependency
 * constraint in {@code fabric.mod.json} ({@code ">=1.21.1 <1.21.4"}),
 * so this class will never see a version outside the supported range in practice.
 * The {@code FUTURE} constant is retained purely as a safe fallback.</p>
 */
public enum McVersion {

    /**
     * Minecraft 1.21.1 – 1.21.3 (Yarn).
     * All SSL APIs are stable.  A single compiled jar covers this entire range.
     */
    YARN_1_21(">=1.21.1 <1.21.4"),

    /**
     * Any version outside the supported range.
     * Fabric Loader should already have refused to load this jar via
     * fabric.mod.json, so reaching this constant indicates a misconfiguration.
     */
    FUTURE(">=1.21.4");

    // ── Static detection ──────────────────────────────────────────────────

    /** Detected version for this JVM session. Never null after class-init. */
    public static final McVersion CURRENT;

    private static final Logger LOGGER = LoggerFactory.getLogger("SSL/McVersion");

    static {
        McVersion detected = FUTURE;
        try {
            ModContainer mc = FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .orElseThrow(() -> new IllegalStateException("minecraft mod not found"));
            Version ver = mc.getMetadata().getVersion();

            for (McVersion candidate : values()) {
                if (candidate == FUTURE) continue;
                try {
                    if (VersionPredicate.parse(candidate.predicate).test(ver)) {
                        detected = candidate;
                        break;
                    }
                } catch (VersionParsingException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.error("[SSL] McVersion detection failed — defaulting to FUTURE: {}", e.getMessage());
        }

        CURRENT = detected;

        switch (CURRENT) {
            case YARN_1_21 ->
                LOGGER.info("[SSL] Detected Minecraft 1.21.1–1.21.3 (Yarn) — full compatibility.");
            case FUTURE ->
                LOGGER.error("[SSL] Unsupported Minecraft version detected. " +
                             "This build of SSL targets 1.21.1–1.21.3. " +
                             "Fabric Loader should have blocked loading — " +
                             "check your fabric.mod.json dependency constraint.");
        }
    }

    // ── Instance ──────────────────────────────────────────────────────────

    private final String predicate;

    McVersion(String predicate) { this.predicate = predicate; }

    /** {@code true} if this build is fully compatible with the running version. */
    public boolean isFullySupported() { return this == YARN_1_21; }

    /** {@code true} if the chunk-gen mixin is expected to apply successfully. */
    public boolean hasMixinSupport() { return this == YARN_1_21; }
}

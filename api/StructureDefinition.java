package com.sapphic.ssl.api;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A datapack-driven structure placement definition.
 *
 * <h2>Datapack location</h2>
 * <pre>
 *   data/&lt;namespace&gt;/ssl_structures/&lt;name&gt;.json
 * </pre>
 *
 * <p>The accompanying {@code .tsaphstruct} file is looked up by the same
 * namespace and name automatically:
 * <pre>
 *   data/&lt;namespace&gt;/ssl_structures/&lt;name&gt;.tsaphstruct
 * </pre>
 *
 * <h2>Full JSON schema</h2>
 * <pre>
 * {
 *   "dimension":   "minecraft:overworld",
 *   "biomes":      ["minecraft:plains", "minecraft:forest"],
 *   "y_placement": "surface",
 *   "y_offset":    0,
 *   "frequency":   0.005,
 *   "salt":        12345,
 *   "loot_overrides": {
 *     "dungeon_chest_slot": "dungeon_chest"
 *   }
 * }
 * </pre>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code dimension} — registry key of the target dimension.
 *       Use {@code "*"} to allow any dimension.  Default: {@code "minecraft:overworld"}.</li>
 *   <li>{@code biomes} — list of biome registry keys to whitelist.
 *       Empty list = all biomes allowed.  Default: all biomes.</li>
 *   <li>{@code y_placement} — one of {@code "surface"}, {@code "ocean_floor"},
 *       {@code "absolute"}.  Default: {@code "surface"}.</li>
 *   <li>{@code y_offset} — integer offset applied on top of the computed Y.
 *       Useful for burying structures slightly underground.  Default: {@code 0}.</li>
 *   <li>{@code frequency} — probability per chunk this structure generates,
 *       {@code 0.0}–{@code 1.0}.  Default: {@code 0.005}.</li>
 *   <li>{@code salt} — per-definition seed modifier ensuring different structures
 *       don't always co-generate in the same chunks.  Default: {@code 0}.</li>
 *   <li>{@code loot_overrides} — optional map of block-entity type identifiers or
 *       names to a {@code .tsaphloot} table name, overriding any loot ref baked
 *       into the structure file at placement time.</li>
 * </ul>
 */
public final class StructureDefinition {

    /** File extension for definition files. */
    public static final String EXTENSION = ".json";

    /** Datapack sub-directory scanned for definitions and structure files. */
    public static final String DATAPACK_DIR = "ssl_structures";

    // ── Fields ────────────────────────────────────────────────────────────

    /** Fully qualified id, e.g. {@code "yourmod:myvillage"}. */
    private final String id;

    /** Dimension registry key, or {@code "*"} for any dimension. */
    private final String dimension;

    /** Biome whitelist.  Empty = all biomes. */
    private final List<String> biomes;

    /** Vertical placement strategy. */
    private final YPlacement yPlacement;

    /** Vertical offset applied on top of the computed Y. */
    private final int yOffset;

    /** Per-chunk generation probability (0.0–1.0). */
    private final float frequency;

    /** Per-definition seed modifier. */
    private final long salt;

    /** Interior fill mode — how air blocks inside structures are handled. */
    private final InteriorFillMode interiorFill;

    // ── Y strategy ────────────────────────────────────────────────────────

    /** How the engine determines the Y coordinate when placing this structure. */
    public enum YPlacement {
        /**
         * Place flush with the world surface ({@code WORLD_SURFACE} heightmap).
         * The structure's {@link StructurePiece#groundOffset()} is applied
         * automatically so empty air rows at the bottom of the selection don't
         * cause floating.
         */
        SURFACE,

        /**
         * Place flush with the ocean floor ({@code OCEAN_FLOOR} heightmap).
         * Useful for underwater ruins or drowned structures.
         */
        OCEAN_FLOOR,

        /**
         * Use the literal {@link #yOffset} value as the absolute world Y.
         * No heightmap lookup is performed.  {@link StructurePiece#groundOffset()}
         * is still applied unless overridden via the Java API.
         */
        ABSOLUTE;

        public static YPlacement fromJson(String s) {
            return switch (s.toLowerCase()) {
                case "surface"     -> SURFACE;
                case "ocean_floor" -> OCEAN_FLOOR;
                case "absolute"    -> ABSOLUTE;
                default -> {
                    throw new IllegalArgumentException(
                            "Unknown y_placement value: '" + s +
                            "'. Expected: surface | ocean_floor | absolute");
                }
            };
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────

    private StructureDefinition(String id, String dimension, List<String> biomes,
                                YPlacement yPlacement, int yOffset,
                                float frequency, long salt,
                                InteriorFillMode interiorFill) {
        this.id           = Objects.requireNonNull(id,         "id");
        this.dimension    = Objects.requireNonNull(dimension,  "dimension");
        this.biomes       = Collections.unmodifiableList(biomes);
        this.yPlacement   = Objects.requireNonNull(yPlacement, "yPlacement");
        this.yOffset      = yOffset;
        this.frequency    = frequency;
        this.salt         = salt;
        this.interiorFill = Objects.requireNonNull(interiorFill, "interiorFill");
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** Fully-qualified id, e.g. {@code "yourmod:myvillage"}. */
    public String       id()          { return id; }
    public String       dimension()   { return dimension; }
    public List<String> biomes()      { return biomes; }
    public YPlacement   yPlacement()  { return yPlacement; }
    public int          yOffset()     { return yOffset; }
    public float        frequency()   { return frequency; }
    public long         salt()        { return salt; }

    /**
     * How air blocks inside the structure are handled during placement.
     *
     * @return the interior fill mode (default: {@link InteriorFillMode#SKIP_AIR})
     */
    public InteriorFillMode interiorFill() { return interiorFill; }

    /**
     * {@code true} if this definition targets any dimension (wildcard {@code "*"}).
     */
    public boolean isAnyDimension() { return "*".equals(dimension); }

    /**
     * {@code true} if this definition generates in all biomes (empty whitelist).
     */
    public boolean isAnyBiome()     { return biomes.isEmpty(); }

    // ── JSON parsing ──────────────────────────────────────────────────────

    /**
     * Parse a {@code .json} definition file.
     *
     * @param id     Fully-qualified id derived from the datapack resource path
     *               (e.g. {@code "yourmod:myvillage"}).
     * @param reader JSON content reader.
     */
    public static StructureDefinition fromJson(String id, Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

        String dimension = root.has("dimension")
                ? root.get("dimension").getAsString()
                : "minecraft:overworld";

        List<String> biomes = new ArrayList<>();
        if (root.has("biomes")) {
            JsonArray arr = root.getAsJsonArray("biomes");
            for (int i = 0; i < arr.size(); i++) {
                biomes.add(arr.get(i).getAsString());
            }
        }

        YPlacement yPlacement = root.has("y_placement")
                ? YPlacement.fromJson(root.get("y_placement").getAsString())
                : YPlacement.SURFACE;

        int   yOffset   = root.has("y_offset")   ? root.get("y_offset").getAsInt()     : 0;
        float frequency = root.has("frequency")   ? root.get("frequency").getAsFloat()  : 0.005f;
        long  salt      = root.has("salt")        ? root.get("salt").getAsLong()        : 0L;

        InteriorFillMode interiorFill = InteriorFillMode.SKIP_AIR;
        if (root.has("interior_fill")) {
            String ifValue = root.get("interior_fill").getAsString().toLowerCase(Locale.ROOT);
            interiorFill = switch (ifValue) {
                case "fill_air" -> InteriorFillMode.FILL_AIR;
                case "skip_air" -> InteriorFillMode.SKIP_AIR;
                default -> throw new IllegalArgumentException(
                        "Unknown interior_fill value: '" + ifValue +
                        "'. Expected: skip_air | fill_air");
            };
        }

        return new StructureDefinition(id, dimension, biomes, yPlacement, yOffset, frequency, salt, interiorFill);
    }

    @Override
    public String toString() {
        return "StructureDefinition[" + id + " dim=" + dimension
               + " freq=" + frequency + " y=" + yPlacement + "+" + yOffset
               + " interior=" + interiorFill.name().toLowerCase(Locale.ROOT) + "]";
    }
}

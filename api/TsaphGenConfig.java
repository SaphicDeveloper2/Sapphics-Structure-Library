package com.sapphic.ssl.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A {@code .tsaphgen} worldgen configuration file.
 *
 * <p>Defines where a structure spawns — which dimensions, which biomes, how
 * frequently, and with which vertical placement strategy.  Unlike the older
 * {@link StructureDefinition} (which tightly couples a single {@code .tsaphstruct}
 * to its JSON definition), a {@code .tsaphgen} file can reference <em>any</em>
 * registered structure by its namespaced id and can target <em>multiple</em>
 * dimensions.
 *
 * <h2>Datapack location</h2>
 * <pre>
 *   data/&lt;namespace&gt;/ssl_worldgen/&lt;name&gt;.tsaphgen
 * </pre>
 *
 * <h2>Full JSON schema</h2>
 * <pre>
 * {
 *   "comment":     "Optional human-readable note — ignored by the engine",
 *   "structure":   "mymod:my_village",
 *   "weight":      0.005,
 *   "dimensions":  ["minecraft:overworld"],
 *   "biomes":      ["minecraft:plains", "minecraft:forest"],
 *   "y_placement": "surface",
 *   "y_offset":    0,
 *   "salt":        12345
 * }
 * </pre>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code structure} — Namespaced id of the target structure, e.g.
 *       {@code "mymod:my_village"}.  The engine looks the structure up in the
 *       {@code ssl_structures} datapack directory (same namespace + name) and
 *       falls back to the world's {@code generated/ssl/} directory.
 *       If omitted, the engine uses the same namespace and stem as the
 *       {@code .tsaphgen} file itself (pairing convention).</li>
 *   <li>{@code weight} — Per-chunk spawn probability in the range {@code 0.0}–{@code 1.0}.
 *       Equivalent to {@code frequency} in {@link StructureDefinition}.
 *       Default: {@code 0.005}.</li>
 *   <li>{@code dimensions} — List of dimension registry keys.
 *       Use {@code ["*"]} to allow any dimension.  Default: {@code ["minecraft:overworld"]}.</li>
 *   <li>{@code biomes} — List of biome registry keys to whitelist.
 *       Empty list or omitted = all biomes.  Default: all biomes.</li>
 *   <li>{@code y_placement} — One of {@code "surface"}, {@code "ocean_floor"},
 *       {@code "absolute"}.  Default: {@code "surface"}.</li>
 *   <li>{@code y_offset} — Integer offset applied on top of the computed Y.
 *       Default: {@code 0}.</li>
 *   <li>{@code salt} — Per-config seed modifier so different structures don't
 *       always co-generate in the same chunks.  Default: {@code 0}.</li>
 * </ul>
 */
public final class TsaphGenConfig {

    // ── Format constants ──────────────────────────────────────────────────

    /** File extension for worldgen config files. */
    public static final String EXTENSION = ".tsaphgen";

    /** Datapack sub-directory scanned for {@code .tsaphgen} files. */
    public static final String DATAPACK_DIR = "ssl_worldgen";

    /** Wildcard sentinel meaning "any dimension". */
    public static final String WILDCARD = "*";

    // ── Fields ────────────────────────────────────────────────────────────

    /** Fully-qualified config id, e.g. {@code "mymod:myvillage"}. */
    private final String       id;

    /**
     * Namespaced id of the structure to place, e.g. {@code "mymod:myvillage"}.
     * May be {@code null} — the engine then uses the same namespace+stem as the
     * {@code .tsaphgen} file (pairing convention).
     */
    private final String       structure;

    /** Per-chunk spawn probability (0.0–1.0). */
    private final float        weight;

    /**
     * Dimension whitelist.  A list containing {@value #WILDCARD} means any
     * dimension.  Empty list is treated identically to {@code ["*"]}.
     */
    private final List<String> dimensions;

    /** Biome whitelist.  Empty = all biomes. */
    private final List<String> biomes;

    /** Vertical placement strategy. */
    private final StructureDefinition.YPlacement yPlacement;

    /** Vertical offset applied on top of the computed Y. */
    private final int yOffset;

    /** Per-config seed modifier. */
    private final long salt;

    // ── Constructor ───────────────────────────────────────────────────────

    private TsaphGenConfig(String id, String structure, float weight,
                           List<String> dimensions, List<String> biomes,
                           StructureDefinition.YPlacement yPlacement,
                           int yOffset, long salt) {
        this.id         = Objects.requireNonNull(id, "id");
        this.structure  = structure;
        this.weight     = Math.max(0f, Math.min(1f, weight));
        this.dimensions = Collections.unmodifiableList(dimensions);
        this.biomes     = Collections.unmodifiableList(biomes);
        this.yPlacement = Objects.requireNonNull(yPlacement, "yPlacement");
        this.yOffset    = yOffset;
        this.salt       = salt;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** Fully-qualified config id, e.g. {@code "mymod:myvillage"}. */
    public String       id()          { return id; }

    /**
     * Structure id, or {@code null} to use pairing convention
     * (same namespace + stem as the {@code .tsaphgen} file).
     */
    public String       structure()   { return structure; }

    /** Per-chunk spawn probability (0.0–1.0). */
    public float        weight()      { return weight; }

    /** Dimension whitelist.  May contain {@value #WILDCARD}. */
    public List<String> dimensions()  { return dimensions; }

    /** Biome whitelist.  Empty = all biomes. */
    public List<String> biomes()      { return biomes; }

    /** Vertical placement strategy. */
    public StructureDefinition.YPlacement yPlacement() { return yPlacement; }

    /** Vertical offset applied on top of the computed Y. */
    public int          yOffset()     { return yOffset; }

    /** Per-config seed modifier. */
    public long         salt()        { return salt; }

    // ── Derived helpers ───────────────────────────────────────────────────

    /**
     * {@code true} if this config allows generation in any dimension
     * (wildcard or empty dimension list).
     */
    public boolean isAnyDimension() {
        return dimensions.isEmpty() || dimensions.contains(WILDCARD);
    }

    /**
     * {@code true} if {@code dimensionKey} is in this config's dimension list,
     * or the list is a wildcard.
     */
    public boolean allowsDimension(String dimensionKey) {
        return isAnyDimension() || dimensions.contains(dimensionKey);
    }

    /**
     * {@code true} if this config generates in all biomes (empty whitelist).
     */
    public boolean isAnyBiome() { return biomes.isEmpty(); }

    /**
     * {@code true} if {@code biomeKey} passes the biome whitelist.
     */
    public boolean allowsBiome(String biomeKey) {
        return isAnyBiome() || biomes.contains(biomeKey);
    }

    // ── JSON parsing ──────────────────────────────────────────────────────

    /**
     * Parse a {@code .tsaphgen} file.
     *
     * @param id     Fully-qualified id derived from the datapack resource path
     *               (e.g. {@code "mymod:myvillage"}).
     * @param reader JSON content reader (UTF-8).
     */
    public static TsaphGenConfig fromJson(String id, Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        return fromJsonObject(id, root);
    }

    /** Parse from an already-deserialized {@link JsonObject}. */
    public static TsaphGenConfig fromJsonObject(String id, JsonObject root) {
        String structure = root.has("structure")
                ? root.get("structure").getAsString()
                : null;

        float weight = root.has("weight")
                ? root.get("weight").getAsFloat()
                : 0.005f;

        List<String> dimensions = new ArrayList<>();
        if (root.has("dimensions")) {
            JsonArray arr = root.getAsJsonArray("dimensions");
            for (int i = 0; i < arr.size(); i++) dimensions.add(arr.get(i).getAsString());
        } else {
            dimensions.add("minecraft:overworld");
        }

        List<String> biomes = new ArrayList<>();
        if (root.has("biomes")) {
            JsonArray arr = root.getAsJsonArray("biomes");
            for (int i = 0; i < arr.size(); i++) biomes.add(arr.get(i).getAsString());
        }

        StructureDefinition.YPlacement yPlacement = root.has("y_placement")
                ? StructureDefinition.YPlacement.fromJson(root.get("y_placement").getAsString())
                : StructureDefinition.YPlacement.SURFACE;

        int  yOffset = root.has("y_offset") ? root.get("y_offset").getAsInt()  : 0;
        long salt    = root.has("salt")      ? root.get("salt").getAsLong()     : 0L;

        return new TsaphGenConfig(id, structure, weight, dimensions, biomes,
                                  yPlacement, yOffset, salt);
    }

    // ── JSON serialisation ────────────────────────────────────────────────

    /** Serialise to a {@link JsonObject} (the raw representation). */
    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();

        obj.addProperty("comment",
                "Generated by Sapphics Structure Library — edit freely");

        if (structure != null) obj.addProperty("structure", structure);
        obj.addProperty("weight", weight);

        JsonArray dims = new JsonArray();
        dimensions.forEach(dims::add);
        obj.add("dimensions", dims);

        if (!biomes.isEmpty()) {
            JsonArray bs = new JsonArray();
            biomes.forEach(bs::add);
            obj.add("biomes", bs);
        }

        obj.addProperty("y_placement", yPlacement.name().toLowerCase());
        obj.addProperty("y_offset",    yOffset);
        obj.addProperty("salt",        salt);

        return obj;
    }

    /**
     * Write a pretty-printed {@code .tsaphgen} file to {@code writer}.
     *
     * @param writer UTF-8 writer pointed at the output file.
     */
    public void write(Writer writer) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        gson.toJson(toJsonObject(), writer);
    }

    // ── Builder ───────────────────────────────────────────────────────────

    /**
     * Fluent builder for programmatic config construction.
     *
     * <pre>
     * TsaphGenConfig config = new TsaphGenConfig.Builder("mymod:myvillage")
     *         .structure("mymod:myvillage")
     *         .weight(0.005f)
     *         .dimensions("minecraft:overworld")
     *         .biomes("minecraft:plains", "minecraft:forest")
     *         .yPlacement(StructureDefinition.YPlacement.SURFACE)
     *         .salt(12345L)
     *         .build();
     * TsaphGenRegistry.register(config);
     * </pre>
     */
    public static final class Builder {

        private final String id;
        private String       structure  = null;
        private float        weight     = 0.005f;
        private List<String> dimensions = new ArrayList<>();
        private List<String> biomes     = new ArrayList<>();
        private StructureDefinition.YPlacement yPlacement = StructureDefinition.YPlacement.SURFACE;
        private int          yOffset    = 0;
        private long         salt       = 0L;

        /**
         * @param id Fully-qualified config id, e.g. {@code "mymod:myvillage"}.
         */
        public Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
            // Default: overworld only
            this.dimensions.add("minecraft:overworld");
        }

        /**
         * Set the structure to spawn.  If not called, the engine uses the
         * pairing convention (same namespace + stem as the config id).
         */
        public Builder structure(String structureId) {
            this.structure = structureId;
            return this;
        }

        /**
         * Per-chunk spawn probability (0.0–1.0).
         * Default: {@code 0.005} (roughly 1-in-200 chunks).
         */
        public Builder weight(float w) {
            this.weight = w;
            return this;
        }

        /**
         * Replace the dimension list.  Pass {@link TsaphGenConfig#WILDCARD}
         * ({@code "*"}) as the sole element to allow any dimension.
         */
        public Builder dimensions(String... dims) {
            this.dimensions = new ArrayList<>(List.of(dims));
            return this;
        }

        /** Allow any dimension (wildcard). */
        public Builder anyDimension() {
            return dimensions(WILDCARD);
        }

        /**
         * Biome whitelist.  Pass no arguments to clear (allow all biomes).
         */
        public Builder biomes(String... bs) {
            this.biomes = new ArrayList<>(List.of(bs));
            return this;
        }

        /** Vertical placement strategy. */
        public Builder yPlacement(StructureDefinition.YPlacement placement) {
            this.yPlacement = Objects.requireNonNull(placement);
            return this;
        }

        /** Vertical offset applied on top of the computed Y. */
        public Builder yOffset(int offset) {
            this.yOffset = offset;
            return this;
        }

        /** Per-config seed modifier. */
        public Builder salt(long salt) {
            this.salt = salt;
            return this;
        }

        /** Build the {@link TsaphGenConfig}. */
        public TsaphGenConfig build() {
            return new TsaphGenConfig(id, structure, weight,
                    new ArrayList<>(dimensions), new ArrayList<>(biomes),
                    yPlacement, yOffset, salt);
        }
    }

    // ── toString ──────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "TsaphGenConfig[" + id
               + " struct=" + (structure != null ? structure : "<paired>")
               + " w=" + weight
               + " dims=" + dimensions
               + " y=" + yPlacement + "+" + yOffset + "]";
    }
}
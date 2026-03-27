package com.sapphic.ssl.api;

import java.util.Objects;

import com.google.gson.JsonObject;

import net.minecraft.nbt.NbtCompound;

/**
 * Configuration for spawning a boss entity when a structure room loads.
 *
 * <p>Boss entities are spawned only once per structure instance when the
 * room containing them is fully loaded into the world. The spawn is tracked
 * persistently per-world to prevent duplicate spawns on server restarts.
 *
 * <h2>JSON schema (inside structure definition or .tsaphgen)</h2>
 * <pre>
 * {
 *   "boss": {
 *     "entity": "minecraft:warden",
 *     "offset_x": 0,
 *     "offset_y": 1,
 *     "offset_z": 0,
 *     "nbt": {
 *       "CustomName": "{\"text\":\"Dungeon Guardian\"}"
 *     }
 *   }
 * }
 * </pre>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code entity} — Namespaced entity type id (required), e.g.
 *       {@code "minecraft:warden"} or {@code "mymod:custom_boss"}.</li>
 *   <li>{@code offset_x}, {@code offset_y}, {@code offset_z} — Position offset
 *       from the structure's center/origin. Default: (0, 1, 0).</li>
 *   <li>{@code nbt} — Optional NBT compound to merge into the spawned entity.
 *       Can include CustomName, attributes, equipment, etc.</li>
 * </ul>
 *
 * @see TsaphGenConfig.Builder#boss(BossSpawnConfig)
 * @see StructureDefinition
 */
public final class BossSpawnConfig {

    /** No boss configured — sentinel for structures without bosses. */
    public static final BossSpawnConfig NONE = new BossSpawnConfig(null, 0, 1, 0, null);

    // ── Fields ────────────────────────────────────────────────────────────

    /**
     * Namespaced entity type id, e.g. {@code "minecraft:warden"}.
     * {@code null} means no boss.
     */
    private final String entityType;

    /** X offset from structure center. */
    private final int offsetX;

    /** Y offset from structure floor (usually 1 to avoid spawning inside ground). */
    private final int offsetY;

    /** Z offset from structure center. */
    private final int offsetZ;

    /**
     * Optional NBT to merge into spawned entity.
     * Can include CustomName, Health, attributes, equipment, etc.
     */
    private final NbtCompound nbt;

    // ── Constructor ───────────────────────────────────────────────────────

    private BossSpawnConfig(String entityType, int offsetX, int offsetY, int offsetZ,
                            NbtCompound nbt) {
        this.entityType = entityType;
        this.offsetX    = offsetX;
        this.offsetY    = offsetY;
        this.offsetZ    = offsetZ;
        this.nbt        = nbt;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /**
     * Entity type id, or {@code null} if this is a "no boss" config.
     */
    public String entityType() { return entityType; }

    /** X offset from structure center. */
    public int offsetX() { return offsetX; }

    /** Y offset from structure floor. */
    public int offsetY() { return offsetY; }

    /** Z offset from structure center. */
    public int offsetZ() { return offsetZ; }

    /**
     * NBT to merge into the spawned entity, or {@code null}.
     */
    public NbtCompound nbt() { return nbt; }

    /**
     * {@code true} if this config actually defines a boss to spawn.
     */
    public boolean hasBoss() {
        return entityType != null && !entityType.isBlank();
    }

    // ── JSON parsing ──────────────────────────────────────────────────────

    /**
     * Parse a boss config from a JSON object.
     *
     * @param obj The "boss" object from the structure definition.
     * @return Parsed config, or {@link #NONE} if the object is null/empty.
     */
    public static BossSpawnConfig fromJson(JsonObject obj) {
        if (obj == null || !obj.has("entity")) {
            return NONE;
        }

        String entity = obj.get("entity").getAsString();
        int ox = obj.has("offset_x") ? obj.get("offset_x").getAsInt() : 0;
        int oy = obj.has("offset_y") ? obj.get("offset_y").getAsInt() : 1;
        int oz = obj.has("offset_z") ? obj.get("offset_z").getAsInt() : 0;

        NbtCompound nbt = null;
        if (obj.has("nbt")) {
            // Parse the nbt object into an NbtCompound
            nbt = parseNbtFromJson(obj.getAsJsonObject("nbt"));
        }

        return new BossSpawnConfig(entity, ox, oy, oz, nbt);
    }

    /**
     * Convert this config to a JSON object for serialisation.
     */
    public JsonObject toJson() {
        if (!hasBoss()) return null;

        JsonObject obj = new JsonObject();
        obj.addProperty("entity", entityType);
        obj.addProperty("offset_x", offsetX);
        obj.addProperty("offset_y", offsetY);
        obj.addProperty("offset_z", offsetZ);
        // NBT serialisation intentionally omitted for simplicity
        return obj;
    }

    // ── Builder ───────────────────────────────────────────────────────────

    /**
     * Fluent builder for programmatic boss config construction.
     *
     * <pre>
     * BossSpawnConfig boss = new BossSpawnConfig.Builder("minecraft:warden")
     *         .offset(0, 1, 0)
     *         .withNbt(myNbtCompound)
     *         .build();
     * </pre>
     */
    public static final class Builder {
        private final String entityType;
        private int offsetX = 0;
        private int offsetY = 1;
        private int offsetZ = 0;
        private NbtCompound nbt = null;

        /**
         * @param entityType Namespaced entity type id, e.g. {@code "minecraft:warden"}.
         */
        public Builder(String entityType) {
            this.entityType = Objects.requireNonNull(entityType, "entityType");
        }

        /** Set the spawn offset from structure center. */
        public Builder offset(int x, int y, int z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        /** Set X offset only. */
        public Builder offsetX(int x) { this.offsetX = x; return this; }

        /** Set Y offset only. */
        public Builder offsetY(int y) { this.offsetY = y; return this; }

        /** Set Z offset only. */
        public Builder offsetZ(int z) { this.offsetZ = z; return this; }

        /**
         * Set NBT to merge into the spawned entity.
         * Can include CustomName, attributes, equipment, etc.
         */
        public Builder withNbt(NbtCompound nbt) {
            this.nbt = nbt;
            return this;
        }

        /** Build the {@link BossSpawnConfig}. */
        public BossSpawnConfig build() {
            return new BossSpawnConfig(entityType, offsetX, offsetY, offsetZ, nbt);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Parse a simple NBT compound from JSON.
     * Supports string, number, and boolean values only (no nested compounds).
     */
    private static NbtCompound parseNbtFromJson(JsonObject json) {
        NbtCompound nbt = new NbtCompound();
        for (String key : json.keySet()) {
            var element = json.get(key);
            if (element.isJsonPrimitive()) {
                var prim = element.getAsJsonPrimitive();
                if (prim.isString()) {
                    nbt.putString(key, prim.getAsString());
                } else if (prim.isNumber()) {
                    // Store as double for flexibility
                    nbt.putDouble(key, prim.getAsDouble());
                } else if (prim.isBoolean()) {
                    nbt.putBoolean(key, prim.getAsBoolean());
                }
            }
        }
        return nbt;
    }

    @Override
    public String toString() {
        if (!hasBoss()) return "BossSpawnConfig[NONE]";
        return "BossSpawnConfig[" + entityType
               + " offset=(" + offsetX + "," + offsetY + "," + offsetZ + ")"
               + (nbt != null ? " +nbt" : "") + "]";
    }
}

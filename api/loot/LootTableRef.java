package com.sapphic.ssl.api.loot;

import java.util.Objects;

/**
 * A reference to a loot table that should populate a container in a placed structure.
 *
 * <p>Two variants:
 * <ul>
 *   <li>{@link RefType#VANILLA} — refers to a vanilla / datapack {@code LootTable} by its
 *       full registry-key string (e.g. {@code "minecraft:chests/simple_dungeon"}).
 *       The engine sets the container's {@code LootTable} and {@code LootTableSeed} NBT
 *       tags and lets Minecraft populate the chest on first open.</li>
 *   <li>{@link RefType#TSAPHLOOT} — refers to a {@link TsaphLootTable} by its name
 *       (e.g. {@code "dungeon_chest"}).  The engine stores a synthetic {@code ssl:tsaphloot/<n>} loot table registry
 *       key on the container; {@code LootableInventoryMixin} intercepts
 *       {@code generateLoot} on first player-open and fills it.</li>
 * </ul>
 *
 * <p>Stored in {@code .tsaphstruct} v2 files in the {@code LOOT REFS} section.
 */
public final class LootTableRef {

    /** Type of loot table being referenced. */
    public enum RefType {
        /** Vanilla / datapack loot table. Wire byte: {@code 0x00}. */
        VANILLA((byte) 0x00),
        /** Custom {@code .tsaphloot} table managed by this library. Wire byte: {@code 0x01}. */
        TSAPHLOOT((byte) 0x01);

        private final byte wireValue;
        RefType(byte wireValue) { this.wireValue = wireValue; }
        public byte wireValue() { return wireValue; }

        /** Decode from the single byte stored in the binary file. */
        public static RefType fromWire(byte b) {
            return switch (b) {
                case 0x00 -> VANILLA;
                case 0x01 -> TSAPHLOOT;
                default   -> throw new IllegalArgumentException("Unknown LootTableRef type byte: 0x" + Integer.toHexString(b & 0xFF));
            };
        }
    }

    private final RefType type;

    /**
     * For {@link RefType#VANILLA}: full loot-table registry key string,
     *   e.g. {@code "minecraft:chests/simple_dungeon"}.
     * For {@link RefType#TSAPHLOOT}: the name of the {@link TsaphLootTable},
     *   e.g. {@code "dungeon_chest"} (no extension).
     */
    private final String  id;

    private LootTableRef(RefType type, String id) {
        this.type = Objects.requireNonNull(type, "type");
        this.id   = Objects.requireNonNull(id,   "id");
    }

    /** Create a reference to a vanilla / datapack loot table. */
    public static LootTableRef vanilla(String lootTableKey) {
        return new LootTableRef(RefType.VANILLA, lootTableKey);
    }

    /** Create a reference to a {@code .tsaphloot} custom table. */
    public static LootTableRef tsaphloot(String tableName) {
        return new LootTableRef(RefType.TSAPHLOOT, tableName);
    }

    public RefType type() { return type; }
    public String  id()   { return id; }

    public boolean isVanilla()    { return type == RefType.VANILLA; }
    public boolean isTsaphloot()  { return type == RefType.TSAPHLOOT; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LootTableRef r)) return false;
        return type == r.type && id.equals(r.id);
    }

    @Override
    public int    hashCode() { return Objects.hash(type, id); }
    @Override
    public String toString()  { return "LootTableRef[" + type + ":" + id + "]"; }
}

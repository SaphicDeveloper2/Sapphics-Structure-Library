package com.sapphic.ssl.api.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fluent builder for constructing {@link TsaphLootTable} instances in code.
 *
 * <p>Replaces hand-written JSON for programmatic loot table creation.  The API
 * mirrors the JSON schema one-to-one so tables are easy to port between the two
 * representations.
 *
 * <h2>Usage</h2>
 * <pre>
 * TsaphLootTable table = TsaphLootBuilder.create("my_dungeon_chest")
 *     .comment("Built in code — same result as the JSON file")
 *     .pool(pool -> pool
 *         .rolls(3, 6)
 *         .item("minecraft:bread")   .weight(30).count(1, 4) .add()
 *         .item("minecraft:arrow")   .weight(25).count(4, 16).add()
 *         .item("minecraft:diamond") .weight(5) .count(1, 3) .add()
 *         .empty(20)                                          // weighted miss
 *     )
 *     .pool(pool -> pool
 *         .rolls(0, 1)
 *         .item("minecraft:enchanted_book")
 *             .weight(10)
 *             .enchant("minecraft:mending", 1)
 *             .add()
 *         .item("minecraft:golden_apple").weight(5).add()
 *     )
 *     .build();
 *
 * // Register and use immediately
 * StructureLoaderBridge.getLootEngine();   // ensure engine is up
 * lootEngine.applyLootTag(world, chestPos, LootTableRef.tsaphloot("my_dungeon_chest"), 0L);
 * </pre>
 */
public final class TsaphLootBuilder {

    private final String       name;
    private String             comment = "";
    private final List<TsaphLootPool> pools = new ArrayList<>();

    private TsaphLootBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    // ── Entry point ───────────────────────────────────────────────────────

    /** Begin building a loot table with the given name (must match the file stem if saved). */
    public static TsaphLootBuilder create(String name) {
        return new TsaphLootBuilder(name);
    }

    // ── Table fields ──────────────────────────────────────────────────────

    /** Optional description — stored in the JSON but ignored by the engine at runtime. */
    public TsaphLootBuilder comment(String comment) {
        this.comment = comment == null ? "" : comment;
        return this;
    }

    /**
     * Add a loot pool configured by the given consumer.
     *
     * <pre>
     *   .pool(p -> p
     *       .rolls(2, 4)
     *       .item("minecraft:iron_ingot").weight(20).count(1, 3).add()
     *       .empty(5)
     *   )
     * </pre>
     */
    public TsaphLootBuilder pool(Consumer<PoolBuilder> configure) {
        PoolBuilder pb = new PoolBuilder();
        configure.accept(pb);
        pools.add(pb.build());
        return this;
    }

    /** Finalise and return the completed {@link TsaphLootTable}. */
    public TsaphLootTable build() {
        return new TsaphLootTable(name, comment, new ArrayList<>(pools));
    }

    // ── Pool builder ──────────────────────────────────────────────────────

    /**
     * Builder for a single {@link TsaphLootPool}.
     *
     * <p>Methods are chained on the pool builder rather than the table builder.
     * Once all entries are added, the pool is automatically committed — there is
     * no explicit {@code endPool()} call.
     */
    public static final class PoolBuilder {

        private LootRange              rolls   = LootRange.fixed(1);
        private final List<TsaphLootEntry> entries = new ArrayList<>();

        private PoolBuilder() {}

        /** Fixed number of rolls. */
        public PoolBuilder rolls(int fixed) {
            this.rolls = LootRange.fixed(fixed);
            return this;
        }

        /** Random roll count in {@code [min, max]} inclusive. */
        public PoolBuilder rolls(int min, int max) {
            this.rolls = LootRange.of(min, max);
            return this;
        }

        /**
         * Begin configuring an item entry.  Call {@link EntryBuilder#add()} to
         * commit the entry and return to this pool builder.
         *
         * <pre>
         *   .item("minecraft:diamond").weight(5).count(1, 3).add()
         * </pre>
         */
        public EntryBuilder item(String itemId) {
            return new EntryBuilder(this, itemId);
        }

        /**
         * Add a weighted empty-roll entry (no item generated).
         * Participates in the weight total to lower effective fill rate.
         */
        public PoolBuilder empty(int weight) {
            entries.add(buildEmpty(weight));
            return this;
        }

        private TsaphLootPool build() {
            return new TsaphLootPool(rolls, new ArrayList<>(entries));
        }

        private static TsaphLootEntry buildEmpty(int weight) {
            return TsaphLootEntry.fromJson(emptyJson(weight));
        }

        private static com.google.gson.JsonObject emptyJson(int weight) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("type", "empty");
            o.addProperty("weight", weight);
            return o;
        }
    }

    // ── Entry builder ─────────────────────────────────────────────────────

    /**
     * Builder for a single {@link TsaphLootEntry} inside a pool.
     *
     * <p>Call {@link #add()} when done to commit the entry and return to the
     * enclosing {@link PoolBuilder}.
     */
    public static final class EntryBuilder {

        private final PoolBuilder pool;
        private final String      item;
        private int               weight      = 1;
        private LootRange         count       = LootRange.fixed(1);
        private final List<LootEnchantment> enchantments = new ArrayList<>();
        private String            nbt         = null;

        private EntryBuilder(PoolBuilder pool, String item) {
            this.pool = pool;
            this.item = Objects.requireNonNull(item, "item");
        }

        /** Relative probability weight. Higher = more likely. Default: 1. */
        public EntryBuilder weight(int weight) {
            this.weight = weight;
            return this;
        }

        /** Fixed stack size. */
        public EntryBuilder count(int fixed) {
            this.count = LootRange.fixed(fixed);
            return this;
        }

        /** Random stack size in {@code [min, max]} inclusive. */
        public EntryBuilder count(int min, int max) {
            this.count = LootRange.of(min, max);
            return this;
        }

        /**
         * Apply a fixed-level enchantment.
         *
         * <pre>
         *   .enchant("minecraft:mending", 1)
         * </pre>
         */
        public EntryBuilder enchant(String enchantmentId, int level) {
            enchantments.add(new LootEnchantment(enchantmentId, LootRange.fixed(level)));
            return this;
        }

        /**
         * Apply a random-level enchantment in {@code [minLevel, maxLevel]} inclusive.
         *
         * <pre>
         *   .enchant("minecraft:sharpness", 1, 5)
         * </pre>
         */
        public EntryBuilder enchant(String enchantmentId, int minLevel, int maxLevel) {
            enchantments.add(new LootEnchantment(enchantmentId, LootRange.of(minLevel, maxLevel)));
            return this;
        }

        /**
         * Merge raw SNBT onto the generated item stack's {@code CUSTOM_DATA} component.
         *
         * <pre>
         *   .nbt("{display:{Name:'[{\"text\":\"Cursed Sword\"}]'}}")
         * </pre>
         */
        public EntryBuilder nbt(String snbt) {
            this.nbt = snbt;
            return this;
        }

        /**
         * Shorthand for setting a custom display name without writing SNBT manually.
         * Cannot be combined with {@link #nbt(String)}.
         */
        public EntryBuilder named(String displayName) {
            String escaped = displayName.replace("\"", "\\\"");
            this.nbt = "{display:{Name:'[{\"text\":\"" + escaped + "\"}]'}}";
            return this;
        }

        /**
         * Commit this entry to the pool and return to the {@link PoolBuilder}.
         */
        public PoolBuilder add() {
            pool.entries.add(buildEntry());
            return pool;
        }

        private TsaphLootEntry buildEntry() {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("item", item);
            o.addProperty("weight", weight);
            o.add("count", count.toJson());
            if (!enchantments.isEmpty()) {
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                enchantments.forEach(e -> arr.add(e.toJson()));
                o.add("enchantments", arr);
            }
            if (nbt != null) o.addProperty("nbt", nbt);
            return TsaphLootEntry.fromJson(o);
        }
    }
}

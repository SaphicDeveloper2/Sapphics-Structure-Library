package com.sapphic.ssl.api.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A single weighted item entry inside a {@link TsaphLootPool}.
 *
 * <h3>Weight semantics — float, two modes</h3>
 * <ul>
 *   <li><strong>{@code weight >= 1.0}</strong> — Standard weighted random.  The
 *       entry competes in the pool's total weight sum and is picked proportionally.
 *       Integer weights in existing {@code .tsaphloot} files continue to work
 *       without any file changes.</li>
 *   <li><strong>{@code 0.0 < weight < 1.0}</strong> — Fractional probability.
 *       The entry is checked <em>independently</em> on every pool activation:
 *       {@code rng.nextFloat() < weight}.  A weight of {@code 0.25} = 25 % flat
 *       chance to appear each time the pool fires, regardless of how many rolls
 *       or other entries exist.  Fractional entries do <em>not</em> consume a
 *       roll slot — they layer on top of the normal weighted draw.</li>
 * </ul>
 *
 * <h3>JSON form</h3>
 * <pre>
 * {
 *   "item":         "minecraft:diamond_sword",
 *   "weight":       10,
 *   "count":        { "min": 1, "max": 2 },
 *   "enchantments": [
 *     { "id": "minecraft:sharpness", "level": { "min": 1, "max": 5 } }
 *   ],
 *   "nbt":     "{display:{Name:'{\"text\":\"Named Sword\"}'}}",
 *   "comment": "Optional note — ignored by the engine"
 * }
 * </pre>
 * <p>Sub-1.0 fractional example:
 * <pre>
 * { "item": "minecraft:diamond", "weight": 0.1, "count": 1 }
 * </pre>
 *
 * <p>An entry with no {@code "item"} key, or with {@code "type": "empty"},
 * is treated as an empty roll (no item generated).
 */
public final class TsaphLootEntry {

    /** The actual item registry id, or {@code null} for an empty-roll entry. */
    private final String                item;
    /**
     * Float weight.
     * >= 1.0  → participates in the pool's weighted random draw.
     * 0 < w < 1.0 → rolled as an independent flat-probability check each pool activation.
     */
    private final float                 weight;
    private final LootRange             count;
    private final List<LootEnchantment> enchantments;
    private final String                nbt;

    private TsaphLootEntry(String item, float weight, LootRange count,
                           List<LootEnchantment> enchantments, String nbt) {
        this.item         = item;
        this.weight       = Math.max(0f, weight);
        this.count        = count;
        this.enchantments = Collections.unmodifiableList(enchantments);
        this.nbt          = (nbt == null || nbt.isBlank()) ? null : nbt.trim();
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** {@code true} if this entry generates no item (empty-roll type). */
    public boolean isEmpty()            { return item == null; }

    public String                  item()         { return item; }
    /** Float weight. >= 1.0 = weighted pool participant; 0 < w < 1 = fractional chance. */
    public float                   weight()       { return weight; }
    public LootRange               count()        { return count; }
    public List<LootEnchantment>   enchantments() { return enchantments; }
    public String                  nbt()          { return nbt; }
    public boolean hasEnchantments()  { return !enchantments.isEmpty(); }
    public boolean hasNbt()           { return nbt != null; }

    /**
     * {@code true} when this entry joins the pool's normal weighted draw
     * (weight >= 1.0).
     */
    public boolean isWeighted()   { return weight >= 1.0f; }

    /**
     * {@code true} when this entry uses the flat fractional-probability path
     * (0 < weight < 1.0).  Rolled independently per pool activation.
     */
    public boolean isFractional() { return weight > 0f && weight < 1.0f; }

    // ── Factories ─────────────────────────────────────────────────────────

    /**
     * Programmatic factory — used by {@link com.sapphic.ssl.internal.loot.SmartLootEngine}
     * to build entries from live inventory data without going through JSON.
     *
     * @param weight Float weight (>= 1.0 = weighted; 0 < w < 1 = fractional).
     */
    public static TsaphLootEntry of(String item, float weight, LootRange count,
                                    List<LootEnchantment> enchantments, String nbt) {
        return new TsaphLootEntry(item, weight, count,
                enchantments != null ? enchantments : Collections.emptyList(), nbt);
    }

    // ── JSON parsing ──────────────────────────────────────────────────────

    /**
     * Parse from a JSON object.
     * Returns {@code null} if the entry should be silently skipped.
     *
     * <p>The {@code "weight"} field is read as a float.  Integer values in
     * existing {@code .tsaphloot} files parse without loss.  Sub-1.0 values
     * activate the fractional-probability path.
     */
    public static TsaphLootEntry fromJson(JsonObject obj) {
        String type = obj.has("type") ? obj.get("type").getAsString() : "item";
        if ("empty".equals(type)) {
            float weight = obj.has("weight") ? obj.get("weight").getAsFloat() : 1f;
            return new TsaphLootEntry(null, weight, LootRange.fixed(0),
                                      Collections.emptyList(), null);
        }
        if (!"item".equals(type)) return null;   // unknown type — skip gracefully

        if (!obj.has("item")) return null;        // malformed entry
        String    item   = obj.get("item").getAsString();
        float     weight = obj.has("weight") ? obj.get("weight").getAsFloat() : 1f;
        LootRange count  = LootRange.fromJson(obj.has("count") ? obj.get("count") : null);

        List<LootEnchantment> enchants = new ArrayList<>();
        if (obj.has("enchantments")) {
            JsonArray arr = obj.getAsJsonArray("enchantments");
            for (int i = 0; i < arr.size(); i++) {
                try {
                    enchants.add(LootEnchantment.fromJson(arr.get(i).getAsJsonObject()));
                } catch (Exception ignored) { /* skip malformed enchant */ }
            }
        }

        String nbt = obj.has("nbt") ? obj.get("nbt").getAsString() : null;
        if (obj.has("name") && nbt == null) {
            String displayName = obj.get("name").getAsString();
            String escaped     = displayName.replace("\"", "\\\"");
            nbt = "{display:{Name:'[{\"text\":\"" + escaped + "\"}]'}}";
        }

        return new TsaphLootEntry(item, weight, count, enchants, nbt);
    }

    // ── JSON serialisation ────────────────────────────────────────────────

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        if (item == null) {
            obj.addProperty("type", "empty");
            serialiseWeight(obj, weight);
            return obj;
        }
        obj.addProperty("item", item);
        serialiseWeight(obj, weight);
        obj.add("count", count.toJson());
        if (!enchantments.isEmpty()) {
            JsonArray arr = new JsonArray();
            enchantments.forEach(e -> arr.add(e.toJson()));
            obj.add("enchantments", arr);
        }
        if (nbt != null) obj.addProperty("nbt", nbt);
        return obj;
    }

    /**
     * Write weight as an integer when it is a whole number >= 1 (cleaner JSON,
     * backward-compatible with old readers), otherwise as a float.
     */
    private static void serialiseWeight(JsonObject obj, float w) {
        if (w >= 1f && w == Math.floor(w)) {
            obj.addProperty("weight", (int) w);
        } else {
            obj.addProperty("weight", w);
        }
    }

    @Override
    public String toString() {
        return item == null ? "TsaphLootEntry[EMPTY w=" + weight + "]"
                : "TsaphLootEntry[" + item + " w=" + weight + " c=" + count + "]";
    }
}
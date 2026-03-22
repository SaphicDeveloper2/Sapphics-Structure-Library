package com.sapphic.ssl.api.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single weighted item entry inside a {@link TsaphLootPool}.
 *
 * <p>JSON form:
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
 *
 * <p>An entry with no {@code "item"} key, or with {@code "type": "empty"},
 * is treated as an empty roll (no item generated).  This lets you add weighted
 * nothing to a pool to lower the effective fill rate.
 *
 * <p>Unknown {@code "type"} values other than {@code "item"} or {@code "empty"}
 * are silently skipped so future format extensions don't break old readers.
 */
public final class TsaphLootEntry {

    /** The actual item registry id, or {@code null} for an empty-roll entry. */
    private final String              item;
    private final int                 weight;
    private final LootRange           count;
    private final List<LootEnchantment> enchantments;
    private final String              nbt;

    private TsaphLootEntry(String item, int weight, LootRange count,
                           List<LootEnchantment> enchantments, String nbt) {
        this.item         = item;
        this.weight       = weight;
        this.count        = count;
        this.enchantments = Collections.unmodifiableList(enchantments);
        this.nbt          = (nbt == null || nbt.isBlank()) ? null : nbt.trim();
    }

    /** {@code true} if this entry generates an item (i.e. not an empty-roll). */
    public boolean isEmpty()          { return item == null; }

    public String                  item()         { return item; }
    public int                     weight()       { return weight; }
    public LootRange               count()        { return count; }
    public List<LootEnchantment>   enchantments() { return enchantments; }
    public String                  nbt()          { return nbt; }
    public boolean hasEnchantments()  { return !enchantments.isEmpty(); }
    public boolean hasNbt()           { return nbt != null; }

    // ── JSON ──────────────────────────────────────────────────────────────

    /**
     * Parse from a JSON object.
     *
     * <p>Returns {@code null} if the entry should be silently skipped
     * (unknown type, or any parse error).
     */
    public static TsaphLootEntry fromJson(JsonObject obj) {
        // Respect an explicit "type" discriminator
        String type = obj.has("type") ? obj.get("type").getAsString() : "item";
        if ("empty".equals(type)) {
            int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
            return new TsaphLootEntry(null, weight, LootRange.fixed(0),
                                      Collections.emptyList(), null);
        }
        if (!"item".equals(type)) {
            // Unknown type (e.g. "vanilla_table") — skip gracefully
            return null;
        }

        if (!obj.has("item")) return null;   // malformed entry
        String item = obj.get("item").getAsString();
        int weight   = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
        LootRange count = LootRange.fromJson(obj.has("count") ? obj.get("count") : null);

        List<LootEnchantment> enchants = new ArrayList<>();
        if (obj.has("enchantments")) {
            JsonArray arr = obj.getAsJsonArray("enchantments");
            for (int i = 0; i < arr.size(); i++) {
                try {
                    enchants.add(LootEnchantment.fromJson(arr.get(i).getAsJsonObject()));
                } catch (Exception ignored) { /* skip malformed enchant */ }
            }
        }

        // Support shorthand "name" field → converted to display NBT automatically
        String nbt = obj.has("nbt") ? obj.get("nbt").getAsString() : null;
        if (obj.has("name") && nbt == null) {
            String displayName = obj.get("name").getAsString();
            // Escape quotes inside the display name for SNBT
            String escaped = displayName.replace("\"", "\\\"");
            nbt = "{display:{Name:'[{\"text\":\"" + escaped + "\"}]'}}";
        }

        return new TsaphLootEntry(item, weight, count, enchants, nbt);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        if (item == null) {
            obj.addProperty("type", "empty");
            obj.addProperty("weight", weight);
            return obj;
        }
        obj.addProperty("item", item);
        obj.addProperty("weight", weight);
        obj.add("count", count.toJson());
        if (!enchantments.isEmpty()) {
            JsonArray arr = new JsonArray();
            enchantments.forEach(e -> arr.add(e.toJson()));
            obj.add("enchantments", arr);
        }
        if (nbt != null) obj.addProperty("nbt", nbt);
        return obj;
    }

    @Override
    public String toString() {
        return item == null ? "TsaphLootEntry[EMPTY w=" + weight + "]"
                : "TsaphLootEntry[" + item + " w=" + weight + " c=" + count + "]";
    }
}

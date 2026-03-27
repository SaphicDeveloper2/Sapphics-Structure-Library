package com.sapphic.ssl.api.loot;

import com.google.gson.JsonObject;

/**
 * Specifies a single enchantment to apply to a loot entry's item.
 *
 * <p>JSON form:
 * <pre>
 * {
 *   "id":    "minecraft:sharpness",
 *   "level": 3
 * }
 * </pre>
 * or with a random level:
 * <pre>
 * {
 *   "id":    "minecraft:protection",
 *   "level": {"min": 1, "max": 4}
 * }
 * </pre>
 */
public final class LootEnchantment {

    /** Enchantment registry identifier (e.g. {@code "minecraft:sharpness"}). */
    private final String   enchantmentId;

    /** Level range — evaluated once per item generation. */
    private final LootRange level;

    public LootEnchantment(String enchantmentId, LootRange level) {
        this.enchantmentId = enchantmentId;
        this.level         = level;
    }

    public String    enchantmentId() { return enchantmentId; }
    public LootRange level()         { return level; }

    /** Parse from a JSON object. */
    public static LootEnchantment fromJson(JsonObject obj) {
        String id  = obj.get("id").getAsString();
        LootRange l = LootRange.fromJson(obj.has("level") ? obj.get("level") : null);
        return new LootEnchantment(id, l);
    }

    /** Serialise to a JSON object. */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", enchantmentId);
        obj.add("level", level.toJson());
        return obj;
    }

    @Override
    public String toString() {
        return "LootEnchantment[" + enchantmentId + " lv=" + level + "]";
    }
}

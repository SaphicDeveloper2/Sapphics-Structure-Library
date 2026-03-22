package com.sapphic.ssl.api.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Random;

/**
 * An inclusive integer range {@code [min, max]}.
 *
 * <p>Used everywhere in {@code .tsaphloot} tables where a value can be fixed or
 * randomly drawn (roll counts, item counts, enchantment levels).
 *
 * <p>JSON forms:
 * <ul>
 *   <li>Fixed value: {@code 3}
 *   <li>Range:       {@code {"min": 1, "max": 5}}
 * </ul>
 */
public final class LootRange {

    private final int min;
    private final int max;

    private LootRange(int min, int max) {
        if (min > max) throw new IllegalArgumentException("LootRange min > max: " + min + " > " + max);
        this.min = min;
        this.max = max;
    }

    /** A fixed value (min == max). */
    public static LootRange fixed(int value) {
        return new LootRange(value, value);
    }

    /** A random range [min, max] inclusive. */
    public static LootRange of(int min, int max) {
        return new LootRange(min, max);
    }

    /** Parse from a JSON element: either a primitive int or {@code {"min":…,"max":…}}. */
    public static LootRange fromJson(JsonElement el) {
        if (el == null || el.isJsonNull()) return fixed(1);
        if (el.isJsonPrimitive()) {
            return fixed(el.getAsInt());
        }
        JsonObject obj = el.getAsJsonObject();
        int lo = obj.has("min") ? obj.get("min").getAsInt() : 0;
        int hi = obj.has("max") ? obj.get("max").getAsInt() : lo;
        return of(lo, hi);
    }

    /** Evaluate a random value within [min, max], or min if min == max. */
    public int evaluate(Random rng) {
        return (min == max) ? min : min + rng.nextInt(max - min + 1);
    }

    public int min() { return min; }
    public int max() { return max; }

    /** Serialise to a compact JSON element. */
    public JsonElement toJson() {
        if (min == max) return new JsonPrimitive(min);
        JsonObject obj = new JsonObject();
        obj.addProperty("min", min);
        obj.addProperty("max", max);
        return obj;
    }

    @Override
    public String toString() {
        return min == max ? String.valueOf(min) : "[" + min + "," + max + "]";
    }
}

package com.sapphic.ssl.api.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A single pool inside a {@link TsaphLootTable}.
 *
 * <p>Each time the pool is evaluated, it fires {@link #rolls} independent draws.
 * Each draw picks one {@link TsaphLootEntry} by weight using weighted-random selection.
 * Empty-type entries (weight-only, no item) are included in the weight total so they
 * act as "miss" slots, letting you tune effective fill rate without removing entries.
 */
public final class TsaphLootPool {

    private final LootRange            rolls;
    private final List<TsaphLootEntry> entries;
    private final int                  totalWeight;

    public TsaphLootPool(LootRange rolls, List<TsaphLootEntry> entries) {
        this.rolls       = rolls;
        this.entries     = Collections.unmodifiableList(entries);
        this.totalWeight = entries.stream().mapToInt(TsaphLootEntry::weight).sum();
    }

    public LootRange             rolls()       { return rolls; }
    public List<TsaphLootEntry>  entries()     { return entries; }
    public int                   totalWeight() { return totalWeight; }

    /**
     * Pick one entry using weighted random selection.
     * May return an {@link TsaphLootEntry#isEmpty() empty} entry (no item generated).
     * Returns {@code null} only if the pool has no entries.
     */
    public TsaphLootEntry pickEntry(Random rng) {
        if (entries.isEmpty() || totalWeight <= 0) return null;
        int roll = rng.nextInt(totalWeight);
        int acc  = 0;
        for (TsaphLootEntry e : entries) {
            acc += e.weight();
            if (roll < acc) return e;
        }
        return entries.get(entries.size() - 1);
    }

    // ── JSON ──────────────────────────────────────────────────────────────

    public static TsaphLootPool fromJson(JsonObject obj) {
        LootRange rolls = LootRange.fromJson(obj.has("rolls") ? obj.get("rolls") : null);
        List<TsaphLootEntry> entries = new ArrayList<>();
        if (obj.has("entries")) {
            JsonArray arr = obj.getAsJsonArray("entries");
            for (int i = 0; i < arr.size(); i++) {
                TsaphLootEntry e = TsaphLootEntry.fromJson(arr.get(i).getAsJsonObject());
                if (e != null) entries.add(e);   // null = skip unknown type
            }
        }
        return new TsaphLootPool(rolls, entries);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.add("rolls", rolls.toJson());
        JsonArray arr = new JsonArray();
        entries.forEach(e -> arr.add(e.toJson()));
        obj.add("entries", arr);
        return obj;
    }

    @Override
    public String toString() {
        return "TsaphLootPool[rolls=" + rolls + ", entries=" + entries.size() +
               ", totalWeight=" + totalWeight + "]";
    }
}

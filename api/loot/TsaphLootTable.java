package com.sapphic.ssl.api.loot;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A complete {@code .tsaphloot} loot table.
 *
 * <p>Full JSON schema:
 * <pre>
 * {
 *   "name":    "dungeon_chest",
 *   "comment": "Optional description — ignored by the engine",
 *   "pools": [
 *     {
 *       "rolls":   { "min": 3, "max": 6 },
 *       "entries": [
 *         {
 *           "item":         "minecraft:diamond",
 *           "weight":       5,
 *           "count":        { "min": 1, "max": 3 }
 *         },
 *         {
 *           "item":         "minecraft:iron_sword",
 *           "weight":       15,
 *           "count":        1,
 *           "enchantments": [
 *             { "id": "minecraft:sharpness", "level": { "min": 1, "max": 3 } }
 *           ]
 *         },
 *         {
 *           "item":         "minecraft:name_tag",
 *           "weight":       2,
 *           "count":        1,
 *           "nbt":          "{display:{Name:'{\"text\":\"Ancient Tag\"}'}}"
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Loot tables are stored as:
 * <ul>
 *   <li><strong>Bundled resources</strong>:
 *       {@code resources/data/sapphics-structure-library/tsaphloot/<name>.tsaphloot}
 *       — ships with the mod, loaded from the classpath.</li>
 *   <li><strong>World-specific</strong>:
 *       {@code <worldSave>/data/ssl_loot/<name>.tsaphloot}
 *       — per-world custom tables, override bundled tables with the same name.</li>
 * </ul>
 */
public final class TsaphLootTable {

    /** File extension for loot table files. */
    public static final String EXTENSION = ".tsaphloot";

    /** Resource directory inside the mod jar. */
    public static final String RESOURCE_DIR = "data/sapphics-structure-library/tsaphloot/";

    /**
     * Unique name used to identify and reference this table.
     * Must match the file stem (filename without extension).
     */
    private final String             name;

    /** Human-readable comment stored in the file. Ignored at runtime. */
    private final String             comment;

    private final List<TsaphLootPool> pools;

    public TsaphLootTable(String name, String comment, List<TsaphLootPool> pools) {
        this.name    = name;
        this.comment = (comment == null) ? "" : comment;
        this.pools   = Collections.unmodifiableList(pools);
    }

    public String              name()    { return name; }
    public String              comment() { return comment; }
    public List<TsaphLootPool> pools()   { return pools; }

    // ── JSON I/O ──────────────────────────────────────────────────────────

    /** Parse a {@code .tsaphloot} file from {@code reader}. */
    public static TsaphLootTable fromJson(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

        String            name    = root.has("name")    ? root.get("name").getAsString()    : "unnamed";
        String            comment = root.has("comment") ? root.get("comment").getAsString() : "";
        List<TsaphLootPool> pools = new ArrayList<>();

        if (root.has("pools")) {
            JsonArray arr = root.getAsJsonArray("pools");
            for (int i = 0; i < arr.size(); i++) {
                pools.add(TsaphLootPool.fromJson(arr.get(i).getAsJsonObject()));
            }
        }

        return new TsaphLootTable(name, comment, pools);
    }

    /** Serialise this table to pretty-printed JSON on {@code writer}. */
    public void toJson(Writer writer) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        if (!comment.isBlank()) root.addProperty("comment", comment);

        JsonArray poolsArr = new JsonArray();
        pools.forEach(p -> poolsArr.add(p.toJson()));
        root.add("pools", poolsArr);

        writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    @Override
    public String toString() {
        return "TsaphLootTable[" + name + ", pools=" + pools.size() + "]";
    }
}

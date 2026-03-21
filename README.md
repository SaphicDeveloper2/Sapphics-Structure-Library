# Sapphics Structure Library

**A high-performance structure engine for Fabric mods.**  
Minecraft 1.21.1 · Fabric · Requires Fabric API

---

Sapphics Structure Library (SSL) is a developer library that gives Fabric mods a complete, production-ready structure system. It replaces vanilla's `StructureTemplate` for use cases that outgrow it — structures larger than 48³, reliable placement across unloaded chunks, and container loot you define yourself.

SSL is **not a content mod**. It adds nothing to the player's game on its own. It exists to be depended on by other mods.

---

## Features

- **No size limit.** The custom `.tsaphstruct` binary format supports structures of any dimensions.
- **Bit-packed storage.** Palette indices are stored at minimum bits-per-block, keeping file sizes small even for large structures.
- **Chunk-safe placement.** Blocks in unloaded chunks are automatically deferred and placed when the chunk loads — including across server restarts.
- **Smart update passes.** A two-pass system places all blocks first with updates suppressed, then runs a filtered neighbour-update sweep on only the blocks that need it (fences, fluids, redstone, stairs, gravity blocks, wall-attached blocks). Stone and dirt never get touched in Pass 2.
- **Custom loot system.** The `.tsaphloot` JSON format gives you weighted pools, random counts, random enchantment levels, and custom item names — baked directly into the structure file.
- **Vanilla loot support.** Containers can alternatively reference any vanilla or datapack loot table by its registry key.
- **Dimension-aware.** Works in the Overworld, Nether, End, and any custom dimension. Deferred queues are stored per-dimension.
- **Obfuscated internals.** All internal implementation code is obfuscated in the distributed jar. Only the public API is exposed.

---

## For Mod Developers

Add SSL as a dependency in your `fabric.mod.json`:

```json
"depends": {
  "sapphics-structure-library": "*"
}
```

Placing a structure takes three lines:

```java
IStructureLoader loader = StructureLoaderBridge.getLoader();
StructurePiece piece = loader.load(pathToYourStructFile);
loader.place(serverWorld, piece, origin);
```

Applying loot to a container:

```java
// Custom .tsaphloot table
LootTableRef ref = LootTableRef.tsaphloot("dungeon_chest");
StructureLoaderBridge.getLootEngine().applyLootTag(world, chestPos, ref, 0L);

// Or a vanilla loot table
LootTableRef ref = LootTableRef.vanilla("minecraft:chests/simple_dungeon");
StructureLoaderBridge.getLootEngine().applyLootTag(world, chestPos, ref, seed);
```

For full API documentation, format specifications, dimension targeting, and the complete `.tsaphloot` schema, see **[documentation.md](documentation.md)**.

---

## Bundled Loot Tables

Six `.tsaphloot` tables are included and ready to use:

| Name | Contents |
|------|---------|
| `dungeon_chest` | General dungeon loot — consumables, mid-tier, rare treasures |
| `armory_chest` | Weapons, armour, ammunition |
| `library_chest` | Books, paper, enchanted books |
| `temple_chest` | Gold, emeralds, temple artefacts |
| `ancient_ruins_chest` | Degraded equipment, ancient rarities |
| `generic_chest` | Minimal fallback for any context |

All tables can be overridden per-world by placing a `.tsaphloot` file of the same name in `<worldSave>/data/ssl_loot/`.

---

## Requirements

| | |
|-|-|
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.18.4 |
| Fabric API | any |
| Java | 21+ |

---

## License

SSL is proprietary software. You are free to use the public API in your own mods and distribute the jar as a dependency. You may not copy, decompile, reverse engineer, or redistribute the internal implementation code.

The full license terms are in **[LICENSE.txt](LICENSE.txt)**.

If the project is ever permanently abandoned, the complete source code will be released publicly on GitHub under the Apache License 2.0.

Contact: saphicdeveloper@gmail.com

# Sapphics Structure Library — Developer Documentation

**Mod ID:** `sapphics-structure-library`  
**Minecraft:** 1.21.1 (Fabric)  
**Depends:** Fabric API

---

## Table of Contents

1. [Overview](#overview)
2. [The `.tsaphstruct` File Format](#the-tsaphstruct-file-format)
3. [Placing a Structure](#placing-a-structure)
4. [Two-Pass Placement System](#two-pass-placement-system)
5. [Deferred Placement & Chunk Queue](#deferred-placement--chunk-queue)
6. [Dimension Targeting](#dimension-targeting)
7. [Loot Table System](#loot-table-system)
8. [Applying Loot to Structure Chests](#applying-loot-to-structure-chests)
9. [The `.tsaphloot` Format Reference](#the-tsaphloot-format-reference)
10. [Bundled Loot Tables](#bundled-loot-tables)
11. [API Quick Reference](#api-quick-reference)

---

## Overview

Sapphics Structure Library (SSL) is a high-performance structure engine for Fabric mods. It uses a custom binary format (`.tsaphstruct`) for storing structure data and a purpose-built loot system (`.tsaphloot`) for populating containers inside structures.

Key design goals:

- **No size ceiling.** Unlike vanilla's 48³ limit, `.tsaphstruct` supports arbitrarily large structures.
- **Chunk-safe placement.** Blocks in unloaded chunks are queued to disk and applied automatically when the chunk loads, even across server restarts.
- **Efficient updates.** A two-pass system ensures fences connect, fluids flow, and redstone resolves correctly — without running neighbour-update calls on every block.
- **Flexible loot.** Containers can use vanilla loot tables or fully custom `.tsaphloot` tables with weighted pools, random counts, and random enchantments.

---

## The `.tsaphstruct` File Format

Structure files use the extension `.tsaphstruct` and a proprietary binary layout. The current version is **v2**.

### Binary Layout

```
┌─────────────────────────────────────────────────────────┐
│  HEADER  (29 bytes fixed)                               │
│    [4]  Magic          0x54534150  ("TSAP")             │
│    [1]  Version        0x02                             │
│    [4]  SizeX          int  (no 48-block ceiling)       │
│    [4]  SizeY          int                              │
│    [4]  SizeZ          int                              │
│    [4]  PaletteSize    int                              │
│    [4]  RegionCount    int                              │
│    [4]  EntityCount    int  (block entity records)      │
│    [4]  LootRefCount   int  (added in v2)               │
├─────────────────────────────────────────────────────────┤
│  PALETTE  (PaletteSize entries)                         │
│    [2]  String length  short (unsigned)                 │
│    [N]  BlockState id  UTF-8                            │
├─────────────────────────────────────────────────────────┤
│  REGION INDEX  (RegionCount × 20 bytes)                 │
│    [4]  RegionX        int  (blockX / 512)              │
│    [4]  RegionZ        int  (blockZ / 512)              │
│    [8]  DataOffset     long                             │
│    [4]  DataLength     int                              │
├─────────────────────────────────────────────────────────┤
│  BLOCK DATA                                             │
│    Bit-packed palette indices (minimum bits per block)  │
│    Sub-header: [int bits][int longs][int blockCount]    │
├─────────────────────────────────────────────────────────┤
│  BLOCK-ENTITY DATA  (EntityCount entries)               │
│    [4]  rx / ry / rz   int (local space)                │
│    [2]  TypeLen        short (unsigned)                 │
│    [N]  TypeId         UTF-8                            │
│    [4]  NbtLen         int                              │
│    [N]  NbtBytes       GZIP-compressed NbtCompound      │
├─────────────────────────────────────────────────────────┤
│  LOOT REFS  (LootRefCount entries)   ← v2 only         │
│    [4]  LinearIndex  int  ((ry*sZ+rz)*sX+rx)           │
│    [1]  RefType      byte  0x00=VANILLA 0x01=TSAPHLOOT  │
│    [2]  IdLen        short (unsigned)                   │
│    [N]  Id           UTF-8                              │
└─────────────────────────────────────────────────────────┘
```

### Palette

Every distinct block state in the structure is assigned a compact integer index. The palette can hold up to **65,535 entries**. Block states are stored as full strings including all properties, e.g.:

```
minecraft:stone
minecraft:oak_stairs[facing=north,half=bottom,shape=straight]
minecraft:chest[facing=west,type=single,waterlogged=false]
```

### Block Data (Bit Packing)

Palette indices are stored using the **minimum number of bits** required to represent the palette size. A structure that only uses 2 block states needs 1 bit per block; one with 256 states needs 8 bits. Values are written LSB-first and span 64-bit long boundaries.

This is equivalent to Minecraft's internal `BitStorage` format.

### Region Index

The region index is a spatial partitioning layer that lets the loader skip sections of the block data immediately without scanning every block. Each region covers a **512 × 512 block column** (32 × 32 chunks). If a structure has empty regions (e.g. from non-rectangular shapes), their `DataLength` is 0 and the loader exits that region immediately with no per-block work.

### Version Compatibility

| Version | Description |
|---------|-------------|
| v1      | No loot ref section. `LootRefCount` is treated as 0. |
| v2      | Adds `LootRefCount` and the LOOT REFS section. |

---

## Placing a Structure

All placement is routed through `StructureLoaderBridge`, which is the only public entry point into the obfuscated internal implementation.

```java
// 1. Get the loader
IStructureLoader loader = StructureLoaderBridge.getLoader();

// 2. Load the structure file into memory
Path structFile = Path.of("path/to/yourstructure.tsaphstruct");
StructurePiece piece = loader.load(structFile);

// 3. Place it in the world, anchored at origin
//    origin = world position of the structure's (minX, minY, minZ) corner
loader.place(serverWorld, piece, origin);
```

The `place` call is synchronous and safe to call from the server thread. Blocks in unloaded chunks are automatically deferred — you do not need to handle that case yourself.

### Loading from Mod Resources

Structure files bundled inside your mod jar are accessible via `FabricLoader` at runtime:

```java
Path structFile = FabricLoader.getInstance()
    .getModContainer("your-mod-id")
    .orElseThrow()
    .findPath("data/your-mod-id/structures/mystructure.tsaphstruct")
    .orElseThrow();
StructurePiece piece = loader.load(structFile);
```

---

## Two-Pass Placement System

Placement happens in two passes. Understanding this is important for predicting exactly when blocks update.

### Pass 1 — Bulk FORCE_STATE Placement

Every non-air block is placed using Minecraft's `Block.FORCE_STATE` flag. This **completely suppresses all neighbour update notifications** during placement. This is intentional: it prevents cascading updates mid-structure (water flowing before all blocks are placed, redstone firing into partial circuits, etc.).

Air variants (`minecraft:air`, `minecraft:cave_air`, `minecraft:void_air`) are all skipped silently.

Simultaneously, each placed block is tested by `BlockUpdateFilter.needsUpdate`. Blocks that pass this test are added to a `sensitiveList` for Pass 2. Blocks that fail — stone, dirt, logs, gravel, glass, wool, concrete — are **never given a Pass 2 update call** at all.

### Pass 2 — Targeted Neighbour Update Sweep

For each position in `sensitiveList`, the loader checks whether all 6 face-adjacent positions are in loaded chunks:

- **All neighbours loaded** → `getStateForNeighborUpdate` is called for each of the 6 face directions, and any resulting state change is written back with `Block.NOTIFY_ALL`. If the block carries a fluid (waterlogged, or a direct source block), a fluid tick is also scheduled.
- **Any neighbour unloaded** → the position is enqueued as an `updateOnly` entry in the world's persistent `StructureQueue`. The update fires automatically when the last unloaded adjacent chunk loads.

### What BlockUpdateFilter Covers

The filter returns `true` (= needs update) for:

| Category | Examples |
|----------|---------|
| Fluid-carrying | Any waterlogged block, direct water/lava |
| Directionally-connected | Fences, walls, glass panes, iron bars, vines, chorus plants |
| Shape-computed | Stairs, rails, leaves |
| Power / redstone | Redstone wire, buttons, pressure plates, observers, target blocks |
| Interaction-state | Doors, trapdoors, fence gates, dispensers, tripwire hooks |
| Attachment-facing | Wall torches, wall lanterns, bells, wall signs, banners, buttons, levers |
| Gravity | Sand, gravel, concrete powder, anvils |
| Crop / fluid class | `CropBlock`, `FluidBlock`, `FallingBlock` |

Everything else (stone, dirt, planks, logs, glass, wool, slabs without water, full-opaque blocks) returns `false` and is never touched in Pass 2.

---

## Deferred Placement & Chunk Queue

### How It Works

When Pass 1 encounters a block whose chunk is not loaded, it creates a `PendingPlacement` record and adds it to the world's `StructureQueue`. The queue is bucket-indexed by `ChunkPos.toLong()` for O(1) lookup.

When a chunk finishes feature generation, the `ChunkGeneratorMixin` calls `StructureLoaderBridge.processChunkQueue`. This drains the queue for the loaded chunk **plus all 8 surrounding chunks**. The 8-way ring drain is what allows deferred `updateOnly` entries on chunk borders to fire: when chunk (X, Z) loads, a block sitting on the edge of already-loaded chunk (X−1, Z) may now have all its face-adjacent neighbours present.

### Persistence Across Restarts

The queue is saved to disk automatically when blocks are deferred, and again on server stop. The on-disk format is per-world, per-dimension:

```
<worldSave>/data/ssl_queue/<sanitised-dimension-key>/pending.bin
```

Examples:
```
saves/MyWorld/data/ssl_queue/minecraft_overworld/pending.bin
saves/MyWorld/data/ssl_queue/minecraft_the_nether/pending.bin
saves/MyWorld/data/ssl_queue/minecraft_the_end/pending.bin
saves/MyWorld/data/ssl_queue/yourmod_yourdimension/pending.bin
```

Colons and slashes in registry keys are replaced with underscores for filesystem safety.

### Queue File Format (v2)

```
[byte]  version = 2
[UTF]   worldKey  (e.g. "minecraft:overworld")
[int]   entry count
[entry × count]
```

Each entry (v2):
```
[boolean]  pendingUpdate flag
[int]      x, y, z
--- if !pendingUpdate ---
[UTF]      blockStateId
[boolean]  hasNbt
[int?]     nbtLength
[bytes?]   nbtBytes (GZIP-compressed)
[boolean]  hasLoot
[byte?]    lootType (0x00=VANILLA, 0x01=TSAPHLOOT)
[short?]   lootIdLength
[bytes?]   lootId (UTF-8)
```

The `worldKey` is written **once** in the header rather than per-entry. Legacy v1 files (where the entry count was the first 4 bytes and each entry embedded its own key) are detected and loaded transparently.

---

## Dimension Targeting

SSL does **not** decide which dimension a structure generates in — that is entirely up to the calling code. The library is dimension-agnostic: `loader.place(world, piece, origin)` accepts any `ServerWorld`, including custom dimensions.

### Common Patterns

**Overworld structure generation (via StructureFeature / Jigsaw):**
```java
// Called from your StructureFeature.generate() or similar
ServerWorld overworld = server.getWorld(World.OVERWORLD);
loader.place(overworld, piece, origin);
```

**Nether structure:**
```java
ServerWorld nether = server.getWorld(World.NETHER);
loader.place(nether, piece, origin);
```

**Custom dimension:**
```java
RegistryKey<World> myDim = RegistryKey.of(RegistryKeys.WORLD,
    Identifier.of("yourmod", "yourdimension"));
ServerWorld myWorld = server.getWorld(myDim);
if (myWorld != null) {
    loader.place(myWorld, piece, origin);
}
```

The queue for each dimension is stored separately on disk (see paths above), so deferred placements always resolve in the correct dimension regardless of how many are active simultaneously.

---

## Loot Table System

Structure containers (chests, barrels, shulker boxes) can be assigned loot at export time. Two loot backends are supported:

### Vanilla Loot Tables

Any standard Minecraft or datapack loot table can be referenced by its full registry key string. The engine writes the `LootTable` and `LootTableSeed` NBT tags onto the container's block entity. Minecraft then populates the container on the first player open, exactly as vanilla dungeons and strongholds work.

Reference format: `"minecraft:chests/simple_dungeon"`, `"minecraft:chests/end_city_treasure"`, etc.

### TsaphLoot Tables (Custom)

The `.tsaphloot` system is SSL's own loot engine. Tables are defined in JSON and stored either inside the mod jar (bundled) or in a world-specific directory (overridable per-world). The engine stores a synthetic loot table key (`ssl:tsaphloot/<name>`) on the container and intercepts first-open via `LootableContainerMixin` to run its pool-based generation.

The two systems can be mixed freely across different containers in the same structure.

---

## Applying Loot to Structure Chests

### At Export Time (Recommended)

When using the in-game export wand, containers are recorded with their existing `LootTable` NBT tag. If you place a vanilla chest and set its loot table via a command or data pack, that reference is captured automatically in the `.tsaphstruct` v2 `LOOT REFS` section.

### Programmatically via the API

```java
ITsaphLootEngine lootEngine = StructureLoaderBridge.getLootEngine();
BlockPos chestPos = new BlockPos(x, y, z);

// Option A: Tag the container — population deferred to first player open
LootTableRef vanillaRef = LootTableRef.vanilla("minecraft:chests/simple_dungeon");
lootEngine.applyLootTag(world, chestPos, vanillaRef, world.random.nextLong());

// Option B: Tag with a custom TsaphLoot table
LootTableRef customRef = LootTableRef.tsaphloot("dungeon_chest");
lootEngine.applyLootTag(world, chestPos, customRef, 0L);  // seed ignored for TsaphLoot

// Option C: Populate immediately (no deferred open required)
lootEngine.populate(world, chestPos, customRef);
```

`applyLootTag` is the standard approach — it is lazy (no items are generated until a player opens the chest) and matches how vanilla handles dungeon loot. Use `populate` when you need the container to be pre-filled, e.g. for display purposes or non-interactive containers.

### In a `.tsaphstruct` File (LOOT REFS Section)

When loot refs are baked into the structure file itself, the loader applies them automatically during `placeBlock` — no extra code required on the caller's side. This is the preferred workflow: design your structure with containers in the right places, tag them with loot tables at export time, and `loader.place(...)` handles the rest.

---

## The `.tsaphloot` Format Reference

`.tsaphloot` files are UTF-8 JSON with the extension `.tsaphloot`.

### Top-Level Schema

```json
{
  "name":    "table_name",
  "comment": "Optional description — ignored by the engine",
  "pools":   [ ...pool objects... ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Must match the file stem (filename without extension). Used as the reference ID. |
| `comment` | No | Ignored at runtime. Use for authoring notes. |
| `pools` | Yes | Array of pool objects. All pools are evaluated every time the chest is opened. |

### Pool Schema

```json
{
  "rolls":   { "min": 3, "max": 6 },
  "entries": [ ...entry objects... ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `rolls` | Yes | How many draws this pool makes. Can be a fixed integer or a `{"min", "max"}` range. |
| `entries` | Yes | Weighted item entries. One entry is selected per roll by weighted random. |

### Entry Schema

```json
{
  "item":         "minecraft:diamond_sword",
  "weight":       10,
  "count":        { "min": 1, "max": 2 },
  "enchantments": [
    { "id": "minecraft:sharpness", "level": { "min": 1, "max": 5 } }
  ],
  "nbt":     "{display:{Name:'[{\"text\":\"Cursed Blade\"}]'}}",
  "name":    "Cursed Blade",
  "comment": "Optional note — ignored"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `item` | Yes (unless `type: empty`) | Item registry ID. |
| `weight` | No (default: 1) | Relative probability weight. Higher = more likely. |
| `count` | No (default: 1) | Stack size. Fixed integer or `{"min", "max"}` range. |
| `enchantments` | No | List of enchantments to apply. Each has `id` and `level` (fixed or range). |
| `nbt` | No | Raw SNBT string to merge onto the item stack. |
| `name` | No | Shorthand for display name. Converted automatically to display NBT. Cannot be combined with `nbt`. |
| `type` | No | `"item"` (default) or `"empty"` for a weighted miss slot. |
| `comment` | No | Ignored at runtime. |

### Empty Entries

An entry with `"type": "empty"` (or with no `"item"` field) generates no item. It participates in the weight calculation, which lets you tune effective fill rate without removing real entries:

```json
{ "type": "empty", "weight": 30 }
```

### LootRange

Wherever a number can be fixed or random, both forms are accepted:

```json
3               // fixed value
{ "min": 1, "max": 5 }   // random [1..5] inclusive
```

### Full Example

```json
{
  "name": "dungeon_chest",
  "comment": "Generic dungeon chest with three pools of escalating rarity.",
  "pools": [
    {
      "comment": "Common consumables — always 3 to 6 rolls",
      "rolls": { "min": 3, "max": 6 },
      "entries": [
        { "item": "minecraft:bread",  "weight": 30, "count": { "min": 1, "max": 4 } },
        { "item": "minecraft:arrow",  "weight": 25, "count": { "min": 4, "max": 16 } },
        { "item": "minecraft:torch",  "weight": 25, "count": { "min": 2, "max": 8 } }
      ]
    },
    {
      "comment": "Mid-tier items — 1 to 3 rolls",
      "rolls": { "min": 1, "max": 3 },
      "entries": [
        { "item": "minecraft:iron_ingot",   "weight": 20, "count": { "min": 1, "max": 4 } },
        { "item": "minecraft:gold_ingot",   "weight": 10, "count": { "min": 1, "max": 3 } },
        { "item": "minecraft:iron_sword",   "weight": 10, "count": 1 },
        { "item": "minecraft:name_tag",     "weight": 4,  "count": 1 }
      ]
    },
    {
      "comment": "Rare treasures — 0 or 1 roll",
      "rolls": { "min": 0, "max": 1 },
      "entries": [
        { "item": "minecraft:diamond",       "weight": 20, "count": { "min": 1, "max": 3 } },
        { "item": "minecraft:golden_apple",  "weight": 5,  "count": 1 },
        {
          "item": "minecraft:enchanted_book",
          "weight": 10,
          "count": 1,
          "enchantments": [ { "id": "minecraft:mending", "level": 1 } ]
        }
      ]
    }
  ]
}
```

---

## Bundled Loot Tables

The following `.tsaphloot` tables are bundled with the mod at:

```
resources/data/sapphics-structure-library/tsaphloot/<name>.tsaphloot
```

| Table Name | Description |
|------------|-------------|
| `dungeon_chest` | Three-pool generic dungeon loot with consumables, mid-tier, and rare treasures |
| `armory_chest` | Military loot — ammunition, weapons, armour, trophy items |
| `library_chest` | Books, paper, writing supplies, and rare enchanted books |
| `temple_chest` | Temple-themed valuables, gold, emeralds, and artefacts |
| `ancient_ruins_chest` | Degraded equipment and ancient-feeling rare drops |
| `generic_chest` | Minimal fallback table suitable for any context |

### Overriding Bundled Tables Per-World

Any bundled table can be overridden for a specific world by placing a `.tsaphloot` file with the same stem in:

```
<worldSave>/data/ssl_loot/<name>.tsaphloot
```

The world-specific file takes precedence. The bundled file is used as a fallback if no world override exists.

---

## API Quick Reference

### Entry Points

| Class | Purpose |
|-------|---------|
| `StructureLoaderBridge` | Single public gateway to all internal implementations |
| `StructureLoaderBridge.getLoader()` | Returns `IStructureLoader` for loading and placing structures |
| `StructureLoaderBridge.getExporter()` | Returns `IStructureExporter` for capturing structures from the world |
| `StructureLoaderBridge.getLootEngine()` | Returns `ITsaphLootEngine` for programmatic loot application |
| `StructureLoaderBridge.getQueue(world)` | Returns the `StructureQueue` for the given world |

### Key Types

| Type | Description |
|------|-------------|
| `StructurePiece` | In-memory decoded structure (palette, block array, block entities, loot refs) |
| `BlockEntry` | A palette entry mapping an index to a block state string |
| `PendingPlacement` | A queued block (or update trigger) waiting for its chunk to load |
| `LootTableRef` | Reference to either a vanilla loot table or a TsaphLoot table |
| `TsaphLootTable` | Parsed `.tsaphloot` table (pools, entries, weights) |
| `StructureQueue` | Per-world queue of deferred placements, backed to disk |

### LootTableRef Factories

```java
// Reference a vanilla / datapack loot table by its full key
LootTableRef ref = LootTableRef.vanilla("minecraft:chests/simple_dungeon");

// Reference a custom .tsaphloot table by its name (no extension)
LootTableRef ref = LootTableRef.tsaphloot("dungeon_chest");
```

### IStructureLoader

```java
StructurePiece load(Path path) throws IOException;
void place(ServerWorld world, StructurePiece piece, BlockPos origin);
void processChunkQueue(ServerWorld world, int chunkX, int chunkZ);
```

### ITsaphLootEngine

```java
// Tag a container for deferred population on first open
boolean applyLootTag(ServerWorld world, BlockPos pos, LootTableRef ref, long seed);

// Populate a container immediately
boolean populate(ServerWorld world, BlockPos pos, LootTableRef ref);

// Resolve a TsaphLoot table by name
Optional<TsaphLootTable> resolve(String name);
```

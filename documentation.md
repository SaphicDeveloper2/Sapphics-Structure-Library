# Sapphics Structure Library — Developer Documentation

**Mod ID:** `sapphics-structure-library`  
**Minecraft:** 1.21.1 (Fabric)  
**Depends:** Fabric API

---

## Table of Contents

1. [Overview](#overview)
2. [The `.tsaphstruct` File Format](#the-tsaphstruct-file-format)
3. [Placing a Structure](#placing-a-structure)
4. [The `StructurePlacement` Fluent Builder](#the-structureplacement-fluent-builder)
5. [Two-Pass Placement System](#two-pass-placement-system)
6. [Deferred Placement & Chunk Queue](#deferred-placement--chunk-queue)
7. [Dimension Targeting](#dimension-targeting)
8. [Datapack Structure Definitions](#datapack-structure-definitions)
9. [Loot Table System](#loot-table-system)
10. [Applying Loot to Structure Chests](#applying-loot-to-structure-chests)
11. [The `TsaphLootBuilder` Fluent Builder](#the-tsaphlootbuilder-fluent-builder)
12. [The `.tsaphloot` Format Reference](#the-tsaphloot-format-reference)
13. [Bundled Loot Tables](#bundled-loot-tables)
14. [API Quick Reference](#api-quick-reference)

---

## Overview

Sapphics Structure Library (SSL) is a high-performance structure engine for Fabric mods. It uses a custom binary format (`.tsaphstruct`) for storing structure data and a purpose-built loot system (`.tsaphloot`) for populating containers inside structures.

Key design goals:

- **No size ceiling.** Unlike vanilla's 48³ limit, `.tsaphstruct` supports arbitrarily large structures.
- **Chunk-safe placement.** Blocks in unloaded chunks are queued to disk and applied automatically when the chunk loads, even across server restarts.
- **Efficient updates.** A two-pass system ensures fences connect, fluids flow, and redstone resolves correctly — without running neighbour-update calls on every block.
- **Flexible loot.** Containers can use vanilla loot tables or fully custom `.tsaphloot` tables with weighted pools, random counts, and random enchantments.
- **Datapack-driven generation.** Structures can be registered for automatic world generation entirely through datapacks — no Java code required.

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

Palette indices are stored using the **minimum number of bits** required to represent the palette size. A structure that only uses 2 block states needs 1 bit per block; one with 256 states needs 8 bits. Values are written LSB-first and span 64-bit long boundaries. This is equivalent to Minecraft's internal `BitStorage` format.

### Region Index

The region index is a spatial partitioning layer that lets the loader skip sections of the block data immediately without scanning every block. Each region covers a **512 × 512 block column** (32 × 32 chunks). If a structure has empty regions, their `DataLength` is 0 and the loader exits that region immediately with no per-block work.

### Version Compatibility

| Version | Description |
|---------|-------------|
| v1 | No loot ref section. `LootRefCount` is treated as 0. |
| v2 | Adds `LootRefCount` and the LOOT REFS section. |

---

## Placing a Structure

### Raw API

All placement is routed through `StructureLoaderBridge`, the only public entry point into the obfuscated internal implementation.

```java
IStructureLoader loader = StructureLoaderBridge.getLoader();
StructurePiece piece = loader.load(Path.of("path/to/yourstructure.tsaphstruct"));
loader.place(serverWorld, piece, origin);
```

`origin` is the world-space position of the structure's `(minX, minY, minZ)` corner. The `place` call is synchronous and safe to call from the server thread. Blocks in unloaded chunks are automatically deferred.

### Loading from Mod Resources

```java
Path structFile = FabricLoader.getInstance()
    .getModContainer("your-mod-id").orElseThrow()
    .findPath("data/your-mod-id/structures/mystructure.tsaphstruct").orElseThrow();
StructurePiece piece = loader.load(structFile);
```

### Ground Offset

When the export selection box captured empty air rows below the actual structure, local Y=0 is air and the first real blocks appear higher up. Placing at a world Y without accounting for this causes structures to float.

`StructurePiece.groundOffset()` returns the local Y of the first non-air layer:

```java
// Correct manual placement flush with ground at Y=64
BlockPos origin = new BlockPos(x, 64 - piece.groundOffset(), z);
loader.place(world, piece, origin);
```

The `StructurePlacement` builder applies this automatically.

---

## The `StructurePlacement` Fluent Builder

`StructurePlacement` replaces the manual three-step pattern with a chainable builder that handles surface lookup, ground offset correction, and centering automatically.

```java
// Surface placement — looks up WORLD_SURFACE heightmap, applies ground offset
StructurePlacement.load(path)
    .at(x, z)           // centres the structure on this X/Z
    .onSurface()
    .place(world);

// Ocean floor placement
StructurePlacement.of(piece)
    .at(x, z)
    .onOceanFloor()
    .place(world);

// Absolute Y — ground offset still applied unless disabled
StructurePlacement.of(piece)
    .atCorner(x, z)     // pins exact (minX, minZ) corner instead of centering
    .atY(64)
    .place(world);

// Bury 3 blocks underground
StructurePlacement.of(piece)
    .at(x, z)
    .onSurface()
    .withYOffset(-3)
    .place(world);

// Disable automatic ground offset correction entirely
StructurePlacement.of(piece)
    .atCorner(x, z)
    .atY(64)
    .withoutGroundOffset()
    .place(world);
```

### Method Reference

| Method | Description |
|--------|-------------|
| `StructurePlacement.load(path)` | Load a `.tsaphstruct` file and begin building |
| `StructurePlacement.of(piece)` | Begin building from an already-loaded `StructurePiece` |
| `.at(x, z)` | Centre the structure on this world X/Z |
| `.atCorner(x, z)` | Pin the exact (minX, minZ) corner — no centering |
| `.onSurface()` | Use the `WORLD_SURFACE` heightmap |
| `.onOceanFloor()` | Use the `OCEAN_FLOOR` heightmap |
| `.atY(y)` | Use a literal world Y coordinate |
| `.withYOffset(n)` | Add `n` blocks on top of any computed Y (negative = buried) |
| `.withoutGroundOffset()` | Disable automatic `groundOffset()` correction |
| `.place(world)` | Execute the placement |

---

## Two-Pass Placement System

### Pass 1 — Bulk FORCE_STATE Placement

Every non-air block is placed using `Block.FORCE_STATE`, suppressing all neighbour update notifications. Air variants (`minecraft:air`, `minecraft:cave_air`, `minecraft:void_air`) are skipped silently.

Each placed block is tested by `BlockUpdateFilter.needsUpdate`. Blocks that pass are added to a `sensitiveList` for Pass 2. Solid featureless blocks — stone, dirt, logs, glass, wool — are never touched in Pass 2.

### Pass 2 — Targeted Neighbour Update Sweep

For each position in `sensitiveList`:

- **All 6 face-adjacent chunks loaded** → `getStateForNeighborUpdate` is called for each face direction. Any resulting state change is written back with `Block.NOTIFY_ALL`. Fluid ticks are scheduled for any block carrying a non-empty `FluidState`.
- **Any adjacent chunk unloaded** → the position is enqueued as an `updateOnly` entry in the persistent `StructureQueue` and fires when the last unloaded adjacent chunk loads.

### What BlockUpdateFilter Covers

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

---

## Deferred Placement & Chunk Queue

### How It Works

When Pass 1 encounters a block whose chunk is not loaded, a `PendingPlacement` record is added to the world's `StructureQueue`, bucket-indexed by `ChunkPos.toLong()` for O(1) lookup.

The queue is drained in two places:

- **`ChunkGeneratorMixin`** — fires after `generateFeatures` completes for freshly-generated chunks.
- **`ServerChunkEvents.CHUNK_LOAD`** — fires every time any chunk becomes accessible, including chunks loaded from disk on server restart. This ensures deferred placements resolve correctly after a reload without requiring the chunk to regenerate.

Both hooks drain the queue for the loaded chunk **plus all 8 surrounding chunks**, allowing `updateOnly` entries on chunk borders to fire once all their neighbours are present.

### Persistence Across Restarts

The queue is saved to disk when blocks are deferred and on server stop. Format is per-world, per-dimension:

```
<worldSave>/data/ssl_queue/<sanitised-dimension-key>/pending.bin
```

Examples:
```
saves/MyWorld/data/ssl_queue/minecraft_overworld/pending.bin
saves/MyWorld/data/ssl_queue/minecraft_the_nether/pending.bin
saves/MyWorld/data/ssl_queue/yourmod_yourdimension/pending.bin
```

### Queue File Format (v2)

```
[byte]  version = 2
[UTF]   worldKey  (e.g. "minecraft:overworld")
[int]   entry count
[entry × count]
```

Each entry:
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

The `worldKey` is written once in the file header. Legacy v1 files (key per-entry, no version byte) are detected and loaded transparently.

---

## Dimension Targeting

SSL is dimension-agnostic. `loader.place(world, piece, origin)` and `StructurePlacement` both accept any `ServerWorld`, including custom dimensions.

```java
// Overworld
loader.place(server.getWorld(World.OVERWORLD), piece, origin);

// Nether
loader.place(server.getWorld(World.NETHER), piece, origin);

// Custom dimension
RegistryKey<World> myDim = RegistryKey.of(RegistryKeys.WORLD,
    Identifier.of("yourmod", "yourdimension"));
ServerWorld myWorld = server.getWorld(myDim);
if (myWorld != null) loader.place(myWorld, piece, origin);
```

Deferred queues are stored per-dimension on disk so placements always resolve in the correct world.

---

## Datapack Structure Definitions

Structures can be registered for automatic world generation entirely through datapacks. No Java code is required.

### File Layout

Place two files in your datapack per structure — the definition JSON and the structure file, with matching stems:

```
data/
└── <namespace>/
    └── ssl_structures/
        ├── myvillage.json           ← placement definition
        └── myvillage.tsaphstruct    ← the structure (same stem)
```

Loot tables can also be shipped alongside structures in a datapack:

```
data/
└── <namespace>/
    └── ssl_loot/
        └── my_custom_chest.tsaphloot
```

Both are loaded automatically on server start and `/reload` via Fabric's resource reload system.

### Definition Schema

```json
{
  "dimension":   "minecraft:overworld",
  "biomes":      ["minecraft:plains", "minecraft:forest"],
  "y_placement": "surface",
  "y_offset":    0,
  "frequency":   0.005,
  "salt":        12345
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `dimension` | `"minecraft:overworld"` | Target dimension registry key. Use `"*"` to allow any dimension. |
| `biomes` | all biomes | Biome registry key whitelist. Empty array = no restriction. |
| `y_placement` | `"surface"` | One of `"surface"`, `"ocean_floor"`, or `"absolute"`. |
| `y_offset` | `0` | Integer offset added on top of the computed Y. Negative values bury the structure. In `"absolute"` mode this is the literal world Y. |
| `frequency` | `0.005` | Per-chunk generation probability, `0.0`–`1.0`. |
| `salt` | `0` | Per-definition seed modifier. Ensures different structures don't always co-generate in the same chunks. |

### Y Placement Modes

| Value | Behaviour |
|-------|-----------|
| `"surface"` | Looks up the `WORLD_SURFACE` heightmap at the chunk centre. `groundOffset()` is applied automatically. |
| `"ocean_floor"` | Looks up the `OCEAN_FLOOR` heightmap. Use for underwater ruins. |
| `"absolute"` | Uses `y_offset` as the literal world Y. No heightmap lookup. |

### Accessing Definitions from Code

```java
List<StructureDefinition> defs = StructureLoaderBridge.definitions();
```

---

## Loot Table System

Structure containers (chests, barrels, shulker boxes) can be assigned loot. Two backends are supported and can be mixed freely across containers in the same structure.

### Vanilla Loot Tables

References any standard Minecraft or datapack loot table by its full registry key. The engine writes `LootTable` and `LootTableSeed` NBT tags onto the container's block entity. Minecraft populates the container on first player open.

```java
LootTableRef.vanilla("minecraft:chests/simple_dungeon")
LootTableRef.vanilla("minecraft:chests/end_city_treasure")
```

### TsaphLoot Tables

The `.tsaphloot` system is SSL's own pool-based loot engine. A synthetic key (`ssl:tsaphloot/<n>`) is written onto the container and intercepted by `LootableContainerMixin` on first open.

Tables are resolved in this order, with later sources overriding earlier ones on name collision:

1. **Bundled** — shipped inside the SSL mod jar
2. **Datapack** — `data/<namespace>/ssl_loot/*.tsaphloot` in any loaded datapack
3. **World-specific** — `<worldSave>/data/ssl_loot/*.tsaphloot`

---

## Applying Loot to Structure Chests

### At Export Time (Recommended)

Containers in the selection region are exported with their existing `LootTable` NBT tag captured into the `.tsaphstruct` v2 `LOOT REFS` section. `loader.place(...)` applies them automatically — no extra caller code needed.

### Programmatically via the API

```java
ITsaphLootEngine lootEngine = StructureLoaderBridge.getLootEngine();
BlockPos chestPos = new BlockPos(x, y, z);

// Tag for deferred population on first player open (recommended)
lootEngine.applyLootTag(world, chestPos,
    LootTableRef.vanilla("minecraft:chests/simple_dungeon"),
    world.random.nextLong());

// Tag with a custom TsaphLoot table (seed ignored)
lootEngine.applyLootTag(world, chestPos,
    LootTableRef.tsaphloot("dungeon_chest"), 0L);

// Populate immediately — no deferred open needed
lootEngine.populate(world, chestPos, LootTableRef.tsaphloot("dungeon_chest"));
```

`applyLootTag` is the standard approach — lazy and crash-safe. Use `populate` for pre-filled display containers or non-interactive chests.

---

## The `TsaphLootBuilder` Fluent Builder

`TsaphLootBuilder` constructs `TsaphLootTable` instances in code without writing JSON. The API mirrors the JSON schema one-to-one.

```java
TsaphLootTable table = TsaphLootBuilder.create("my_chest")
    .comment("Built in code")
    .pool(p -> p
        .rolls(3, 6)
        .item("minecraft:bread")    .weight(30).count(1, 4).add()
        .item("minecraft:arrow")    .weight(25).count(4, 16).add()
        .item("minecraft:diamond")  .weight(5) .count(1, 3).add()
        .empty(20)                              // weighted miss slot
    )
    .pool(p -> p
        .rolls(0, 1)
        .item("minecraft:enchanted_book")
            .weight(10)
            .enchant("minecraft:mending", 1)
            .add()
        .item("minecraft:diamond_sword")
            .weight(4)
            .enchant("minecraft:sharpness", 1, 4)
            .named("Ancient Blade")
            .add()
    )
    .build();
```

### Entry Builder Methods

| Method | Description |
|--------|-------------|
| `.weight(n)` | Relative probability weight. Default: 1. |
| `.count(n)` | Fixed stack size. |
| `.count(min, max)` | Random stack size in `[min, max]`. |
| `.enchant(id, level)` | Apply a fixed-level enchantment. |
| `.enchant(id, min, max)` | Apply a random-level enchantment. |
| `.nbt(snbt)` | Merge raw SNBT onto the item's `CUSTOM_DATA` component. |
| `.named(text)` | Set a custom display name without writing SNBT manually. Cannot be combined with `.nbt()`. |
| `.add()` | Commit the entry and return to the pool builder. |

For the simplest workflow, write the table as a `.tsaphloot` JSON file and let the loader register it automatically on server start.

---

## The `.tsaphloot` Format Reference

`.tsaphloot` files are UTF-8 JSON.

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
| `name` | Yes | Must match the file stem. Used as the reference ID. |
| `comment` | No | Ignored at runtime. |
| `pools` | Yes | All pools are evaluated every time the chest is opened. |

### Pool Schema

```json
{
  "rolls":   { "min": 3, "max": 6 },
  "entries": [ ...entry objects... ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `rolls` | Yes | How many draws this pool makes. Fixed integer or `{"min", "max"}` range. |
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
| `weight` | No (default: 1) | Relative probability weight. |
| `count` | No (default: 1) | Fixed integer or `{"min", "max"}` range. |
| `enchantments` | No | List of `{"id", "level"}` objects. Level can be fixed or a range. |
| `nbt` | No | Raw SNBT merged onto the item stack. |
| `name` | No | Display name shorthand — converts to display NBT automatically. Cannot be combined with `nbt`. |
| `type` | No | `"item"` (default) or `"empty"` for a weighted miss slot. |
| `comment` | No | Ignored at runtime. |

### LootRange

```json
3                        // fixed value
{ "min": 1, "max": 5 }  // random [1..5] inclusive
```

### Full Example

```json
{
  "name": "dungeon_chest",
  "comment": "Generic dungeon chest with three pools of escalating rarity.",
  "pools": [
    {
      "rolls": { "min": 3, "max": 6 },
      "entries": [
        { "item": "minecraft:bread",  "weight": 30, "count": { "min": 1, "max": 4 } },
        { "item": "minecraft:arrow",  "weight": 25, "count": { "min": 4, "max": 16 } },
        { "item": "minecraft:torch",  "weight": 25, "count": { "min": 2, "max": 8 } }
      ]
    },
    {
      "rolls": { "min": 1, "max": 3 },
      "entries": [
        { "item": "minecraft:iron_ingot", "weight": 20, "count": { "min": 1, "max": 4 } },
        { "item": "minecraft:iron_sword", "weight": 10, "count": 1 },
        { "item": "minecraft:name_tag",   "weight": 4,  "count": 1 }
      ]
    },
    {
      "rolls": { "min": 0, "max": 1 },
      "entries": [
        { "item": "minecraft:diamond",        "weight": 20, "count": { "min": 1, "max": 3 } },
        { "item": "minecraft:golden_apple",   "weight": 5,  "count": 1 },
        { "item": "minecraft:enchanted_book", "weight": 10, "count": 1,
          "enchantments": [ { "id": "minecraft:mending", "level": 1 } ] }
      ]
    }
  ]
}
```

---

## Bundled Loot Tables

Bundled at `resources/data/sapphics-structure-library/tsaphloot/<n>.tsaphloot`:

| Table Name | Description |
|------------|-------------|
| `dungeon_chest` | Three-pool generic dungeon loot with consumables, mid-tier, and rare treasures |
| `armory_chest` | Military loot — ammunition, weapons, armour, trophy items |
| `library_chest` | Books, paper, writing supplies, and rare enchanted books |
| `temple_chest` | Temple-themed valuables, gold, emeralds, and artefacts |
| `ancient_ruins_chest` | Degraded equipment and ancient-feeling rare drops |
| `generic_chest` | Minimal fallback table suitable for any context |

### Override Priority

A table name is resolved in this order, with later sources winning:

1. Bundled (mod jar)
2. Datapack (`data/<namespace>/ssl_loot/`)
3. World-specific (`<worldSave>/data/ssl_loot/`)

---

## API Quick Reference

### Entry Points

| Class | Purpose |
|-------|---------|
| `StructureLoaderBridge` | Single public gateway to all internal implementations |
| `StructureLoaderBridge.getLoader()` | Returns `IStructureLoader` |
| `StructureLoaderBridge.getExporter()` | Returns `IStructureExporter` |
| `StructureLoaderBridge.getLootEngine()` | Returns `ITsaphLootEngine` |
| `StructureLoaderBridge.getQueue(world)` | Returns the `StructureQueue` for a world |
| `StructureLoaderBridge.definitions()` | All registered datapack `StructureDefinition` instances |

### Key Types

| Type | Description |
|------|-------------|
| `StructurePiece` | In-memory decoded structure (palette, blocks, block entities, loot refs) |
| `StructurePlacement` | Fluent builder for loading and placing structures |
| `StructureDefinition` | A datapack-driven structure placement definition |
| `BlockEntry` | Palette entry mapping an index to a block state string |
| `PendingPlacement` | A queued block or update trigger waiting for its chunk to load |
| `LootTableRef` | Reference to a vanilla or TsaphLoot loot table |
| `TsaphLootTable` | Parsed `.tsaphloot` table |
| `TsaphLootBuilder` | Fluent builder for constructing loot tables in code |
| `StructureQueue` | Per-world persistent deferred placement queue |

### `StructurePiece`

```java
// Returns local Y of the first non-air layer — applied automatically by StructurePlacement
int groundOffset()
```

### `IStructureLoader`

```java
StructurePiece load(Path path) throws IOException;
void place(ServerWorld world, StructurePiece piece, BlockPos origin);
void processChunkQueue(ServerWorld world, int chunkX, int chunkZ);
```

### `ITsaphLootEngine`

```java
boolean applyLootTag(ServerWorld world, BlockPos pos, LootTableRef ref, long seed);
boolean populate(ServerWorld world, BlockPos pos, LootTableRef ref);
Optional<TsaphLootTable> resolve(String name);
```

### `LootTableRef` Factories

```java
LootTableRef.vanilla("minecraft:chests/simple_dungeon");
LootTableRef.tsaphloot("dungeon_chest");
```

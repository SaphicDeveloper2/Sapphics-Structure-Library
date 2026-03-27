# Sapphics Structure Library — Developer Documentation

**Mod ID:** `sapphics-structure-library`  
**Minecraft:** 1.21.1 (Fabric)  
**Depends:** Fabric API  
**Status:** Updated for the current codebase as of 2026-03-27

---

## Table of Contents

1. [Overview](#overview)
2. [What changed since older docs](#what-changed-since-older-docs)
3. [In-game tools](#in-game-tools)
4. [Command reference](#command-reference)
5. [Single-structure workflow](#single-structure-workflow)
6. [Multi-structure workflow](#multi-structure-workflow)
7. [Connection Point Block](#connection-point-block)
8. [Structure Terrain Block](#structure-terrain-block)
9. [Loot Barrel](#loot-barrel)
10. [Single-structure Java API](#single-structure-java-api)
11. [StructurePlacement builder](#structureplacement-builder)
12. [Multi-structure Java API](#multi-structure-java-api)
13. [Exact-origin and pre-queued generation](#exact-origin-and-pre-queued-generation)
14. [Two queue systems](#two-queue-systems)
15. [Two-pass placement system](#two-pass-placement-system)
16. [Chunk generation modes](#chunk-generation-modes)
17. [Interior fill modes](#interior-fill-modes)
18. [TsaphGen worldgen system](#tsaphgen-worldgen-system)
19. [Programmatic loot tables](#programmatic-loot-tables)
20. [Dimension targeting](#dimension-targeting)
21. [Datapack loading](#datapack-loading)
22. [File formats](#file-formats)
23. [Loot system](#loot-system)
24. [Network packets](#network-packets)
25. [API quick reference](#api-quick-reference)

---

## Overview

Sapphics Structure Library (SSL) is a Fabric structure engine built around two binary formats:

- **`.tsaphstruct`** — one standalone structure piece.
- **`.tsaphmultistruct`** — a bundle of tagged pieces used by the procedural generator.

Core capabilities:

- no vanilla-size ceiling
- rotation-aware placement
- chunk-safe deferred block placement
- targeted second-pass block updates
- embedded block-entity NBT and loot refs
- datapack loading for structures, multi-struct bundles, and loot tables
- public API isolated to `com.sapphic.ssl.api`

The current codebase also supports **high-level queued generation requests** for both standalone structures and multi-structures. Those requests can be registered **before the target dimension is entered**, then applied automatically when the anchor chunk becomes available.

---

## What changed since older docs

Older SSL documentation is now outdated in several important ways. The current codebase adds or changes all of the following:

- **`ANCHOR` is now a valid `PieceRole`.**
  - It is placed first and seeds the multi-structure.
  - Only the first anchor in a bundle is used.
- **Exact-origin multi-structure generation exists.**
  - `StructureLoaderBridge.spawnMultiStructAt(...)`
- **Cross-dimension pre-queue generation exists.**
  - `StructureLoaderBridge.queueStructure(...)`
  - `StructureLoaderBridge.queueMultiStruct(...)`
- **Queued generation is distinct from the persistent block queue.**
  - `DeferredGenerationQueue` is in-memory and high-level.
  - `StructureQueue` is persistent and per-block.
- **Chunk hooks now process queued generation requests as well as block queue drains.**
- **`/tsaph export <name>` exists.**
  - Exports a datapack-style directory for loot distribution.
- **`/tsaph loot setbarrel ...` exists.**
  - Lets you switch a placed Loot Barrel into REGISTRY mode from commands.
- **`/tsaph multi spawn` accepts an optional seed.**
- **Multi-struct format docs must include `ANCHOR` role byte = `3`.**
- **The on-disk `pending.bin` queue file is not versioned the way some older docs claimed.**
  - It is written as `[int count]` followed by repeated `PendingPlacement` records.
- **Loot Barrel packets now include `ExportTsaphloot`.**
- **`ChunkGenerationMode` enum for controlling chunk handling.**
  - `QUEUE` — default deferred placement
  - `FORCE_GENERATE` — immediate chunk preparation
- **`TsaphGenConfig` and `TsaphGenRegistry` for programmatic worldgen.**
  - A new `.tsaphgen` file format for flexible worldgen definitions.
- **`TsaphLootBuilder` for programmatic loot table construction.**
- **`StructureLoaderBridge.definitions()` method for listing datapack definitions.**
- **`StructureLoaderBridge.ensureChunksGenerated(...)` for manual chunk preparation.**
- **`StructureLoaderBridge.placeStructure(...)` with chunk mode control.**
- **`ITsaphLootEngine.resolve(String name)` for looking up loot tables.**
- **`StructurePlacement.withChunkGenerationMode(...)` in the fluent API.**
- **`InteriorFillMode` enum for controlling terrain bleed-through.**
  - `SKIP_AIR` — default, air blocks skipped (terrain can bleed into interiors)
  - `FILL_AIR` — air blocks placed explicitly (clears terrain from interiors)
- **`StructurePlacement.withInteriorAirFill()` in the fluent API.**

---

## In-game tools

### Structure Wand
Used to select two corners of a rectangular region.

- **Right-click** → set position 1
- **Sneak + right-click** → set position 2

After both corners are set, SSL reports dimensions and total volume in chat.

### Connection Point Block
**Block ID:** `sapphics-structure-library:connector_block`

A directional full block that marks openings in a multi-structure piece.

- Place it at each doorway/open end that should connect to another piece.
- It is consumed as metadata at generation time.
- After placement, SSL replaces it with the block immediately inward from the connector, or air if nothing valid exists there.

### Structure Terrain Block
**Block ID:** `sapphics-structure-library:structure_terrain`

A transparent placeholder meaning "leave the world alone here". When the structure loader sees it, it skips that block position entirely.

Typical uses:

- hillside corridors
- natural underground foundations
- structures that should inherit local terrain or stone

### Loot Barrel
**Block ID:** `sapphics-structure-library:loot_barrel`

Developer-only authoring block for structure loot.

- Not in the creative tab.
- Obtain with `/give @s sapphics-structure-library:loot_barrel`.
- Replaced with a vanilla chest when the structure is generated.

Modes:

- **SNAPSHOT** — inventory contents define weighted loot directly.
- **REGISTRY** — references either a `.tsaphloot` table name or a vanilla loot-table key.

---

## Command reference

All `/tsaph` commands require operator permission level 2.

### Single structure

| Command | Description |
|---|---|
| `/tsaph save <name>` | Export current wand selection to `generated/ssl/<name>.tsaphstruct` |
| `/tsaph load <name> <x> <y> <z>` | Load and place a saved structure at exact origin |
| `/tsaph info` | Show current wand selection |
| `/tsaph list` | List saved `.tsaphstruct` files |
| `/tsaph queue` | Show persistent deferred block count for the current world |
| `/tsaph export <name>` | Export a datapack-style folder for loot distribution |

### Multi-structure

| Command | Description |
|---|---|
| `/tsaph multi begin` | Start a new bundle-building session |
| `/tsaph multi add <name> <role> [ctype] [weight] [maxCount]` | Capture the current selection into the active session |
| `/tsaph multi save <name>` | Save session to `.tsaphmultistruct` + companion JSON |
| `/tsaph multi cancel` | Cancel the current session |
| `/tsaph multi info` | Show current session contents |
| `/tsaph multi list` | List known multi-struct bundles |
| `/tsaph multi reload` | Reload bundles from disk |
| `/tsaph multi spawn <name> <x> <z> [depth] [seed]` | Generate a bundle at X/Z with optional depth and seed |

### Loot

| Command | Description |
|---|---|
| `/tsaph loot list` | List loaded `.tsaphloot` tables |
| `/tsaph loot reload` | Reload loot tables |
| `/tsaph loot apply tsaphloot <name> <pos>` | Stamp a TsaphLoot ref onto a container |
| `/tsaph loot apply vanilla <id> <pos>` | Stamp a vanilla loot-table ref onto a container |
| `/tsaph loot fill tsaphloot <name> <pos>` | Populate immediately from TsaphLoot |
| `/tsaph loot fill vanilla <id> <pos>` | Populate immediately from vanilla loot |
| `/tsaph loot setbarrel <pos> tsaphloot <name>` | Set a placed Loot Barrel to REGISTRY mode using a TsaphLoot table |
| `/tsaph loot setbarrel <pos> vanilla <id>` | Set a placed Loot Barrel to REGISTRY mode using a vanilla loot table |

---

## Single-structure workflow

### Save

1. Select a region with the Structure Wand.
2. Run:

```text
/tsaph save <name>
```

Written to:

```text
<world>/generated/ssl/<name>.tsaphstruct
```

### Load

```text
/tsaph load <name> <x> <y> <z>
```

This places the structure with its `(minX, minY, minZ)` corner at `(x, y, z)`.

Unloaded target chunks are handled automatically through the persistent `StructureQueue`.

---

## Multi-structure workflow

### Roles

Current valid `PieceRole` values are:

| Role | Purpose |
|---|---|
| `ANCHOR` | Fixed seed piece placed first at the target origin |
| `ROOM` | Terminal/cap piece |
| `HALLWAY` | Underground connector |
| `PATH` | Surface connector |

Important:

- `ANCHOR` is new compared to older docs.
- A bundle may contain more than one `ANCHOR`, but **only the first one is used**.
- If no `ANCHOR` exists, SSL falls back to a weighted random `ROOM` seed.

### Build session

```text
/tsaph multi begin
```

Then repeat:

1. build or locate one piece in the world
2. place connector blocks at all intended openings
3. select it with the wand
4. run:

```text
/tsaph multi add <name> <role> [connection_type] [weight] [max_count]
```

Then save:

```text
/tsaph multi save <bundle_name>
```

Written files:

- `<bundle_name>.tsaphmultistruct`
- `<bundle_name>.tsaphmultistruct.json`

### Generate from commands

```text
/tsaph multi spawn <bundle_name> <x> <z> [depth] [seed]
```

Notes:

- `depth` defaults to `ProceduralEngine.DEFAULT_MAX_DEPTH` (6).
- `seed` is optional.
- If an `ANCHOR` exists, it is used as the seed piece.
- If not, a weighted `ROOM` is used.

---

## Connection Point Block

Connection points are extracted from connector block positions when a multi-structure is written.

Generation behavior:

1. SSL finds an open end.
2. It searches candidate pieces with compatible connector points.
3. It computes a `BlockRotation` that aligns the entry connector.
4. It snaps the new piece so the connectors overlap exactly.
5. All remaining connector points on the new piece become new open ends.

Because rotation is applied during placement, pieces do not need separate pre-rotated variants.

---

## Structure Terrain Block

When SSL sees a Structure Terrain block during placement, it places nothing at that position.

That means:

- natural blocks already in the world remain
- caves, fluids, and terrain are preserved
- the terrain marker itself never appears in the final world

---

## Loot Barrel

### SNAPSHOT mode

- Uses the 27-slot inventory.
- Each item type becomes a weighted entry.
- Default weight is total stack count per item type.
- Per-item overrides are stored in `SslWeightOverrides`.
- The replacement chest is populated immediately during structure placement.

### REGISTRY mode

- Stores a string key in `SslRegistryKey`.
- Keys with `:` that do **not** start with `ssl:` are treated as vanilla loot-table IDs.
- All other keys are treated as TsaphLoot names.
- The replacement chest is tagged and fills lazily on first open.

### NBT layout

```text
{
  SslBarrelMode: 0 | 1,
  SslRegistryKey: "...",
  Items: [...],
  SslWeightOverrides: {
    "minecraft:diamond": 5,
    "minecraft:iron_ingot": 20
  }
}
```

### Useful command helpers

```text
/tsaph loot setbarrel <pos> tsaphloot <name>
/tsaph loot setbarrel <pos> vanilla <id>
```

### Exporting loot content

There are now **two separate export-related features**:

1. **`/tsaph export <name>`**
   - writes a datapack-style directory under `generated/ssl/export/<name>/`
   - includes `pack.mcmeta`
   - writes SNAPSHOT barrel content as vanilla loot-table JSON
2. **`LootBarrelPackets.ExportTsaphloot`**
   - server packet path for exporting a barrel snapshot to:
   - `<world>/generated/ssl/tsaphloot/<name>.tsaphloot`

---

## Single-structure Java API

### Basic load and place

```java
IStructureLoader loader = StructureLoaderBridge.getLoader();
StructurePiece piece = loader.load(path);
loader.place(world, piece, origin);
```

### Rotation-aware placement

```java
loader.place(world, piece, origin, BlockRotation.CLOCKWISE_90);
```

This rotates both:

- block coordinates
- directional block-state properties

### Ground offset example

```java
BlockPos origin = new BlockPos(x, 64 - piece.groundOffset(), z);
loader.place(world, piece, origin);
```

### Placement with chunk mode control

```java
StructureLoaderBridge.placeStructure(
    world,
    piece,
    origin,
    BlockRotation.CLOCKWISE_90,
    ChunkGenerationMode.FORCE_GENERATE
);
```

---

## StructurePlacement builder

`StructurePlacement` is the recommended convenience API for single structures.

```java
StructurePlacement.load(path)
    .at(x, z)
    .onSurface()
    .place(world);

StructurePlacement.of(piece)
    .atCorner(x, z)
    .atY(64)
    .withYOffset(-3)
    .place(world);
```

Methods:

| Method | Meaning |
|---|---|
| `load(path)` | Load structure and start builder |
| `of(piece)` | Start from an already-loaded piece |
| `at(x, z)` | Center structure on X/Z |
| `atCorner(x, z)` | Pin exact min corner |
| `onSurface()` | Use `WORLD_SURFACE` |
| `onOceanFloor()` | Use `OCEAN_FLOOR` |
| `atY(y)` | Use literal Y |
| `withYOffset(n)` | Apply extra Y offset |
| `withoutGroundOffset()` | Disable ground-offset correction |
| `withChunkGenerationMode(mode)` | Override chunk handling (see [Chunk generation modes](#chunk-generation-modes)) |
| `withInteriorAirFill()` | Enable interior clearing (see [Interior fill modes](#interior-fill-modes)) |
| `withInteriorFillMode(mode)` | Set interior fill mode explicitly |
| `place(world)` | Execute placement |
| `piece()` | Get the underlying `StructurePiece` |

---

## Multi-structure Java API

### Load and spawn

```java
MultiStructBundle bundle = StructureLoaderBridge.loadMultiStruct(path);
StructureLoaderBridge.spawnMultiStruct(world, bundle, x, z, 6, null);
```

### Exact-origin generation

```java
StructureLoaderBridge.spawnMultiStructAt(world, bundle, new BlockPos(x, y, z), 6, 1234L);
```

This is newer than older docs. It does **not** sample terrain height. The supplied Y is used directly.

### With chunk mode control

```java
StructureLoaderBridge.spawnMultiStruct(
    world, bundle, x, z, 6, seed,
    ChunkGenerationMode.FORCE_GENERATE
);

StructureLoaderBridge.spawnMultiStructAt(
    world, bundle, origin, 6, seed,
    ChunkGenerationMode.FORCE_GENERATE
);
```

### Registry access

```java
Optional<MultiStructBundle> opt = StructureLoaderBridge.getMultiStruct("my_bundle");
Set<String> names = StructureLoaderBridge.multiStructNames();
StructureLoaderBridge.reloadMultiStructRegistry(sslDir);
```

### Session API

```java
UUID playerUuid = player.getUuid();
WandSession session = StructureLoaderBridge.beginSession(playerUuid);
session.addPiece("anchor", PieceRole.ANCHOR, ConnectionType.NONE, 1, 1, piece);
MultiStructBundle bundle = session.build("my_bundle");
StructureLoaderBridge.endSession(playerUuid);
```

---

## Exact-origin and pre-queued generation

This is the largest missing feature from older docs.

SSL now supports **high-level queued generation requests** for cases where you want to schedule structure generation in a dimension **before that dimension is entered**.

### Standalone structure queueing

```java
StructureLoaderBridge.queueStructure(
    World.NETHER,
    piece,
    new BlockPos(100, 64, 100),
    BlockRotation.CLOCKWISE_90
);
```

Overloads also accept:

- `String dimensionKey`
- `RegistryKey<World>`
- no-rotation convenience form

### Multi-structure queueing

```java
StructureLoaderBridge.queueMultiStruct(
    World.END,
    bundle,
    new BlockPos(0, 80, 0),
    8,
    987654321L
);
```

### How queued generation is applied

Queued requests are processed when the anchor chunk becomes available through:

- `ChunkGeneratorMixin`
- `ServerChunkEvents.CHUNK_LOAD`

Both call:

```java
StructureLoaderBridge.processQueuedGenerations(world, chunkPos);
```

### Important limitation

This high-level queue is **not persisted to disk**.

It exists in memory only and is intended for runtime orchestration such as:

- TARDIS-style interior dimensions
- delayed dimension setup
- server-side scripted encounters
- mods that stage a structure before teleporting the player in

---

## Two queue systems

SSL now has **two different queue layers**.

### 1. `StructureQueue` — persistent per-block queue

This is the long-standing queue used during structure placement when a block's chunk is not loaded.

Properties:

- per-world
- disk-backed
- survives restart
- stores `PendingPlacement` entries
- located under `data/ssl_queue/<dimension>/pending.bin`

### 2. `DeferredGenerationQueue` — in-memory high-level queue

This is the newer queue used for whole-generation requests.

Properties:

- keyed by dimension and anchor chunk
- not persisted
- stores requests like "generate this structure when that chunk in that dimension becomes available"
- used by `queueStructure(...)` and `queueMultiStruct(...)`

Do **not** confuse these two systems in integrations.

---

## Two-pass placement system

### Pass 1

- non-air blocks are placed with `SslCompat`'s force-state flags
- unloaded target chunks are written into the persistent `StructureQueue`
- update-sensitive placed blocks are collected for pass 2

### Pass 2

For each sensitive block:

- if all face-adjacent chunks are loaded → resolve neighbor updates immediately
- otherwise → enqueue `PendingPlacement.updateOnly(...)`

This is what fixes:

- fences and walls connecting
- stairs updating shape
- waterlogged block updates
- fluid propagation across chunk boundaries

---

## Chunk generation modes

SSL now supports two chunk-handling strategies for structure placement:

### `ChunkGenerationMode.QUEUE`

This is the **default** mode.

Behavior:

- loaded chunks are written immediately
- unloaded chunks are deferred into SSL's persistent queue
- queued blocks are applied when those chunks load naturally
- this is the best choice for normal worldgen and low-latency server behavior

Use when:

- the structure does not need to exist instantly
- the destination will be entered naturally anyway
- you want the cheapest server-side behavior

### `ChunkGenerationMode.FORCE_GENERATE`

This mode is **opt-in**.

Behavior:

- SSL computes the exact chunk footprint of the placement
- only the required chunks are synchronously generated/loaded
- the structure is placed immediately
- no wide-area preload loop is required

Use when:

- a teleport destination must exist right now
- another mod needs immediate block access after placement
- you are integrating SSL into a custom dimension travel or portal flow

### API usage

#### Single structures

```java
// With rotation
StructureLoaderBridge.placeStructure(
    world, piece, origin,
    BlockRotation.CLOCKWISE_90,
    ChunkGenerationMode.FORCE_GENERATE
);

// Without rotation
StructureLoaderBridge.placeStructure(
    world, piece, origin,
    ChunkGenerationMode.FORCE_GENERATE
);
```

#### Fluent API

```java
StructurePlacement.of(piece)
    .at(x, z)
    .onSurface()
    .withChunkGenerationMode(ChunkGenerationMode.FORCE_GENERATE)
    .place(world);
```

#### Multi-structures

```java
StructureLoaderBridge.spawnMultiStruct(
    world, bundle, x, z, maxDepth, seed,
    ChunkGenerationMode.FORCE_GENERATE
);

StructureLoaderBridge.spawnMultiStructAt(
    world, bundle, origin, maxDepth, seed,
    ChunkGenerationMode.FORCE_GENERATE
);
```

### Manual chunk preparation

For advanced use cases, you can prepare chunks before placement:

```java
// By block bounds
StructureLoaderBridge.ensureChunksGenerated(
    world,
    new BlockPos(minX, minY, minZ),
    new BlockPos(maxX, maxY, maxZ)
);

// By chunk radius around a center
StructureLoaderBridge.ensureChunksGenerated(
    world,
    centerPos,
    3  // chunk radius
);
```

---

## Interior fill modes

By default, SSL skips air blocks when placing structures — existing world blocks (terrain, ores, caves) remain where the structure has air. This is efficient but can cause terrain to "bleed through" into enclosed interior spaces.

SSL now supports explicit interior clearing through `InteriorFillMode`.

### `InteriorFillMode.SKIP_AIR`

This is the **default** mode.

Behavior:

- air blocks in the structure definition are skipped
- existing world blocks remain in those positions
- terrain, ores, and caves can intersect structure interiors
- fastest placement (fewer block operations)

Use when:

- open-air structures (ruins, monuments, towers)
- structures designed to blend with terrain
- performance is critical

### `InteriorFillMode.FILL_AIR`

This mode is **opt-in**.

Behavior:

- air blocks in the structure definition are explicitly placed as air
- clears any terrain that would otherwise intrude into interiors
- structure blocks are placed on top, so the structure itself is never affected
- `StructureTerrain` blocks are still respected (never replaced with air)

Use when:

- enclosed structures with interior rooms (dungeons, houses, bunkers)
- underground structures where terrain would fill hallways
- any structure where interior air space must be guaranteed

### API usage

#### Fluent API (recommended)

```java
// Simple toggle
StructurePlacement.of(dungeon)
    .at(x, z)
    .atY(32)
    .withInteriorAirFill()   // enables FILL_AIR mode
    .place(world);

// Explicit mode setting
StructurePlacement.of(piece)
    .at(x, z)
    .onSurface()
    .withInteriorFillMode(InteriorFillMode.FILL_AIR)
    .place(world);
```

#### Bridge API

```java
// With all options
StructureLoaderBridge.placeStructure(
    world, piece, origin,
    BlockRotation.NONE,
    ChunkGenerationMode.QUEUE,
    InteriorFillMode.FILL_AIR
);

// Without rotation
StructureLoaderBridge.placeStructure(
    world, piece, origin,
    ChunkGenerationMode.QUEUE,
    InteriorFillMode.FILL_AIR
);
```

#### Direct loader API

```java
IStructureLoader loader = StructureLoaderBridge.getLoader();
loader.place(world, piece, origin, BlockRotation.NONE, InteriorFillMode.FILL_AIR);
```

### Combining with StructureTerrain

`StructureTerrain` blocks always indicate "preserve world terrain here" regardless of interior fill mode. This means you can:

1. Use `FILL_AIR` to clear interiors
2. Place `StructureTerrain` blocks under floors or at terrain entry points
3. Get clean interiors while still blending structure edges with the world

---

## TsaphGen worldgen system

SSL provides a flexible worldgen configuration system through `.tsaphgen` files and the `TsaphGenRegistry` API.

### TsaphGenConfig

`TsaphGenConfig` defines where a structure spawns — which dimensions, which biomes, how frequently, and with which vertical placement strategy. Unlike `StructureDefinition` (which tightly couples a single `.tsaphstruct` to its JSON definition), a `.tsaphgen` file can reference **any** registered structure and target **multiple** dimensions.

### Datapack location

```text
data/<namespace>/ssl_worldgen/<name>.tsaphgen
```

### JSON schema

```json
{
  "comment":     "Optional human-readable note — ignored by the engine",
  "structure":   "mymod:my_village",
  "weight":      0.005,
  "dimensions":  ["minecraft:overworld"],
  "biomes":      ["minecraft:plains", "minecraft:forest"],
  "y_placement": "surface",
  "y_offset":    0,
  "salt":        12345
}
```

### Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `structure` | string | (paired) | Namespaced id of the target structure. If omitted, uses the same namespace/stem as the `.tsaphgen` file. |
| `weight` | float | `0.005` | Per-chunk spawn probability (0.0–1.0). ~1-in-200 chunks by default. |
| `dimensions` | array | `["minecraft:overworld"]` | List of dimension registry keys. Use `["*"]` for any dimension. |
| `biomes` | array | (all) | Biome whitelist. Empty or omitted = all biomes. |
| `y_placement` | string | `"surface"` | One of `"surface"`, `"ocean_floor"`, `"absolute"`. |
| `y_offset` | int | `0` | Offset applied on top of the computed Y. |
| `salt` | long | `0` | Per-config seed modifier. |

### Programmatic registration

Register configs from code during `ModInitializer.onInitialize()`:

```java
TsaphGenRegistry.register(
    new TsaphGenConfig.Builder("mymod:my_village")
        .structure("mymod:my_village")
        .weight(0.004f)
        .dimensions("minecraft:overworld")
        .biomes("minecraft:plains", "minecraft:sunflower_plains")
        .yPlacement(StructureDefinition.YPlacement.SURFACE)
        .salt(7391L)
        .build()
);
```

### Builder methods

| Method | Description |
|---|---|
| `structure(id)` | Set the structure to spawn (optional — defaults to pairing). |
| `weight(float)` | Per-chunk spawn probability. |
| `dimensions(String...)` | Replace the dimension list. |
| `anyDimension()` | Allow any dimension (wildcard `*`). |
| `biomes(String...)` | Set biome whitelist (empty = all biomes). |
| `yPlacement(YPlacement)` | Vertical placement strategy. |
| `yOffset(int)` | Vertical offset on top of computed Y. |
| `salt(long)` | Per-config seed modifier. |
| `build()` | Finalize and return the `TsaphGenConfig`. |

### Registry API

```java
// All registered configs (code + datapack)
List<TsaphGenConfig> all = TsaphGenRegistry.all();

// Count of registered configs
int count = TsaphGenRegistry.size();
```

---

## Programmatic loot tables

SSL provides `TsaphLootBuilder` for constructing loot tables directly in Java code.

### Usage

```java
TsaphLootTable table = TsaphLootBuilder.create("my_dungeon_chest")
    .comment("Built in code — same result as the JSON file")
    .pool(pool -> pool
        .rolls(3, 6)
        .item("minecraft:bread")   .weight(30).count(1, 4) .add()
        .item("minecraft:arrow")   .weight(25).count(4, 16).add()
        .item("minecraft:diamond") .weight(5) .count(1, 3) .add()
        .empty(20)   // weighted miss
    )
    .pool(pool -> pool
        .rolls(0, 1)
        .item("minecraft:enchanted_book")
            .weight(10)
            .enchant("minecraft:mending", 1)
            .add()
        .item("minecraft:golden_apple").weight(5).add()
    )
    .build();
```

### Table builder methods

| Method | Description |
|---|---|
| `create(name)` | Start building a table with the given name |
| `comment(text)` | Optional description (stored in JSON, ignored at runtime) |
| `pool(Consumer<PoolBuilder>)` | Add a loot pool with the given configuration |
| `build()` | Finalize and return the `TsaphLootTable` |

### Pool builder methods

| Method | Description |
|---|---|
| `rolls(int)` | Fixed number of rolls |
| `rolls(min, max)` | Random roll count in `[min, max]` inclusive |
| `item(itemId)` | Begin configuring an item entry (returns `EntryBuilder`) |
| `empty(weight)` | Add a weighted empty-roll entry (reduces effective fill rate) |

### Entry builder methods

| Method | Description |
|---|---|
| `weight(int)` | Relative probability weight (higher = more likely) |
| `count(int)` | Fixed stack size |
| `count(min, max)` | Random stack size in `[min, max]` inclusive |
| `enchant(enchantId, level)` | Apply a fixed-level enchantment |
| `add()` | Commit the entry and return to the pool builder |

---

## Dimension targeting

SSL accepts any `ServerWorld`.

```java
loader.place(server.getWorld(World.OVERWORLD), piece, origin);
loader.place(server.getWorld(World.NETHER), piece, origin);
```

For queued generation before dimension entry, use the bridge queue methods:

```java
StructureLoaderBridge.queueStructure(World.NETHER, piece, origin);
StructureLoaderBridge.queueMultiStruct(World.END, bundle, origin, 6, null);
```

The procedural engine also routes connector preference by dimension string:

- Nether/End-like dimension keys prefer `HALLWAY`
- other dimensions prefer `PATH`
- if the preferred connector list is empty, the other connector list is used as fallback

---

## Datapack loading

### Single structures

```text
data/<namespace>/ssl_structures/<name>.json
data/<namespace>/ssl_structures/<name>.tsaphstruct
```

### Multi-structures

```text
data/<namespace>/ssl_multistructs/<name>.tsaphmultistruct
```

### Loot tables

```text
data/<namespace>/ssl_loot/<name>.tsaphloot
```

### Worldgen configs

```text
data/<namespace>/ssl_worldgen/<name>.tsaphgen
```

### World-specific loot overrides

```text
<world>/data/ssl_loot/<name>.tsaphloot
```

### Load order for loot tables

Current effective load order is:

1. bundled resources
2. datapack loot (`ssl_loot/`)
3. world-specific loot (`data/ssl_loot/`)

Later sources win on name collision.

---

## File formats

### `.tsaphstruct`

Current version: **v2**

Highlights:

- magic: `TSAP`
- version byte: `0x02`
- block palette
- region index
- packed block data
- block-entity NBT section
- loot ref section

### `.tsaphmultistruct`

Current version: **v2**

Highlights:

- magic: `TSMS`
- version byte: `0x02`
- per-piece role, connection type, weight, max count
- per-piece connection point records
- embedded full `.tsaphstruct` payload per piece

Current role wire values:

| Role | Byte |
|---|---:|
| `ROOM` | `0` |
| `HALLWAY` | `1` |
| `PATH` | `2` |
| `ANCHOR` | `3` |

### `.tsaphgen`

JSON format for worldgen configuration. See [TsaphGen worldgen system](#tsaphgen-worldgen-system).

### Persistent queue file: `pending.bin`

Older docs describing a separate queue-file version/header are no longer accurate.

Current `WorldQueueCache` persistence layout is simply:

```text
[int totalEntryCount]
[PendingPlacement]
[PendingPlacement]
...
```

Each `PendingPlacement` serializes its own:

- `pendingUpdate` flag
- `worldKey`
- `x`, `y`, `z`
- optional block-state / NBT / loot-ref payload

---

## Loot system

`LootTableRef` supports two kinds of references:

```java
LootTableRef.vanilla("minecraft:chests/simple_dungeon");
LootTableRef.tsaphloot("dungeon_chest");
```

Programmatic application:

```java
ITsaphLootEngine engine = StructureLoaderBridge.getLootEngine();
engine.applyLootTag(world, pos, LootTableRef.tsaphloot("dungeon_chest"), 0L);
engine.populate(world, pos, LootTableRef.vanilla("minecraft:chests/simple_dungeon"));
```

### Looking up loot tables

```java
Optional<TsaphLootTable> table = engine.resolve("dungeon_chest");
```

---

## Network packets

Registered in `LootBarrelPackets`.

### C2S

| Packet | Purpose |
|---|---|
| `SetMode` | switch SNAPSHOT / REGISTRY |
| `SetRegistryKey` | update registry key text |
| `SetWeight` | update one override |
| `RequestSync` | request current override snapshot |
| `ExportTsaphloot` | export current barrel snapshot as `.tsaphloot` |

### S2C

| Packet | Purpose |
|---|---|
| `SyncWeights` | replace client-side override map |

Client receiver location:

- `SapphicsStructureLibraryClient.onInitializeClient()`

That receiver:

- clears local overrides
- applies server overrides
- calls `LootBarrelScreen.invalidateRowCache()`

---

## API quick reference

### Core bridge methods

| Method | Purpose |
|---|---|
| `StructureLoaderBridge.getLoader()` | standalone structure load/place API |
| `StructureLoaderBridge.getExporter()` | export selected world regions |
| `StructureLoaderBridge.getLootEngine()` | loot API |
| `StructureLoaderBridge.getQueue(world)` | persistent per-block deferred queue |
| `StructureLoaderBridge.processChunkQueue(world, pos)` | drain persistent block queue |
| `StructureLoaderBridge.processQueuedGenerations(world, pos)` | apply queued high-level generation requests |
| `StructureLoaderBridge.processChunkDefinitions(world, pos)` | run datapack definition placement checks |
| `StructureLoaderBridge.onServerStopping()` | save all persistent queues |
| `StructureLoaderBridge.definitions()` | list all datapack `StructureDefinition` entries |
| `StructureLoaderBridge.loadMultiStruct(path)` | load bundle from disk |
| `StructureLoaderBridge.saveMultiStruct(bundle, path)` | save bundle and companion JSON |
| `StructureLoaderBridge.spawnMultiStruct(world, bundle, x, z, depth, seed)` | terrain-aware bundle generation |
| `StructureLoaderBridge.spawnMultiStruct(world, bundle, x, z, depth, seed, chunkMode)` | with chunk mode control |
| `StructureLoaderBridge.spawnMultiStructAt(world, bundle, origin, depth, seed)` | exact-origin bundle generation |
| `StructureLoaderBridge.spawnMultiStructAt(world, bundle, origin, depth, seed, chunkMode)` | with chunk mode control |
| `StructureLoaderBridge.placeStructure(world, piece, origin, rotation, chunkMode)` | single structure with chunk mode |
| `StructureLoaderBridge.placeStructure(world, piece, origin, chunkMode)` | convenience overload (no rotation) |
| `StructureLoaderBridge.placeStructure(world, piece, origin, rotation, chunkMode, interiorMode)` | with interior fill mode |
| `StructureLoaderBridge.placeStructure(world, piece, origin, chunkMode, interiorMode)` | convenience overload (no rotation) |
| `StructureLoaderBridge.ensureChunksGenerated(world, min, max)` | prepare chunks by block bounds |
| `StructureLoaderBridge.ensureChunksGenerated(world, center, radius)` | prepare chunks by radius |
| `StructureLoaderBridge.queueStructure(...)` | pre-queue a standalone structure for later chunk availability |
| `StructureLoaderBridge.queueMultiStruct(...)` | pre-queue a bundle for later chunk availability |
| `StructureLoaderBridge.getMultiStruct(name)` | lookup by name |
| `StructureLoaderBridge.multiStructNames()` | list bundle names |
| `StructureLoaderBridge.reloadMultiStructRegistry(path)` | reload bundles from directory |
| `StructureLoaderBridge.beginSession(uuid)` | start multi-build session |
| `StructureLoaderBridge.getSession(uuid)` | fetch active session |
| `StructureLoaderBridge.hasSession(uuid)` | session existence check |
| `StructureLoaderBridge.endSession(uuid)` | end one session |
| `StructureLoaderBridge.endAllSessions()` | end all sessions |

### Important types

| Type | Meaning |
|---|---|
| `StructurePiece` | decoded standalone structure |
| `StructurePlacement` | fluent single-structure placement builder |
| `MultiStructBundle` | decoded multi-structure bundle |
| `MultiStructPiece` | one bundle piece |
| `ConnectionPoint` | connector position + facing |
| `PieceRole` | `ANCHOR`, `ROOM`, `HALLWAY`, `PATH` |
| `ChunkGenerationMode` | `QUEUE` (default) or `FORCE_GENERATE` |
| `InteriorFillMode` | `SKIP_AIR` (default) or `FILL_AIR` |
| `StructureQueue` | persistent per-world block queue |
| `PendingPlacement` | one deferred block or update-only trigger |
| `LootTableRef` | TsaphLoot or vanilla loot reference |
| `TsaphLootTable` | parsed SSL loot table |
| `TsaphLootBuilder` | fluent builder for constructing loot tables in code |
| `LootBarrelBlockEntity` | authoring block entity for in-world loot |
| `StructureDefinition` | datapack-driven structure placement definition |
| `TsaphGenConfig` | flexible worldgen configuration (`.tsaphgen`) |
| `TsaphGenRegistry` | registry for programmatic worldgen configs |
| `StructureRotation` | rotation math utilities |
| `RegionMarker` | fast-fail region gate for binary loader |

---

## Final notes for integrators

- Mixins should only call the **public API**, especially `StructureLoaderBridge`.
- Use `queueStructure(...)` / `queueMultiStruct(...)` when you need to stage content before a dimension is live.
- Use `StructureQueue` only for low-level per-block deferred placement behavior.
- If you need exact Y control for a multi-structure, use `spawnMultiStructAt(...)`, not `spawnMultiStruct(...)`.
- Use `ChunkGenerationMode.FORCE_GENERATE` when you need the structure to exist immediately (e.g., teleport destinations).
- Use `InteriorFillMode.FILL_AIR` or `.withInteriorAirFill()` for enclosed structures (dungeons, houses) to prevent terrain bleeding into interiors.
- Use `TsaphGenConfig` and `TsaphGenRegistry` for flexible worldgen definitions that can target multiple dimensions.
- Use `TsaphLootBuilder` to construct loot tables programmatically instead of writing JSON.

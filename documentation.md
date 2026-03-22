# Sapphics Structure Library — Developer Documentation

**Mod ID:** `sapphics-structure-library`  
**Minecraft:** 1.21.1 (Fabric)  
**Depends:** Fabric API

---

## Table of Contents

1. [Overview](#overview)
2. [In-Game Tools](#in-game-tools)
3. [Saving a Single Structure](#saving-a-single-structure)
4. [Saving a Multi-Structure Bundle](#saving-a-multi-structure-bundle)
5. [The Connection Point Block](#the-connection-point-block)
6. [The `/tsaph` Command Reference](#the-tsaph-command-reference)
7. [The `.tsaphstruct` File Format](#the-tsaphstruct-file-format)
8. [The `.tsaphmultistruct` File Format](#the-tsaphmultistruct-file-format)
9. [Placing a Single Structure (Java API)](#placing-a-single-structure-java-api)
10. [The `StructurePlacement` Fluent Builder](#the-structureplacement-fluent-builder)
11. [Spawning a Multi-Structure Bundle (Java API)](#spawning-a-multi-structure-bundle-java-api)
12. [Two-Pass Placement System](#two-pass-placement-system)
13. [Deferred Placement & Chunk Queue](#deferred-placement--chunk-queue)
14. [Dimension Targeting](#dimension-targeting)
15. [Datapack Structure Definitions](#datapack-structure-definitions)
16. [Datapack Multi-Structure Bundles](#datapack-multi-structure-bundles)
17. [Loot Table System](#loot-table-system)
18. [Applying Loot to Structure Chests](#applying-loot-to-structure-chests)
19. [The `TsaphLootBuilder` Fluent Builder](#the-tsaphlootbuilder-fluent-builder)
20. [The `.tsaphloot` Format Reference](#the-tsaphloot-format-reference)
21. [Bundled Loot Tables](#bundled-loot-tables)
22. [API Quick Reference](#api-quick-reference)

---

## Overview

Sapphics Structure Library (SSL) is a high-performance structure engine for Fabric mods. It supports two structure formats:

- **`.tsaphstruct`** — a single structure piece, equivalent to a vanilla structure block save but with no size ceiling, bit-packed storage, and built-in loot references.
- **`.tsaphmultistruct`** — a bundle of named, tagged pieces with weights and connection points, consumed by the procedural engine to generate randomised dungeons, villages, and multi-part mega-structures.

Key design goals:

- **No size ceiling.** Both formats support structures far beyond vanilla's 48³ limit.
- **Chunk-safe placement.** Blocks in unloaded chunks are queued to disk and applied when the chunk loads, including across server restarts.
- **Efficient updates.** A two-pass system ensures fences connect, fluids flow, and redstone resolves correctly without touching every block.
- **Flexible loot.** Containers use vanilla loot tables or custom `.tsaphloot` tables, baked into the file at export time.
- **Datapack-driven generation.** Both formats can be loaded from datapacks with no Java code required.
- **Fully obfuscated internals.** Only the `com.sapphic.ssl.api` package is public; everything else is obfuscated in production builds.

---

## In-Game Tools

Two items are available in the **Structure Library** creative tab:

### Structure Wand
Used to select rectangular regions for export. Renders as a vanilla stick.

- **Right-click a block** → Set Position 1 (first corner)
- **Sneak + Right-click a block** → Set Position 2 (second corner)

Once both corners are set, the wand displays the selection's dimensions and volume in chat, including a warning if it exceeds the vanilla 48³ limit. There is no such limit in SSL — the warning is purely informational.

### Connection Point Block
A directional full block used to mark the open ends of a multi-structure piece. The block faces the direction the player is looking when placed (opposite of player facing). Place it at every opening of a hallway, path, or room that should connect to an adjacent piece.

Connection point blocks are **automatically removed** at generation time and replaced with the floor block beneath them, so they never appear in the final world.

---

## Saving a Single Structure

**Step 1 — Select the region**

Use the **Structure Wand** to right-click two opposite corners of your build. The entire build must be within loaded chunks.

**Step 2 — Export**

```
/tsaph save <name>
```

The file is saved to `<worldSave>/generated/ssl/<name>.tsaphstruct`. The `.tsaphstruct` extension is appended automatically if omitted.

**Step 3 — Load it back**

```
/tsaph load <name> <x> <y> <z>
```

The structure is placed with its `(minX, minY, minZ)` corner at `(x, y, z)`. Blocks in unloaded chunks are deferred automatically.

**Tip — avoiding floating structures**

If your selection box captured empty air rows below the actual ground level of your build, use the `groundOffset()` helper when placing via the Java API so the first real block lands at the intended world Y. See [The `StructurePlacement` Fluent Builder](#the-structureplacement-fluent-builder).

---

## Saving a Multi-Structure Bundle

A multi-structure bundle (`bundle`) groups several independently-selected pieces into a single `.tsaphmultistruct` file. The procedural engine then chains them together at generation time using each piece's connection points.

### Full workflow

**Step 1 — Begin a session**

```
/tsaph multi begin
```

This starts a building session. Any previous session is discarded.

**Step 2 — Build a piece in the world**

Build or place one of your structure pieces (a house, a corridor segment, a T-junction, etc.) somewhere in the world. Place **Connection Point blocks** at every opening where another piece should attach, facing outward.

**Step 3 — Select the piece with the wand**

Use the Structure Wand to select the piece's bounding box, including its connection point blocks.

**Step 4 — Tag and add the piece**

```
/tsaph multi add <name> <role> [connection_type] [weight] [max_count]
```

| Argument | Required | Description |
|----------|----------|-------------|
| `name` | Yes | A readable label for this piece, e.g. `house_a` or `t_junction`. |
| `role` | Yes | `ROOM`, `HALLWAY`, or `PATH`. If you type an invalid role, clickable buttons appear in chat to select the correct one — the piece name is preserved. |
| `connection_type` | No | The junction shape (see table below). Ignored for `ROOM`. If invalid, clickable buttons appear with the full command pre-filled. |
| `weight` | No | Relative spawn probability (default 1). Higher = more common. |
| `max_count` | No | Maximum number of times this piece may appear in one generation (default -1 = unlimited). |

**Roles:**

| Role | Purpose |
|------|---------|
| `ROOM` | Terminal node — a house, chamber, plaza, or dungeon boss room. Caps open connector ends. No connection type needed. |
| `HALLWAY` | Underground connector — a corridor, tunnel, or cave passage. Requires a connection type. Used preferentially in Nether/End dimensions. |
| `PATH` | Surface connector — a dirt road, paved street, or bridge. Requires a connection type. Used preferentially in Overworld. |

**Connection types** (for HALLWAY and PATH pieces):

| Type | Shape | Open ends |
|------|-------|-----------|
| `STRAIGHT` | ━━ | Forward + back |
| `T_SHAPE` | ┤ | Forward + left + right |
| `T_INVERTED` | ├ | Back + back-left + back-right |
| `CORNER_LEFT` | ┘ | Forward + left |
| `CORNER_RIGHT` | └ | Forward + right |
| `CORNER_LEFT_INVERTED` | ┐ | Back + left |
| `CORNER_RIGHT_INVERTED` | ┌ | Back + right |
| `MIDSECTION_BRANCH` | ┣ | Forward + back + right (asymmetric detour) |

The connection type you declare must match the number and direction of connection point blocks you placed. The engine uses the actual blocks for snapping — the type field is metadata for the companion JSON.

**Step 5 — Repeat for each piece**

Clear your wand selection, build (or locate) the next piece, select it, and run `/tsaph multi add` again. Repeat until all piece types are tagged.

**Step 6 — Save the bundle**

```
/tsaph multi save <bundle_name>
```

Two files are written to `<worldSave>/generated/ssl/`:

- `<bundle_name>.tsaphmultistruct` — binary bundle (all pieces embedded)
- `<bundle_name>.tsaphmultistruct.json` — companion config (edit weights and max counts here without reopening the game)

**Step 7 — Generate a structure**

```
/tsaph multi spawn <bundle_name> <x> <z> [depth]
```

The engine seeds a random ROOM piece at `(x, z)`, then chains connectors outward from each connection point, capping open ends with ROOM pieces. `depth` controls how many connector chains can be chained before forced capping (default 6).

### Reviewing and managing sessions

```
/tsaph multi info     — Show all pieces added so far with name, role, type, weight
/tsaph multi cancel   — Discard the current session
/tsaph multi list     — List all saved .tsaphmultistruct bundles
/tsaph multi reload   — Reload all bundles from disk
```

### Editing the companion JSON

After saving, open `<bundle_name>.tsaphmultistruct.json` in any text editor. Adjust `weight` and `max_count` freely. Do not change `id` or `role` — those are structural. Run `/tsaph multi reload` to apply changes without restarting.

```json
{
  "name": "my_village",
  "pieces": [
    { "id": "...", "name": "house_a",    "role": "ROOM",    "connection_type": "NONE",     "weight": 10, "max_count": -1,  "connection_points": 4 },
    { "id": "...", "name": "road_cross", "role": "PATH",    "connection_type": "T_SHAPE",  "weight": 5,  "max_count": 3,   "connection_points": 3 },
    { "id": "...", "name": "road_end",   "role": "PATH",    "connection_type": "STRAIGHT", "weight": 8,  "max_count": -1,  "connection_points": 2 }
  ]
}
```

---

## The Connection Point Block

The **Connection Point** block (`sapphics-structure-library:connector_block`) is a directional full block placed inside a structure piece to mark where adjacent pieces will attach.

**Placement rules:**
- Place one connection point block at each opening of a connector piece facing **outward** (away from the interior of the piece).
- The block faces the direction opposite to the player's look direction when placed — so stand inside the opening looking outward and right-click to place correctly.
- ROOM pieces can have any number of connection points (one per doorway/entrance).
- HALLWAY and PATH pieces must have connection points whose directions match the declared connection type.

**How the engine uses them:**

When chaining pieces, the engine looks for a connector piece whose connection points include one facing the **opposite** of the open end's direction (i.e. it has an "entry" facing inward). The piece is then snapped so that entry point aligns exactly with the open end. All other connection points on the newly placed connector become new open ends for the next chaining round.

After placement, every connection point block is replaced with the floor block directly beneath it. If there is no floor block, it is replaced with air.

---

## The `/tsaph` Command Reference

All commands require operator permission level 2.

### Single structure

| Command | Description |
|---------|-------------|
| `/tsaph save <name>` | Export wand selection → `generated/ssl/<name>.tsaphstruct` |
| `/tsaph load <name> <x> <y> <z>` | Load and place at origin (x, y, z) |
| `/tsaph info` | Show current wand selection dimensions |
| `/tsaph list` | List all `.tsaphstruct` files in `generated/ssl/` |
| `/tsaph queue` | Show deferred block count for the current world |

### Multi-structure

| Command | Description |
|---------|-------------|
| `/tsaph multi begin` | Start a new bundle-building session |
| `/tsaph multi add <name> <role> [ctype] [weight] [max]` | Tag the current wand selection and add it to the session |
| `/tsaph multi save <name>` | Finish and write the bundle to disk |
| `/tsaph multi cancel` | Discard the current session |
| `/tsaph multi info` | List all pieces in the current session |
| `/tsaph multi list` | List all saved `.tsaphmultistruct` bundles |
| `/tsaph multi reload` | Reload all bundles from disk (picks up companion JSON changes) |
| `/tsaph multi spawn <name> <x> <z> [depth]` | Generate a structure from the named bundle at (x, z) |

### Loot

| Command | Description |
|---------|-------------|
| `/tsaph loot apply tsaphloot <name> <pos>` | Stamp a TsaphLoot table onto the container at `pos` (fills on first open) |
| `/tsaph loot apply vanilla <id> <pos>` | Stamp a vanilla loot table onto the container |
| `/tsaph loot fill tsaphloot <name> <pos>` | Immediately populate a container with TsaphLoot |
| `/tsaph loot fill vanilla <id> <pos>` | Immediately populate a container with a vanilla loot table |
| `/tsaph loot list` | List all loaded TsaphLoot tables |
| `/tsaph loot reload` | Reload all `.tsaphloot` files from disk |

---

## The `.tsaphstruct` File Format

Structure files use a proprietary binary layout. The current version is **v2**.

```
┌─────────────────────────────────────────────────────────┐
│  HEADER  (29 bytes fixed)                               │
│    [4]  Magic          0x54534150  ("TSAP")             │
│    [1]  Version        0x02                             │
│    [4]  SizeX / Y / Z  int                              │
│    [4]  PaletteSize    int  (max 65,535)                 │
│    [4]  RegionCount    int                              │
│    [4]  EntityCount    int  (block entities)            │
│    [4]  LootRefCount   int  (v2 only)                   │
├─────────────────────────────────────────────────────────┤
│  PALETTE  — [short len][UTF-8 blockstate id] per entry  │
├─────────────────────────────────────────────────────────┤
│  REGION INDEX  — 20 bytes per region (512×512 stride)   │
├─────────────────────────────────────────────────────────┤
│  BLOCK DATA  — bit-packed palette indices               │
│    sub-header: [int bits][int longs][int count]         │
├─────────────────────────────────────────────────────────┤
│  BLOCK-ENTITY DATA  — GZIP-compressed NBT per entity    │
├─────────────────────────────────────────────────────────┤
│  LOOT REFS  (v2 only)                                   │
│    [int linearIndex][byte refType][short idLen][UTF-8]  │
└─────────────────────────────────────────────────────────┘
```

Block states are stored as full property strings, e.g. `minecraft:oak_stairs[facing=north,half=bottom,shape=straight]`. Palette indices are bit-packed at the minimum bits-per-block needed for the palette size, identical to Minecraft's internal `BitStorage`.

### Version compatibility

| Version | Notes |
|---------|-------|
| v1 | No loot refs section. `LootRefCount` treated as 0. |
| v2 | Adds the LOOT REFS section. |

---

## The `.tsaphmultistruct` File Format

Multi-structure bundles embed multiple complete `.tsaphstruct` v2 payloads alongside per-piece metadata.

```
┌─────────────────────────────────────────────────────────┐
│  HEADER                                                 │
│    [4]  Magic          0x54534D53  ("TSMS")             │
│    [1]  Version        0x02                             │
│    [short+UTF]  Bundle name                             │
│    [4]  PieceCount     int                              │
├─────────────────────────────────────────────────────────┤
│  PIECE RECORDS  (PieceCount entries)                    │
│    [short+UTF]  id      (UUID string)                   │
│    [short+UTF]  name    (human-readable label)          │
│    [1]  role            (0=ROOM 1=HALLWAY 2=PATH)       │
│    [1]  connectionType  (0–8, see ConnectionType enum)  │
│    [4]  weight          int                             │
│    [4]  maxCount        int  (-1 = unlimited)           │
│  v2: connection points before struct data               │
│    [4]  connPtCount     int                             │
│    per point: [4 rx][4 ry][4 rz][1 facing]             │
│    [4]  dataLen         int                             │
│    [N]  data            .tsaphstruct v2 binary          │
└─────────────────────────────────────────────────────────┘
```

The embedded structure data for each piece is a complete, self-contained `.tsaphstruct` v2 binary, giving multi-structs full support for large structures, block-entity NBT, and loot refs.

### Version compatibility

| Version | Notes |
|---------|-------|
| v1 | No connection points section. `connPtCount` treated as 0. |
| v2 | Adds per-piece connection point records. |

A companion `<name>.tsaphmultistruct.json` is always written alongside the binary when saving in-game. It is optional — the binary is self-contained and can be loaded without it.

---

## Placing a Single Structure (Java API)

```java
IStructureLoader loader = StructureLoaderBridge.getLoader();
StructurePiece   piece  = loader.load(Path.of("path/to/structure.tsaphstruct"));
loader.place(serverWorld, piece, origin);
```

`origin` is the world-space `(minX, minY, minZ)` corner. Blocks in unloaded chunks are deferred automatically. The `place` call is safe to invoke from the server thread.

### Loading from mod resources

```java
Path structFile = FabricLoader.getInstance()
    .getModContainer("your-mod-id").orElseThrow()
    .findPath("data/your-mod-id/structures/mystructure.tsaphstruct").orElseThrow();
StructurePiece piece = loader.load(structFile);
```

### Ground offset

```java
// Avoid floating: subtract the piece's first non-air layer from target Y
BlockPos origin = new BlockPos(x, 64 - piece.groundOffset(), z);
loader.place(world, piece, origin);
```

---

## The `StructurePlacement` Fluent Builder

`StructurePlacement` is the recommended way to place single structures. It handles surface lookup, ground offset correction, and centering automatically.

```java
// Surface placement — centred on X/Z, flush with terrain
StructurePlacement.load(path)
    .at(x, z)
    .onSurface()
    .place(world);

// Ocean floor placement
StructurePlacement.of(piece).at(x, z).onOceanFloor().place(world);

// Absolute Y, corner-pinned, 3 blocks underground
StructurePlacement.of(piece)
    .atCorner(x, z)
    .atY(64)
    .withYOffset(-3)
    .place(world);

// Disable automatic ground offset correction
StructurePlacement.of(piece).atCorner(x, z).atY(64).withoutGroundOffset().place(world);
```

| Method | Description |
|--------|-------------|
| `StructurePlacement.load(path)` | Load from file and begin |
| `StructurePlacement.of(piece)` | Begin from an already-loaded piece |
| `.at(x, z)` | Centre the structure on this X/Z |
| `.atCorner(x, z)` | Pin the exact (minX, minZ) corner |
| `.onSurface()` | Use `WORLD_SURFACE` heightmap |
| `.onOceanFloor()` | Use `OCEAN_FLOOR` heightmap |
| `.atY(y)` | Use a literal world Y |
| `.withYOffset(n)` | Add `n` to the computed Y (negative = buried) |
| `.withoutGroundOffset()` | Skip automatic `groundOffset()` correction |
| `.place(world)` | Execute the placement |

---

## Spawning a Multi-Structure Bundle (Java API)

```java
// Load from disk
MultiStructBundle bundle = StructureLoaderBridge.loadMultiStruct(path);

// Or retrieve from the registry (populated by datapacks and /tsaph multi reload)
Optional<MultiStructBundle> bundle = MultiStructRegistry.get("my_village");

// Generate
StructureLoaderBridge.spawnMultiStruct(world, bundle, x, z,
        ProceduralEngine.DEFAULT_MAX_DEPTH);
```

The engine selects a random ROOM piece as the seed, places it at (x, z), then chains connector pieces (PATH for surface worlds, HALLWAY for Nether/End) outward from each connection point until `maxDepth` is reached or no eligible connectors remain. All open ends are capped with ROOM pieces.

**Session management from code:**

```java
UUID playerUuid = player.getUuid();

// Start a session
WandSession session = StructureLoaderBridge.beginSession(playerUuid);

// Add a piece (with pre-decoded StructurePiece)
// Connection points are extracted from connector blocks automatically
session.addPiece("house_a", PieceRole.ROOM, ConnectionType.NONE, 10, -1, piece);

// Build the bundle
MultiStructBundle bundle = session.build("my_village");

// End the session
StructureLoaderBridge.endSession(playerUuid);
```

---

## Two-Pass Placement System

### Pass 1 — Bulk `FORCE_STATE` placement

Every non-air block is placed with `Block.FORCE_STATE | 2` — suppressing all neighbour update notifications but still dispatching network update packets to tracking clients (preventing ghost blocks). Air variants are skipped silently.

Each placed block is tested by `BlockUpdateFilter.needsUpdate`. Blocks that pass are queued for Pass 2. Solid featureless blocks (stone, dirt, logs, glass, wool) are never touched in Pass 2.

### Pass 2 — Targeted neighbour update sweep

For each position in the sensitive list:

- **All 6 face-adjacent chunks loaded** → `getStateForNeighborUpdate` is called for each face. Any resulting state change is written back with `Block.NOTIFY_ALL`. Fluid ticks are scheduled for waterlogged or source blocks.
- **Any adjacent chunk unloaded** → the position is enqueued as an `updateOnly` entry in the persistent `StructureQueue` and fires when the last adjacent chunk loads.

### What gets updated

| Category | Examples |
|----------|---------|
| Fluid-carrying | Waterlogged blocks, direct water/lava |
| Directionally-connected | Fences, walls, glass panes, iron bars, vines |
| Shape-computed | Stairs, rails, leaves |
| Power / redstone | Redstone wire, buttons, pressure plates, observers |
| Interaction-state | Doors, trapdoors, fence gates, dispensers, tripwire |
| Attachment-facing | Wall torches, wall lanterns, bells, wall signs, banners |
| Gravity | Sand, gravel, concrete powder, anvils |
| Crop / fluid class | `CropBlock`, `FluidBlock`, `FallingBlock` |

---

## Deferred Placement & Chunk Queue

When Pass 1 encounters a block whose chunk is not loaded, a `PendingPlacement` is added to the world's `StructureQueue`, bucket-indexed by `ChunkPos.toLong()` for O(1) lookup.

The queue is drained by two hooks:

- **`ChunkGeneratorMixin`** — fires after `generateFeatures` for freshly-generated chunks.
- **`ServerChunkEvents.CHUNK_LOAD`** — fires for every chunk load, including from disk on server restart. This is what makes deferred placements resolve after a world reload without requiring chunk regeneration.

Both hooks drain the target chunk and all 8 surrounding chunks, allowing deferred `updateOnly` entries on chunk borders to fire as soon as all their neighbours are loaded.

### On-disk queue location

```
<worldSave>/data/ssl_queue/<dimension>/pending.bin
```

Examples:
```
saved/MyWorld/data/ssl_queue/minecraft_overworld/pending.bin
saved/MyWorld/data/ssl_queue/minecraft_the_nether/pending.bin
saved/MyWorld/data/ssl_queue/yourmod_yourdimension/pending.bin
```

The queue file version is 2. The dimension key is written once in the header. Legacy v1 files (key per-entry) are loaded transparently.

---

## Dimension Targeting

SSL is dimension-agnostic. Any `ServerWorld` is accepted:

```java
loader.place(server.getWorld(World.OVERWORLD), piece, origin);
loader.place(server.getWorld(World.NETHER),    piece, origin);

RegistryKey<World> myDim = RegistryKey.of(RegistryKeys.WORLD,
    Identifier.of("yourmod", "yourdimension"));
if (server.getWorld(myDim) != null)
    loader.place(server.getWorld(myDim), piece, origin);
```

The procedural engine also uses the dimension key to choose between PATH (surface) and HALLWAY (Nether/End) connectors automatically.

---

## Datapack Structure Definitions

Single structures can be registered for automatic world generation via datapacks — no Java code required.

### File layout

```
data/
└── <namespace>/
    └── ssl_structures/
        ├── myvillage.json           ← placement definition
        └── myvillage.tsaphstruct    ← structure file (same stem)
```

### Definition schema

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
| `dimension` | `"minecraft:overworld"` | Target dimension. Use `"*"` for any. |
| `biomes` | all biomes | Whitelist of biome registry keys. Empty = no restriction. |
| `y_placement` | `"surface"` | `"surface"`, `"ocean_floor"`, or `"absolute"` |
| `y_offset` | `0` | Added to computed Y. Negative buries the structure. In `"absolute"` mode, this is the literal world Y. |
| `frequency` | `0.005` | Per-chunk probability, 0.0–1.0. |
| `salt` | `0` | Per-definition seed modifier — prevents all definitions generating in the same chunks. |

Definitions and their `.tsaphstruct` files are reloaded on server start and `/reload`.

---

## Datapack Multi-Structure Bundles

Multi-structure bundles can also be shipped in datapacks:

```
data/
└── <namespace>/
    └── ssl_multistructs/
        └── mybundle.tsaphmultistruct
```

Bundles in `ssl_multistructs/` are loaded into `MultiStructRegistry` on every datapack reload. A companion JSON is not supported for datapack bundles — weights and counts are fixed in the binary. Generate them in-game with `/tsaph multi spawn` or from code via `StructureLoaderBridge.spawnMultiStruct`.

Loot tables can also be shipped in datapacks:

```
data/<namespace>/ssl_loot/<name>.tsaphloot
```

---

## Loot Table System

Structure containers can be assigned loot. Two backends are supported and can be mixed freely:

### Vanilla loot tables

References any Minecraft or datapack loot table by its full registry key. The container fills itself on first player open.

```java
LootTableRef.vanilla("minecraft:chests/simple_dungeon")
```

### TsaphLoot tables

SSL's own pool-based engine. A synthetic key (`ssl:tsaphloot/<n>`) is written to the container and intercepted on first open. Tables load from three sources in order, with later sources overriding earlier ones:

1. Bundled (mod jar)
2. Datapack (`data/<namespace>/ssl_loot/`)
3. World-specific (`<worldSave>/data/ssl_loot/`)

---

## Applying Loot to Structure Chests

### At export time (recommended)

Containers in the selection already carrying a `LootTable` NBT tag are captured into the `.tsaphstruct` v2 LOOT REFS section at export time. `loader.place(...)` applies them automatically — no extra caller code needed.

### Programmatically

```java
ITsaphLootEngine engine = StructureLoaderBridge.getLootEngine();
BlockPos pos = new BlockPos(x, y, z);

// Tag for deferred population (fills on first player open)
engine.applyLootTag(world, pos,
    LootTableRef.vanilla("minecraft:chests/simple_dungeon"),
    world.random.nextLong());

// Tag with a TsaphLoot table
engine.applyLootTag(world, pos, LootTableRef.tsaphloot("dungeon_chest"), 0L);

// Populate immediately
engine.populate(world, pos, LootTableRef.tsaphloot("dungeon_chest"));
```

---

## The `TsaphLootBuilder` Fluent Builder

Constructs `TsaphLootTable` instances in code without writing JSON:

```java
TsaphLootTable table = TsaphLootBuilder.create("my_chest")
    .pool(p -> p
        .rolls(3, 6)
        .item("minecraft:bread")   .weight(30).count(1, 4).add()
        .item("minecraft:diamond") .weight(5) .count(1, 3).add()
        .empty(20)
    )
    .pool(p -> p
        .rolls(0, 1)
        .item("minecraft:enchanted_book")
            .weight(10).enchant("minecraft:mending", 1).add()
        .item("minecraft:diamond_sword")
            .weight(4).enchant("minecraft:sharpness", 1, 4).named("Ancient Blade").add()
    )
    .build();
```

| Method | Description |
|--------|-------------|
| `.weight(n)` | Relative probability weight |
| `.count(n)` | Fixed stack size |
| `.count(min, max)` | Random stack size |
| `.enchant(id, level)` | Fixed-level enchantment |
| `.enchant(id, min, max)` | Random-level enchantment |
| `.nbt(snbt)` | Merge raw SNBT onto the item |
| `.named(text)` | Set display name without writing SNBT |
| `.add()` | Commit entry and return to pool builder |

---

## The `.tsaphloot` Format Reference

```json
{
  "name": "dungeon_chest",
  "comment": "Optional note — ignored at runtime",
  "pools": [
    {
      "rolls": { "min": 3, "max": 6 },
      "entries": [
        { "item": "minecraft:bread",  "weight": 30, "count": { "min": 1, "max": 4 } },
        { "item": "minecraft:diamond","weight": 5,  "count": { "min": 1, "max": 3 } },
        { "type": "empty",            "weight": 20 }
      ]
    },
    {
      "rolls": { "min": 0, "max": 1 },
      "entries": [
        {
          "item": "minecraft:enchanted_book",
          "weight": 10,
          "enchantments": [ { "id": "minecraft:mending", "level": 1 } ]
        }
      ]
    }
  ]
}
```

`count` and `level` accept either a fixed integer or `{"min": N, "max": N}`. An entry with `"type": "empty"` generates no item but participates in the weight total to lower effective fill rate. The `name` field must match the file stem.

---

## Bundled Loot Tables

| Table | Description |
|-------|-------------|
| `dungeon_chest` | Three-pool generic dungeon loot |
| `armory_chest` | Weapons, armour, ammunition |
| `library_chest` | Books, paper, enchanted books |
| `temple_chest` | Gold, emeralds, artefacts |
| `ancient_ruins_chest` | Degraded equipment, ancient rarities |
| `generic_chest` | Minimal fallback |

Override any table per-world by placing a `.tsaphloot` file of the same name in `<worldSave>/data/ssl_loot/`.

---

## API Quick Reference

### Entry points

| Class | Purpose |
|-------|---------|
| `StructureLoaderBridge` | Single gateway to all internal implementations |
| `StructureLoaderBridge.getLoader()` | `IStructureLoader` — load and place `.tsaphstruct` |
| `StructureLoaderBridge.getExporter()` | `IStructureExporter` — export from world |
| `StructureLoaderBridge.getLootEngine()` | `ITsaphLootEngine` — loot application |
| `StructureLoaderBridge.getQueue(world)` | `StructureQueue` — deferred placement queue |
| `StructureLoaderBridge.definitions()` | All registered datapack `StructureDefinition` instances |
| `StructureLoaderBridge.loadMultiStruct(path)` | Load a `.tsaphmultistruct` bundle |
| `StructureLoaderBridge.saveMultiStruct(bundle, path)` | Write a bundle to disk |
| `StructureLoaderBridge.spawnMultiStruct(world, bundle, x, z, depth)` | Generate from a bundle |
| `StructureLoaderBridge.beginSession(uuid)` | Start a multi-struct building session |
| `StructureLoaderBridge.getSession(uuid)` | Retrieve an active session |
| `StructureLoaderBridge.endSession(uuid)` | End and remove a session |

### Key types

| Type | Description |
|------|-------------|
| `StructurePiece` | Decoded single structure |
| `StructurePlacement` | Fluent builder for placing single structures |
| `MultiStructBundle` | Decoded bundle of named pieces |
| `MultiStructPiece` | A single piece within a bundle |
| `ConnectionPoint` | A connector block's local position and facing direction |
| `PieceRole` | `ROOM`, `HALLWAY`, or `PATH` |
| `ConnectionType` | Junction shape for connector pieces |
| `StructureDefinition` | Datapack-driven single structure placement definition |
| `LootTableRef` | Reference to a vanilla or TsaphLoot table |
| `TsaphLootTable` | Parsed `.tsaphloot` table |
| `TsaphLootBuilder` | Fluent builder for loot tables |
| `StructureQueue` | Per-world persistent deferred placement queue |

### `LootTableRef` factories

```java
LootTableRef.vanilla("minecraft:chests/simple_dungeon");
LootTableRef.tsaphloot("dungeon_chest");
```

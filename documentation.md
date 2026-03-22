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
18. [The Loot Barrel Block](#the-loot-barrel-block)
19. [Loot Barrel — SNAPSHOT Mode](#loot-barrel--snapshot-mode)
20. [Loot Barrel — REGISTRY Mode](#loot-barrel--registry-mode)
21. [Loot Barrel — NBT Save Format](#loot-barrel--nbt-save-format)
22. [Loot Barrel — Network Packets](#loot-barrel--network-packets)
23. [Applying Loot to Structure Chests](#applying-loot-to-structure-chests)
24. [The `TsaphLootBuilder` Fluent Builder](#the-tsaphlootbuilder-fluent-builder)
25. [The `.tsaphloot` Format Reference](#the-tsaphloot-format-reference)
26. [Bundled Loot Tables](#bundled-loot-tables)
27. [API Quick Reference](#api-quick-reference)

---

## Overview

Sapphics Structure Library (SSL) is a high-performance structure engine for Fabric mods. It supports two structure formats:

- **`.tsaphstruct`** — a single structure piece, equivalent to a vanilla structure block save but with no size ceiling, bit-packed storage, and built-in loot references.
- **`.tsaphmultistruct`** — a bundle of named, tagged pieces with weights and connection points, consumed by the procedural engine to generate randomised dungeons, villages, and multi-part mega-structures.

Key design goals:

- **No size ceiling.** Both formats support structures far beyond vanilla's 48³ limit.
- **Chunk-safe placement.** Blocks in unloaded chunks are queued to disk and applied when the chunk loads, including across server restarts.
- **Efficient updates.** A two-pass system ensures fences connect, fluids flow, and redstone resolves correctly without touching every block.
- **Flexible loot.** Containers can be driven by vanilla loot tables, custom `.tsaphloot` tables, or the in-world **Loot Barrel** block — a structure-authoring tool that bakes weighted item pools directly into a structure file without writing any JSON.
- **Datapack-driven generation.** Both formats can be loaded from datapacks with no Java code required.
- **Fully obfuscated internals.** Only the `com.sapphic.ssl.api` package is public; everything else is obfuscated in production builds.

---

## In-Game Tools

Three items are available in the **Structure Library** creative tab — the **Selection Wand**, **Connection Point Block**, and **Structure Terrain Block**. The Loot Barrel is a developer tool obtained via `/give` and is intentionally absent from the creative tab.

### Structure Wand
Used to select rectangular regions for export. Renders as a vanilla stick.

- **Right-click a block** → Set Position 1 (first corner)
- **Sneak + Right-click a block** → Set Position 2 (second corner)

Once both corners are set, the wand displays the selection's dimensions and volume in chat, including a warning if it exceeds the vanilla 48³ limit. There is no such limit in SSL — the warning is purely informational.

### Connection Point Block
A directional full block used to mark the open ends of a multi-structure piece. The block faces the direction the player is looking when placed (opposite of player facing). Place it at every opening of a hallway, path, or room that should connect to an adjacent piece.

Connection point blocks are **automatically removed** at generation time and replaced with the floor block beneath them, so they never appear in the final world.

### Structure Terrain Block
**Block ID:** `sapphics-structure-library:structure_terrain`

A glass-textured transparent placeholder used inside structure pieces to mark positions that should defer entirely to the world. When the loader encounters a Structure Terrain block during Pass 1, it **skips that position completely** — whatever the world already has there (stone, dirt, air, water) is left undisturbed.

Typical uses:
- **Hillside corridors** — fill lower rows with Structure Terrain so natural rock fills in behind walls rather than being overwritten with air.
- **Organic dungeon bases** — place under floors so rooms that generate underground inherit the local stone type.
- **Uneven ground** — use in the below-grade portion of a foundation so the structure adapts to slopes without leaving floating blocks or unwanted fills.

Structure Terrain is captured normally in the `.tsaphstruct` palette; the skip logic is in the loader. The block is never written to the generated world.

### Loot Barrel
**Block ID:** `sapphics-structure-library:loot_barrel`  
**Obtain via:** `/give @s sapphics-structure-library:loot_barrel` (not in the creative tab)

A structure-authoring block used to define chest loot inline, without writing any JSON. Place it anywhere in a structure build in place of a chest. When the structure is generated in the world, `SmartLootEngine` replaces every Loot Barrel with a vanilla chest and fills it according to the barrel's configuration. The barrel itself never appears in the generated world.

Open the Loot Barrel with right-click to configure it. Two modes are available (toggle with the **SNAPSHOT** / **REGISTRY** buttons at the top of the UI):

- **SNAPSHOT** — place items in the 27-slot inventory; each item type becomes a weighted loot entry. Weights default to total stack count and can be fine-tuned in the panel. The chest is filled immediately at generation time.
- **REGISTRY** — type a `.tsaphloot` table name or vanilla loot-table key into the text field. The chest is filled lazily on first player open.

See [The Loot Barrel Block](#the-loot-barrel-block) for the full reference.

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
│  BLOCK-ENTITY DATA  (EntityCount entries)               │
│    [4]  rx / ry / rz   int (local space, 0-based)       │
│    [2]  TypeLen        short (unsigned)                  │
│    [N]  TypeId         UTF-8 (block entity type id)     │
│    [4]  NbtLen         int                              │
│    [N]  NbtBytes       GZIP-compressed NbtCompound      │
├─────────────────────────────────────────────────────────┤
│  LOOT REFS  (v2 only, LootRefCount entries)             │
│    [4]  LinearIndex    int  ((ry*sZ+rz)*sX+rx)          │
│    [1]  RefType        byte (0x00=VANILLA 0x01=TSAPHLOOT)│
│    [2]  IdLen          short (unsigned)                  │
│    [N]  Id             UTF-8 (vanilla key or table name) │
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
Optional<MultiStructBundle> bundle = StructureLoaderBridge.getMultiStruct("my_village");

// All registered bundle names
Set<String> names = StructureLoaderBridge.multiStructNames();

// Force-reload from a directory (e.g. after writing a new file)
StructureLoaderBridge.reloadMultiStructRegistry(sslDir);

// Generate
StructureLoaderBridge.spawnMultiStruct(world, bundle, x, z,
        ProceduralEngine.DEFAULT_MAX_DEPTH);
```

The engine selects a random ROOM piece as the seed, places it at (x, z), then chains connector pieces (PATH for surface worlds, HALLWAY for Nether/End) outward from each connection point until `maxDepth` is reached or no eligible connectors remain. All open ends are capped with ROOM pieces.

**Applying companion JSON overrides from code:**

```java
// withOverrides returns a new bundle — original is unchanged.
// Map key = piece UUID string; value = int[]{weight, maxCount}
Map<String, int[]> overrides = Map.of(
    piece.id(), new int[]{ 5, 3 }
);
MultiStructBundle adjusted = bundle.withOverrides(overrides);
StructureLoaderBridge.spawnMultiStruct(world, adjusted, x, z, 6);
```

**Session management from code:**

```java
UUID playerUuid = player.getUuid();

// Check for an existing session before starting
if (!StructureLoaderBridge.hasSession(playerUuid)) {
    WandSession session = StructureLoaderBridge.beginSession(playerUuid);

    // Add a piece (with pre-decoded StructurePiece)
    // Connection points are extracted from connector blocks automatically
    session.addPiece("house_a", PieceRole.ROOM, ConnectionType.NONE, 10, -1, piece);

    // Build the bundle
    MultiStructBundle bundle = session.build("my_village");

    // End this session
    StructureLoaderBridge.endSession(playerUuid);
}

// On server stop — end all sessions (called automatically by SapphicsStructureLibrary)
StructureLoaderBridge.endAllSessions();
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

### Two entry types

`PendingPlacement` has two distinct entry types. Both are stored in the same queue and serialised in the same binary format:

**Block placement entry** (`isPendingUpdate() == false`) — carries `blockStateId`, optional block-entity NBT bytes, and an optional `LootTableRef`. Applied when the target chunk loads: the block is placed and any loot ref is stamped onto the container.

**Update-only entry** (`isPendingUpdate() == true`) — no block to place. The block was already written to the world in Pass 1 but its neighbour-update sweep was deferred because one or more face-adjacent chunks were unloaded. When the last adjacent chunk loads, the loader calls `world.updateNeighbors(pos, block)` and schedules fluid ticks. This is what makes fences connect, stairs orient correctly, and fluids flow across chunk boundaries.

Create an update-only entry via the static factory:

```java
PendingPlacement trigger = PendingPlacement.updateOnly(worldKey, x, y, z);
```

The queue is drained by two hooks:

- **`ChunkGeneratorMixin`** — fires after `generateFeatures` for freshly-generated chunks.
- **`ServerChunkEvents.CHUNK_LOAD`** — fires for every chunk load, including from disk on server restart. This is what makes deferred placements resolve after a world reload without requiring chunk regeneration.

Both hooks drain the target chunk and all 8 surrounding chunks, allowing deferred update-only entries on chunk borders to fire as soon as all their neighbours are loaded.

### `StructureQueue` API

```java
StructureQueue queue = StructureLoaderBridge.getQueue(world);

// Enqueue a deferred placement or update-only trigger
queue.enqueue(placement);

// Drain all entries for a specific chunk (removes and returns them)
List<PendingPlacement> pending = queue.drain(chunkX, chunkZ);
// or using ChunkPos:
List<PendingPlacement> pending = queue.drain(chunkPos);

// Inspect
int count   = queue.size();
boolean empty = queue.isEmpty();

// Persist to disk (called automatically on server stop)
queue.save();

// Reload from disk (replaces in-memory state)
queue.load();
```

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
  "salt":        12345,
  "loot_overrides": {
    "minecraft:chest": "dungeon_chest"
  }
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
| `loot_overrides` | *(absent)* | Optional map of block-entity type IDs to a `.tsaphloot` table name. Overrides any loot ref baked into the structure file at placement time. Keys are block-entity type registry IDs (e.g. `"minecraft:chest"`); values are tsaphloot table names. |

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

Structure containers can be assigned loot. Three approaches are available and can be mixed freely within a single structure:

### Loot Barrel block (recommended for new structures)

Place a **Loot Barrel** in the build instead of a chest. Configure its weights in-game, then export. No JSON required. See [The Loot Barrel Block](#the-loot-barrel-block).

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

## The Loot Barrel Block

**Block ID:** `sapphics-structure-library:loot_barrel`

The Loot Barrel is a structure-authoring block. At generation time, `SmartLootEngine` detects every Loot Barrel in the placed structure, replaces it with a vanilla chest, and fills that chest with loot derived from the barrel's configuration. The barrel itself never appears in the generated world.

### Modes

| Mode | How loot is determined |
|---|---|
| **SNAPSHOT** | Weights baked from the 27-slot inventory. Chest filled immediately at generation time. |
| **REGISTRY** | String key pointing to a `.tsaphloot` table or vanilla loot-table. Chest filled lazily on first player open. |

Toggle between modes using the **SNAPSHOT** / **REGISTRY** buttons at the top of the barrel UI.

---

## Loot Barrel — SNAPSHOT Mode

### Authoring

Open the Loot Barrel and place items in its 27 slots. Each distinct item type becomes one loot entry. The weight of each entry defaults to the **total stack count** of that item across all 27 slots.

**Example:**
20× Iron Ingot + 1× Diamond → 20:1 weight ratio. Iron Ingot has a ~95% chance per roll; Diamond ~5%.

### Weight overrides

The weight panel shows all item types in the inventory with their computed weights. Fine-tune without counting out exact stacks:

- Click **[−]** / **[+]** to adjust a weight by 1.
- Scroll the mouse wheel over a row to adjust it.
- **Right-click** either button to reset that item back to its stack-count default.
- A small amber dot next to a weight value indicates an active override.

Overrides are saved in the barrel's NBT under `SslWeightOverrides` and survive save/reload. They take precedence over stack counts at generation time.

### Generation behaviour

`SmartLootEngine.compileSnapshot`:
1. Reads the 27-slot inventory from the structure-file NBT.
2. Groups stacks by item registry ID and sums counts.
3. Applies weight overrides (override wins over stack count where present).
4. Builds an in-memory `TsaphLootTable` with a single pool.
5. Sets pool rolls = `min(distinct item types, 8)`.
6. Calls `TsaphLootEngine.populateDirect` — chest is filled immediately.
7. Clears the chest's loot-table key so the chest never re-rolls on subsequent opens.

---

## Loot Barrel — REGISTRY Mode

### Authoring

Switch to REGISTRY mode and type a key into the text field:

| Key format | Resolves to |
|---|---|
| `dungeon_chest` | A named `.tsaphloot` table in `LootRegistry` |
| `ssl:tsaphloot/dungeon_chest` | Same — the prefix is stripped automatically |
| `minecraft:chests/simple_dungeon` | A vanilla loot-table key |

Any key containing `:` that does **not** start with `ssl:` is treated as a vanilla key. Everything else is treated as a tsaphloot table name.

### Generation behaviour

`SmartLootEngine.compileRegistry` attaches the resolved `LootTableRef` to the chest via `TsaphLootEngine.applyLootTag`. The chest fills lazily on first player open via the normal `LootableInventoryMixin` path, matching vanilla chest-loot behaviour.

Use REGISTRY mode when loot should be shared and updatable across many structures — editing the `.tsaphloot` file updates all future chest opens without re-exporting any structure file.

### Important: clearing the key does not revert mode

Sending `SetRegistryKey` with an empty string sets the stored key to `null` but leaves the barrel in REGISTRY mode. To switch back to SNAPSHOT, use the SNAPSHOT button — it sends a separate `SetMode` packet.

---

## Loot Barrel — NBT Save Format

The barrel's block-entity NBT:

```
{
  "SslBarrelMode":      0 | 1,          // 0 = SNAPSHOT, 1 = REGISTRY
  "SslRegistryKey":     "string",       // REGISTRY only; absent in SNAPSHOT
  "Items":              [ ... ],        // standard 27-slot inventory (SNAPSHOT)
  "SslWeightOverrides": {               // per-item weight overrides (SNAPSHOT)
    "minecraft:diamond":    5,
    "minecraft:iron_ingot": 20
  }
}
```

`SslWeightOverrides` is always written (empty compound if no overrides exist) and always read back — fully round-trip safe across save/reload.

---

## Loot Barrel — Network Packets

All packets are in `LootBarrelPackets`, registered from `SapphicsStructureLibrary.onInitialize()`. The `SyncWeights` client receiver is in `SapphicsStructureLibraryClient.onInitializeClient()` because `ClientPlayNetworking` is only available on the client physical side — never add `ClientPlayNetworking` calls to `LootBarrelPackets`.

### Client → Server (C2S)

| Packet | Payload | Effect |
|---|---|---|
| `SetMode` | `byte mode` | Switches the barrel between SNAPSHOT (0) and REGISTRY (1). |
| `SetRegistryKey` | `String key` (max 256 chars) | Writes the key. Switches to REGISTRY if non-empty; sets key to `null` if empty but **does not revert mode**. |
| `SetWeight` | `String itemId` (max 256 chars), `int weight` | Sets an override. Weight ≤ 0 removes the override. Server immediately sends a `SyncWeights` response. |
| `RequestSync` | *(empty)* | Asks the server to push current overrides. Sent automatically when the screen opens. |

### Server → Client (S2C)

| Packet | Payload | When sent |
|---|---|---|
| `SyncWeights` | `Map<String, Integer> overrides` | On `RequestSync` (screen open) and after every `SetWeight`. |

**`SyncWeights` wire format** — a manual `writeInt(count)` prefix followed by `(writeString(itemId, 256), writeInt(weight))` pairs. Not a standard map codec; read the count first, then loop.

The client receiver calls `barrel.clearWeightOverrides()`, repopulates from the packet, then calls `screen.invalidateRowCache()` to force `rebuildRows()` on the next frame.

---

## Applying Loot to Structure Chests

### Via Loot Barrel (recommended for structure authoring)

Place a **Loot Barrel** in your build instead of a chest and configure it in-game. On export, the barrel's inventory and weight data are saved into the `.tsaphstruct` file as block-entity NBT. At generation time, `SmartLootEngine` replaces the barrel with a filled chest automatically — no code required. See [The Loot Barrel Block](#the-loot-barrel-block).

### At export time (legacy)

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

// Populate immediately (no deferred open required)
engine.populate(world, pos, LootTableRef.tsaphloot("dungeon_chest"));

// Resolve a table by name (returns empty if not loaded)
Optional<TsaphLootTable> table = engine.resolve("dungeon_chest");
```

### Programmatic export

```java
IStructureExporter exporter = StructureLoaderBridge.getExporter();
StructureBoundingBox box = StructureBoundingBox.fromCorners(x1, y1, z1, x2, y2, z2);
exporter.export(world, box, Path.of("path/to/output.tsaphstruct"));
// The .tsaphstruct extension is appended automatically if omitted.
// Throws IllegalStateException if any chunk in the selection is unloaded.
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
| `StructureLoaderBridge.processChunkQueue(world, chunkPos)` | Drain the queue for a chunk and all 8 neighbours |
| `StructureLoaderBridge.processChunkDefinitions(world, chunkPos)` | Run datapack-definition placement checks for a chunk |
| `StructureLoaderBridge.onServerStopping()` | Persist all queue caches to disk |
| `StructureLoaderBridge.loadMultiStruct(path)` | Load a `.tsaphmultistruct` bundle from disk |
| `StructureLoaderBridge.saveMultiStruct(bundle, path)` | Write a bundle to disk (also writes companion JSON) |
| `StructureLoaderBridge.spawnMultiStruct(world, bundle, x, z, depth)` | Generate from a bundle |
| `StructureLoaderBridge.getMultiStruct(name)` | Retrieve a registered bundle by name (`Optional`) |
| `StructureLoaderBridge.multiStructNames()` | All registered bundle names (`Set<String>`) |
| `StructureLoaderBridge.reloadMultiStructRegistry(sslDir)` | Reload all bundles from a directory |
| `StructureLoaderBridge.beginSession(uuid)` | Start a multi-struct building session |
| `StructureLoaderBridge.getSession(uuid)` | Retrieve an active session (`Optional`) |
| `StructureLoaderBridge.hasSession(uuid)` | `true` if the player has an active session |
| `StructureLoaderBridge.endSession(uuid)` | End and remove a session |
| `StructureLoaderBridge.endAllSessions()` | End all sessions (call on server stop) |

### Key types

| Type | Description |
|------|-------------|
| `StructurePiece` | Decoded single structure — palette, packed block indices, block-entity NBT map, loot ref map |
| `StructureBoundingBox` | Axis-aligned bounding box for a structure; supports coordinates beyond vanilla 48³ |
| `BlockEntry` | One entry in a structure's block-state palette — index + full block-state ID string |
| `RegionMarker` | 512×512-block column index entry used as a fast-fail gate during loading |
| `StructurePlacement` | Fluent builder for placing single structures |
| `MultiStructBundle` | Decoded bundle of named pieces, partitioned by role |
| `MultiStructPiece` | A single piece within a bundle |
| `ConnectionPoint` | A connector block's local position and facing direction (record) |
| `PieceRole` | `ROOM`, `HALLWAY`, or `PATH` |
| `ConnectionType` | Junction shape for connector pieces |
| `StructureDefinition` | Datapack-driven single structure placement definition |
| `StructureQueue` | Per-world persistent deferred placement queue |
| `PendingPlacement` | A single queued entry — either a deferred block placement or an update-only neighbour trigger |
| `LootTableRef` | Reference to a vanilla or TsaphLoot table |
| `LootRange` | An inclusive integer range `[min, max]` — used for roll counts, item counts, enchantment levels |
| `LootEnchantment` | One enchantment with an ID and a `LootRange` level |
| `TsaphLootTable` | Parsed `.tsaphloot` table |
| `TsaphLootPool` | One pool inside a `TsaphLootTable` — rolls + weighted entries |
| `TsaphLootEntry` | One weighted item entry inside a pool (or an empty-roll entry) |
| `TsaphLootBuilder` | Fluent builder for constructing `TsaphLootTable` instances in code |
| `LootBarrelBlockEntity` | Block entity for the in-world Loot Barrel authoring block |
| `LootBarrelEntry` | One entry in a `LootBarrelBlockEntity` palette — item + float weight (see below) |

### `StructureBoundingBox`

```java
StructureBoundingBox box = StructureBoundingBox.fromCorners(x1, y1, z1, x2, y2, z2);
// Corners are normalised — min/max order doesn't matter

int w = box.sizeX();         // inclusive width
int h = box.sizeY();
int d = box.sizeZ();
long vol = box.volume();     // total block count (long — supports huge boxes)

boolean oversized = box.exceedsVanillaLimit(); // true if any axis > 48
```

### `StructurePiece` inspection API

```java
StructurePiece piece = loader.load(path);

// Bounding box
StructureBoundingBox bounds = piece.bounds();

// Palette — list of all distinct block states in this structure
List<BlockEntry> palette = piece.palette();

// Block at local coordinates
int paletteIdx     = piece.paletteIndexAt(rx, ry, rz);
BlockEntry entry   = piece.blockEntryAt(rx, ry, rz);
String blockStateId = entry.blockStateId();  // e.g. "minecraft:oak_stairs[facing=north,...]"

// Linear index used as key in both sparse maps
int linearIdx = piece.linearIndex(rx, ry, rz);  // (ry*sZ+rz)*sX+rx

// Block-entity NBT at a position (null if none)
byte[] nbtBytes = piece.blockEntityNbtAt(rx, ry, rz);  // GZIP-compressed NbtCompound

// Loot ref at a position (null if none)
LootTableRef ref = piece.lootRefAt(rx, ry, rz);
```

### `LootRange`

```java
LootRange fixed = LootRange.fixed(3);          // always 3
LootRange range = LootRange.of(1, 6);          // random 1–6 inclusive
int value = range.evaluate(random);            // draw a value
JsonElement json = range.toJson();             // compact: 3 or {"min":1,"max":6}
```

### `LootBarrelEntry`

`LootBarrelEntry` is the palette entry type used internally by `LootBarrelBlockEntity` to store item–weight pairs. Weights are floats clamped to **0.01–2.00** and stored as integers (weight × 100) in NBT for compactness. A barrel can hold at most **16 entries**.

```java
// Weight constants
float min = LootBarrelEntry.MIN_WEIGHT;   // 0.01f
float max = LootBarrelEntry.MAX_WEIGHT;   // 2.00f
int   maxEntries = LootBarrelEntry.MAX_ENTRIES; // 16

// Construction
LootBarrelEntry entry = new LootBarrelEntry(itemStack, 1.5f);
entry.setWeight(0.25f);           // clamped automatically
int asInt = entry.weightAsInt();  // 25  (weight × 100)
float back = LootBarrelEntry.weightFromInt(25); // 0.25f

// NBT round-trip
NbtCompound tag = entry.writeNbt(registries);
LootBarrelEntry loaded = LootBarrelEntry.readNbt(tag, registries);
```

Note: the in-game SNAPSHOT weight panel uses integer counts (from item stack totals) overridden by `SslWeightOverrides` in the barrel's block-entity NBT, not `LootBarrelEntry` float weights. `LootBarrelEntry` is the underlying storage type — its float weight range is distinct from the integer weights shown in the UI.

### `LootTableRef` factories

```java
LootTableRef.vanilla("minecraft:chests/simple_dungeon");
LootTableRef.tsaphloot("dungeon_chest");

// Inspect
ref.type();        // RefType.VANILLA or RefType.TSAPHLOOT
ref.id();          // the key/name string
ref.isVanilla();
ref.isTsaphloot();

// Wire values (used in .tsaphstruct LOOT REFS section)
// RefType.VANILLA    → 0x00
// RefType.TSAPHLOOT  → 0x01
```

### Bundled loot table classpath location

```
resources/data/sapphics-structure-library/tsaphloot/<name>.tsaphloot
```

Constant: `TsaphLootTable.RESOURCE_DIR = "data/sapphics-structure-library/tsaphloot/"`

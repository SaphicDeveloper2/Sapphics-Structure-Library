# Sapphics Structure Library — Beginner Guide

Welcome to **Sapphics Structure Library (SSL)** — a powerful mod that lets you save, load, and generate custom structures in Minecraft without size limits.

This guide will teach you the basics in just a few minutes.

---

# 🧱 What This Mod Does

SSL lets you:

- Save builds as structure files
- Load them anywhere instantly
- Create procedural structures (like villages or dungeons)
- Add custom loot without writing JSON
- Generate structures automatically using datapacks

---

# 🚀 Quick Start (5 Minutes)

## 1. Get the Structure Wand

Open the creative inventory and find:

- **Structure Wand**

---

## 2. Select Your Build

1. Right-click a block → sets Position 1  
2. Sneak + Right-click another block → sets Position 2  

You’ve now selected your structure.

---

## 3. Save the Structure

```
/tsaph save my_structure
```

Your structure is now saved.

---

## 4. Load the Structure

```
/tsaph load my_structure ~ ~ ~
```

This will place the structure at your current location.

---

✅ Done! You’ve just saved and loaded your first structure.

---

# 🏗️ Creating Better Structures

## Avoid Floating Builds

If your structure floats when placed:

- Make sure you **don’t include empty air under it** when selecting
- Or place it slightly lower manually

---

## Use Structure Terrain Blocks

- These blocks **don’t place anything**
- They let the world generate naturally instead

Use them for:
- Underground builds
- Hillside structures
- Natural blending

---

# 🔗 Multi-Structure (Procedural Generation)

This lets you create things like:
- Villages
- Dungeons
- Road systems

## Step 1 — Start a Session

```
/tsaph multi begin
```

---

## Step 2 — Build a Piece

Build something like:
- A house
- A corridor
- A road

---

## Step 3 — Add Connection Points

Use the **Connection Point Block** at entrances.

👉 Place them facing OUTWARD

Example:

```
[ROOM]
   |
[CONNECTION]
```

---

## Step 4 — Select and Add It

```
/tsaph multi add house ROOM
```

---

## Step 5 — Save the Bundle

```
/tsaph multi save my_village
```

---

## Step 6 — Spawn It

```
/tsaph multi spawn my_village ~ ~
```

---

🎉 Your procedural structure is now generating!

---

# 🎁 Adding Loot (Easy Way)

## Use the Loot Barrel

Get it with:

```
/give @s sapphics-structure-library:loot_barrel
```

---

## How It Works

1. Place the Loot Barrel in your structure
2. Put items inside
3. Save the structure

When generated:
- The barrel becomes a chest
- Loot is automatically added

---

## Example

- 20 Iron = common
- 1 Diamond = rare

No JSON needed.

---

# 📦 Useful Commands

## Structures

```
/tsaph save <name>
/tsaph load <name> <x> <y> <z>
/tsaph list
```

---

## Multi-Structures

```
/tsaph multi begin
/tsaph multi add <name> <role>
/tsaph multi save <name>
/tsaph multi spawn <name> <x> <z>
```

---

## Loot

```
/tsaph loot list
/tsaph loot reload
```

---

# 🧠 Tips

- You can make **huge structures** (no size limit)
- Structures work across **unloaded chunks**
- Everything saves and loads even after restarting the world
- Use multiple small pieces for better procedural generation

---

# ⚠️ Common Mistakes

❌ Forgetting connection points  
→ Structures won’t connect

❌ Wrong direction on connection blocks  
→ Pieces won’t attach

❌ Selecting too much air  
→ Floating builds

---

# 📚 What Next?

Once you understand the basics, check the full developer documentation for:

- Java API usage
- Datapack generation
- Advanced loot systems
- Internal formats

---

# 💬 Final Notes

This mod is designed to be:

- Fast ⚡
- Flexible 🧩
- Easy to use 🧠

Start simple, experiment, and build something massive.

Happy building!

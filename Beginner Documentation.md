# 🏛️ Sapphics Structure Library (SSL) — Beginner Guide

Welcome to the **Sapphics Structure Library**. SSL is a high-performance structure engine designed to bypass the limitations of vanilla Minecraft. Whether you're building a single house or a procedurally generated empire, SSL handles the heavy lifting across infinite distances and unloaded chunks.

---

## 🧱 What is SSL?

SSL isn't just a clipboard; it’s a world-generation powerhouse.
* **Infinite Scale:** Save and load builds with no size limits.
* **Procedural Mastery:** Generate complex dungeons, villages, and road networks.
* **Stability:** Works seamlessly across unloaded chunks without "cutting off" builds.
* **Zero-JSON Loot:** Create custom loot tables physically in-game.
* **Developer Ready:** Fully compatible with datapacks for automatic world-gen.

---

## 🚀 Quick Start (5-Minute Setup)

### 1. Get the Tools
Grab the **Structure Wand** from the creative inventory.

### 2. Select Your Build
1.  **Right-Click** a block to set **Position 1**.
2.  **Sneak + Right-Click** a block to set **Position 2**.
*Your selection area is now defined.*

### 3. Save & Load
* **To Save:** `/tsaph save <name>`
* **To Load:** `/tsaph load <name> ~ ~ ~`

---

## 🔗 Multi-Structure (Procedural Generation)

The real power of SSL lies in **Connection Points**. Think of these as "sockets" that allow different structure files to snap together like LEGO bricks.

### The 6-Step Workflow:
1.  **Initialize:** Start a session with `/tsaph multi begin`.
2.  **Build:** Create a module (e.g., a room, a hallway, or a tower).
3.  **Place Sockets:** Put **Connection Point Blocks** at the exits. 
    * *Note: The arrows must face **OUTWARD** toward where the next piece should attach.*
4.  **Register:** Add the piece to your current session: `/tsaph multi add <name> <role>`
5.  **Finalize:** Save the entire logic bundle: `/tsaph multi save <bundle_name>`
6.  **Generate:** Spawn your procedural creation: `/tsaph multi spawn <bundle_name> ~ ~`

---

## 🎁 Loot Made Easy (The "Loot Barrel")

Forget writing complex JSON files. SSL lets you design loot visually.

1.  Give yourself a barrel: `/give @s sapphics-structure-library:loot_barrel`
2.  **Place it** inside your structure.
3.  **Fill it** with items. (e.g., 64 Cobblestone for a common drop, 1 Diamond for a rare drop).
4.  **Save** your structure normally.

When SSL generates that structure, the barrel transforms into a chest and distributes the items based on the quantities you provided.

---

## 🛠️ Command Reference

| Action | Command |
| :--- | :--- |
| **Save Structure** | `/tsaph save <name>` |
| **Load Structure** | `/tsaph load <name> <x> <y> <z>` |
| **Start Multi-Session**| `/tsaph multi begin` |
| **Add to Multi** | `/tsaph multi add <name> <role>` |
| **Save Multi-Bundle** | `/tsaph multi save <bundle_name>` |
| **Spawn Multi** | `/tsaph multi spawn <bundle_name> <x> <z>` |

---

## 💡 Pro Tips for Better Builds

* **Avoid the "Floating" Look:** When selecting your build, ensure you include the foundation or one layer of dirt/stone to help it blend into the terrain.
* **Use Terrain Blocks:** Use SSL's special **Terrain Blocks** in your save files. These act as "transparent" blocks that allow the natural world terrain to remain visible through the structure—perfect for hillsides or ruins.
* **Modular is Better:** Instead of saving one giant city, save 10 different houses and use the Multi-Structure system to generate a unique city every time.

---

## ⚠️ Troubleshooting
* **My pieces aren't connecting:** Check the rotation of your Connection Point Blocks. They must face the direction of the expansion.
* **My build is flying:** You likely selected too many "Air" blocks beneath your build. Re-select and save with a tighter base.

---

**Happy Building!** SSL is built to be fast, flexible, and intuitive. We can't wait to see what massive worlds you create.

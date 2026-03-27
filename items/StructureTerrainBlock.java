package com.sapphic.ssl.items;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TransparentBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * The SSL Structure Terrain block.
 *
 * <h2>Purpose</h2>
 * <p>A glass-textured, fully transparent placeholder used inside structure pieces to
 * mark positions that should <em>defer to whatever the world already contains</em>.
 * When the {@link com.sapphic.ssl.internal.StructureLoaderImpl} encounters a
 * Structure Terrain block during Pass 1, it skips placement entirely — the world
 * block at that position (stone, dirt, air, water, whatever terrain generated there)
 * is left completely undisturbed.
 *
 * <h2>Typical use cases</h2>
 * <ul>
 *   <li><b>Floors that blend into hillsides</b> — fill the lower rows of a corridor
 *       with Structure Terrain so natural rock fills in behind walls where the
 *       hillside intersects the structure.</li>
 *   <li><b>Organic dungeon bases</b> — place Structure Terrain under room floors
 *       so rooms that generate partly underground inherit the local stone type.</li>
 *   <li><b>Surface structures with uneven ground</b> — use Structure Terrain in
 *       the "below ground" portion of a house foundation so it adapts to the slope
 *       without leaving air pockets or unwanted fills.</li>
 * </ul>
 *
 * <h2>Visual design</h2>
 * <p>Inherits from {@link TransparentBlock} and uses vanilla glass as its model
 * texture, making it clearly distinguishable during authoring while hinting at its
 * "see-through / passthrough" semantics at generation time.  A tinted cyan-teal
 * tint distinguishes it from plain glass in the world.
 *
 * <h2>Placement rules</h2>
 * <ul>
 *   <li>The block <em>is</em> captured in the {@code .tsaphstruct} palette normally —
 *       the writer treats it like any other block.  The skip logic lives entirely in
 *       the loader so round-trip export→load→place works correctly.</li>
 *   <li>Structure Terrain blocks are <em>never</em> written into the generated
 *       world — if the world position is air, it stays air; if it is stone, it stays
 *       stone.  The structure simply has no opinion about that slot.</li>
 *   <li>LootBarrel compilation, connector cleanup, and all other Pass-1 special
 *       cases run <em>before</em> the Structure Terrain check, so they take priority
 *       over terrain deference if there is ever overlap.</li>
 * </ul>
 *
 * <p>Obtain via {@code /give @s sapphics-structure-library:structure_terrain}.
 * <p><strong>Obfuscated in production builds.</strong>
 */
public class StructureTerrainBlock extends TransparentBlock {

    public StructureTerrainBlock(Settings settings) {
        super(settings);
    }

    // ── Shape ──────────────────────────────────────────────────────────────
    //
    // Full-cube collision and outline shapes — this makes authoring feel solid
    // and ensures the selection wand can target the block face accurately.
    // (TransparentBlock renders with ambient occlusion disabled but keeps full
    // cube shapes by default, so no override is needed here.)

    // ── No block entity, no state properties, no custom drops ─────────────
    // This block is intentionally minimal.  Its only purpose is to occupy a
    // palette slot in the structure file so the loader can recognise it.
}

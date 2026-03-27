package com.sapphic.ssl.api;

/**
 * Controls how SSL handles air blocks during structure placement.
 *
 * <p>By default, air blocks in the structure definition are skipped — the world's
 * existing blocks remain.  This is efficient but means terrain can "bleed through"
 * into structure interiors (e.g. a cave or hillside intersecting an enclosed room).
 *
 * <p>Enabling {@link #FILL_AIR} causes SSL to explicitly place air blocks, clearing
 * the structure's interior volume of any intersecting terrain while leaving the
 * structure's own blocks intact.
 *
 * <h2>When to use each mode</h2>
 *
 * <h3>{@link #SKIP_AIR} (default)</h3>
 * <ul>
 *   <li>Open-air structures (ruins, monuments, towers)</li>
 *   <li>Structures designed to blend with terrain</li>
 *   <li>When performance is critical (fewer block placements)</li>
 * </ul>
 *
 * <h3>{@link #FILL_AIR}</h3>
 * <ul>
 *   <li>Enclosed structures with interior rooms (dungeons, houses, bunkers)</li>
 *   <li>Underground structures where terrain would fill hallways</li>
 *   <li>Any structure where interior air space must be guaranteed</li>
 * </ul>
 *
 * <p>Note: {@code StructureTerrain} blocks are always respected regardless of mode —
 * they indicate "preserve world terrain here" and are never replaced with air.
 */
public enum InteriorFillMode {

    /**
     * Default mode.  Air blocks in the structure definition are skipped.
     *
     * <p>Existing world blocks (terrain, ores, caves) remain where the structure
     * has air.  This is the fastest mode but may cause terrain bleed-through
     * into enclosed interiors.
     */
    SKIP_AIR,

    /**
     * Fill mode.  Air blocks in the structure definition are placed as air.
     *
     * <p>The structure's entire bounding box is "carved out" — any world blocks
     * intersecting air positions in the structure are replaced with air, ensuring
     * clean interior spaces.
     *
     * <p>Structure blocks are placed on top of this air, so the structure itself
     * is never affected — only terrain that would have intruded into air spaces.
     */
    FILL_AIR
}

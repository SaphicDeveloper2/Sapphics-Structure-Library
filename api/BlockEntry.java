package com.sapphic.ssl.api;

import java.util.Objects;

/**
 * An immutable entry in the {@code .tsaphstruct} block-state palette.
 *
 * <p>Each entry maps a human-readable block-state identifier (e.g.
 * {@code "minecraft:stone"} or {@code "minecraft:oak_stairs[facing=north,half=bottom]"})
 * to a compact integer index used in the packed block-data section.
 */
public final class BlockEntry {

    private final int    index;
    private final String blockStateId;

    /**
     * @param index       Zero-based palette index (&lt; {@link SaphStructFormat#MAX_PALETTE}).
     * @param blockStateId Full block-state string, including properties when non-default.
     */
    public BlockEntry(int index, String blockStateId) {
        if (index < 0 || index >= SaphStructFormat.MAX_PALETTE) {
            throw new IllegalArgumentException("Palette index out of range: " + index);
        }
        this.index        = index;
        this.blockStateId = Objects.requireNonNull(blockStateId, "blockStateId");
    }

    /** Zero-based index used in the packed block-data section. */
    public int index()        { return index; }

    /** Full block-state identifier string. */
    public String blockStateId() { return blockStateId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockEntry be)) return false;
        return index == be.index && blockStateId.equals(be.blockStateId);
    }

    @Override public int    hashCode() { return Objects.hash(index, blockStateId); }
    @Override public String toString()  { return "BlockEntry[" + index + "=" + blockStateId + "]"; }
}

package com.sapphic.ssl.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single structure piece inside a {@link MultiStructBundle}.
 *
 * <p>Each piece wraps a fully-decoded {@link StructurePiece} (using the same
 * bit-packed palette encoding as a standalone {@code .tsaphstruct} file) alongside
 * the metadata the procedural engine needs to decide where and how to place it.
 *
 * <p>{@link ConnectionPoint} instances record exactly where
 * {@link com.sapphic.ssl.items.ConnectorBlock}s were placed inside the exported
 * selection — the engine uses these to snap adjacent pieces together and replaces
 * each connector block with the floor block beneath it after placement.
 */
public final class MultiStructPiece {

    /** Unique identifier assigned at session time (UUID string). */
    private final String         id;

    /** Human-readable name given during in-game tagging. */
    private final String         name;

    /** Structural role of this piece in the generation graph. */
    private final PieceRole      role;

    /** Junction shape — only meaningful for {@link PieceRole#HALLWAY} / {@link PieceRole#PATH}. */
    private final ConnectionType connectionType;

    /** Relative spawn weight for weighted-random selection. */
    private final int            weight;

    /**
     * Maximum number of times this piece may appear in a single generated structure.
     * {@code -1} means unlimited.
     */
    private final int            maxCount;

    /** The decoded structure data for this piece. */
    private final StructurePiece structure;

    /**
     * Connection points derived from {@link com.sapphic.ssl.items.ConnectorBlock}
     * positions in the exported selection.  Each point defines where an adjacent
     * piece will attach and which block should be replaced after placement.
     */
    private final List<ConnectionPoint> connectionPoints;

    public MultiStructPiece(String id, String name, PieceRole role,
                            ConnectionType connectionType, int weight,
                            int maxCount, StructurePiece structure,
                            List<ConnectionPoint> connectionPoints) {
        this.id               = Objects.requireNonNull(id,             "id");
        this.name             = Objects.requireNonNull(name,           "name");
        this.role             = Objects.requireNonNull(role,           "role");
        this.connectionType   = Objects.requireNonNull(connectionType, "connectionType");
        this.weight           = weight;
        this.maxCount         = maxCount;
        this.structure        = Objects.requireNonNull(structure,      "structure");
        this.connectionPoints = Collections.unmodifiableList(
                Objects.requireNonNull(connectionPoints, "connectionPoints"));
    }

    /** Backwards-compatible constructor — no connection points. */
    public MultiStructPiece(String id, String name, PieceRole role,
                            ConnectionType connectionType, int weight,
                            int maxCount, StructurePiece structure) {
        this(id, name, role, connectionType, weight, maxCount, structure, List.of());
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public String              id()               { return id; }
    public String              name()             { return name; }
    public PieceRole           role()             { return role; }
    public ConnectionType      connectionType()   { return connectionType; }
    public int                 weight()           { return weight; }
    public int                 maxCount()         { return maxCount; }
    public StructurePiece      structure()        { return structure; }
    public List<ConnectionPoint> connectionPoints() { return connectionPoints; }

    /** {@code true} if this piece has no spawn cap. */
    public boolean isUnlimited() { return maxCount < 0; }

    /** {@code true} if this piece is a terminal node (ROOM). */
    public boolean isRoom() { return role == PieceRole.ROOM; }

    /**
     * Returns a copy of this piece with updated weight and maxCount.
     * Used when applying companion JSON overrides.
     */
    public MultiStructPiece withWeightAndCount(int newWeight, int newMaxCount) {
        return new MultiStructPiece(id, name, role, connectionType,
                newWeight, newMaxCount, structure, connectionPoints);
    }

    @Override
    public String toString() {
        return "MultiStructPiece[" + name + " role=" + role
               + " ctype=" + connectionType + " w=" + weight
               + " max=" + (maxCount < 0 ? "∞" : maxCount)
               + " connectors=" + connectionPoints.size() + "]";
    }
}

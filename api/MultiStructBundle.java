package com.sapphic.ssl.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An in-memory representation of a fully-decoded {@code .tsaphmultistruct} bundle.
 *
 * <p>A bundle is a named collection of {@link MultiStructPiece} instances with
 * associated metadata controlling how the procedural engine assembles them.
 * Pieces are partitioned by {@link PieceRole} for efficient lookup during generation.
 */
public final class MultiStructBundle {

    private final String                name;
    private final List<MultiStructPiece> pieces;

    // ── Derived role partitions (computed once at construction) ────────────

    private final List<MultiStructPiece> rooms;
    private final List<MultiStructPiece> hallways;
    private final List<MultiStructPiece> paths;
    private final List<MultiStructPiece> anchors;

    public MultiStructBundle(String name, List<MultiStructPiece> pieces) {
        this.name  = Objects.requireNonNull(name,   "name");
        this.pieces = Collections.unmodifiableList(Objects.requireNonNull(pieces, "pieces"));

        this.rooms    = partition(pieces, PieceRole.ROOM);
        this.hallways = partition(pieces, PieceRole.HALLWAY);
        this.paths    = partition(pieces, PieceRole.PATH);
        this.anchors  = partition(pieces, PieceRole.ANCHOR);
    }

    private static List<MultiStructPiece> partition(List<MultiStructPiece> all, PieceRole role) {
        return all.stream()
                .filter(p -> p.role() == role)
                .collect(Collectors.toUnmodifiableList());
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** Bundle name — matches the file stem of the {@code .tsaphmultistruct} file. */
    public String name() { return name; }

    /** All pieces in this bundle, in declaration order. */
    public List<MultiStructPiece> pieces() { return pieces; }

    /** All pieces with role {@link PieceRole#ROOM}. */
    public List<MultiStructPiece> rooms() { return rooms; }

    /** All pieces with role {@link PieceRole#HALLWAY}. */
    public List<MultiStructPiece> hallways() { return hallways; }

    /** All pieces with role {@link PieceRole#PATH}. */
    public List<MultiStructPiece> paths() { return paths; }

    /**
     * All pieces with role {@link PieceRole#ANCHOR}.
     *
     * <p>Only the first anchor in this list is used by the engine.
     * Bundles should have at most one anchor piece.
     */
    public List<MultiStructPiece> anchors() { return anchors; }

    /** Total piece count. */
    public int size() { return pieces.size(); }

    /**
     * Returns a new bundle with piece weights/counts replaced by values from the
     * companion JSON.  Any piece whose id is not present in {@code overrides} keeps
     * its original values.
     */
    public MultiStructBundle withOverrides(java.util.Map<String, int[]> overrides) {
        List<MultiStructPiece> updated = pieces.stream()
                .map(p -> {
                    int[] vals = overrides.get(p.id());
                    return vals != null ? p.withWeightAndCount(vals[0], vals[1]) : p;
                })
                .collect(Collectors.toList());
        return new MultiStructBundle(name, updated);
    }

    @Override
    public String toString() {
        return "MultiStructBundle[" + name + ", pieces=" + pieces.size()
               + " (anchors=" + anchors.size() + " rooms=" + rooms.size()
               + " hallways=" + hallways.size() + " paths=" + paths.size() + ")]";
    }
}

package com.sapphic.ssl.api;

import com.sapphic.ssl.api.loot.LootTableRef;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A single deferred operation stored in the per-world {@link StructureQueue}.
 *
 * <p>There are two kinds of entry:
 *
 * <h3>Block placement entry ({@link #isPendingUpdate()} == false)</h3>
 * <p>A block that could not be placed immediately because its target chunk was not
 * loaded at placement time.  Carries a {@code blockStateId}, optional NBT, and an
 * optional {@link LootTableRef}.  Applied by
 * {@link com.sapphic.ssl.api.IStructureLoader#processChunkQueue} when the target
 * chunk loads.
 *
 * <h3>Update-only entry ({@link #isPendingUpdate()} == true)</h3>
 * <p>A post-placement neighbour-update trigger for a block that was already placed in
 * Pass 1 but whose update sweep had to be deferred because one or more of its
 * face-adjacent neighbours lived in an unloaded chunk.  Carries <em>no block state</em>
 * — it is purely a positional trigger.  Applied when the last unloaded adjacent chunk
 * for that position loads, at which point the loader calls
 * {@code world.updateNeighbors(pos, block)} and schedules any fluid ticks.
 *
 * <p>The update-only path is what makes fences connect, stairs orient correctly,
 * and fluids flow across chunk boundaries once all chunks are loaded.
 *
 * <p>Wire format (binary, all big-endian):
 * <pre>
 *   [boolean]  pendingUpdate flag
 *   [UTF]      worldKey
 *   [int]      x
 *   [int]      y
 *   [int]      z
 *   --- if !pendingUpdate ---
 *   [UTF]      blockStateId
 *   [boolean]  hasNbt
 *   [int?]     nbtLen (if hasNbt)
 *   [bytes?]   nbtBytes (if hasNbt)
 *   [boolean]  hasLoot
 *   [byte?]    lootType (if hasLoot)
 *   [short?]   lootIdLen (if hasLoot)
 *   [bytes?]   lootId (if hasLoot)
 * </pre>
 */
public final class PendingPlacement {

    private final String       worldKey;
    private final int          x, y, z;
    private final String       blockStateId;   // null for update-only entries
    private final byte[]       blockEntityNbt;
    private final LootTableRef lootRef;

    /**
     * {@code true} if this entry is a deferred neighbour-update trigger rather than
     * an actual block placement.  Update-only entries have a {@code null}
     * {@link #blockStateId}.
     */
    private final boolean pendingUpdate;

    // ── Full placement constructor ─────────────────────────────────────

    public PendingPlacement(String worldKey, int x, int y, int z,
                            String blockStateId, byte[] blockEntityNbt,
                            LootTableRef lootRef) {
        this.worldKey       = worldKey;
        this.x              = x;
        this.y              = y;
        this.z              = z;
        this.blockStateId   = blockStateId;
        this.blockEntityNbt = blockEntityNbt;
        this.lootRef        = lootRef;
        this.pendingUpdate  = false;
    }

    // ── Update-only factory ────────────────────────────────────────────

    /**
     * Create a deferred neighbour-update trigger (no block placement).
     *
     * <p>The loader will call {@code world.updateNeighbors(pos, block)} and schedule
     * fluid ticks when this entry's chunk loads, ensuring connected-block states
     * (fences, walls, stairs, glass panes) and fluids resolve correctly across
     * chunk boundaries.
     */
    public static PendingPlacement updateOnly(String worldKey, int x, int y, int z) {
        return new PendingPlacement(worldKey, x, y, z, true);
    }

    private PendingPlacement(String worldKey, int x, int y, int z, boolean pendingUpdate) {
        this.worldKey       = worldKey;
        this.x              = x;
        this.y              = y;
        this.z              = z;
        this.blockStateId   = null;
        this.blockEntityNbt = null;
        this.lootRef        = null;
        this.pendingUpdate  = pendingUpdate;
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public String       worldKey()       { return worldKey; }
    public int          x()              { return x; }
    public int          y()              { return y; }
    public int          z()              { return z; }
    public String       blockStateId()   { return blockStateId; }
    public byte[]       blockEntityNbt() { return blockEntityNbt; }
    public LootTableRef lootRef()        { return lootRef; }

    /**
     * {@code true} if this is a deferred neighbour-update trigger with no block
     * placement.  The block has already been placed; the update was deferred because
     * an adjacent chunk was not yet loaded.
     */
    public boolean isPendingUpdate() { return pendingUpdate; }

    // ── Serialisation ──────────────────────────────────────────────────

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeBoolean(pendingUpdate);
        out.writeUTF(worldKey);
        out.writeInt(x); out.writeInt(y); out.writeInt(z);

        if (!pendingUpdate) {
            out.writeUTF(blockStateId != null ? blockStateId : "");

            boolean hasNbt = blockEntityNbt != null && blockEntityNbt.length > 0;
            out.writeBoolean(hasNbt);
            if (hasNbt) { out.writeInt(blockEntityNbt.length); out.write(blockEntityNbt); }

            boolean hasLoot = lootRef != null;
            out.writeBoolean(hasLoot);
            if (hasLoot) {
                out.writeByte(lootRef.type().wireValue());
                byte[] idB = lootRef.id().getBytes(StandardCharsets.UTF_8);
                out.writeShort(idB.length);
                out.write(idB);
            }
        }
    }

    public static PendingPlacement readFrom(DataInputStream in) throws IOException {
        boolean pendingUpdate = in.readBoolean();
        String  worldKey      = in.readUTF();
        int x = in.readInt(), y = in.readInt(), z = in.readInt();

        if (pendingUpdate) {
            return new PendingPlacement(worldKey, x, y, z, true);
        }

        String blockStateId = in.readUTF();

        byte[] nbt = null;
        if (in.readBoolean()) {
            int len = in.readInt(); nbt = new byte[len]; in.readFully(nbt);
        }

        LootTableRef lootRef = null;
        if (in.readBoolean()) {
            byte typeByte = in.readByte();
            int  idLen    = in.readUnsignedShort();
            byte[] idB    = new byte[idLen]; in.readFully(idB);
            String id = new String(idB, StandardCharsets.UTF_8);
            lootRef = LootTableRef.RefType.fromWire(typeByte) == LootTableRef.RefType.VANILLA
                    ? LootTableRef.vanilla(id) : LootTableRef.tsaphloot(id);
        }

        return new PendingPlacement(worldKey, x, y, z, blockStateId, nbt, lootRef);
    }

    @Override
    public String toString() {
        if (pendingUpdate) {
            return "PendingPlacement[UPDATE " + worldKey + " @(" + x + "," + y + "," + z + ")]";
        }
        return "PendingPlacement[" + worldKey + " @(" + x + "," + y + "," + z + ") "
               + blockStateId + (lootRef != null ? " loot=" + lootRef : "") + "]";
    }
}

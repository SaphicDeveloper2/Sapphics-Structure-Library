package com.sapphic.ssl.items;

import com.sapphic.ssl.api.StructureBoundingBox;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The SSL Selection Wand.
 *
 * <ul>
 *   <li><strong>Right-click block</strong> → Set Position 1 (first corner).</li>
 *   <li><strong>Sneak + Right-click block</strong> → Set Position 2 (second corner).</li>
 * </ul>
 *
 * <p>Selections are stored per-player UUID in thread-safe maps and have no size limit —
 * they are not constrained by vanilla's 48 × 48 × 48 structure-block ceiling.
 */
public class SelectionWandItem extends Item {

    /** Thread-safe per-player corner positions. */
    private static final Map<UUID, BlockPos> POS1 = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> POS2 = new ConcurrentHashMap<>();

    public SelectionWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        BlockPos clicked = context.getBlockPos();
        UUID uid         = player.getUuid();

        if (player.isSneaking()) {
            POS2.put(uid, clicked);
            player.sendMessage(Text.literal("§b[SSL] §fPosition 2 → " + fmt(clicked)), true);
        } else {
            POS1.put(uid, clicked);
            player.sendMessage(Text.literal("§b[SSL] §fPosition 1 → " + fmt(clicked)), true);
        }

        // Show live selection size when both corners are set
        if (POS1.containsKey(uid) && POS2.containsKey(uid)) {
            StructureBoundingBox box = buildBox(POS1.get(uid), POS2.get(uid));
            String info = String.format("§7Selection: §f%dx%dx%d §7(%,d blocks)%s",
                    box.sizeX(), box.sizeY(), box.sizeZ(), box.volume(),
                    box.exceedsVanillaLimit() ? " §6[>48³]" : "");
            player.sendMessage(Text.literal("§b[SSL] " + info), false);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Right-click block §f→ Position 1"));
        tooltip.add(Text.literal("§7Sneak + Right-click §f→ Position 2"));
        tooltip.add(Text.literal("§b/tsaph save <name> §7to export  §b/tsaph load §7to place"));
    }

    // ── Static API used by commands ───────────────────────────────────────

    /**
     * Returns the active {@link StructureBoundingBox} for this player, or
     * {@code null} if one or both corners have not been set yet.
     */
    public static StructureBoundingBox getSelection(PlayerEntity player) {
        BlockPos p1 = POS1.get(player.getUuid());
        BlockPos p2 = POS2.get(player.getUuid());
        return (p1 != null && p2 != null) ? buildBox(p1, p2) : null;
    }

    /** Clear the wand selection for this player (called after a successful export). */
    public static void clearSelection(PlayerEntity player) {
        POS1.remove(player.getUuid());
        POS2.remove(player.getUuid());
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private static StructureBoundingBox buildBox(BlockPos p1, BlockPos p2) {
        return StructureBoundingBox.fromCorners(
                p1.getX(), p1.getY(), p1.getZ(),
                p2.getX(), p2.getY(), p2.getZ());
    }

    private static String fmt(BlockPos p) {
        return "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
    }
}

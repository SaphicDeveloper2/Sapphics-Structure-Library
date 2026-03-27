package com.sapphic.ssl.mixin;

import com.sapphic.ssl.api.StructureLoaderBridge;
import com.sapphic.ssl.api.loot.ITsaphLootEngine;
import com.sapphic.ssl.api.loot.LootTableRef;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.LootableInventory;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@link LootableInventory#generateLoot(PlayerEntity)} — a {@code default}
 * method on the interface — to handle containers whose loot table key was set to
 * {@code ssl:tsaphloot/<tableName>} by the TsaphLoot engine.
 *
 * <h3>Interface mixin constraints</h3>
 * SpongePowered Mixin strictly forbids non-{@code @Shadow} fields in interface mixins.
 * This class therefore contains <em>no fields of any kind</em>; all constants are
 * inlined as literals and the logger is obtained on-demand via
 * {@link org.slf4j.LoggerFactory#getLogger}.
 */
@Mixin(LootableInventory.class)
public interface LootableInventoryMixin {

    @Inject(
        method = "generateLoot(Lnet/minecraft/entity/player/PlayerEntity;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    default void ssl$interceptGenerateLoot(PlayerEntity player, CallbackInfo ci) {
        LootableInventory self = (LootableInventory) this;

        RegistryKey<LootTable> key = self.getLootTable();
        if (key == null) return;

        // Only intercept keys in the "ssl" namespace with the "tsaphloot/" path prefix.
        // Inline literals — interface mixins must not declare any fields.
        if (!"ssl".equals(key.getValue().getNamespace())) return;
        String path = key.getValue().getPath();
        if (!path.startsWith("tsaphloot/")) return;

        // Our key — cancel vanilla's lookup (which returns LootTable.EMPTY for unknown keys)
        ci.cancel();

        World world = self.getWorld();
        if (world == null || world.isClient()) return;

        String tableName = path.substring("tsaphloot/".length());
        BlockPos pos     = self.getPos();

        // Clear the loot table key BEFORE running the engine so a crash
        // during population can't trigger infinite refill on the next open.
        self.setLootTable(null);

        ITsaphLootEngine engine = StructureLoaderBridge.getLootEngine();
        boolean ok = engine.populate(
                (ServerWorld) world,
                pos,
                LootTableRef.tsaphloot(tableName));

        if (!ok) {
            // Logger obtained on-demand — no stored field allowed in interface mixins
            org.slf4j.LoggerFactory.getLogger("SSL/LootMixin")
                .warn("SSL/Loot: failed to populate container at {} with TsaphLoot table '{}'. "
                    + "Is the .tsaphloot file present?", pos, tableName);
        }
    }
}

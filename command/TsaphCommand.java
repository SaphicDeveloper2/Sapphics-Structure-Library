package com.sapphic.ssl.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sapphic.ssl.api.*;
import com.sapphic.ssl.api.loot.*;
import com.sapphic.ssl.internal.MultiStructRegistry;
import com.sapphic.ssl.internal.WandSession;
import com.sapphic.ssl.api.loot.LootBarrelBlockEntity;
import com.sapphic.ssl.internal.loot.LootRegistry;
import com.sapphic.ssl.items.SelectionWandItem;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registers the full {@code /tsaph} command tree.
 *
 * <pre>
 * /tsaph save &lt;name&gt;
 * Export wand selection → &lt;world&gt;/generated/ssl/&lt;name&gt;.tsaphstruct
 *
 * /tsaph load &lt;name&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;
 * Load structure file and place it at origin (x,y,z).
 *
 * /tsaph info
 * Show current wand selection dimensions.
 *
 * /tsaph list
 * List all .tsaphstruct files in generated/ssl/.
 *
 * /tsaph queue
 * Show deferred block count for the current world.
 *
 * /tsaph loot apply tsaphloot &lt;name&gt; &lt;pos&gt;
 * Stamp a TsaphLoot table onto the container at &lt;pos&gt;.
 * Fills on next player-open.
 *
 * /tsaph loot apply vanilla &lt;lootTableId&gt; &lt;pos&gt;
 * Stamp a vanilla loot table onto the container at &lt;pos&gt;.
 *
 * /tsaph loot fill tsaphloot &lt;name&gt; &lt;pos&gt;
 * Immediately fill the container at &lt;pos&gt; using the named TsaphLoot table.
 *
 * /tsaph loot fill vanilla &lt;lootTableId&gt; &lt;pos&gt;
 * Immediately fill the container using a vanilla loot table.
 *
 * /tsaph loot list
 * List all loaded TsaphLoot tables.
 *
 * /tsaph loot reload
 * Reload all .tsaphloot files from disk without a full server restart.
 * </pre>
 *
 * <p>Requires permission level 2 (operator).
 */
public final class TsaphCommand {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("SSL/Command");

    private TsaphCommand() {}

    // ── Registration ──────────────────────────────────────────────────────

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment env) {

        dispatcher.register(CommandManager.literal("tsaph")
            .requires(src -> src.hasPermissionLevel(2))

            // /tsaph save <name>
            .then(CommandManager.literal("save")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(TsaphCommand::executeSave)))

            // /tsaph load <name> <x> <y> <z>
            .then(CommandManager.literal("load")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .then(CommandManager.argument("x", IntegerArgumentType.integer())
                      .then(CommandManager.argument("y", IntegerArgumentType.integer())
                        .then(CommandManager.argument("z", IntegerArgumentType.integer())
                          .executes(TsaphCommand::executeLoad))))))

            // /tsaph info
            .then(CommandManager.literal("info")
                .executes(TsaphCommand::executeInfo))

            // /tsaph list
            .then(CommandManager.literal("list")
                .executes(TsaphCommand::executeList))

            // /tsaph queue
            .then(CommandManager.literal("queue")
                .executes(TsaphCommand::executeQueue))

            // /tsaph export <name>
            // Compile the current wand selection and all its loot barrels into a
            // self-contained datapack directory under generated/ssl/export/<name>/
            .then(CommandManager.literal("export")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(TsaphCommand::executeExport)))


            // /tsaph loot …
            .then(CommandManager.literal("loot")

                // /tsaph loot list
                .then(CommandManager.literal("list")
                    .executes(TsaphCommand::executeLootList))

                // /tsaph loot reload
                .then(CommandManager.literal("reload")
                    .executes(TsaphCommand::executeLootReload))

                // /tsaph loot apply tsaphloot <name> <pos>
                .then(CommandManager.literal("apply")
                    .then(CommandManager.literal("tsaphloot")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> executeLootApply(ctx, false)))))
                    // /tsaph loot apply vanilla <lootTableId> <pos>
                    .then(CommandManager.literal("vanilla")
                        .then(CommandManager.argument("lootTableId", IdentifierArgumentType.identifier())
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> executeLootApplyVanilla(ctx))))))

                // /tsaph loot fill tsaphloot <name> <pos>
                .then(CommandManager.literal("fill")
                    .then(CommandManager.literal("tsaphloot")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> executeLootFill(ctx, false)))))
                    // /tsaph loot fill vanilla <lootTableId> <pos>
                    .then(CommandManager.literal("vanilla")
                        .then(CommandManager.argument("lootTableId", IdentifierArgumentType.identifier())
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> executeLootFillVanilla(ctx))))))

                // /tsaph loot setbarrel <pos> tsaphloot <n>  |  vanilla <id>
                // Switch a placed LootBarrel to REGISTRY mode and set its key.
                .then(CommandManager.literal("setbarrel")
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .then(CommandManager.literal("tsaphloot")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(TsaphCommand::executeSetBarrel)))
                        .then(CommandManager.literal("vanilla")
                            .then(CommandManager.argument("lootTableId", IdentifierArgumentType.identifier())
                                .executes(TsaphCommand::executeSetBarrelVanilla)))))
            )

            // /tsaph multi …
            .then(CommandManager.literal("multi")

                // /tsaph multi begin
                .then(CommandManager.literal("begin")
                    .executes(TsaphCommand::executeMultiBegin))

                // /tsaph multi add <name> <role> [ctype] [weight] [maxCount]
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .then(CommandManager.argument("role", StringArgumentType.word())
                            .executes(ctx -> executeMultiAdd(ctx, "NONE", 1, -1))
                            .then(CommandManager.argument("ctype", StringArgumentType.word())
                                .executes(ctx -> executeMultiAdd(ctx,
                                    StringArgumentType.getString(ctx, "ctype"), 1, -1))
                                .then(CommandManager.argument("weight", IntegerArgumentType.integer(1))
                                    .executes(ctx -> executeMultiAdd(ctx,
                                        StringArgumentType.getString(ctx, "ctype"),
                                        IntegerArgumentType.getInteger(ctx, "weight"), -1))
                                    .then(CommandManager.argument("maxCount", IntegerArgumentType.integer(-1))
                                        .executes(ctx -> executeMultiAdd(ctx,
                                            StringArgumentType.getString(ctx, "ctype"),
                                            IntegerArgumentType.getInteger(ctx, "weight"),
                                            IntegerArgumentType.getInteger(ctx, "maxCount")))))))))

                // /tsaph multi save <name>
                .then(CommandManager.literal("save")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(TsaphCommand::executeMultiSave)))

                // /tsaph multi cancel
                .then(CommandManager.literal("cancel")
                    .executes(TsaphCommand::executeMultiCancel))

                // /tsaph multi info
                .then(CommandManager.literal("info")
                    .executes(TsaphCommand::executeMultiInfo))

                // /tsaph multi list
                .then(CommandManager.literal("list")
                    .executes(TsaphCommand::executeMultiList))

                // /tsaph multi reload
                .then(CommandManager.literal("reload")
                    .executes(TsaphCommand::executeMultiReload))

                // /tsaph multi spawn <name> <x> <z> [depth] [seed]
                .then(CommandManager.literal("spawn")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .then(CommandManager.argument("x", IntegerArgumentType.integer())
                          .then(CommandManager.argument("z", IntegerArgumentType.integer())
                            .executes(ctx -> executeMultiSpawn(ctx, com.sapphic.ssl.internal.ProceduralEngine.DEFAULT_MAX_DEPTH, null))
                            .then(CommandManager.argument("depth", IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> executeMultiSpawn(ctx,
                                    IntegerArgumentType.getInteger(ctx, "depth"), null))
                                .then(CommandManager.argument("seed", LongArgumentType.longArg())
                                    .executes(ctx -> executeMultiSpawn(ctx, 
                                        IntegerArgumentType.getInteger(ctx, "depth"), 
                                        LongArgumentType.getLong(ctx, "seed")))))))))
            )
        );
    }

    // ── /tsaph save ───────────────────────────────────────────────────────

    private static int executeSave(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        ServerPlayerEntity  player = src.getPlayerOrThrow();

        StructureBoundingBox box = SelectionWandItem.getSelection(player);
        if (box == null) {
            src.sendError(Text.literal(
                "§c[SSL] No selection. Use the §bSelection Wand §cto set two corners first."));
            return 0;
        }

        String name      = sanitise(StringArgumentType.getString(ctx, "name"));
        Path destination = sslDir(src).resolve(name);
        ServerWorld world = src.getWorld();

        try {
            src.sendMessage(Text.literal("§b[SSL] §fExporting §e" + box + " §f…"));
            StructureLoaderBridge.getExporter().export(world, box, destination);
            src.sendMessage(Text.literal(
                "§a[SSL] §fExported §b" + name + SaphStructFormat.EXTENSION + "\n" +
                String.format("§7  %dx%dx%d  (%,d blocks)%s",
                    box.sizeX(), box.sizeY(), box.sizeZ(), box.volume(),
                    box.exceedsVanillaLimit() ? "  §6[>48³]" : "")));
            SelectionWandItem.clearSelection(player);
            return 1;
        } catch (IllegalStateException e) {
            src.sendError(Text.literal("§c[SSL] Unloaded chunk: §7" + e.getMessage()));
            return 0;
        } catch (IOException e) {
            src.sendError(Text.literal("§c[SSL] Export failed: §7" + e.getMessage()));
            LOGGER.error("SSL: /tsaph save", e);
            return 0;
        }
    }

    // ── /tsaph load ───────────────────────────────────────────────────────

    private static int executeLoad(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src   = ctx.getSource();
        String name = sanitise(StringArgumentType.getString(ctx, "name"));
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");

        Path file = resolveStructureFile(src, name);
        if (file == null) {
            src.sendError(Text.literal(
                "§c[SSL] Structure '§b" + name + "§c' not found. Use §b/tsaph list§c."));
            return 0;
        }

        ServerWorld world  = src.getWorld();
        BlockPos    origin = new BlockPos(x, y, z);

        try {
            src.sendMessage(Text.literal(
                "§b[SSL] §fLoading §e" + name + " §fat §e" + fmt(origin) + " §f…"));
            StructurePiece piece = StructureLoaderBridge.getLoader().load(file);
            StructureLoaderBridge.getLoader().place(world, piece, origin);

            int deferred = StructureLoaderBridge.getQueue(world).size();
            src.sendMessage(Text.literal(
                "§a[SSL] §fPlacement started — §b" + name + "\n§7  " + piece +
                (deferred > 0
                    ? "\n§7  §6" + deferred + " block(s) deferred (unloaded chunks)"
                    : "\n§7  All blocks placed immediately.")));
            return 1;
        } catch (IOException e) {
            src.sendError(Text.literal("§c[SSL] Load failed: §7" + e.getMessage()));
            LOGGER.error("SSL: /tsaph load", e);
            return 0;
        }
    }

    // ── /tsaph info ───────────────────────────────────────────────────────

    private static int executeInfo(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        ServerPlayerEntity  player = src.getPlayerOrThrow();
        StructureBoundingBox box   = SelectionWandItem.getSelection(player);

        if (box == null) {
            src.sendMessage(Text.literal("§b[SSL] §7No selection active."));
            return 1;
        }
        src.sendMessage(Text.literal(String.format(
            "§b[SSL] §fSelection:\n" +
            "§7  Corner 1: §f(%d, %d, %d)\n" +
            "§7  Corner 2: §f(%d, %d, %d)\n" +
            "§7  Size:     §f%dx%dx%d §7(%.2fM blocks)%s",
            box.minX(), box.minY(), box.minZ(),
            box.maxX(), box.maxY(), box.maxZ(),
            box.sizeX(), box.sizeY(), box.sizeZ(),
            box.volume() / 1_000_000.0,
            box.exceedsVanillaLimit() ? "\n§6  ⚠ Exceeds vanilla 48³ limit." : "")));
        return 1;
    }

    // ── /tsaph list ───────────────────────────────────────────────────────

    private static int executeList(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        Path dir = sslDir(src);

        if (!Files.isDirectory(dir)) {
            src.sendMessage(Text.literal("§b[SSL] §7No structures yet."));
            return 1;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            String names = stream
                .filter(p -> p.toString().endsWith(SaphStructFormat.EXTENSION))
                .map(p -> { String n = p.getFileName().toString();
                            return n.substring(0, n.length() - SaphStructFormat.EXTENSION.length()); })
                .sorted()
                .collect(Collectors.joining("\n  §7- §b"));

            src.sendMessage(Text.literal(names.isEmpty()
                ? "§b[SSL] §7No .tsaphstruct files found."
                : "§b[SSL] §fStructures:\n  §7- §b" + names));
            return 1;
        } catch (IOException e) {
            src.sendError(Text.literal("§c[SSL] List failed: §7" + e.getMessage()));
            return 0;
        }
    }

    // ── /tsaph queue ──────────────────────────────────────────────────────

    private static int executeQueue(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src   = ctx.getSource();
        ServerWorld         world = src.getWorld();
        int size = StructureLoaderBridge.getQueue(world).size();
        src.sendMessage(Text.literal(size == 0
            ? "§b[SSL] §7Queue is empty."
            : "§b[SSL] §7Queue for §f" + world.getRegistryKey().getValue() +
              "§7: §e" + size + " §7block(s) pending."));
        return 1;
    }

    // ── /tsaph export <name> ─────────────────────────────────────────────
    //
    // Compiles the current wand selection (plus all embedded Loot Barrels) into a
    // self-contained datapack directory:
    //
    //   generated/ssl/export/<name>/
    //     pack.mcmeta
    //     data/<name>/loot_table/   ← one vanilla JSON per SNAPSHOT LootBarrel
    //     data/<name>/tsaphloot/    ← .tsaphloot files if REGISTRY mode refs exist
    //
    // SNAPSHOT barrels produce a vanilla loot_table JSON that the placed chest can
    // reference.  REGISTRY barrels pointing at existing .tsaphloot tables are noted
    // in the feedback but no file is written (they already exist in the mod/datapack).
    // ─────────────────────────────────────────────────────────────────────────────

    private static int executeExport(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        ServerPlayerEntity  player = src.getPlayerOrThrow();
        ServerWorld         world  = src.getWorld();

        StructureBoundingBox box = SelectionWandItem.getSelection(player);
        if (box == null) {
            src.sendError(Text.literal(
                "§c[SSL] No wand selection. Right-click two corners with the §bStructure Wand§c first."));
            return 0;
        }

        String name     = sanitise(StringArgumentType.getString(ctx, "name"));
        Path   exportDir = sslDir(src).resolve("export").resolve(name);
        Path   lootDir   = exportDir.resolve("data").resolve(name).resolve("loot_table");
        Path   tsaphDir  = exportDir.resolve("data").resolve(name).resolve("tsaphloot");

        try {
            // ── 1. Validate all chunks loaded ──────────────────────────────
            int cMinX = Math.floorDiv(box.minX(), 16), cMaxX = Math.floorDiv(box.maxX(), 16);
            int cMinZ = Math.floorDiv(box.minZ(), 16), cMaxZ = Math.floorDiv(box.maxZ(), 16);
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                for (int cx = cMinX; cx <= cMaxX; cx++) {
                    if (!world.isChunkLoaded(cx, cz)) {
                        src.sendError(Text.literal(
                            "§c[SSL] Chunk (" + cx + "," + cz + ") not loaded. " +
                            "Use §b/forceload add " + (cx*16) + " " + (cz*16) + "§c first."));
                        return 0;
                    }
                }
            }

            Files.createDirectories(lootDir);

            // ── 2. Write pack.mcmeta ───────────────────────────────────────
            Path mcmeta = exportDir.resolve("pack.mcmeta");
            Files.writeString(mcmeta,
                "{\n" +
                "  \"pack\": {\n" +
                "    \"pack_format\": 48,\n" +
                "    \"description\": \"SSL export: " + name + "\"\n" +
                "  }\n" +
                "}\n");

            // ── 3. Scan selection for LootBarrel blocks ────────────────────
            int snapshotCount = 0;
            int registryCount = 0;

            for (int ry = 0; ry < box.sizeY(); ry++) {
                for (int rz = 0; rz < box.sizeZ(); rz++) {
                    for (int rx = 0; rx < box.sizeX(); rx++) {
                        net.minecraft.util.math.BlockPos bpos =
                            new net.minecraft.util.math.BlockPos(
                                box.minX() + rx, box.minY() + ry, box.minZ() + rz);

                        net.minecraft.block.BlockState bs = world.getBlockState(bpos);
                        if (!(bs.getBlock() instanceof com.sapphic.ssl.items.LootBarrelBlock)) continue;

                        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(bpos);
                        if (!(be instanceof com.sapphic.ssl.api.loot.LootBarrelBlockEntity barrel)) continue;

                        String barrelId = "barrel_" + rx + "_" + ry + "_" + rz;

                        if (barrel.getMode() == com.sapphic.ssl.api.loot.LootBarrelBlockEntity.Mode.SNAPSHOT) {
                            // ── Build vanilla loot_table JSON from item weights ──
                            Map<String, Integer> weightMap = new LinkedHashMap<>();
                            for (net.minecraft.item.ItemStack stack : barrel.getItems()) {
                                if (stack.isEmpty()) continue;
                                net.minecraft.util.Identifier itemId =
                                    net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
                                if (itemId == null) continue;
                                weightMap.merge(itemId.toString(), stack.getCount(), Integer::sum);
                            }

                            // Vanilla loot_table format (pack_format 48 = 1.21.1)
                            StringBuilder json = new StringBuilder();
                            json.append("{\n");
                            json.append("  \"type\": \"minecraft:chest\",\n");
                            json.append("  \"pools\": [\n");
                            json.append("    {\n");
                            json.append("      \"bonus_rolls\": 0.0,\n");
                            json.append("      \"entries\": [\n");

                            int totalWeight = weightMap.values().stream().mapToInt(i -> i).sum();
                            boolean firstEntry = true;
                            for (Map.Entry<String, Integer> e : weightMap.entrySet()) {
                                if (!firstEntry) json.append(",\n");
                                firstEntry = false;
                                json.append("        {\n");
                                json.append("          \"type\": \"minecraft:item\",\n");
                                json.append("          \"name\": \"").append(e.getKey()).append("\",\n");
                                json.append("          \"weight\": ").append(e.getValue()).append("\n");
                                json.append("        }");
                            }

                            int rolls = Math.min(weightMap.size(), 8);
                            json.append("\n      ],\n");
                            json.append("      \"rolls\": ").append(rolls).append(".0\n");
                            json.append("    }\n");
                            json.append("  ]\n");
                            json.append("}\n");

                            Path tableFile = lootDir.resolve(barrelId + ".json");
                            Files.writeString(tableFile, json.toString());
                            snapshotCount++;

                        } else {
                            // REGISTRY — just note the reference key in feedback
                            registryCount++;
                        }
                    }
                }
            }

            // ── 4. Report ──────────────────────────────────────────────────
            src.sendMessage(Text.literal(
                "§a[SSL] §fExport §b'" + name + "'§f complete:\n" +
                "§7  Directory: §f" + exportDir + "\n" +
                "§7  SNAPSHOT barrels baked: §e" + snapshotCount + "\n" +
                "§7  REGISTRY barrel refs: §e" + registryCount + "\n" +
                "§7  pack.mcmeta written.\n" +
                "§7  Drop the §b" + name + "§7 folder into your datapack zip to distribute loot tables."));

            LOGGER.info("SSL: export '{}' → {} (snapshot={}, registry={})",
                name, exportDir, snapshotCount, registryCount);
            return 1;

        } catch (IOException e) {
            src.sendError(Text.literal("§c[SSL] Export failed: §7" + e.getMessage()));
            LOGGER.error("SSL: /tsaph export", e);
            return 0;
        }
    }

    // ── /tsaph loot setbarrel <pos> tsaphloot <name> ─────────────────────

    private static int executeSetBarrel(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src   = ctx.getSource();
        ServerWorld         world = src.getWorld();
        net.minecraft.util.math.BlockPos pos =
                net.minecraft.command.argument.BlockPosArgumentType
                        .getLoadedBlockPos(ctx, "pos");

        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof com.sapphic.ssl.api.loot.LootBarrelBlockEntity barrel)) {
            src.sendError(Text.literal(
                "§c[SSL] No LootBarrel at " + fmt(pos) + ". Place one first."));
            return 0;
        }

        String key = StringArgumentType.getString(ctx, "name");
        barrel.setMode(com.sapphic.ssl.api.loot.LootBarrelBlockEntity.Mode.REGISTRY);
        barrel.setRegistryKey(key);

        src.sendMessage(Text.literal(
            "§a[SSL] §fLootBarrel at §b" + fmt(pos) + "§f set to §eREGISTRY§f mode.\n" +
            "§7  Key: §b" + key + "\n" +
            "§7  Chest will be filled from this TsaphLoot table when the structure generates."));
        return 1;
    }

    private static int executeSetBarrelVanilla(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src   = ctx.getSource();
        ServerWorld         world = src.getWorld();
        net.minecraft.util.math.BlockPos pos =
                net.minecraft.command.argument.BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
        net.minecraft.util.Identifier tableId =
                net.minecraft.command.argument.IdentifierArgumentType
                        .getIdentifier(ctx, "lootTableId");

        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof com.sapphic.ssl.api.loot.LootBarrelBlockEntity barrel)) {
            src.sendError(Text.literal(
                "§c[SSL] No LootBarrel at " + fmt(pos) + ". Place one first."));
            return 0;
        }

        barrel.setMode(com.sapphic.ssl.api.loot.LootBarrelBlockEntity.Mode.REGISTRY);
        barrel.setRegistryKey(tableId.toString());

        src.sendMessage(Text.literal(
            "§a[SSL] §fLootBarrel at §b" + fmt(pos) + "§f set to §eREGISTRY §f(vanilla) mode.\n" +
            "§7  Key: §b" + tableId + "\n" +
            "§7  Chest will be filled from this vanilla loot table when the structure generates."));
        return 1;
    }

    // ── /tsaph loot list ─────────────────────────────────────────────────

    private static int executeLootList(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src   = ctx.getSource();
        Set<String>         names = LootRegistry.names();

        if (names.isEmpty()) {
            src.sendMessage(Text.literal(
                "§b[SSL] §7No TsaphLoot tables loaded. " +
                "Add §b.tsaphloot §7files to your resources or §b<world>/data/ssl_loot/§7."));
            return 1;
        }

        String list = names.stream().sorted()
                .collect(Collectors.joining("\n  §7- §a"));
        src.sendMessage(Text.literal(
            "§b[SSL] §fLoaded TsaphLoot tables (" + names.size() + "):\n  §7- §a" + list));
        return 1;
    }

    // ── /tsaph loot reload ────────────────────────────────────────────────

    private static int executeLootReload(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        LootRegistry.reload(src.getServer());
        src.sendMessage(Text.literal(
            "§a[SSL] §fTsaphLoot registry reloaded — §b" + LootRegistry.size() + " §ftable(s) loaded."));
        return 1;
    }

    // ── /tsaph loot apply tsaphloot <name> <pos> ─────────────────────────

    private static int executeLootApply(CommandContext<ServerCommandSource> ctx,
                                        boolean fillNow) throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        String              name   = StringArgumentType.getString(ctx, "name");
        BlockPos            pos    = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
        ServerWorld         world  = src.getWorld();

        // Verify table exists
        Optional<TsaphLootTable> table = LootRegistry.get(name);
        if (table.isEmpty()) {
            src.sendError(Text.literal(
                "§c[SSL] TsaphLoot table '§b" + name + "§c' not found. " +
                "Use §b/tsaph loot list §cto see available tables."));
            return 0;
        }

        ITsaphLootEngine engine = StructureLoaderBridge.getLootEngine();
        LootTableRef     ref    = LootTableRef.tsaphloot(name);

        boolean ok = engine.applyLootTag(world, pos, ref, 0L);
        if (ok) {
            src.sendMessage(Text.literal(
                "§a[SSL] §fTsaphLoot tag '§b" + name + "§f' applied to " + fmt(pos) + ".\n" +
                "§7  Container will fill on next player-open."));
        } else {
            src.sendError(Text.literal(
                "§c[SSL] Failed to apply loot tag at " + fmt(pos) +
                " — is there a container there?"));
        }
        return ok ? 1 : 0;
    }

    // ── /tsaph loot apply vanilla <id> <pos> ─────────────────────────────

    private static int executeLootApplyVanilla(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src       = ctx.getSource();
        Identifier          tableId   = IdentifierArgumentType.getIdentifier(ctx, "lootTableId");
        BlockPos            pos       = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
        ServerWorld         world     = src.getWorld();

        ITsaphLootEngine engine = StructureLoaderBridge.getLootEngine();
        LootTableRef     ref    = LootTableRef.vanilla(tableId.toString());

        boolean ok = engine.applyLootTag(world, pos, ref, 0L);
        if (ok) {
            src.sendMessage(Text.literal(
                "§a[SSL] §fVanilla loot table '§b" + tableId + "§f' applied to " + fmt(pos) + "."));
        } else {
            src.sendError(Text.literal(
                "§c[SSL] Failed to apply vanilla loot tag at " + fmt(pos) + "."));
        }
        return ok ? 1 : 0;
    }

    // ── /tsaph loot fill tsaphloot <name> <pos> ──────────────────────────

    private static int executeLootFill(CommandContext<ServerCommandSource> ctx,
                                       boolean ignored) throws CommandSyntaxException {
        ServerCommandSource src   = ctx.getSource();
        String              name  = StringArgumentType.getString(ctx, "name");
        BlockPos            pos   = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
        ServerWorld         world = src.getWorld();

        if (LootRegistry.get(name).isEmpty()) {
            src.sendError(Text.literal(
                "§c[SSL] TsaphLoot table '§b" + name + "§c' not found."));
            return 0;
        }

        ITsaphLootEngine engine = StructureLoaderBridge.getLootEngine();
        boolean ok = engine.populate(world, pos, LootTableRef.tsaphloot(name));
        if (ok) {
            src.sendMessage(Text.literal(
                "§a[SSL] §fContainer at " + fmt(pos) + " filled with '§b" + name + "§f'."));
        } else {
            src.sendError(Text.literal(
                "§c[SSL] Failed to fill container at " + fmt(pos) + "."));
        }
        return ok ? 1 : 0;
    }

    // ── /tsaph loot fill vanilla <id> <pos> ──────────────────────────────

    private static int executeLootFillVanilla(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src     = ctx.getSource();
        Identifier          tableId = IdentifierArgumentType.getIdentifier(ctx, "lootTableId");
        BlockPos            pos     = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
        ServerWorld         world   = src.getWorld();

        boolean ok = StructureLoaderBridge.getLootEngine()
                .populate(world, pos, LootTableRef.vanilla(tableId.toString()));
        if (ok) {
            src.sendMessage(Text.literal(
                "§a[SSL] §fContainer at " + fmt(pos) + " filled with vanilla table '§b" + tableId + "§f'."));
        } else {
            src.sendError(Text.literal(
                "§c[SSL] Failed to fill container at " + fmt(pos) + "."));
        }
        return ok ? 1 : 0;
    }


    // ── /tsaph multi begin ────────────────────────────────────────────────

    private static int executeMultiBegin(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        ServerPlayerEntity  player = src.getPlayerOrThrow();

        boolean hadSession = StructureLoaderBridge.hasSession(player.getUuid());
        StructureLoaderBridge.beginSession(player.getUuid());

        src.sendMessage(Text.literal(hadSession
            ? "§6[SSL] §fPrevious session discarded. New multi-struct session started."
            : "§a[SSL] §fMulti-struct session started."));
        src.sendMessage(Text.literal(
            "§7  1. Select a region with the §bStructure Wand§7.\n" +
            "§7  2. Run §b/tsaph multi add <name> <role>§7 to tag it.\n" +
            "§7  2b. §dANCHOR§7 = placed first (one only)  §aROOM§7 = terminal  §bHALLWAY§7/§ePATH§7 = connector\n" +
            "§7  3. Repeat for each piece.\n" +
            "§7  4. Run §b/tsaph multi save <bundleName>§7 when done."));
        return 1;
    }

    // ── /tsaph multi add ──────────────────────────────────────────────────

    private static int executeMultiAdd(CommandContext<ServerCommandSource> ctx,
                                       String ctypeStr, int weight, int maxCount)
            throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        ServerPlayerEntity  player = src.getPlayerOrThrow();

        if (!StructureLoaderBridge.hasSession(player.getUuid())) {
            src.sendError(Text.literal(
                "§c[SSL] No active session. Run §b/tsaph multi begin §cfirst."));
            return 0;
        }

        StructureBoundingBox box = SelectionWandItem.getSelection(player);
        if (box == null) {
            src.sendError(Text.literal(
                "§c[SSL] No wand selection. Right-click two corners with the §bStructure Wand§c first."));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        String roleStr = StringArgumentType.getString(ctx, "role");

        PieceRole role;
        ConnectionType ctype;
        try {
            role = PieceRole.fromString(roleStr);
        } catch (IllegalArgumentException e) {
            src.sendError(Text.literal(
                "§c[SSL] Unknown role '§b" + roleStr + "§c'. Use: §bANCHOR§c, §bROOM§c, §bHALLWAY§c, §bPATH§c"));
            sendRoleUI(src, name);
            return 0;
        }

        try {
            ctype = role.isConnector()
                    ? ConnectionType.fromString(ctypeStr.equals("NONE") ? "STRAIGHT" : ctypeStr)
                    : ConnectionType.NONE;
        } catch (IllegalArgumentException e) {
            src.sendError(Text.literal(
                "§c[SSL] Unknown connection type '§b" + ctypeStr + "§c'. Valid types:"));
            sendConnectionTypeUI(src, name, roleStr, weight, maxCount);
            return 0;
        }

        // Export the current selection to an in-memory StructurePiece
        ServerWorld world = src.getWorld();
        StructurePiece piece;
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("ssl_multi_piece_", ".tsaphstruct");
            tmp.toFile().deleteOnExit();
            StructureLoaderBridge.getExporter().export(world, box, tmp);
            piece = StructureLoaderBridge.getLoader().load(tmp);
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            src.sendError(Text.literal("§c[SSL] Failed to capture selection: §7" + e.getMessage()));
            LOGGER.error("SSL: /tsaph multi add", e);
            return 0;
        }

        WandSession session = StructureLoaderBridge.getSession(player.getUuid()).orElseThrow();
        session.addPiece(name, role, ctype, weight, maxCount, piece);
        SelectionWandItem.clearSelection(player);

        src.sendMessage(Text.literal(String.format(
            "§a[SSL] §fAdded piece §b'%s'§f  role=§e%s§f  ctype=§e%s§f  w=§e%d§f  max=§e%s\n" +
            "§7  Bundle now has §b%d§7 piece(s). Run §b/tsaph multi info§7 to review.",
            name, role, ctype,
            weight, maxCount < 0 ? "∞" : String.valueOf(maxCount),
            session.pieceCount())));

        // Show quick-add UI for next piece
        sendNextPieceUI(src);
        return 1;
    }

    // ── /tsaph multi save ─────────────────────────────────────────────────

    private static int executeMultiSave(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        ServerPlayerEntity  player = src.getPlayerOrThrow();

        WandSession session = StructureLoaderBridge.getSession(player.getUuid()).orElse(null);
        if (session == null) {
            src.sendError(Text.literal("§c[SSL] No active session."));
            return 0;
        }
        if (session.isEmpty()) {
            src.sendError(Text.literal("§c[SSL] Session has no pieces. Use §b/tsaph multi add§c first."));
            return 0;
        }

        String bundleName = sanitise(StringArgumentType.getString(ctx, "name"));
        Path   dest       = sslDir(src).resolve(bundleName);

        MultiStructBundle bundle = session.build(bundleName);

        // Warn if the bundle has more than one ANCHOR — only the first will be used
        if (bundle.anchors().size() > 1) {
            src.sendMessage(Text.literal(
                "§6[SSL] §eWarning: bundle has " + bundle.anchors().size() +
                " ANCHOR pieces — only the first (§b" + bundle.anchors().get(0).name() +
                "§e) will be placed at generation time. Consider removing the extras."));
        }

        try {
            StructureLoaderBridge.saveMultiStruct(bundle, dest);
            MultiStructRegistry.register(bundle);
            StructureLoaderBridge.endSession(player.getUuid());

            src.sendMessage(Text.literal(String.format(
                "§a[SSL] §fSaved §b'%s'§f with §b%d§f piece(s).\n" +
                "§7  Binary: §f%s%s\n" +
                "§7  Config: §f%s%s\n" +
                "§7  Spawn with: §b/tsaph multi spawn %s <x> <z>",
                bundleName, bundle.size(),
                bundleName, TsaphMultiStructFormat.EXTENSION,
                bundleName, TsaphMultiStructFormat.COMPANION_EXTENSION,
                bundleName)));
            return 1;
        } catch (IOException e) {
            src.sendError(Text.literal("§c[SSL] Save failed: §7" + e.getMessage()));
            LOGGER.error("SSL: /tsaph multi save", e);
            return 0;
        }
    }

    // ── /tsaph multi cancel ───────────────────────────────────────────────

    private static int executeMultiCancel(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        ServerPlayerEntity  player = src.getPlayerOrThrow();

        if (!StructureLoaderBridge.hasSession(player.getUuid())) {
            src.sendMessage(Text.literal("§b[SSL] §7No active session to cancel."));
            return 1;
        }
        int count = StructureLoaderBridge.getSession(player.getUuid())
                .map(WandSession::pieceCount).orElse(0);
        StructureLoaderBridge.endSession(player.getUuid());
        src.sendMessage(Text.literal(
            "§6[SSL] §fSession cancelled. §7(" + count + " piece(s) discarded)"));
        return 1;
    }

    // ── /tsaph multi info ─────────────────────────────────────────────────

    private static int executeMultiInfo(CommandContext<ServerCommandSource> ctx)
            throws CommandSyntaxException {
        ServerCommandSource src    = ctx.getSource();
        ServerPlayerEntity  player = src.getPlayerOrThrow();

        WandSession session = StructureLoaderBridge.getSession(player.getUuid()).orElse(null);
        if (session == null) {
            src.sendMessage(Text.literal("§b[SSL] §7No active session."));
            return 1;
        }
        if (session.isEmpty()) {
            src.sendMessage(Text.literal("§b[SSL] §7Session is active but has no pieces yet."));
            return 1;
        }

        StringBuilder sb = new StringBuilder("§b[SSL] §fSession pieces (")
                .append(session.pieceCount()).append("):\n");
        for (int i = 0; i < session.pieces().size(); i++) {
            WandSession.PendingPiece p = session.pieces().get(i);
            sb.append(String.format("§7  %d. §b%-20s §eROLE=§f%-8s §eTYPE=§f%-22s §ew=%d max=%s\n",
                i + 1, p.name(), p.role(), p.connectionType(),
                p.weight(), p.maxCount() < 0 ? "∞" : String.valueOf(p.maxCount())));
        }
        src.sendMessage(Text.literal(sb.toString().stripTrailing()));
        return 1;
    }

    // ── /tsaph multi list ─────────────────────────────────────────────────

    private static int executeMultiList(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src   = ctx.getSource();
        Set<String>         names = MultiStructRegistry.names();

        // Also scan disk for any not yet loaded
        Path dir = sslDir(src);
        java.util.Set<String> diskNames = new java.util.LinkedHashSet<>(names);
        if (java.nio.file.Files.isDirectory(dir)) {
            try (Stream<Path> stream = java.nio.file.Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(TsaphMultiStructFormat.EXTENSION))
                      .map(p -> { String n = p.getFileName().toString();
                                  return n.substring(0, n.length() - TsaphMultiStructFormat.EXTENSION.length()); })
                      .forEach(diskNames::add);
            } catch (IOException ignored) {}
        }

        if (diskNames.isEmpty()) {
            src.sendMessage(Text.literal("§b[SSL] §7No .tsaphmultistruct bundles found."));
            return 1;
        }

        String list = diskNames.stream().sorted()
                .collect(java.util.stream.Collectors.joining("\n  §7- §b"));
        src.sendMessage(Text.literal("§b[SSL] §fMulti-struct bundles:\n  §7- §b" + list));
        return 1;
    }

    // ── /tsaph multi reload ───────────────────────────────────────────────

    private static int executeMultiReload(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        StructureLoaderBridge.reloadMultiStructRegistry(sslDir(src));
        src.sendMessage(Text.literal(
            "§a[SSL] §fMulti-struct registry reloaded — §b" +
            MultiStructRegistry.size() + " §fbundle(s)."));
        return 1;
    }

    // ── /tsaph multi spawn ────────────────────────────────────────────────

    private static int executeMultiSpawn(CommandContext<ServerCommandSource> ctx, int depth, Long seed) {
        ServerCommandSource src  = ctx.getSource();
        String name = sanitise(StringArgumentType.getString(ctx, "name"));
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int z = IntegerArgumentType.getInteger(ctx, "z");

        MultiStructBundle bundle;
        // Try registry first, then try loading from disk
        Optional<MultiStructBundle> opt = StructureLoaderBridge.getMultiStruct(name);
        if (opt.isPresent()) {
            bundle = opt.get();
        } else {
            Path file = sslDir(src).resolve(name + TsaphMultiStructFormat.EXTENSION);
            if (!java.nio.file.Files.exists(file)) {
                src.sendError(Text.literal(
                    "§c[SSL] Bundle '§b" + name + "§c' not found. Use §b/tsaph multi list§c."));
                return 0;
            }
            try {
                bundle = StructureLoaderBridge.loadMultiStruct(file);
                MultiStructRegistry.register(bundle);
            } catch (IOException e) {
                src.sendError(Text.literal("§c[SSL] Failed to load bundle: §7" + e.getMessage()));
                LOGGER.error("SSL: /tsaph multi spawn", e);
                return 0;
            }
        }

        ServerWorld world = src.getWorld();
        src.sendMessage(Text.literal(String.format(
            "§b[SSL] §fGenerating §b'%s'§f at §e(%d, %d)§f depth=§e%d seed=§e%s §f…",
            name, x, z, depth, seed == null ? "RANDOM" : seed)));
        
        StructureLoaderBridge.spawnMultiStruct(world, bundle, x, z, depth, seed);
        
        src.sendMessage(Text.literal("§a[SSL] §fGeneration dispatched."));
        return 1;
    }

    // ── In-game UI helpers ────────────────────────────────────────────────

    /**
     * Send a clickable role-selection UI to the player's chat.
     * Each button pre-fills the next /tsaph multi add command with the already-typed
     * piece name and the selected role, so the player only has to supply the
     * connection type (if needed).
     *
     * @param pieceName The name argument the player already typed — preserved in the
     * suggested command so they don't have to retype it.
     */
    private static void sendRoleUI(ServerCommandSource src, String pieceName) {
        src.sendMessage(Text.literal(
            "§b[SSL] §7Unknown role. Select one for §b'" + pieceName + "'§7:"));

        String base = "/tsaph multi add " + pieceName + " ";
        MutableText buttons = Text.literal("  ");
        buttons.append(clickableButton("[ANCHOR]",
            base + "ANCHOR NONE", Formatting.LIGHT_PURPLE,
            "Fixed centrepiece — placed first at the target coordinates.\nConnect other pieces outward from its connector blocks.\nOnly one ANCHOR per bundle is used."));
        buttons.append(Text.literal("  "));
        buttons.append(clickableButton("[ROOM]",
            base + "ROOM NONE", Formatting.GREEN,
            "Terminal node — house, chamber, plaza.\nNo connection type needed."));
        buttons.append(Text.literal("  "));
        buttons.append(clickableButton("[HALLWAY]",
            base + "HALLWAY STRAIGHT", Formatting.AQUA,
            "Underground connector — corridor, tunnel.\nYou can change STRAIGHT to another type."));
        buttons.append(Text.literal("  "));
        buttons.append(clickableButton("[PATH]",
            base + "PATH STRAIGHT", Formatting.YELLOW,
            "Surface connector — road, bridge, alley.\nYou can change STRAIGHT to another type."));
        src.sendMessage(buttons);
    }

    /**
     * Send a clickable connection-type selection UI for connector pieces.
     */
    private static void sendConnectionTypeUI(ServerCommandSource src,
                                              String pieceName, String roleStr,
                                              int weight, int maxCount) {
        src.sendMessage(Text.literal(
            "§b[SSL] §7Unknown connection type for §b'" + pieceName + "'§7 (role=§e" + roleStr + "§7). Pick one:"));

        // Append weight and maxCount so clicking a button produces a fully-valid command
        // that preserves what the player already typed — nothing is lost.
        String base   = "/tsaph multi add " + pieceName + " " + roleStr + " ";
        String suffix = " " + weight + " " + maxCount;

        MutableText row1 = Text.literal("  ");
        row1.append(clickableButton("[STRAIGHT]",   base + "STRAIGHT"              + suffix, Formatting.WHITE, "━━  Two open ends (forward/back)"));
        row1.append(Text.literal(" "));
        row1.append(clickableButton("[T_SHAPE]",    base + "T_SHAPE"               + suffix, Formatting.WHITE, "┤  Three-way — forward + left + right"));
        row1.append(Text.literal(" "));
        row1.append(clickableButton("[T_INVERTED]", base + "T_INVERTED"            + suffix, Formatting.WHITE, "├  Three-way — back + back-left + back-right"));

        MutableText row2 = Text.literal("  ");
        row2.append(clickableButton("[CORNER_L]",   base + "CORNER_LEFT"           + suffix, Formatting.WHITE, "┘  Left turn"));
        row2.append(Text.literal(" "));
        row2.append(clickableButton("[CORNER_R]",   base + "CORNER_RIGHT"          + suffix, Formatting.WHITE, "└  Right turn"));
        row2.append(Text.literal(" "));
        row2.append(clickableButton("[CORNER_LI]",  base + "CORNER_LEFT_INVERTED"  + suffix, Formatting.WHITE, "┐  Inverted left turn"));
        row2.append(Text.literal(" "));
        row2.append(clickableButton("[CORNER_RI]",  base + "CORNER_RIGHT_INVERTED" + suffix, Formatting.WHITE, "┌  Inverted right turn"));

        MutableText row3 = Text.literal("  ");
        row3.append(clickableButton("[MIDSECTION]", base + "MIDSECTION_BRANCH"     + suffix, Formatting.WHITE, "┣  Long straight with asymmetric side branch"));

        src.sendMessage(row1);
        src.sendMessage(row2);
        src.sendMessage(row3);
    }

    /** Send a short prompt reminding the player how to add the next piece. */
    private static void sendNextPieceUI(ServerCommandSource src) {
        MutableText msg = Text.literal("§7  Next: select a region, then ");
        msg.append(clickableButton("[Add another piece]",
            "/tsaph multi add ", Formatting.AQUA, "Click to start /tsaph multi add ..."));
        msg.append(Text.literal("  or  "));
        msg.append(clickableButton("[Save bundle]",
            "/tsaph multi save ", Formatting.GREEN, "Click to start /tsaph multi save ..."));
        src.sendMessage(msg);
    }

    /** Build a clickable chat button that suggests a command. */
    private static MutableText clickableButton(String label, String suggestCmd,
                                               Formatting color, String hoverText) {
        return Text.literal(label)
                .setStyle(Style.EMPTY
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestCmd))
                        .withHoverEvent(new net.minecraft.text.HoverEvent(
                                net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                                Text.literal(hoverText))));
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private static Path sslDir(ServerCommandSource src) {
        return src.getServer()
                  .getSavePath(WorldSavePath.ROOT)
                  .resolve("generated").resolve("ssl");
    }

    private static Path resolveStructureFile(ServerCommandSource src, String name) {
        Path dir = sslDir(src);
        Path with = dir.resolve(name + SaphStructFormat.EXTENSION);
        Path without = dir.resolve(name);
        if (Files.exists(with))    return with;
        if (Files.exists(without)) return without;
        return null;
    }

    private static String sanitise(String raw) {
        return raw.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    private static String fmt(BlockPos p) {
        return "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
    }
}
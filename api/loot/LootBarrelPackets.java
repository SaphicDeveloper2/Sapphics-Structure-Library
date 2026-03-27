package com.sapphic.ssl.api.loot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

/**
 * Client-to-server and server-to-client packets for the Loot Barrel UI.
 *
 * <h3>C2S packets</h3>
 * <ul>
 * <li>{@link SetMode}          — toggle SNAPSHOT / REGISTRY mode.</li>
 * <li>{@link SetRegistryKey}   — commit a new registry key string.</li>
 * <li>{@link SetWeight}        — adjust a per-item weight override.</li>
 * <li>{@link RequestSync}      — ask the server to send current weight overrides.</li>
 * <li>{@link ExportTsaphloot}  — saves the snapshot palette as a .tsaphloot JSON.</li>
 * </ul>
 *
 * <h3>S2C packets</h3>
 * <ul>
 * <li>{@link SyncWeights}      — full snapshot of all current weight overrides.</li>
 * </ul>
 */
public final class LootBarrelPackets {

    public static final String NS = "sapphics-structure-library";

    private LootBarrelPackets() {}

    // ── C2S: SetMode ──────────────────────────────────────────────────────

    public record SetMode(byte mode) implements CustomPayload {
        public static final CustomPayload.Id<SetMode> ID =
                new CustomPayload.Id<>(Identifier.of(NS, "loot_barrel_set_mode"));
        public static final PacketCodec<PacketByteBuf, SetMode> CODEC = PacketCodec.of(
                (v, buf) -> buf.writeByte(v.mode()),
                buf -> new SetMode(buf.readByte()));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── C2S: SetRegistryKey ───────────────────────────────────────────────

    public record SetRegistryKey(String key) implements CustomPayload {
        public static final CustomPayload.Id<SetRegistryKey> ID =
                new CustomPayload.Id<>(Identifier.of(NS, "loot_barrel_set_key"));
        public static final PacketCodec<PacketByteBuf, SetRegistryKey> CODEC = PacketCodec.of(
                (v, buf) -> buf.writeString(v.key(), 256),
                buf -> new SetRegistryKey(buf.readString(256)));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── C2S: SetWeight ────────────────────────────────────────────────────

    public record SetWeight(String itemId, int weight) implements CustomPayload {
        public static final CustomPayload.Id<SetWeight> ID =
                new CustomPayload.Id<>(Identifier.of(NS, "loot_barrel_set_weight"));
        public static final PacketCodec<PacketByteBuf, SetWeight> CODEC = PacketCodec.of(
                (v, buf) -> { buf.writeString(v.itemId(), 256); buf.writeInt(v.weight()); },
                buf -> new SetWeight(buf.readString(256), buf.readInt()));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── C2S: RequestSync ─────────────────────────────────────────────────

    public record RequestSync() implements CustomPayload {
        public static final CustomPayload.Id<RequestSync> ID =
                new CustomPayload.Id<>(Identifier.of(NS, "loot_barrel_request_sync"));
        public static final PacketCodec<PacketByteBuf, RequestSync> CODEC = PacketCodec.of(
                (v, buf) -> {},
                buf -> new RequestSync());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── C2S: ExportTsaphloot ─────────────────────────────────────────────
    
    public record ExportTsaphloot(String name) implements CustomPayload {
        public static final CustomPayload.Id<ExportTsaphloot> ID =
                new CustomPayload.Id<>(Identifier.of(NS, "loot_barrel_export"));
        public static final PacketCodec<PacketByteBuf, ExportTsaphloot> CODEC = PacketCodec.of(
                (v, buf) -> buf.writeString(v.name(), 256),
                buf -> new ExportTsaphloot(buf.readString(256)));
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── S2C: SyncWeights ─────────────────────────────────────────────────

    public record SyncWeights(Map<String, Integer> overrides) implements CustomPayload {
        public static final CustomPayload.Id<SyncWeights> ID =
                new CustomPayload.Id<>(Identifier.of(NS, "loot_barrel_sync_weights"));
        public static final PacketCodec<PacketByteBuf, SyncWeights> CODEC = PacketCodec.of(
                (v, buf) -> {
                    buf.writeInt(v.overrides().size());
                    v.overrides().forEach((k, w) -> { buf.writeString(k, 256); buf.writeInt(w); });
                },
                buf -> {
                    int size = buf.readInt();
                    Map<String, Integer> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        String k = buf.readString(256);
                        int    w = buf.readInt();
                        map.put(k, w);
                    }
                    return new SyncWeights(map);
                });
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── Registration ──────────────────────────────────────────────────────

    public static void register() {
        PayloadTypeRegistry.playC2S().register(SetMode.ID,       SetMode.CODEC);
        PayloadTypeRegistry.playC2S().register(SetRegistryKey.ID, SetRegistryKey.CODEC);
        PayloadTypeRegistry.playC2S().register(SetWeight.ID,      SetWeight.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestSync.ID,    RequestSync.CODEC);
        PayloadTypeRegistry.playC2S().register(ExportTsaphloot.ID, ExportTsaphloot.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncWeights.ID, SyncWeights.CODEC);

        // ── Server receivers ──────────────────────────────────────────────

        ServerPlayNetworking.registerGlobalReceiver(SetMode.ID, (payload, ctx) ->
            ctx.server().execute(() -> {
                if (!(ctx.player().currentScreenHandler instanceof LootBarrelScreenHandler h)) return;
                h.getBarrel().setMode(LootBarrelBlockEntity.Mode.fromWire(payload.mode()));
            }));

        ServerPlayNetworking.registerGlobalReceiver(SetWeight.ID, (payload, ctx) ->
            ctx.server().execute(() -> {
                if (!(ctx.player().currentScreenHandler instanceof LootBarrelScreenHandler h)) return;
                h.getBarrel().setWeightOverride(payload.itemId(), payload.weight());
                sendSyncWeights(ctx.player(), h.getBarrel());
            }));

        ServerPlayNetworking.registerGlobalReceiver(SetRegistryKey.ID, (payload, ctx) ->
            ctx.server().execute(() -> {
                if (!(ctx.player().currentScreenHandler instanceof LootBarrelScreenHandler h)) return;
                LootBarrelBlockEntity barrel = h.getBarrel();
                String key = payload.key().strip();
                if (!key.isEmpty()) barrel.setMode(LootBarrelBlockEntity.Mode.REGISTRY);
                barrel.setRegistryKey(key.isEmpty() ? null : key);
            }));

        ServerPlayNetworking.registerGlobalReceiver(RequestSync.ID, (payload, ctx) ->
            ctx.server().execute(() -> {
                if (!(ctx.player().currentScreenHandler instanceof LootBarrelScreenHandler h)) return;
                sendSyncWeights(ctx.player(), h.getBarrel());
            }));

        // Handles exporting current snapshot UI out into a tsaphloot file cleanly
        ServerPlayNetworking.registerGlobalReceiver(ExportTsaphloot.ID, (payload, ctx) ->
            ctx.server().execute(() -> {
                if (!(ctx.player().currentScreenHandler instanceof LootBarrelScreenHandler h)) return;
                
                String name = payload.name().replaceAll("[^a-zA-Z0-9_\\-]", "_");
                if (name.isBlank()) name = "exported_barrel";
                
                RegistryWrapper.WrapperLookup registries = com.sapphic.ssl.compat.SslCompat.get()
                        .registryLookup(ctx.player().getServerWorld());
                
                List<TsaphLootEntry> entries = com.sapphic.ssl.internal.loot.SmartLootEngine.buildEntriesFromBarrel(
                    h.getBarrel().getItems(), h.getBarrel().getWeightOverrides(), registries);
                
                int rolls = Math.min(entries.size(), 8);
                if (rolls == 0) rolls = 1;
                
                // Build JSON tree dynamically avoiding needing any explicit format wrappers
                com.google.gson.JsonObject root = new com.google.gson.JsonObject();
                com.google.gson.JsonArray poolsArray = new com.google.gson.JsonArray();
                com.google.gson.JsonObject poolObj = new com.google.gson.JsonObject();
                
                com.google.gson.JsonObject rollsObj = new com.google.gson.JsonObject();
                rollsObj.addProperty("min", rolls);
                rollsObj.addProperty("max", rolls);
                poolObj.add("rolls", rollsObj);
                
                com.google.gson.JsonArray entriesArray = new com.google.gson.JsonArray();
                for (TsaphLootEntry entry : entries) {
                    entriesArray.add(entry.toJson());
                }
                poolObj.add("entries", entriesArray);
                poolsArray.add(poolObj);
                root.add("pools", poolsArray);
                
                try {
                    java.nio.file.Path dir = ctx.server().getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                        .resolve("generated").resolve("ssl").resolve("tsaphloot");
                    java.nio.file.Files.createDirectories(dir);
                    java.nio.file.Path file = dir.resolve(name + ".tsaphloot");
                    
                    com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                    java.nio.file.Files.writeString(file, gson.toJson(root));
                    
                    ctx.player().sendMessage(net.minecraft.text.Text.literal("§a[SSL] §fExported barrel to §b" + name + ".tsaphloot"), false);
                } catch (Exception e) {
                    ctx.player().sendMessage(net.minecraft.text.Text.literal("§c[SSL] Export failed: " + e.getMessage()), false);
                }
            }));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static void sendSyncWeights(net.minecraft.server.network.ServerPlayerEntity player,
                                        LootBarrelBlockEntity barrel) {
        ServerPlayNetworking.send(player,
                new SyncWeights(new HashMap<>(barrel.getWeightOverrides())));
    }
}
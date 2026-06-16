package com.hoaug.recall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RecallState extends PersistentState {
    private static final String STATE_ID = "recall_homes";

    private final Map<UUID, GlobalPos> homes = new HashMap<>();
    private final Map<UUID, Long> lastSetHomeTimes = new HashMap<>();

    private record HomeEntry(
            String uuid,
            String dimension,
            int x,
            int y,
            int z,
            long lastTime) {
        static final Codec<HomeEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("uuid").forGetter(HomeEntry::uuid),
                Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(HomeEntry::dimension),
                Codec.INT.optionalFieldOf("x", 0).forGetter(HomeEntry::x),
                Codec.INT.optionalFieldOf("y", 64).forGetter(HomeEntry::y),
                Codec.INT.optionalFieldOf("z", 0).forGetter(HomeEntry::z),
                Codec.LONG.optionalFieldOf("last_time", 0L).forGetter(HomeEntry::lastTime))
                .apply(instance, HomeEntry::new));
    }

    public static final Codec<RecallState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            HomeEntry.CODEC.listOf().optionalFieldOf("homes", List.of()).forGetter(RecallState::toEntries))
            .apply(instance, RecallState::fromEntries));

    public static final PersistentStateType<RecallState> TYPE = new PersistentStateType<>(
            STATE_ID,
            RecallState::new,
            CODEC,
            null);

    public void setHome(UUID uuid, GlobalPos pos) {
        homes.put(uuid, pos);
        lastSetHomeTimes.put(uuid, System.currentTimeMillis() / 1000L);
        markDirty();
    }

    public GlobalPos getHome(UUID uuid) {
        return homes.get(uuid);
    }

    public long getLastSetHomeTime(UUID uuid) {
        return lastSetHomeTimes.getOrDefault(uuid, 0L);
    }

    public void resetSetHomeTime(UUID uuid) {
        lastSetHomeTimes.remove(uuid);
        markDirty();
    }

    private List<HomeEntry> toEntries() {
        return homes.entrySet().stream().map(entry -> {
            UUID uuid = entry.getKey();
            GlobalPos globalPos = entry.getValue();
            BlockPos pos = globalPos.pos();

            return new HomeEntry(
                    uuid.toString(),
                    globalPos.dimension().getValue().toString(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    lastSetHomeTimes.getOrDefault(uuid, 0L));
        }).toList();
    }

    private static RecallState fromEntries(List<HomeEntry> entries) {
        RecallState state = new RecallState();

        for (HomeEntry entry : entries) {
            try {
                UUID uuid = UUID.fromString(entry.uuid());
                Identifier dimId = Identifier.of(entry.dimension());
                RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, dimId);
                BlockPos pos = new BlockPos(entry.x(), entry.y(), entry.z());

                state.homes.put(uuid, GlobalPos.create(dimension, pos));
                state.lastSetHomeTimes.put(uuid, entry.lastTime());
            } catch (Exception ignored) {
                // Skip broken saved entry.
            }
        }

        return state;
    }

    public static RecallState getServerState(MinecraftServer server) {
        return server.getOverworld()
                .getPersistentStateManager()
                .getOrCreate(TYPE);
    }
}
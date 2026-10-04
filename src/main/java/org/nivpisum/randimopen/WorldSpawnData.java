package org.nivpisum.randimopen;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

/** Only the dimension and selection provenance are extra data; coordinates stay in vanilla level.dat. */
public final class WorldSpawnData extends SavedData {
    public static final String DATA_NAME = "randimopen_spawn";
    public static final int FORMAT = 1;
    private String dimension;
    private List<String> candidates = List.of();
    private long candidatePosition;
    private double scale;
    private boolean legacy;

    public static WorldSpawnData get(MinecraftServer server) {
        return get(server.overworld().getDataStorage(), server.getWorldPath(LevelResource.ROOT).resolve("data"));
    }

    static WorldSpawnData get(DimensionDataStorage storage, Path folder) {
        WorldSpawnData existing = readChecked(storage, folder, WorldSpawnData::load, DATA_NAME);
        if (existing != null) return existing;
        WorldSpawnData created = new WorldSpawnData();
        storage.set(DATA_NAME, created);
        return created;
    }

    /** Vanilla catches load exceptions and returns null. Distinguish a missing file from a failed read. */
    static <T extends SavedData> T readChecked(DimensionDataStorage storage, Path folder,
                                              Function<CompoundTag, T> loader, String name) {
        T data = storage.get(loader, name);
        if (data == null && !Files.notExists(folder.resolve(name + ".dat")))
            throw new IllegalStateException("Existing saved data " + name
                + " could not be read. It was preserved; no world-spawn reroll is permitted.");
        return data;
    }

    public boolean initialized() { return dimension != null; }
    public String dimensionId() { return dimension; }
    public ResourceKey<Level> dimensionKey() {
        if (!initialized()) throw new IllegalStateException("World spawn has not been selected");
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(dimension));
    }
    public List<String> candidates() { return candidates; }
    public BlockPos candidatePosition() { return BlockPos.of(candidatePosition); }
    public double scale() { return scale; }
    public boolean migratedLegacy() { return legacy; }

    public void initialize(String id, List<String> candidates, BlockPos candidate, double scale, boolean legacy) {
        if (initialized()) throw new IllegalStateException("A saved world spawn must not be rerolled");
        if (ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("Invalid dimension ID: " + id);
        this.dimension = id;
        this.candidates = List.copyOf(candidates);
        this.candidatePosition = candidate.asLong();
        this.scale = scale;
        this.legacy = legacy;
        setDirty();
    }

    public static WorldSpawnData load(CompoundTag tag) {
        if (tag.getInt("format") != FORMAT)
            throw new IllegalStateException("Unsupported RandomDimensionOpening saved-data format: " + tag.getInt("format"));
        String dimension = tag.getString("dimension");
        if (ResourceLocation.tryParse(dimension) == null)
            throw new IllegalStateException("Invalid saved world-spawn dimension: " + dimension);
        WorldSpawnData data = new WorldSpawnData();
        data.dimension = dimension;
        data.candidates = tag.getList("candidates", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
        data.candidatePosition = tag.getLong("candidate");
        data.scale = tag.getDouble("scale");
        data.legacy = tag.getBoolean("legacy");
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        if (!initialized()) throw new IllegalStateException("Cannot save an uninitialized world spawn");
        tag.putInt("format", FORMAT);
        tag.putString("dimension", dimension);
        ListTag ids = new ListTag();
        candidates.forEach(id -> ids.add(StringTag.valueOf(id)));
        tag.put("candidates", ids);
        tag.putLong("candidate", candidatePosition);
        tag.putDouble("scale", scale);
        tag.putBoolean("legacy", legacy);
        return tag;
    }
}

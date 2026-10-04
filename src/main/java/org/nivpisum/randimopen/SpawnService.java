package org.nivpisum.randimopen;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.WeakHashMap;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.LevelResource;
import org.nivpisum.randimopen.mixin.MinecraftServerAccessor;

public final class SpawnService {
    private record InitialOptions(boolean bonusChest, boolean debug) {}
    private record Generation(ServerLevel level, BlockPos candidate) {}
    private static final Map<MinecraftServer, InitialOptions> DEFERRED = new WeakHashMap<>();
    private static final ThreadLocal<Generation> GENERATING = new ThreadLocal<>();
    private static final long RANDOM_SALT = 0x52444f3253504157L;

    private SpawnService() {}

    /** Defer only vanilla's new-world spawn call until all actual ServerLevels exist. */
    public static boolean deferInitialSpawn(ServerLevel level, boolean bonusChest, boolean debug) {
        if (GENERATING.get() != null) return false;
        DEFERRED.put(level.getServer(), new InitialOptions(bonusChest, debug));
        return true;
    }

    /** Replace the climate sampler's search origin, not the vanilla terrain-search procedure. */
    public static BlockPos searchOrigin(Climate.Sampler sampler) {
        Generation generation = GENERATING.get();
        return generation == null ? sampler.findSpawnPosition() : generation.candidate;
    }

    public static void initialize(MinecraftServer server) {
        InitialOptions options = DEFERRED.remove(server);
        WorldSpawnData data = WorldSpawnData.get(server);
        if (data.initialized()) {
            selectedLevel(server); // Refuse to replace a missing saved dimension with a new random choice.
            return;
        }
        List<String> ids = StreamSupport.stream(server.getAllLevels().spliterator(), false)
            .map(level -> level.dimension().location().toString()).sorted().toList();
        ServerLevelData vanillaData = (ServerLevelData) server.overworld().getLevelData();
        LegacyData old = WorldSpawnData.readChecked(server.overworld().getDataStorage(),
            server.getWorldPath(LevelResource.ROOT).resolve("data"), LegacyData::load, "randimopen_mapvars");
        if (old != null && old.choice >= 1 && old.choice <= 3 && old.choice == Math.rint(old.choice)) {
            String id = old.choice == 1 ? "minecraft:overworld" : old.choice == 2 ? "minecraft:the_nether" : "minecraft:the_end";
            BlockPos point = old.choice == 1 ? server.overworld().getSharedSpawnPos()
                : BlockPos.containing(old.x, old.y, old.z);
            data.initialize(id, ids, point, 0.0, true);
            selectedLevel(server);
            vanillaData.setSpawn(point, vanillaData.getSpawnAngle());
            RandomDimensionOpening.LOGGER.info("Imported 1.0.0 world spawn: {} {}", id, point);
            return;
        }
        SplittableRandom random = new SplittableRandom(server.overworld().getSeed() ^ RANDOM_SALT);
        String selected = DimensionSelection.select(ids, random);
        ServerLevel level = server.getAllLevels().iterator().next();
        for (ServerLevel candidate : server.getAllLevels())
            if (candidate.dimension().location().toString().equals(selected)) { level = candidate; break; }
        double scale = RandomDimensionOpening.COORDINATE_SCALE.get();
        WorldBorder border = level.getWorldBorder();
        // Exactly the integer cells accepted by vanilla WorldBorder.isWithinBounds(BlockPos).
        RadialDistribution.Bounds bounds = new RadialDistribution.Bounds(
            (int) Math.floor(border.getMinX()), (int) Math.ceil(border.getMaxX()) - 1,
            (int) Math.floor(border.getMinZ()), (int) Math.ceil(border.getMaxZ()) - 1);
        RadialDistribution.Point horizontal = new RadialDistribution(bounds, scale).sample(random);
        BlockPos target = new BlockPos(horizontal.x(), 0, horizontal.z());
        boolean newWorld = options != null;
        if (options == null) options = new InitialOptions(false, server.getWorldData().isDebugWorld());
        GENERATING.set(new Generation(level, target));
        try {
            // createLevels sets this flag after the deferred call; preserve the original event's flag.
            if (newWorld) vanillaData.setInitialized(false);
            // Same private method used by vanilla world creation: 11x11 chunk search,
            // height fallback, Forge spawn event, and the user's original bonus-chest choice.
            MinecraftServerAccessor.randimopen$setInitialSpawn(level, vanillaData, options.bonusChest, options.debug);
            if (newWorld) vanillaData.setInitialized(true);
        } finally {
            GENERATING.remove();
        }
        data.initialize(selected, ids, target, scale, false);
        RandomDimensionOpening.LOGGER.info("World spawn selected from {} dimensions: {}; candidate {}; vanilla result {}",
            ids.size(), selected, target, level.getSharedSpawnPos());
    }

    public static ServerLevel selectedLevel(MinecraftServer server) {
        WorldSpawnData data = WorldSpawnData.get(server);
        if (!data.initialized()) throw new IllegalStateException("RandomDimensionOpening world spawn is not initialized");
        ServerLevel level = server.getLevel(data.dimensionKey());
        if (level == null)
            throw new IllegalStateException("Saved world-spawn dimension " + data.dimensionId()
                + " is missing. Restore its mod/datapack; the saved selection was not rerolled.");
        return level;
    }

    /** DerivedLevelData intentionally ignores setSpawn; retain the vanilla world coordinate storage. */
    public static void retainWorldSpawnCoordinates(ServerLevel level, BlockPos point, float angle) {
        if (level.dimension() == Level.OVERWORLD) return;
        WorldSpawnData data = WorldSpawnData.get(level.getServer());
        Generation generation = GENERATING.get();
        if ((generation != null && generation.level == level)
            || (data.initialized() && level.dimension().equals(data.dimensionKey())))
            ((ServerLevelData) level.getServer().overworld().getLevelData()).setSpawn(point, angle);
    }

    private static final class LegacyData extends SavedData {
        double choice, x, y, z;
        static LegacyData load(CompoundTag tag) {
            for (String key : List.of("dimopen", "px", "py", "pz"))
                if (!tag.contains(key, Tag.TAG_ANY_NUMERIC))
                    throw new IllegalStateException("Invalid 1.0.0 world-spawn data: missing numeric " + key);
            LegacyData data = new LegacyData();
            data.choice = tag.getDouble("dimopen");
            data.x = tag.getDouble("px"); data.y = tag.getDouble("py"); data.z = tag.getDouble("pz");
            if (!Double.isFinite(data.choice) || data.choice < 0 || data.choice > 3 || data.choice != Math.rint(data.choice)
                || !Double.isFinite(data.x) || !Double.isFinite(data.y) || !Double.isFinite(data.z))
                throw new IllegalStateException("Invalid 1.0.0 world-spawn choice or coordinates");
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag) { return tag; } // Never marked dirty or rewritten.
    }
}

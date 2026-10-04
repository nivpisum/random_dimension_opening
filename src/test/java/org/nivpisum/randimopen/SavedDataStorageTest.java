package org.nivpisum.randimopen;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavedDataStorageTest {
    @TempDir Path folder;
    private static final DataFixer NO_UPGRADE = new DataFixer() {
        @Override public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int from, int to) { return input; }
        @Override public Schema getSchema(int key) { throw new UnsupportedOperationException(); }
    };

    @BeforeAll static void version() { SharedConstants.tryDetectVersion(); }

    @Test void aMissingFileAllowsOneNewSelectionWhichIsThenReadFromNativeStorage() {
        DimensionDataStorage first = new DimensionDataStorage(folder.toFile(), NO_UPGRADE);
        WorldSpawnData original = WorldSpawnData.get(first, folder);
        original.initialize("test:flat", List.of("test:flat"), BlockPos.ZERO, 1024, false);
        first.save();
        WorldSpawnData loaded = WorldSpawnData.get(new DimensionDataStorage(folder.toFile(), NO_UPGRADE), folder);
        assertEquals("test:flat", loaded.dimensionId());
        assertFalse(loaded.isDirty());
    }

    @Test void futureFormatIsNotTreatedAsMissingAndCannotBeOverwritten() throws Exception {
        CompoundTag payload = new CompoundTag();
        payload.putInt("format", WorldSpawnData.FORMAT + 1);
        payload.putString("dimension", "test:future");
        write(payload);
        assertReadFailurePreservesBytes();
    }

    @Test void malformedNbtIsNotTreatedAsMissingAndCannotBeOverwritten() throws Exception {
        Files.write(file(), new byte[] {0, 1, 2, 3, 4});
        assertReadFailurePreservesBytes();
    }

    @Test void invalidDimensionSurvivesNativeStorageSwallowingTheLoadException() throws Exception {
        CompoundTag payload = new CompoundTag();
        payload.putInt("format", WorldSpawnData.FORMAT);
        payload.putString("dimension", "Invalid ID!");
        write(payload);
        assertReadFailurePreservesBytes();
    }

    private Path file() { return folder.resolve(WorldSpawnData.DATA_NAME + ".dat"); }
    private void write(CompoundTag payload) throws Exception {
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", payload);
        wrapper.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        NbtIo.writeCompressed(wrapper, file().toFile());
    }
    private void assertReadFailurePreservesBytes() throws Exception {
        byte[] before = Files.readAllBytes(file());
        DimensionDataStorage storage = new DimensionDataStorage(folder.toFile(), NO_UPGRADE);
        assertThrows(IllegalStateException.class, () -> WorldSpawnData.get(storage, folder));
        assertThrows(IllegalStateException.class, () -> WorldSpawnData.get(storage, folder));
        storage.save();
        assertArrayEquals(before, Files.readAllBytes(file()));
    }
}

package org.nivpisum.randimopen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorldSpawnDataTest {
    @Test void savedSelectionRoundTripsWithoutReplacingVanillaCoordinateStorage() {
        WorldSpawnData data = new WorldSpawnData();
        data.initialize("test:void", List.of("minecraft:overworld", "test:void"), new BlockPos(-481, 0, 782), 1024, false);
        CompoundTag tag = data.save(new CompoundTag());
        WorldSpawnData loaded = WorldSpawnData.load(tag);
        assertEquals(data.dimensionId(), loaded.dimensionId());
        assertEquals(data.candidates(), loaded.candidates());
        assertEquals(data.candidatePosition(), loaded.candidatePosition());
        assertEquals(1024, loaded.scale());
        assertFalse(loaded.migratedLegacy());
        assertFalse(tag.contains("SpawnX"));
        assertFalse(tag.contains("SpawnY"));
        assertFalse(tag.contains("SpawnZ"));
    }

    @Test void initializedSelectionCannotSilentlyReroll() {
        WorldSpawnData data = new WorldSpawnData();
        data.initialize("minecraft:the_end", List.of("minecraft:the_end"), BlockPos.ZERO, 1024, false);
        assertThrows(IllegalStateException.class, () -> data.initialize("minecraft:overworld", List.of(), BlockPos.ZERO, 2, false));
    }

    @Test void malformedOrFutureDataFailsExplicitly() {
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("format", WorldSpawnData.FORMAT);
        malformed.putString("dimension", "Invalid ID!");
        assertThrows(IllegalStateException.class, () -> WorldSpawnData.load(malformed));
        malformed.putInt("format", WorldSpawnData.FORMAT + 1);
        assertThrows(IllegalStateException.class, () -> WorldSpawnData.load(malformed));
    }
}

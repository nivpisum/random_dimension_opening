package org.nivpisum.randimopen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class DimensionSelectionTest {
    @Test void everyLoadedIdIncludingCustomVoidDimensionsIsEquallyEligible() {
        List<String> ids = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "test:void", "test:custom");
        Map<String, Integer> counts = new HashMap<>();
        SplittableRandom random = new SplittableRandom(41);
        for (int i = 0; i < 100_000; i++) counts.merge(DimensionSelection.select(ids, random), 1, Integer::sum);
        assertEquals(ids.size(), counts.size());
        for (String id : ids) assertTrue(counts.get(id) > 19_000 && counts.get(id) < 21_000);
    }

    @Test void orderingAndDuplicatesDoNotChangeASeededChoice() {
        List<String> forward = List.of("test:a", "test:b", "test:c");
        List<String> reversed = List.of("test:c", "test:b", "test:a", "test:c");
        for (int i = 0; i < 100; i++)
            assertEquals(DimensionSelection.select(forward, new SplittableRandom(i)),
                DimensionSelection.select(reversed, new SplittableRandom(i)));
    }

    @Test void emptySetIsAnErrorAndSingleDimensionIsAlwaysChosen() {
        assertThrows(IllegalArgumentException.class, () -> DimensionSelection.select(List.of(), new SplittableRandom()));
        assertEquals("test:a", DimensionSelection.select(List.of("test:a"), new SplittableRandom()));
    }
}

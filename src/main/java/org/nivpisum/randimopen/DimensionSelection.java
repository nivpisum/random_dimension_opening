package org.nivpisum.randimopen;

import java.util.Collection;
import java.util.List;
import java.util.random.RandomGenerator;

public final class DimensionSelection {
    private DimensionSelection() {}

    /** Sort for reproducibility, then make exactly one unbiased bounded-integer draw. */
    public static String select(Collection<String> ids, RandomGenerator random) {
        List<String> candidates = ids.stream().distinct().sorted().toList();
        if (candidates.isEmpty()) throw new IllegalArgumentException("World has no loaded dimensions");
        return candidates.get(random.nextInt(candidates.size()));
    }
}

package org.nivpisum.randimopen;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RadialDistributionTest {
    @Test void originIsMaximumAndEveryWorldCoordinateRetainsPositiveWeight() {
        RadialDistribution d = new RadialDistribution(new RadialDistribution.Bounds(-100, 100, -100, 100), 1024);
        assertEquals(1.0, d.weight(0, 0));
        assertTrue(d.weight(0, 0) > d.weight(1, 0));
        assertTrue(d.weight(1, 0) > d.weight(2, 0));
        assertEquals(d.weight(3, 4), d.weight(0, 5));
        assertEquals(d.weight(3, 4), d.weight(-4, -3));
        assertTrue(d.weight(30_000_000, 30_000_000) > 0.0);
    }

    @Test void smallGridFrequenciesMatchExactRadialWeights() {
        checkFrequencies(new RadialDistribution.Bounds(-3, 3, -3, 3), 1.7, 300_000, 114);
    }

    @Test void remoteNarrowBorderHasNoClippedEdgeSpike() {
        checkFrequencies(new RadialDistribution.Bounds(29_999_990, 29_999_995, -29_999_994, -29_999_990),
            1024, 200_000, 90);
    }

    @Test void thinOffsetBorderRemainsConditionedOnItsExactWeights() {
        checkFrequencies(new RadialDistribution.Bounds(-2, -2, -8, 6), 2, 200_000, 60);
    }

    @Test void singleCellHasProbabilityOne() {
        RadialDistribution d = new RadialDistribution(new RadialDistribution.Bounds(137, 137, -79, -79), 1024);
        for (int i = 0; i < 100; i++) assertEquals(new RadialDistribution.Point(137, -79), d.sample(new SplittableRandom(i)));
    }

    @Test void fullWorldBuildAndSamplingAreBoundedInPractice() {
        assertTimeout(Duration.ofSeconds(10), () -> {
            RadialDistribution d = new RadialDistribution(new RadialDistribution.Bounds(-29_999_984, 29_999_983,
                -29_999_984, 29_999_983), 1024);
            SplittableRandom random = new SplittableRandom(840721);
            int inner = 0;
            for (int i = 0; i < 100_000; i++) {
                RadialDistribution.Point p = d.sample(random);
                if ((long) p.x() * p.x() + (long) p.z() * p.z() <= 1024L * 1024) inner++;
            }
            assertTrue(inner > 48_500 && inner < 51_500, "Continuous-limit median radius should be near 1024");
        });
    }

    @Test void minimumScaleStillSamplesAFullWorldWithoutUniformFallback() {
        assertTimeout(Duration.ofSeconds(10), () -> {
            RadialDistribution d = new RadialDistribution(new RadialDistribution.Bounds(-29_999_984, 29_999_983,
                -29_999_984, 29_999_983), 1);
            assertTrue(d.weight(29_999_983, 29_999_983) > 0);
            assertNotNull(d.sample(new SplittableRandom(17)));
        });
    }

    @Test void invalidBoundsAndScaleAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RadialDistribution.Bounds(1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RadialDistribution.Bounds(Integer.MIN_VALUE, 0, 0, 0));
        RadialDistribution.Bounds b = new RadialDistribution.Bounds(0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new RadialDistribution(b, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new RadialDistribution(b, 0));
    }

    private static void checkFrequencies(RadialDistribution.Bounds b, double scale, int draws, double chiSquareLimit) {
        RadialDistribution d = new RadialDistribution(b, scale);
        Map<RadialDistribution.Point, Integer> counts = new HashMap<>();
        SplittableRandom random = new SplittableRandom(39884);
        double total = 0;
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) total += d.weight(x, z);
        for (int i = 0; i < draws; i++) {
            RadialDistribution.Point p = d.sample(random);
            assertTrue(b.contains(p));
            counts.merge(p, 1, Integer::sum);
        }
        double chiSquare = 0;
        for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
            double expected = draws * d.weight(x, z) / total;
            double difference = counts.getOrDefault(new RadialDistribution.Point(x, z), 0) - expected;
            chiSquare += difference * difference / expected;
        }
        assertTrue(chiSquare < chiSquareLimit, "Unexpected distribution bias: chi square " + chiSquare);
    }
}

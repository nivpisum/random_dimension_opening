package org.nivpisum.randimopen;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Discrete radial weights on a legal rectangle, with no clipping or tail fallback. */
public final class RadialDistribution {
    public record Point(int x, int z) {}
    public record Bounds(int minX, int maxX, int minZ, int maxZ) {
        public Bounds {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Empty coordinate bounds");
            // Minecraft's hard coordinate limit; also guarantees all areas and squared radii fit in long.
            if (Math.max(Math.abs((long) minX), Math.abs((long) maxX)) > 30_000_000L
                || Math.max(Math.abs((long) minZ), Math.abs((long) maxZ)) > 30_000_000L)
                throw new IllegalArgumentException("Coordinate bounds exceed Minecraft's world limit");
        }
        public boolean contains(Point point) {
            return point.x >= minX && point.x <= maxX && point.z >= minZ && point.z <= maxZ;
        }
    }

    private static final double SQRT_TWO = Math.sqrt(2.0);
    private final double scaleSquared;
    private final Node root;

    public RadialDistribution(Bounds bounds, double scale) {
        Objects.requireNonNull(bounds);
        if (!Double.isFinite(scale) || scale < 1.0 || scale > 1_000_000.0)
            throw new IllegalArgumentException("Scale must be finite and in [1, 1000000]");
        scaleSquared = scale * scale;
        root = build(bounds);
    }

    public Point sample(RandomGenerator random) {
        Objects.requireNonNull(random);
        while (true) {
            Node node = root;
            while (node.left != null) {
                // Test the smaller branch directly: a cumulative near-1 threshold could erase a small tail.
                Node smaller = node.left.mass <= node.right.mass ? node.left : node.right;
                Node larger = smaller == node.left ? node.right : node.left;
                node = random.nextDouble() < smaller.mass / node.mass ? smaller : larger;
            }
            Bounds b = node.bounds;
            int x = random.nextInt(b.minX, b.maxX + 1);
            int z = random.nextInt(b.minZ, b.maxZ + 1);
            double ratio = node.nearestDenominator / denominator(x, z);
            // Each leaf has an acceptance probability of at least 1/2.
            if (random.nextDouble() < ratio * ratio) return new Point(x, z);
        }
    }

    public double weight(int x, int z) {
        double ratio = scaleSquared / denominator(x, z);
        return ratio * ratio;
    }

    private double denominator(int x, int z) {
        return scaleSquared + (long) x * x + (long) z * z;
    }

    private Node build(Bounds bounds) {
        int nearX = Math.max(bounds.minX, Math.min(0, bounds.maxX));
        int nearZ = Math.max(bounds.minZ, Math.min(0, bounds.maxZ));
        int farX = Math.abs((long) bounds.minX) >= Math.abs((long) bounds.maxX) ? bounds.minX : bounds.maxX;
        int farZ = Math.abs((long) bounds.minZ) >= Math.abs((long) bounds.maxZ) ? bounds.minZ : bounds.maxZ;
        double nearest = denominator(nearX, nearZ);
        long width = (long) bounds.maxX - bounds.minX + 1;
        long depth = (long) bounds.maxZ - bounds.minZ + 1;
        if (denominator(farX, farZ) <= SQRT_TWO * nearest) {
            double ratio = scaleSquared / nearest;
            return new Node(bounds, nearest, width * depth * ratio * ratio, null, null);
        }
        Bounds first;
        Bounds second;
        if (width >= depth) {
            int split = (int) (bounds.minX + (width - 1) / 2);
            first = new Bounds(bounds.minX, split, bounds.minZ, bounds.maxZ);
            second = new Bounds(split + 1, bounds.maxX, bounds.minZ, bounds.maxZ);
        } else {
            int split = (int) (bounds.minZ + (depth - 1) / 2);
            first = new Bounds(bounds.minX, bounds.maxX, bounds.minZ, split);
            second = new Bounds(bounds.minX, bounds.maxX, split + 1, bounds.maxZ);
        }
        Node left = build(first);
        Node right = build(second);
        return new Node(bounds, nearest, left.mass + right.mass, left, right);
    }

    private record Node(Bounds bounds, double nearestDenominator, double mass, Node left, Node right) {}
}

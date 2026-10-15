package me.cortex.voxy.client.core.rendering.building;

/** Worker-owned heights follow the same eight-bucket compaction as the packed quad buffer. */
final class FluidHeightSidecar {
    private final long[][] buckets = new long[8][];
    private boolean present;
    void reset() { present = false; }
    void put(int bucket, int index, long heights) {
        if (bucket < 0 || bucket >= 8 || index < 0 || index >= 65536)
            throw new IllegalArgumentException("Fluid sidecar bucket capacity exceeded");
        if (heights != 0) {
            if (buckets[bucket] == null) buckets[bucket] = new long[65536];
            present = true;
        }
        // Non-fluid writes clear slots retained from the worker's preceding section.
        if (buckets[bucket] != null) buckets[bucket][index] = heights;
    }
    long[] copy(int[] counts) {
        if (counts.length != 8) throw new IllegalArgumentException("Expected eight quad buckets");
        int total = 0;
        for (int count : counts) {
            if (count < 0 || count > 65536) throw new IllegalArgumentException("Quad bucket capacity exceeded");
            total = Math.addExact(total, count);
        }
        if (!present) return new long[0];
        long[] packed = new long[total];
        int offset = 0;
        for (int bucket = 0; bucket < counts.length; bucket++) {
            if (buckets[bucket] != null) System.arraycopy(buckets[bucket], 0, packed, offset, counts[bucket]);
            offset += counts[bucket];
        }
        return packed;
    }
}

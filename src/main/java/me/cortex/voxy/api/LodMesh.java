package me.cortex.voxy.api;

import java.nio.LongBuffer;

/** Owned immutable copy of Voxy's merged quads. Coordinates are section-local voxels. */
public final class LodMesh {
    public final long key;
    public final long revision;
    public final int level, x, y, z, aabb, children;
    private final long[] quads;
    private final int[] offsets;
    private final long[] fluidHeights;
    private long validatedEpoch;

    public LodMesh(long key, long revision, int level, int x, int y, int z,
                   int aabb, int children, long[] quads, int[] offsets) {
        this(key, revision, level, x, y, z, aabb, children, quads, offsets, new long[0]);
    }

    public LodMesh(long key, long revision, int level, int x, int y, int z,
                   int aabb, int children, long[] quads, int[] offsets, long[] fluidHeights) {
        if (fluidHeights.length != 0 && fluidHeights.length != quads.length)
            throw new IllegalArgumentException("Fluid heights must match quad order");
        this.key = key;
        this.revision = revision;
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
        this.aabb = aabb;
        this.children = children;
        this.quads = quads.clone();
        this.offsets = offsets.clone();
        this.fluidHeights = fluidHeights.clone();
    }

    public LongBuffer quads() { return LongBuffer.wrap(quads).asReadOnlyBuffer(); }
    public int quadCount() { return quads.length; }
    /** Empty for API 5 data/no fluids; otherwise one packed UNORM16x4 value per quad (zero = absent). */
    public LongBuffer fluidHeights() { return LongBuffer.wrap(fluidHeights).asReadOnlyBuffer(); }
    /** Quad/height payload bytes for the LRU; object/offset/palette overhead is separate. */
    public long retainedBytes() { return (quads.length + (long)fluidHeights.length) * Long.BYTES; }
    boolean validated(long epoch) { return validatedEpoch == epoch; }
    void validatedFor(long epoch) { validatedEpoch = epoch; }
    public int[] offsets() { return offsets.clone(); }
    public int blockSize() { return 32 << level; }
    public int originX() { return x * blockSize(); }
    public int originY() { return y * blockSize(); }
    public int originZ() { return z * blockSize(); }
}

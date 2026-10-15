package me.cortex.voxy.api;

/** API 6 corner order is (x<<1)|z; each unsigned 16-bit lane stores a height in [0,1]. */
public final class LodFluidHeight {
    private LodFluidHeight() {}
    public static int encode(float height) {
        if (!Float.isFinite(height) || height < 0 || height > 1)
            throw new IllegalArgumentException("Fluid height outside [0,1]");
        return Math.round(height * 65535f);
    }
    public static float decode(long packed, int corner) {
        if (corner < 0 || corner > 3) throw new IllegalArgumentException("Fluid corner outside 0..3");
        return ((packed >>> (corner * 16)) & 65535L) / 65535f;
    }
}

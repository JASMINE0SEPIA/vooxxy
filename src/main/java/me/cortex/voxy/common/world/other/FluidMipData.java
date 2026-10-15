package me.cortex.voxy.common.world.other;

import net.minecraft.world.level.material.FluidState;

/**
 * Surface elevation retained before voxel aggregation. The mapping's formerly unused low 27 bits
 * store Q16 block height (21 bits), mip level (3 bits), and a format tag (3 bits). Block, biome and
 * light IDs, and the section serializer's 64-bit palette format, are unchanged.
 */
public final class FluidMipData {
    private static final long MASK = (1L << 27) - 1;
    private static final long TAG = 5L << 24;
    private static final long TAG_MASK = 7L << 24;
    private static final int HEIGHT_MASK = (1 << 21) - 1;
    private static final int UNIT = 1 << 16;

    private FluidMipData() {}

    public static boolean hasHeight(long value, int level) {
        return level >= 1 && level <= 4 && (value & TAG_MASK) == TAG
                && ((value >>> 21) & 7) == level
                && (value & HEIGHT_MASK) > 0 && (value & HEIGHT_MASK) <= ((long) UNIT << level);
    }

    public static float cellHeight(long value, int level) {
        if (!hasHeight(value, level)) throw new IllegalArgumentException("Missing fluid mip height");
        return (value & HEIGHT_MASK) / (float) (UNIT << level);
    }

    static long withHeight(long value, int level, int height) {
        if (level < 1 || level > 4 || height <= 0 || height > (UNIT << level))
            throw new IllegalArgumentException("Invalid fluid mip height");
        return (value & ~MASK) | TAG | ((long) level << 21) | height;
    }

    static long reduce(long selected, Mapper mapper, int level,
                       long i000, long i100, long i001, long i101,
                       long i010, long i110, long i011, long i111) {
        FluidState fluid = fluid(mapper, selected);
        if (fluid.isEmpty()) return selected & ~MASK;
        // Use the highest source surface of the chosen fluid type, not the arbitrary child chosen
        // by opacity ordering. An upper air child must not turn a shallow source into a full cell.
        int height = 0;
        height = Math.max(height, childHeight(mapper, fluid, i000, level - 1, 0));
        height = Math.max(height, childHeight(mapper, fluid, i100, level - 1, 0));
        height = Math.max(height, childHeight(mapper, fluid, i001, level - 1, 0));
        height = Math.max(height, childHeight(mapper, fluid, i101, level - 1, 0));
        int upper = UNIT << (level - 1);
        height = Math.max(height, childHeight(mapper, fluid, i010, level - 1, upper));
        height = Math.max(height, childHeight(mapper, fluid, i110, level - 1, upper));
        height = Math.max(height, childHeight(mapper, fluid, i011, level - 1, upper));
        height = Math.max(height, childHeight(mapper, fluid, i111, level - 1, upper));
        // Unknown old-cache children cannot establish a precise surface. Keep the old fallback.
        if (height == Integer.MAX_VALUE) return selected & ~MASK;
        return withHeight(selected, level, height);
    }

    private static int childHeight(Mapper mapper, FluidState expected, long value, int level, int offset) {
        FluidState child = fluid(mapper, value);
        if (!expected.getType().isSame(child.getType())) return 0;
        if (level == 0) return offset + Math.round(child.getOwnHeight() * UNIT);
        if (!hasHeight(value, level)) return Integer.MAX_VALUE;
        return offset + (int) (value & HEIGHT_MASK);
    }

    private static FluidState fluid(Mapper mapper, long value) {
        return mapper.getBlockStateFromBlockId(Mapper.getBlockId(value)).getFluidState();
    }
}

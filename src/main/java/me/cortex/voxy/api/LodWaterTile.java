package me.cortex.voxy.api;

import java.nio.ShortBuffer;
import java.util.Arrays;

/** Immutable, uniquely owned 16x16 world-lattice nodes at one source water-block Y. */
public final class LodWaterTile {
    public static final int SIDE = 16, NODES = SIDE * SIDE;
    public static final float MAX_CLEARANCE = 3;
    private final int x, y, z;
    private final long revision;
    private final short[] clearance;
    private final long[] known;

    public LodWaterTile(int x, int y, int z, long revision, short[] clearance, long[] known) {
        key(x, y, z);
        if ((clearance.length != 1 && clearance.length != NODES) || known.length != 4)
            throw new IllegalArgumentException("Invalid canonical water tile payload");
        this.x=x; this.y=y; this.z=z; this.revision=revision;
        boolean constant=true;
        for(short value:clearance) constant &= value==clearance[0];
        this.clearance=constant ? new short[]{clearance[0]} : clearance.clone();
        this.known=known.clone();
    }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public long revision() { return revision; }
    public long key() { return key(x,y,z); }
    public ShortBuffer clearance() { return ShortBuffer.wrap(clearance).asReadOnlyBuffer(); }
    public long[] known() { return known.clone(); }
    public boolean complete() { return Arrays.stream(known).allMatch(value -> value == -1L); }
    public boolean known(int x,int z) { int index=index(x,z); return (known[index>>>6] & (1L << index)) != 0; }
    public float clearance(int x,int z) { int i=index(x,z); return decode(clearance[clearance.length==1 ? 0 : i]); }
    /** Conservative cache accounting including entry/array headers; not a JVM heap measurement. */
    public long retainedBytes() { return 128L + known.length*8L + clearance.length*2L; }
    LodWaterTile withRevision(long value) { return new LodWaterTile(x,y,z,value,clearance,known); }
    public static short encode(float clearance) {
        if(!Float.isFinite(clearance) || clearance<0 || clearance>MAX_CLEARANCE)
            throw new IllegalArgumentException("Water clearance outside [0,3]");
        return (short)Math.round(clearance/MAX_CLEARANCE*65535f);
    }
    public static float decode(short code) { return (code & 65535)*MAX_CLEARANCE/65535f; }
    public static long key(int x,int y,int z) {
        if(x < -(1<<21) || x >= (1<<21) || z < -(1<<21) || z >= (1<<21) || y < -(1<<19) || y >= (1<<19))
            throw new IllegalArgumentException("Water tile coordinate outside packed world range");
        return ((long)x & 0x3fffffL)<<42 | ((long)z & 0x3fffffL)<<20 | (y & 0xfffffL);
    }
    private static int index(int x,int z) {
        if(x<0 || x>=SIDE || z<0 || z>=SIDE) throw new IllegalArgumentException("Water node outside tile");
        return z*SIDE+x;
    }
}

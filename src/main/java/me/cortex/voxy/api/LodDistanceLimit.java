package me.cortex.voxy.api;

/** External-consumer pressure limit, independent of the user's saved render distance. */
final class LodDistanceLimit {
    private volatile int blocks;
    void set(int value) { blocks=value<=0 ? 0 : Math.clamp(value,128,10000); }
    int get() { return blocks; }
    int apply(int requested) { int limit=blocks; return limit==0 ? requested : Math.min(requested,limit); }
}

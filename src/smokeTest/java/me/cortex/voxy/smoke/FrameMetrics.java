package me.cortex.voxy.smoke;

import java.util.Arrays;
import java.util.Locale;

public final class FrameMetrics {
    private static final long[] intervals = new long[30000];
    private static int count, warmup;
    private static long previous;
    public static void sample(boolean inWorld) {
        if (!inWorld) { previous = 0; return; }
        long now = System.nanoTime();
        if (previous != 0 && ++warmup > 120 && count < intervals.length) intervals[count++] = now - previous;
        previous = now;
    }
    static void report() {
        if (count == 0) throw new IllegalStateException("No rendered frame intervals sampled");
        var sorted = Arrays.copyOf(intervals, count);
        Arrays.sort(sorted);
        System.out.printf(Locale.ROOT,
                "VOXY_SMOKE: frame intervals (ms; includes FPS cap, loading and validation): n=%d p50=%.3f p95=%.3f p99=%.3f max=%.3f%n",
                count, sorted[count / 2] / 1e6, sorted[(int)(count * .95)] / 1e6,
                sorted[(int)(count * .99)] / 1e6, sorted[count - 1] / 1e6);
    }
}

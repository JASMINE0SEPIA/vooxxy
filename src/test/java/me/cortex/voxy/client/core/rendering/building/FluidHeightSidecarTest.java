package me.cortex.voxy.client.core.rendering.building;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidHeightSidecarTest {
    @Test void followsFinalQuadOrderAcrossEmptyAndNonFluidBuckets() {
        var sidecar=new FluidHeightSidecar();
        sidecar.put(7,0,73); sidecar.put(0,1,12); sidecar.put(0,0,0); sidecar.put(2,0,0);
        assertArrayEquals(new long[]{0,12,0,73},sidecar.copy(new int[]{2,0,1,0,0,0,0,1}));
    }
    @Test void workerReuseClearsNonFluidSlotsAndSnapshotsStayOwned() {
        var sidecar=new FluidHeightSidecar(); sidecar.put(0,0,11); sidecar.put(0,1,22);
        long[] first=sidecar.copy(new int[]{2,0,0,0,0,0,0,0});
        sidecar.reset(); sidecar.put(0,0,0); sidecar.put(0,1,33);
        assertArrayEquals(new long[]{0,33},sidecar.copy(new int[]{2,0,0,0,0,0,0,0}));
        assertArrayEquals(new long[]{11,22},first);
        sidecar.reset(); sidecar.put(0,0,0);
        assertEquals(0,sidecar.copy(new int[]{1,0,0,0,0,0,0,0}).length);
    }
    @Test void rejectsInvalidBucketCapacityBeforeNativeQuadWrites() {
        var sidecar=new FluidHeightSidecar();
        assertThrows(IllegalArgumentException.class,()->sidecar.put(0,65536,1));
        assertThrows(IllegalArgumentException.class,()->sidecar.put(8,0,1));
        assertThrows(IllegalArgumentException.class,()->sidecar.copy(new int[7]));
        assertThrows(IllegalArgumentException.class,()->sidecar.copy(new int[]{65537,0,0,0,0,0,0,0}));
    }
}

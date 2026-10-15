package me.cortex.voxy.api;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.world.WorldEngine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LodFluidDependenciesTest {
    @Test void diagonalCeilingEditInvalidatesWaterAcrossAllThreeAxes() {
        long changed=WorldEngine.getWorldSectionId(0,1,4,1);
        var keys=new LongOpenHashSet();
        LodFluidDependencies.forEach(changed,keys::add);
        assertEquals(27,keys.size());
        assertTrue(keys.contains(changed));
        assertTrue(keys.contains(WorldEngine.getWorldSectionId(0,2,3,2)),"Water at 64,127,64 depends on ceiling at 63,128,63");
        assertTrue(keys.contains(WorldEngine.getWorldSectionId(0,2,3,1)));
        assertTrue(keys.contains(WorldEngine.getWorldSectionId(0,1,3,2)));
        assertFalse(keys.contains(WorldEngine.getWorldSectionId(0,3,3,2)));
    }
    @Test void dependencyRadiusAndLevelHoldAtNegativeCoordinates() {
        for(int level=0;level<=WorldEngine.MAX_LOD_LAYER;level++) {
            var keys=new LongOpenHashSet();
            LodFluidDependencies.forEach(WorldEngine.getWorldSectionId(level,-2,-1,-3),keys::add);
            assertEquals(27,keys.size());
            assertTrue(keys.contains(WorldEngine.getWorldSectionId(level,-3,-2,-4)));
            assertTrue(keys.contains(WorldEngine.getWorldSectionId(level,-1,0,-2)));
            for(long key:keys) {
                assertEquals(level,WorldEngine.getLevel(key));
                assertTrue(Math.abs(WorldEngine.getX(key)+2)<=1);
                assertTrue(Math.abs(WorldEngine.getY(key)+1)<=1);
                assertTrue(Math.abs(WorldEngine.getZ(key)+3)<=1);
            }
        }
    }
}

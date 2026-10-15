package me.cortex.voxy.api;

import org.junit.jupiter.api.Test;
import java.nio.ReadOnlyBufferException;
import static org.junit.jupiter.api.Assertions.*;

class LodFluidHeightTest {
    @Test void unorm16RetainsStillWaterAndFullHeightEndpoints() {
        assertEquals(0,LodFluidHeight.encode(0));
        assertEquals(65535,LodFluidHeight.encode(1));
        assertEquals(58253,LodFluidHeight.encode(8f/9f));
        assertEquals(8f/9f,LodFluidHeight.decode(58253,0),.5f/65535f+1e-7f);
        assertEquals(1,LodFluidHeight.decode(-1L,3));
    }
    @Test void quantizationBoundHoldsAcrossFlowingHeightsAndPreservesOrder() {
        float previous=-1;
        for(int i=0;i<=10000;i++) {
            float source=i/10000f;
            float decoded=LodFluidHeight.decode(LodFluidHeight.encode(source),0);
            assertEquals(source,decoded,.5f/65535f+1e-7f);
            assertTrue(decoded>=previous); previous=decoded;
        }
        long corners=7282L|(21845L<<16)|(50972L<<32)|(58253L<<48);
        assertEquals(1f/9f,LodFluidHeight.decode(corners,0),1e-5f);
        assertEquals(1f/3f,LodFluidHeight.decode(corners,1),1e-5f);
        assertEquals(7f/9f,LodFluidHeight.decode(corners,2),1e-5f);
        assertEquals(8f/9f,LodFluidHeight.decode(corners,3),1e-5f);
    }
    @Test void invalidCoordinatesAreRejected() {
        for(float value:new float[]{-1,2,Float.NaN,Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->LodFluidHeight.encode(value));
        assertThrows(IllegalArgumentException.class,()->LodFluidHeight.decode(1,-1));
        assertThrows(IllegalArgumentException.class,()->LodFluidHeight.decode(1,4));
    }
    @Test void meshOwnsReadOnlyHeightDataAndAccountsForBothArrays() {
        long[] quads={1,2},heights={0,-1L};
        var mesh=new LodMesh(0,1,0,0,0,0,0,0,quads,new int[8],heights);
        quads[0]=99; heights[1]=0;
        assertEquals(1,mesh.quads().get(0)); assertEquals(-1L,mesh.fluidHeights().get(1));
        assertThrows(ReadOnlyBufferException.class,()->mesh.fluidHeights().put(0,1));
        assertEquals(32,mesh.retainedBytes());
        var legacy=new LodMesh(0,1,0,0,0,0,0,0,new long[]{1,2},new int[8]);
        assertFalse(legacy.fluidHeights().hasRemaining()); assertEquals(16,legacy.retainedBytes());
        assertThrows(IllegalArgumentException.class,()->new LodMesh(0,1,0,0,0,0,0,0,new long[2],new int[8],new long[1]));
    }
}

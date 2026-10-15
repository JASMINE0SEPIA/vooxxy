package me.cortex.voxy.client.core.rendering.building;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalFluidSurfaceTest {
    @Test void fullAndEmptyNeighborsHaveOppositePoliciesForEveryFace() {
        for(Direction direction:Direction.values()) {
            assertTrue(ExternalFluidSurface.occludes(Shapes.block(),direction,1));
            assertFalse(ExternalFluidSurface.occludes(Shapes.empty(),direction,1));
            assertTrue(ExternalFluidSurface.selfOccludes(Shapes.block(),direction));
            assertFalse(ExternalFluidSurface.selfOccludes(Shapes.empty(),direction));
        }
    }
    @Test void ceilingUsesTheBottomFaceOfTheNeighborShape() {
        var bottom=Shapes.box(0,0,0,1,.5,1);
        var top=Shapes.box(0,.5,0,1,1,1);
        assertTrue(ExternalFluidSurface.occludes(bottom,Direction.UP,1));
        assertFalse(ExternalFluidSurface.occludes(top,Direction.UP,1));
        assertFalse(ExternalFluidSurface.occludes(bottom,Direction.DOWN,1));
        assertTrue(ExternalFluidSurface.occludes(top,Direction.DOWN,1));
    }
    @Test void sideOcclusionTracksActualFluidHeightInsteadOfFullCubeOnly() {
        var slab=Shapes.box(0,0,0,1,.5,1);
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            assertTrue(ExternalFluidSurface.occludes(slab,direction,.5f));
            assertFalse(ExternalFluidSurface.occludes(slab,direction,8f/9f));
        }
    }
    @Test void sideAndTopHeightBoundsUseTheCorrectSharedCorners() {
        float[] h={.2f,.4f,.6f,.8f};
        assertEquals(1,ExternalFluidSurface.faceHeight(0,h));
        assertEquals(.2f,ExternalFluidSurface.faceHeight(1,h));
        assertEquals(.6f,ExternalFluidSurface.faceHeight(2,h));
        assertEquals(.8f,ExternalFluidSurface.faceHeight(3,h));
        assertEquals(.4f,ExternalFluidSurface.faceHeight(4,h));
        assertEquals(.8f,ExternalFluidSurface.faceHeight(5,h));
    }
}

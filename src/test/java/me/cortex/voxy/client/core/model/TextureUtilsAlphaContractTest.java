package me.cortex.voxy.client.core.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TextureUtilsAlphaContractTest {
    @Test void exactOpaquePromiseIgnoresRgbButRequiresEveryAlphaByte() {
        int[] rgba=new int[256];
        for(int i=0;i<rgba.length;i++)rgba[i]=0xff000000|(i*65537);
        assertTrue(TextureUtils.isSolid(new ColourDepthTextureData(rgba,new int[256],16,16)));
        for(int index=0;index<rgba.length;index++)for(int alpha:new int[]{0,1,127,254}) {
            int saved=rgba[index];rgba[index]=(alpha<<24)|(saved&0xffffff);
            assertFalse(TextureUtils.isSolid(new ColourDepthTextureData(rgba,new int[256],16,16)),
                    "one uncovered or translucent texel invalidates the promise");
            rgba[index]=saved;
        }
    }

    @Test void rasterCoverageAboveNinetyPercentCannotAuthorizeSkippingAlpha() {
        int[] rgba=new int[256],depth=new int[256];
        java.util.Arrays.fill(rgba,0xffffffff);java.util.Arrays.fill(depth,1);
        rgba[127]=0;
        var texture=new ColourDepthTextureData(rgba,depth,16,16);
        assertTrue(TextureUtils.getWrittenPixelCount(texture,TextureUtils.WRITE_CHECK_ALPHA)/256f>.9f);
        assertFalse(TextureUtils.isSolid(texture),"stencil/raster coverage does not close the alpha hole");
    }
}

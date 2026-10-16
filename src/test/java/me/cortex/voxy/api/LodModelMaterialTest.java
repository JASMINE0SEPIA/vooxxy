package me.cortex.voxy.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LodModelMaterialTest {
    private static LodModel model(int flags) {return new LodModel(0,0,0,0,0,0,0,flags,-1,null);}
    @Test void optimizedBinaryAlphaGlassKeepsItsAuthoredMaterialWithoutChangingRasterFlags() {
        assertEquals(32,LodModel.AUTHORED_TRANSLUCENT);
        var glass=model(LodModel.AUTHORED_TRANSLUCENT|8);
        assertTrue(glass.authoredTranslucent());
        assertEquals(0,glass.flags()&4,"optimized raster translucency stays off");
        assertEquals(8,glass.flags()&8,"shading flag remains independent");
    }
    @Test void legacyTranslucencyAndOpaqueFoliageRemainDistinct() {
        assertTrue(model(4).authoredTranslucent());
        for(int flags:new int[]{0,1,2,8,16,1|2|8})assertFalse(model(flags).authoredTranslucent());
    }
}

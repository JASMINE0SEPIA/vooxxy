package me.cortex.voxy.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LodDistanceLimitTest {
    @Test void pressureLimitNeverRaisesTheUsersRequestedDistance() {
        var limit=new LodDistanceLimit(); limit.set(256);
        assertEquals(256,limit.apply(1024)); assertEquals(128,limit.apply(128));
    }
    @Test void clearingTheTransientLimitRestoresTheOriginalRequest() {
        var limit=new LodDistanceLimit(); limit.set(128); limit.set(0);
        assertEquals(1024,limit.apply(1024)); assertEquals(0,limit.get());
    }
    @Test void invalidLimitsAreClampedOrClearedWithoutTouchingSavedConfiguration() {
        var limit=new LodDistanceLimit(); limit.set(1); assertEquals(128,limit.get());
        limit.set(Integer.MAX_VALUE); assertEquals(10000,limit.get());
        limit.set(-1); assertEquals(0,limit.get());
    }
}

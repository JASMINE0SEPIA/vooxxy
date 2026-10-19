package me.cortex.voxy.client.core.backend;

import org.junit.jupiter.api.Test;
import static me.cortex.voxy.client.core.backend.VoxyGraphicsBackend.*;
import static org.junit.jupiter.api.Assertions.*;

final class ExternalRendererPreferenceTest {
    @Test void externalVulkanConsumerGetsItsDataProviderDespiteSavedBlaze3dPreference() {
        assertEquals(RendererMode.NATIVE, effectivePreference(VULKAN, RendererMode.BLAZE3D, true));
        assertEquals(RendererMode.NATIVE, effectivePreference(VULKAN, RendererMode.NATIVE, true));
    }

    @Test void standalonePreferenceIsRestoredWhenExternalRendererIsInactive() {
        assertEquals(RendererMode.BLAZE3D, effectivePreference(VULKAN, RendererMode.BLAZE3D, false));
        assertEquals(RendererMode.NATIVE, effectivePreference(VULKAN, RendererMode.NATIVE, false));
    }

    @Test void openGlAndUnknownBackendsKeepTheirExistingSelection() {
        assertEquals(RendererMode.BLAZE3D, effectivePreference(OPENGL, RendererMode.BLAZE3D, true));
        assertEquals(RendererMode.BLAZE3D, effectivePreference(UNKNOWN, RendererMode.BLAZE3D, true));
        assertEquals(RendererMode.NATIVE, effectivePreference(UNKNOWN, null, false));
    }
}

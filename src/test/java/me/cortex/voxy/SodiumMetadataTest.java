package me.cortex.voxy;

import com.google.gson.*;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise Fabric's actual predicates against processed metadata, including +mc build suffixes. */
final class SodiumMetadataTest {
    private static JsonObject metadata() throws Exception {
        try(var in=SodiumMetadataTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static boolean matches(JsonArray predicates,String version) throws Exception {
        for(var predicate:predicates)
            if(VersionPredicate.parse(predicate.getAsString()).test(Version.parse(version)))return true;
        return false;
    }
    @Test void testedVersionsAreSuggestedAndNeverBlockedByTheHardConflictRule() throws Exception {
        var mod=metadata();
        for(String version:new String[]{"0.9.2+mc26.3","0.9.3-alpha.1+mc26.3","0.9.3-beta.1+mc26.3"}) {
            assertTrue(matches(mod.getAsJsonObject("suggests").getAsJsonArray("sodium"),version),version);
            assertFalse(matches(mod.getAsJsonObject("breaks").getAsJsonArray("sodium"),version),version);
        }
    }
    @Test void unqualifiedPrereleasesAndFutureReleasesRemainBlocked() throws Exception {
        var conflicts=metadata().getAsJsonObject("breaks").getAsJsonArray("sodium");
        for(String version:new String[]{"0.9.1","0.9.3-alpha.0","0.9.3-alpha.2","0.9.3-beta.0",
                "0.9.3-beta.2","0.9.3","0.10.0"})assertTrue(matches(conflicts,version),version);
    }
    @Test void sodiumRemainsOptionalForExternalRayTracingConsumers() throws Exception {
        assertFalse(metadata().getAsJsonObject("depends").has("sodium"));
    }
}

# Authored transparent models for external ray tracing

Vulkan.11 preserves the original optical material when the raster model baker optimizes a
binary-alpha translucent texture to SOLID/CUTOUT. Plain glass previously lost the translucent
flag in the external palette, while the game's near model still used its translucent layer.
The gysf mixed/near RT capture reproduced opaque/cutout distant glass versus refractive near
glass. This is a material handoff defect, independent of water displacement continuity.

`LodModel.AUTHORED_TRANSLUCENT` is bit 32 of the existing flags word; no record or buffer stride
changes. It marks models whose authored quads are wholly translucent before raster optimization.
Mixed opaque/translucent models are not promoted wholesale. Existing bit 4 retains its raster
classification; raster layer/alpha/occlusion logic is unchanged and shaders ignore the new bit.
Consumers can use `authoredTranslucent()` (bit 4 or 32), excluding fluids as appropriate.

The bake-only `HAS_OPAQUE_GEOMETRY` flag is separate from GPU/model flags. The authored optical
classification is also included in the texture-deduplication key so identically baked opaque
and transparent materials cannot inherit one another's optical metadata. The legacy colour
bridge uses the preserved classification too. Older consumers can ignore bit 32; older producers
with bit 4 continue to work, but cannot expose the optical meaning lost by their optimization.

`gradlew --no-daemon check build` passes 69 tests, including the flag/legacy/opaque separation
contract, and the backend isolation check. This is compiler/JVM/package evidence. Actual RT
interface and optical qualification belongs to the paired gysf runtime checks.

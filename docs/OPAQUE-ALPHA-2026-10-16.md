# Exact opaque face coverage

Vulkan.12 exports optional face-data bit 26 (`LodModel.FACE_OPAQUE_ALPHA`) only when every alpha
byte of the complete baked face tile is 255. The existing raster coverage/discard heuristics and
face/model layouts are unchanged. GPU decoders mask their existing fields and ignore bit 26.

This promise can avoid an external RT coverage texture lookup for an ordinary solid/emissive
surface after ownership and atlas-availability checks. It does not classify glass or water as
opaque, replace cutout coverage, or authorize skipping near/LOD ownership. Consumers of older
providers must retain texture lookup when the promise is absent. Tests include a single alpha
hole in an otherwise full tile, which still passes the raster 90% coverage heuristic.

Actual performance benefit and RT shading qualification belong to the paired consumer checks.

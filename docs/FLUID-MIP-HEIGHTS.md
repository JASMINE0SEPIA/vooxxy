# Source water elevation through LOD aggregation

The old mip path kept a chosen block state but discarded its source elevation.
`RenderDataFactory` then treated the state's fractional height as a fraction of
the whole coarse voxel. A flat source surface at Y=8/9 became Y=128/9 at level
four. Higher precision in the API 6 sidecar alone could not fix this scaling.

`FluidMipData` now carries the highest source elevation of the selected fluid
type through each 2x2x2 reduction. Its Q16 height is measured in original blocks
from the coarse cell's base. The formerly unused low 27 mapping bits hold the
21-bit height, 3-bit output level and 3-bit format tag. Block, biome and light
fields are unchanged. The existing section serializer already stores full
64-bit palette entries; its format is unchanged and older readers ignore the
new data. Changing the mapped block through `Mapper.withBlockBiome` clears it.

Fluid meshing uses this retained elevation when present, then exports the same
four normalized UNORM16 lanes through API 6. New source ingestion also compares
all parent mips when it contains fluid metadata, even if level zero did not
change. This upgrades old parents without deleting a player's database. Old
coarse data that has not been reingested remains an explicit unknown and uses
the previous fallback; it is not advertised as reconstructed source geometry.

The regression first failed with a 13.333252-block error. CPU tests now cover
all 16 source top offsets at all five levels, five flowing states at positive
and negative world origins, maximum-height selection independent of opacity
ordering, light/biome identity, serialization, unknown children, and reingestion
of unchanged fine water. These exercise the real mipper and fluid-corner code,
but do not construct GPU acceleration structures or establish shoreline
continuity. Game-side mesh conversion is checked separately by gysf's optional
`gysf.smoke.fluidMip` fixture.

This change preserves homogeneous body elevations; it does not recover erased
horizontal shoreline shapes, a shared near/LOD wave envelope, or arbitrarily
thin water lost to opaque voxel selection. Legacy three-bit fluid quads also
retain their original 1/8..1 coarse-cell precision; the precise external API
does not impose that limitation.

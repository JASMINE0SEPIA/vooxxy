# Canonical source-water field, capability 1

The base mesh API remains version 6. The optional `waterDomainVersion()` and
`pollWaterDomain(int[] xyz)` methods on `VoxyLodApi`/`CausticaBridge` expose a
separate CPU capability. Polling only checks a bounded cache and queues work;
it does not load database sections, compute shapes, or touch Vulkan.

A request contains ordered `(tileX, sourceWaterBlockY, tileZ)` triples, at most
4096 per poll. Completed immutable tiles have 16x16 **nodes**, not 17x17 copies
of neighboring borders. Node `(x,z)` belongs only to tile
`(floorDiv(x,16), floorDiv(z,16))`. A unit cell crossing a tile edge reads nodes
from their respective owners. This avoids divergent border copies when one
tile has updated and its neighbor is still pending.

`clearance()` is one unsigned-16 code for a constant tile, or 256 codes in
`z*16+x` order. Codes map linearly to 0..3 blocks of conservative vertical
excursion above `sourceWaterBlockY+1`. All four water columns meeting a node
must be water. Rigid shapes reaching the water cell's top, adjacent dry cells,
and immediately covered/stacked fluid suppress that node. Overhead shapes and
fluids limit clearance; the shape context reads the same source snapshots.
This is a conservative displacement budget, not a depth or wave-height image.

Four 64-bit masks identify known nodes. Unknown data has a zero code with its
known bit clear, and is not advertised as air. A known dry column can determine
a zero shore node even when another column is unknown. A tile's `complete()`
means that all its nodes were determined, not that every world feature has
been reconstructed or visually validated.

The worker has at most 1024 pending tiles, 32768 cached tiles, and 16 MiB of
conservatively accounted tile entries. A reusable fine-section snapshot LRU
holds at most 32 arrays (8 MiB), with at most 16 acquired source references per
job. Burst invalidations coalesce; an overflowing change set resets the tile
cache instead of growing indefinitely. Changed fine sections invalidate the
corner/shape/ceiling dependency neighborhood. In-flight invalidated results
are rebuilt, and closing joins the worker before releasing its world reference.
These are implementation limits, not measured JVM or frame-time guarantees.

## Exact-source knowledge

An empty cached section is not evidence that its air was visited. Level-zero
sections now retain an eight-bit mask for ingested 16-cube source chunks,
including air. `SaveLoadSystem3` stores it in previously unused metadata bits
24..31, with tag 0xa5 in bits 56..63. Palette and child-mask fields are unchanged.
Old readers ignore this extension; old data and markers dropped by old writers
remain unknown until reingested. No database deletion is required.

Coarse DH imports explicitly set `VoxelizedSection.sourceExact=false`. They can
still render using the existing voxel path, but cannot establish exact-source
shore nodes. Replacing exact data with a reconstruction clears the associated
source-knowledge bit. Native chunk/region conversion establishes it again.

Source-only knowledge updates are saved and notify field listeners without
enqueueing geometry rebuilds. World revisions are published before callbacks,
so a fast snapshot worker cannot reuse the old revision after an edit event.

## Validation and remaining work

Tests cover known air versus cached missing air, signed source octants, metadata
round trips and legacy fallback, approximate imports, source-only invalidation,
open/covered/waterlogged shapes, fluid barriers, immutable payloads, queue and
cache limits, in-flight changes and invalidation floods, source edits, and world
reference release. The paired gysf probe uses a frozen 96x96 source pool and
checks open/shore/covered/unknown nodes, a glass-ceiling edit and restoration,
immutable previous data and integer terrain rebases through the actual optional
reflection boundary.

This capability does **not** yet modify GPU water rendering. The consumer must
bind a shared world field, retain previous data for motion/normal history,
traverse canonical unit cells within coarse primitives, and read the same
field for intersections and shading. Interpolating four values across each
primitive is insufficient: a 1-block and 16-block smoothstep differ by about
0.009 in weight even with consistent endpoint samples. Coarse rest topology,
unvisited/approximate data, temporal edits, GPU guides and frame time remain
separate qualification requirements.

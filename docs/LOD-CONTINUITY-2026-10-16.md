# Preserve existing detail while discovering new siblings

A previously drawn fine branch may cover the visited part of a root whose nonempty-child mask
later grows. Requiring identical masks discarded that fine coverage and selected the coarse
parent while the new sibling's mesh was pending. Large coarse cells could therefore replace
terrain that had already been rendered at finer detail.

`LodHierarchy.Coverage` now retains the actual previously published leaves, including empty
markers and partial in-range branches, across mask growth and repeated snapshots. A removed
previously populated child still invalidates stale detail. Requested coarse targets are still
built and can replace fine coverage when actually ready; no GPU resource ownership changes.

The 72-test check/build passes. The new regression repeats a growing-root selection twenty times,
then admits the new sibling and verifies its activation without replacing the old fine leaves.
The consumer's player-save visual replay and lifecycle smoke qualify the combined pair separately.

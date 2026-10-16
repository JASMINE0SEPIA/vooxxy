# Preserve existing detail while discovering new siblings

A previously drawn fine branch may cover the visited part of a root whose nonempty-child mask
later grows. Requiring identical masks discarded that fine coverage and selected the coarse
parent while the new sibling's mesh was pending. Large coarse cells could therefore replace
terrain that had already been rendered at finer detail.

`LodHierarchy.Coverage` now retains the actual previously published leaves, including empty
markers and partial in-range branches, across mask growth and repeated snapshots. A removed
previously populated child still invalidates stale detail. Requested coarse targets are still
built and can replace fine coverage when actually ready; no GPU resource ownership changes.

The scene also reports whether a selected nonempty parent is a temporary fallback for requested,
unfinished refinement. An RT consumer can defer admitting this transient partition while keeping its
current scene: otherwise an early 512-block parent can remain drawn for minutes while thousands of
fine BLAS batches are prepared for its atomic replacement. Intentional distant coarse targets and
empty coverage markers do not set the flag. Atlas growth preserves the metadata; older constructors
default to false and keep API 6 compatibility.

The 73-test check/build passes. The new regression repeats a growing-root selection twenty times,
then admits the new sibling and verifies its activation without replacing the old fine leaves.
The consumer's player-save visual replay and lifecycle smoke qualify the combined pair separately.

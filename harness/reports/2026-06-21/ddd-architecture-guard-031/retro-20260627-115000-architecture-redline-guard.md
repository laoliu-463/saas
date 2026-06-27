# Retro - DDD100 Guard Issue #31

## What Changed

- Added `DddArchitectureRedlineGuardTest` as an executable guard for DDD100 architecture redlines.
- Added a frozen legacy whitelist for current Controller direct Mapper/Gateway imports.
- Kept existing debt visible: new Controller debt fails, and retired debt requires whitelist cleanup.

## What Worked

- Reused the existing architecture-test pattern instead of adding a separate scanner runtime.
- Split current debt into two policies: frozen whitelist for existing Controller imports, zero tolerance for strict domain-layer mapper imports and frontend third-party HTTP calls.

## What Did Not Change

- No production backend code changed.
- No frontend, SQL, Docker, API, schema, or default real-pre config changed.
- No Controller debt was retired in this slice.

## Harness Upgrade

No Harness script upgrade. The new guard is a Maven/JUnit architecture test and should be included in future backend validation for DDD slices.

## Next

- #32 should codify the DDD migration metric script and include #31 guard status in the evidence table.
- Later domain issues should remove entries from `architecture-redline-legacy-whitelist.txt` as Controller debt is retired.

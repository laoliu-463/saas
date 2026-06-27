# Retro - DDD100 Metric Issue #32

## What Changed

- Added `harness/scripts/probes/ddd-migration-metrics.ps1`.
- The script outputs Markdown or JSON metrics for global DDD share, business migration proxy, per-domain layers, legacy service LOC, and legacy entry LOC.
- Recorded the current metric snapshot in evidence.

## What Worked

- The #30 one-off baseline became a reusable command.
- JSON output gives later agents a stable way to embed metrics in evidence without parsing Markdown.

## What Did Not Change

- No backend, frontend, SQL, Docker, API, schema, or real-pre config changed.
- The script does not decide whether a domain is semantically complete; it only reports a reproducible source-code proxy.

## Harness Upgrade

No command runner change. This is a new read-only probe script under `harness/scripts/probes`.

## Next

- #33 should use the script output as the starting metric before changing user data-scope code.
- Later closeout should compare this report against the final #89 metric.

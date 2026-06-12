# Home Automation — Design Index
**Date:** 2026-06-05

The home automation design is split across two repos, matching the two-session implementation sequence.

## Implementation order

1. **`casehub-iot` foundation** (own session, new repo) — must be published to GitHub Packages first  
2. **`casehub-life` Layer 9** (own session, existing repo) — depends on `casehub-iot` being available

## Specs

| What | Where |
|---|---|
| `casehub-iot` foundation — device hierarchy, provider SPIs, HA/OpenHAB implementations, bridge | `casehubio/casehub-iot` — `docs/superpowers/specs/2026-06-05-iot-foundation-design.md` |
| `casehub-life` Layer 9 — trigger mechanism, case types, community automations, workers, tutorial | `docs/superpowers/specs/2026-06-05-life-layer9-home-automation.md` (this repo) |

## Research

Full technical and business findings, design decisions with rationale, market analysis:  
`docs/superpowers/research/2026-06-05-home-automation-research.md`

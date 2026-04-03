# QuickAddressFill Stability Manual Tests

These scenarios focus on the recent hardening changes (state reset, fallback cleanup, click handling).

## Checkpoint Smoke Plan (short)

### A) Normal Apply Flow
- Preparation: load edit layer with building ways and visible named street.
- Steps: open dialog, set street/postcode/house number, click building twice.
- Expected: first click applies tags, second click uses incremented house number.
- Relevant logs: `QuickAddressFill click-path: outcome=applied, source=...` (debug).

### B) Ctrl+Click Readback/Pickup
- Preparation: one building with `addr:*` tags, one nearby named highway without building under cursor.
- Steps: Ctrl+click on tagged building; then Ctrl+click on named street area.
- Expected: building readback loads `street/postcode/housenumber`; street pickup loads street and sets house number to `1`.
- Relevant logs: `outcome=address-picked` or `outcome=street-picked` (debug).

### C) Overwrite Dialog + Suppression
- Preparation: existing building has different `addr:street` or `addr:postcode` than dialog.
- Steps: click building, deny overwrite once; click again, accept with "Do not warn again".
- Expected: first click cancels apply; second suppresses warning for that overwritten street.
- Relevant logs: `outcome=overwrite-cancelled` on cancel (debug).

### D) DataSet Switch
- Preparation: set non-default values in DataSet A dialog.
- Steps: close dialog, switch to DataSet B, reopen dialog.
- Expected: remembered values from A are invalidated for B.
- Relevant logs: optional activation logs if map state is unavailable.

### E) BuildingSplitter Handoff
- Preparation: BuildingSplitter present (or intentionally absent for negative check).
- Steps: use `Split building` with populated street/postcode.
- Expected: reflection handoff clears fallback on success; stale fallback is cleaned when pending is old.
- Relevant logs: `Address context reflection handoff succeeded.` or `Clearing stale BuildingSplitter handoff fallback ...`.

### F) Dense Area / Candidate Limits
- Preparation: dense city area; debug logs enabled; optionally lower scan limits.
- Steps: repeated clicks in dense area with default and low limits.
- Expected: default stays responsive with fewer misses; low limits increase `source=no-hit` and `*LimitReached=true`.
- Relevant logs: `QuickAddressFill click-path: ... relationLimitReached=..., wayLimitReached=..., durationMs=...`.

## 1) DataSet Switch Resets Remembered Dialog Context

### Preparation
- Start JOSM with QuickAddressFill plugin enabled.
- Load DataSet A with streets/buildings.
- Open Quick Address Fill dialog and enter distinctive values:
  - Postcode: `99999`
  - Street: pick any street
  - Building type: `warehouse`
  - House number: `77a`
  - Increment: `+2`

### Steps
1. Close the dialog.
2. Switch to another editable layer or open DataSet B.
3. Open Quick Address Fill again.

### Expected
- Remembered values from DataSet A are not reused in DataSet B.
- Default-like state appears (house number starts from default, previous custom values are cleared).

### Relevant Logs
- Optional diagnostic around mode activation can appear in JOSM log if map view is unavailable.

## 2) BuildingSplitter Fallback: Stale Pending Is Cleared

### Preparation
- JOSM closed.
- In preferences, set (or keep) stale values for:
  - `quickaddressfill.buildingsplitter.handoff.pending=true`
  - `quickaddressfill.buildingsplitter.handoff.timestamp=<very old timestamp>`

### Steps
1. Start JOSM and open Quick Address Fill dialog.
2. Trigger `Split building`.

### Expected
- Stale handoff preference data is cleared before activation.

### Relevant Logs
- `QuickAddressFill: Clearing stale BuildingSplitter handoff fallback (differentSession=..., expired=...)`

## 3) BuildingSplitter Reflection Handoff Clears Fallback

### Preparation
- Install a BuildingSplitter version that provides `AddressContextBridge.setAddressContext(String,String)`.

### Steps
1. Open Quick Address Fill, set street/postcode.
2. Trigger `Split building`.

### Expected
- Reflection handoff succeeds and fallback keys are cleared.

### Relevant Logs
- `QuickAddressFill: Address context reflection handoff succeeded.`

## 4) Click Deduplizierung

### Preparation
- Open area with buildings.
- Enable debug logs in JOSM.

### Steps
1. Click one building once normally.
2. Trigger near-identical duplicated release event (for example via touchpad ghost click) if reproducible.
3. Perform two very fast but slightly different clicks at different positions.

### Expected
- True duplicate release is suppressed once.
- Fast distinct clicks are processed independently.

### Relevant Logs
- Duplicate suppression:
  - `QuickAddressFill StreetMapMode.mouseReleased: duplicate release suppressed at x,y`
- Slow click diagnostics (only when threshold exceeded):
  - `...slow click handling (... ms)...`

## 5) Candidate Limit Monitoring

### Preparation
- Load a dense urban area with many buildings/relations.
- Enable debug logs in JOSM.
- Optional: set custom limits in advanced preferences:
  - `quickaddressfill.streetmode.relationScanLimit`
  - `quickaddressfill.streetmode.wayScanLimit`

Suggested test values:
- baseline/default: leave both keys unset
- intentionally low: `100` / `100`
- intentionally high: `10000` / `20000`

### Steps
1. Click repeatedly in dense area where nearest-hit fallback scans are likely.
2. Repeat with low and high limit values.

### Expected
- Plugin remains responsive.
- With low limits, fallback misses become more frequent (false negatives).
- With higher limits, misses should decrease, but click-path runtime can increase.

### Relevant Logs
- Full click diagnostic (debug):
  - `QuickAddressFill click-path: outcome=..., source=..., nearestCandidates=..., relationChecked=.../..., wayChecked=.../..., relationLimitReached=..., wayLimitReached=..., ... durationMs=...`
- Slow click diagnostic (debug):
  - `QuickAddressFill StreetMapMode.mouseReleased: slow click handling (... ms), source=..., outcome=..., x=..., y=...`

How to identify limits that are too low:
- `relationLimitReached=true` or `wayLimitReached=true` appears frequently.
- `source=no-hit` appears despite likely building targets in dense regions.


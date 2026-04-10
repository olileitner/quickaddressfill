# AGENTS.md

## Project snapshot
- This is a JOSM plugin (`HouseNumberClick`) for high-speed address tagging + split/overview tooling on building geometries.
- Runtime entry is `src/org/openstreetmap/josm/plugins/housenumberclick/HouseNumberClickPlugin.java`; UI action is `HouseNumberClickAction`.
- Core orchestration is centralized in `StreetModeController` (map modes, overlays, split flows, dialogs, lifecycle).

## Architecture and data flow (read this first)
- Dialog -> controller -> map mode path: `StreetSelectionDialog.notifyAddressChanged()` builds `AddressSelection`, then `StreetModeController.activate(...)` pushes values into `HouseNumberClickStreetMapMode`.
- Left-click apply path in `HouseNumberClickStreetMapMode`: resolve building (`BuildingResolver`) -> conflict analysis (`AddressConflictService`) -> write tags (`BuildingTagApplier` via `ChangePropertyCommand`) -> auto-increment (`HouseNumberService`) -> controller refresh.
- Ctrl+click readback path: `AddressReadbackService.readFromBuilding(...)` or street fallback (`resolveStreetNameAtClick` on named highways).
- Split flow is separate map mode (`HouseNumberSplitMapMode`): line split uses `SingleBuildingSplitService`; right-click row-house split uses `TerraceSplitService`.
- Collector/layer pattern: data shaping in collectors (`HouseNumberOverlayCollector`, `HouseNumberOverviewCollector`, `StreetHouseNumberCountCollector`, `BuildingOverviewCollector`), rendering in `Layer`/dialog classes.

## Build, test, and release workflow
- Local build/test uses Ant + Java 17 (`build.xml`). Main commands:
  - `ant clean test`
  - `ant dist`
  - `ant release-artifact`
- CI runs `ant -q clean test` and uploads `dist/HouseNumberClick.jar` + `dist/HouseNumberClick-*.jar` (`.github/workflows/ci.yml`).
- Regression tests are a single executable harness (`test/.../HouseNumberClickRiskRegressionTests.java`) launched by Ant `test` target (not JUnit).
- `dist` also packages generated translations from `i18n/lang/` into `data/HouseNumberClick/lang/` when present.

## Codebase-specific conventions
- Most implementation classes are package-private `final class`; preserve that style unless a concrete extension point is needed.
- Normalization is explicit and repeated (`normalize(...)` helpers); trim inputs before comparisons/writes.
- Editing OSM data should go through JOSM commands (`UndoRedoHandler`, `ChangePropertyCommand`, `SplitWayCommand`, `SequenceCommand`) rather than mutating primitives directly.
- Building/address eligibility logic is centralized in `AddressedBuildingMatcher`; collectors rely on it for consistency.
- User-facing text is translated with `I18n.tr(...)`; keep new strings translatable.

## Integration points and risk hotspots
- Map-mode lifecycle + global key dispatchers are fragile: `HouseNumberClickStreetMapMode` and `HouseNumberSplitMapMode` must register/unregister listeners correctly in `enterMode/exitMode`.
- Multipolygon behavior is intentional: `BuildingResolver` prefers multipolygon building relations over ways; `resolveWriteTargetForApply(...)` writes to relation when appropriate.
- Split operations require rollback-safe behavior (`SingleBuildingSplitService.rollbackCommandsAddedSince(...)`) to avoid leaving partial edits.
- Overlay ordering is deliberate (`StreetModeController.ensureOverlayLayerAboveBuildingOverview(...)`); do not change layer index logic casually.
- Preference keys are part of runtime behavior (`BuildingResolver.PREF_RELATION_SCAN_LIMIT`, `PREF_WAY_SCAN_LIMIT`, toolbar migration pref in `HouseNumberClickPlugin`).

## Practical editing guidance for agents
- When changing click behavior, update both runtime code and regression harness assertions in `HouseNumberClickRiskRegressionTests`.
- For new collector rules, check all collector consumers (overlay, overview table, street counts) so street/address matching stays aligned.
- For split changes, verify both flows: line drag (`startInternalSingleSplitFlowFromDialog`) and row-house actions (`executeInternalTerraceSplitFromDialog` + right-click path).
- If you add UI strings or help text, keep `README.md` shortcuts/behavior sections in sync with actual key handling.


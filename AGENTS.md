# AGENTS.md

## Project Snapshot

* This is a JOSM plugin (`HouseNumberClick`) for high-speed address tagging and building split workflows.
* Runtime entry: `HouseNumberClickPlugin`
* Main UI action: `HouseNumberClickAction`
* Core orchestration: `StreetModeController`
* Main interaction logic: `HouseNumberClickStreetMapMode`

---

## Core Interaction Model (CRITICAL)

This plugin uses a **single-mode interaction model**.

There is exactly one active map mode:

* `HouseNumberClickStreetMapMode`

There are **no secondary modes** (e.g. no split mode).

All interactions happen directly within this mode:

| Input        | Action                |
| ------------ | --------------------- |
| Left Click   | Apply address         |
| Ctrl + Click | Read address / street |
| Alt + Right Click | Row-house split  |
| Alt + Drag   | Line split            |
| Alt + 1..9   | Set row-house parts   |

Important:

* Split is **not a mode**
* Split is a **temporary gesture**
* The user never leaves Street Mode

### Input Handling (Critical)

* Never block JOSM core shortcuts (`Ctrl+*`, `Ctrl+Shift+*`).
* Modifier keys must not be consumed unless strictly required for a plugin gesture.
* `Ctrl` must not trigger plugin behavior when used together with `Shift` (reserved for JOSM shortcuts).

❗ Agents must NOT reintroduce:

* `HouseNumberSplitMapMode`
* mode switching for split
* temporary split modes
* controller-driven mode transitions

---

## Architecture and Data Flow

### Dialog → Controller → MapMode

* `StreetSelectionDialog.notifyAddressChanged()`
* builds `AddressSelection`
* passed to `StreetModeController.activate(...)`
* pushed into `HouseNumberClickStreetMapMode`

### Left Click (Apply)

* resolve building (`BuildingResolver`)
* conflict detection (`AddressConflictService`)
* apply tags (`BuildingTagApplier`, `ChangePropertyCommand`) including `addr:city` when configured
* auto-increment (`HouseNumberService`)
* controller refresh

### Ctrl + Click (Readback)

* `AddressReadbackService.readFromBuilding(...)`
* reads street/postcode/house number/building type and `addr:city` from building tags
* fallback: street detection via highways

### Duplicate checks (scope-specific)

* Global duplicate analysis (`Show duplicates`, `Show all street counts` duplicate marker) uses conditional city matching.
* Local selected-street house-number overlay duplicate highlighting stays city-agnostic (`street+postcode+housenumber`).
* Do not silently mix global and local duplicate key strategies.

### Line Split (Alt + Drag)

* handled entirely inside `HouseNumberClickStreetMapMode`
* no mode switching
* uses `SingleBuildingSplitService`

### Row-House Split (Alt + Right Click)

* triggered from map mode
* executed via `StreetModeController.executeInternalTerraceSplitAtClick(...)`
* uses `TerraceSplitService`
* uses `configuredTerraceParts` from controller

---

## Reference Street (Overpass)

* Loaded automatically when a street is selected
* Must be lightweight and fast
* Must NOT block UI interaction

### Behavior

* Auto-load is allowed, but must be:

  * debounced (no repeated rapid calls)
  * cached per street
  * non-blocking (background thread)

### Data constraints

* Only load ways with matching street name
* No buildings
* No unrelated geometry

### Rendering

* Separate layer
* Read-only
* Red, dashed
* Clipped to outside `DataSourceBounds`

❗ Agents must NOT:

* trigger recursive loading chains
* expand search to unrelated streets
* merge reference data into main dataset
* block UI while loading

---

## Split System (IMPORTANT)

Split behavior is fully inline:

### Line Split

* Trigger: `Alt + Drag`
* Scope: exactly one building
* Validation:

  * exactly two intersection points
  * adjacency constraints
* Execution:

  * `SingleBuildingSplitService`

### Row-House Split

* Trigger: `Alt + Right Click`
* Parts:

  * controlled via dialog (`Parts`)
  * quick-set via `Alt + 1..9`
  * stored in controller (`configuredTerraceParts`)
* Execution:

  * `TerraceSplitService`

❗ Never:

* introduce a split-specific map mode
* move split logic into UI classes
* bypass command system

---

## Build, Test, and Release

* Build: Ant + Java 17
* No external BuildingSplitter dependency remains

* Commands:

  * `ant clean test`
  * `ant dist`
  * `ant release-artifact`

* Tests:

  * single regression harness
  * `HouseNumberClickRiskRegressionTests`
  * NOT JUnit

### Local Test + Deploy Workflow (Persistent)

For every testable code change, agents MUST do all of the following:

1. run tests (at minimum `ant test`)
2. build deployable plugin jar (`ant dist`)
3. deploy `dist/HouseNumberClick.jar` to `~/.josm/plugins/HouseNumberClick.jar`

Agents MUST NOT deploy to `~/.local/share/JOSM/plugins`.

### Non-Interactive Release Rules

* Never open interactive editors during release commands (`git`, `gh`, etc.).
* Always use non-interactive, reproducible commands.

#### Git Tagging

* Always use annotated tags:

  * `git tag -a v<version> -m "Release v<version>"`

* Never run lightweight tags:

  * `git tag v<version>`

#### GitHub Release

* Preferred path: push `v<version>` tag and let `.github/workflows/release.yml` publish the release artifact.
* Manual fallback is allowed only when automation is unavailable, and must stay non-interactive:

  * `gh release create v<version> dist/HouseNumberClick-<version>.jar --title "HouseNumberClick v<version>" --notes-file RELEASE_NOTES.md`
* Never publish twice for the same tag (automation + manual).

#### Hard Rule

* If you are about to run a command that opens an editor:

  * STOP and choose a non-interactive alternative with explicit flags.

---

## Codebase Conventions

* Prefer `final` package-private classes
* Normalize strings (`normalize(...)`)
* Use JOSM command system:

  * `ChangePropertyCommand`
  * `SplitWayCommand`
  * `SequenceCommand`
* Do NOT mutate primitives directly

---

## Critical Integration Points

### MapMode lifecycle

* `enterMode` / `exitMode` must correctly register/unregister:

  * key listeners
  * mouse listeners
  * cursors

### Multipolygon behavior

* `BuildingResolver` prefers relations
* tagging must respect relation vs way

### Split safety

* must be rollback-safe
* never leave partial commands

### Overlay ordering

* controlled by `StreetModeController`
* do not change layer order casually

---

## Known Risk Areas

Agents must be careful with:

* global key handling (Alt / Ctrl conflicts)
* cursor handling (must match interaction state)
* split detection edge cases
* regression harness expectations

---

## Practical Editing Guidance

### When changing click behavior

* update:

  * `HouseNumberClickStreetMapMode`
  * `ClickHandlerService`
  * `AddressReadbackService`
  * `AddressConflictService`
  * regression tests
* verify shortcut safety:

  * `Ctrl+Shift+*` still reaches JOSM core actions
  * plugin only reacts to intended standalone modifiers
* verify city-specific behavior:

  * Ctrl+Click readback propagates `addr:city` back into dialog state
  * overwrite warning includes `addr:city` conflicts
  * suppression for street/postcode/city stays independently selectable

---

### When changing split behavior

Verify BOTH:

1. Line split (`Alt + Drag`)
2. Row-house split (`Alt + Right Click`)

Do NOT:

* introduce new modes
* split logic across multiple UI layers

---

### When changing dialog behavior

* keep dialog as configuration only
* do NOT move logic into UI
* controller remains source of truth

---

### When adding UI strings

* always use `I18n.tr(...)`
* keep README in sync
* keep `i18n/POTFILES.in` in sync with all Java files that contain `I18n.tr(...)` usage
* run `ant i18n-extract` and verify new/changed strings appear in `i18n/po/templates/HouseNumberClick.pot`
* if `po/*.po` files are present, verify `ant i18n-lang` succeeds (use `-Di18n.pl=/path/to/i18n.pl` when `i18n/i18n.pl` is not available locally)

---

### When changing class code

* If a class responsibility/behavior changes, update its class comment (`/** ... */`) in the same change.
* Keep `docs/class-inventory.md` in sync with the current classes and class comments.
* Treat comment + inventory updates as required maintenance, not optional cleanup.

---

## Golden Rules

1. **Single MapMode only**
2. **No mode switching for split**
3. **Split is always a temporary gesture**
4. **Controller owns state**
5. **MapMode handles interaction**
6. **Services handle logic**

If unsure:
→ prefer simpler, stateless interaction
→ avoid introducing new global state

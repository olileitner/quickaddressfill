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
| Right Click  | Row-house split       |
| Alt + Drag   | Line split            |
| Alt + 1..9   | Set row-house parts   |

Important:

* Split is **not a mode**
* Split is a **temporary gesture**
* The user never leaves Street Mode

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
* apply tags (`BuildingTagApplier`, `ChangePropertyCommand`)
* auto-increment (`HouseNumberService`)
* controller refresh

### Ctrl + Click (Readback)

* `AddressReadbackService.readFromBuilding(...)`
* fallback: street detection via highways

### Line Split (Alt + Drag)

* handled entirely inside `HouseNumberClickStreetMapMode`
* no mode switching
* uses `SingleBuildingSplitService`

### Row-House Split (Right Click)

* triggered from map mode
* executed via `StreetModeController.executeInternalTerraceSplitAtClick(...)`
* uses `TerraceSplitService`
* uses `configuredTerraceParts` from controller

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

* Trigger: right-click
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
  * regression tests

---

### When changing split behavior

Verify BOTH:

1. Line split (`Alt + Drag`)
2. Row-house split (Right Click)

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

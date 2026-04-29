# HouseNumberClick 1.1.8 Release Notes

HouseNumberClick is a JOSM plugin for fast address tagging on buildings with street-focused editing workflows.

## Highlights Since 1.1.7

- Added a full right-sidebar workflow with persistent `Street Counts` and `House Numbers` dialogs, including header-safe ToggleDialog layout integration and synchronized state/hints.
- Added robust dialog lifecycle handling for data-layer transitions: the main dialog now pauses on edit-layer loss, auto-recovers safely, and refreshes visible content when the active dataset is replaced.
- Added persistent advanced dialog behavior: collapsible advanced sections with remembered state and restored window bounds with off-screen fallback.
- Added postcode analysis expansion: three-state postcode overlay cycle (off -> buildings -> schematic areas), deterministic color/legend behavior, and stronger cache invalidation.
- Added country-aware address flow end-to-end (`addr:country` detection, constrained country selection in dialog, and apply/readback propagation).
- Added directly connected driveway highlighting in the house-number overlay while keeping strict exclusion of non-direct/service/parking cases.

## Dialog and UI Changes

- Moved street navigation arrows inline into the Address row and removed global left/right key handling conflicts.
- Grouped `Line Split` and `Row Houses` controls side-by-side in one row and aligned split-panel heights for consistent layout.
- Updated advanced-section toggle visuals (`More`/`Less`) and refined compact spacing/alignment in display and split sections.
- Improved row-house parts controls (field/button sizing, mode-to-dialog sync, and safer deferred updates during document events).
- Clarified sidebar titles to reflect scope: `Street Counts (House Numbers)` and `House Numbers (Base Numbers only)`.

## Stability and Bug Fixes

- Hardened asynchronous reference-street loading lifecycle with generation guards and better stale-callback protection.
- Improved overview and overlay refresh reliability after edits/downloads/undo-redo and after dataset/layer transitions.
- Removed legacy overview window classes in favor of the sidebar architecture and centralized cleanup paths.
- Expanded regression coverage around dialog lifecycle, split layout/wiring, sidebar integration, and dataset replacement behavior.
- Updated release/i18n process documentation and tag-driven automation guards for reproducible non-interactive releases.

## Build Artifact

- Release artifact: `dist/HouseNumberClick-1.1.8.jar`


## Draft (Next Release)

### Added

- Read-only Unterstuetzung fuer zusaetzliche OSM-Adress-Traeger eingefuehrt (`ADDRESS_NODE`, `ENTRANCE_NODE`, weitere `addr:*`-Objekte neben `building=*`).
- Neues neutrales Adressmodell hinzugefuegt: `AddressEntry`.
- Zentralen Collector fuer Adressquellen eingefuehrt: `AddressEntryCollector`.
- Zentrale Duplicate-Auswertung ergaenzt: `AddressDuplicateAnalyzer`.
- Regressionstests fuer neue read-only Adressquellen und Co-located-Duplikatfaelle ergaenzt.

### Changed

- Street Counts beruecksichtigen jetzt alle read-only Address-Carrier statt nur Gebaeude.
- Building-Overview beruecksichtigt indirekte Adressierung ueber zugeordnete Address-/Entrance-Nodes.
- Building-Overview-Rendering unterscheidet indirekt adressierte Gebaeude visuell (zusaetzliche gestrichelte Kontur).
- House-Number-Overlay verarbeitet Labels/Duplikatlogik ueber das neue Carrier-Modell.
- Duplicate-Overview-Layer bewertet Duplikate ueber alle Carrier-Typen.
- Co-located Faelle (z. B. Gebaeude + interner Adressnode mit identischer Adresse) werden als nicht-harter Duplicate behandelt.
- Build-/Testausfuehrung auf reproduzierbare JDK-17-Toolchain ausgerichtet.

### Fixed

- ant-Buildproblem `release version 17 not supported` durch konsistente JDK-17-Nutzung behoben.
- Headless/Test-Kontext stabilisiert (projektion-sichere Fallbacks bei Label-/Geometriepfaden).
- `ant clean test` und `ant dist` wieder erfolgreich.
- Plugin-JAR erfolgreich nach `~/.josm/plugins/HouseNumberClick.jar` deployed.



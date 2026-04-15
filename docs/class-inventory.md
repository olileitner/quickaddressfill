# HouseNumberClick Class Inventory

This table lists all classes/enums/records in the plugin package `src/org/openstreetmap/josm/plugins/housenumberclick`, including existing class comments.

Core classes as defined in `AGENTS.md` are marked: `HouseNumberClickPlugin`, `HouseNumberClickAction`, `StreetModeController`, `HouseNumberClickStreetMapMode`.

| Class | File | Core class | Class comment |
|---|---|---|---|
| `AddressConflictService` | `AddressConflictService.java` | No | Detects address/tag conflicts between existing building tags and the values selected for apply. |
| `AddressConflictService.ConflictAnalysis` | `AddressConflictService.java` | No | Structured outcome of conflict detection used by overwrite-warning UI. |
| `AddressConflictService.ConflictField` | `AddressConflictService.java` | No | One differing tag field between existing and proposed address/building values. |
| `AddressedBuildingMatcher` | `AddressedBuildingMatcher.java` | No | Shared predicate helper for identifying addressable buildings, optionally filtered by street. |
| `AddressReadbackService` | `AddressReadbackService.java` | No | Reads street, postcode, house number, and building type from clicked buildings or fallback street ways. |
| `AddressReadbackService.AddressReadbackResult` | `AddressReadbackService.java` | No | Readback payload containing street/address values and the source category. |
| `BuildingOverviewCollector` | `BuildingOverviewCollector.java` | No | Collects building diagnostics used by completeness and postcode overview layers. |
| `BuildingOverviewCollector.BuildingOverviewEntry` | `BuildingOverviewCollector.java` | No | Public entry used by overview layers to render completeness and diagnostics for one building. |
| `BuildingOverviewCollector.CandidateEntry` | `BuildingOverviewCollector.java` | No | Internal collection-stage representation before duplicate-address evaluation is finalized. |
| `BuildingOverviewLayer` | `BuildingOverviewLayer.java` | No | Map layer that visualizes building-level address status to support completeness inspection. |
| `BuildingOverviewLayer.MissingField` | `BuildingOverviewLayer.java` | No | Selected required address field used to focus completeness-missing highlighting. |
| `BuildingResolver` | `BuildingResolver.java` | No | Resolves the clicked building primitive with relation-first logic and bounded candidate scanning. |
| `BuildingResolver.BuildingResolutionResult` | `BuildingResolver.java` | No | Full resolver diagnostics and selected building primitive for one click resolution. |
| `BuildingResolver.RelationScanResult` | `BuildingResolver.java` | No | Internal relation-scan outcome with counters and limit state. |
| `BuildingResolver.WayScanResult` | `BuildingResolver.java` | No | Internal way-scan outcome with counters and limit state. |
| `BuildingTagApplier` | `BuildingTagApplier.java` | No | Applies address and building tags via JOSM commands, including relation-aware write targets. |
| `ClickHandlerService` | `ClickHandlerService.java` | No | Encapsulates click interaction flow for applying tags, reading addresses, and conflict handling. |
| `ClickHandlerService.ClickResult` | `ClickHandlerService.java` | No | Lightweight result of non-primary click flows with outcome and resolver diagnostics. |
| `ClickHandlerService.PrimaryClickResult` | `ClickHandlerService.java` | No | Result of a primary (apply) click, including outcome, resolution stats, and next UI state. |
| `ConflictDialogModelBuilder` | `ConflictDialogModelBuilder.java` | No | Converts conflict analysis into table-oriented dialog rows for overwrite confirmation UI. |
| `ConflictDialogModelBuilder.DialogModel` | `ConflictDialogModelBuilder.java` | No | Immutable dialog data model containing all rows shown in overwrite confirmation. |
| `ConflictDialogModelBuilder.DialogRow` | `ConflictDialogModelBuilder.java` | No | One row in the conflict confirmation table (field, existing value, proposed value). |
| `CornerSnapService` | `CornerSnapService.java` | No | Computes robust split-line intersections against building edges, including corner snapping logic. |
| `CornerSnapService.IntersectionType` | `CornerSnapService.java` | No | Segment intersection classification used by split-line edge intersection logic. |
| `CornerSnapService.SegmentIntersection` | `CornerSnapService.java` | No | Internal intersection result for two segments, including type and optional point. |
| `DialogController` | `DialogController.java` | No | Keeps dialog-side configuration state normalized and synchronized with controller callbacks. |
| `DialogState` | `DialogState.java` | No | Immutable snapshot of the dialog input values used to compare and restore UI state. |
| `DuplicateAddressOverviewLayer` | `DuplicateAddressOverviewLayer.java` | No | Map layer that highlights buildings with duplicate exact address keys. |
| `HouseNumberClickAction` | `HouseNumberClickAction.java` | Yes | Main toolbar/menu action that opens the street selection dialog and activates street mode. |
| `HouseNumberClickPlugin` | `HouseNumberClickPlugin.java` | Yes | Plugin entry point that wires the menu action and performs one-time toolbar migration. |
| `HouseNumberClickStreetMapMode` | `HouseNumberClickStreetMapMode.java` | Yes | Single active map mode that handles address apply/readback and temporary split gestures. |
| `HouseNumberClickStreetMapMode.ClickResolutionStats` | `HouseNumberClickStreetMapMode.java` | Yes | Captures per-click diagnostics for debug logging of resolver and interaction paths. |
| `HouseNumberOverlayCollector` | `HouseNumberOverlayCollector.java` | No | Collects and normalizes addressable building entries for street-specific house-number overlays. |
| `HouseNumberOverlayCollector.ParsedHouseNumber` | `HouseNumberOverlayCollector.java` | No | Parsed representation of a house number split into sortable numeric and suffix parts. |
| `HouseNumberOverlayEntry` | `HouseNumberOverlayEntry.java` | No | Value object describing one rendered house-number label entry in the overlay layer. |
| `HouseNumberOverlayLayer` | `HouseNumberOverlayLayer.java` | No | Renders street-specific house-number highlights and optional connection lines in a dedicated layer. |
| `HouseNumberOverviewCollector` | `HouseNumberOverviewCollector.java` | No | Aggregates and orders house numbers for the selected street to power the overview dialog. |
| `HouseNumberOverviewCollector.BaseNumberGroup` | `HouseNumberOverviewCollector.java` | No | Groups occurrences of one base number and tracks duplicate exact values plus representative primitives. |
| `HouseNumberOverviewCollector.OverviewCellData` | `HouseNumberOverviewCollector.java` | No | Intermediate formatted cell data used while composing final overview rows. |
| `HouseNumberOverviewCollector.ParsedHouseNumber` | `HouseNumberOverviewCollector.java` | No | Parsed house-number token with normalized base number and suffix. |
| `HouseNumberOverviewDialog` | `HouseNumberOverviewDialog.java` | No | Dialog that displays house-number completeness for the selected street and resume actions. |
| `HouseNumberOverviewRow` | `HouseNumberOverviewRow.java` | No | Row model used by the house-number overview table for odd/even values and linked primitives. |
| `HouseNumberService` | `HouseNumberService.java` | No | Encapsulates house-number parsing, normalization, and increment/decrement behavior. |
| `IntersectionPoint` | `IntersectionPoint.java` | No | Represents one detected intersection between a split line and a building outline segment. |
| `IntersectionScanResult` | `IntersectionScanResult.java` | No | Result container for intersection scanning, including success state, message, and points. |
| `NavigationService` | `NavigationService.java` | No | Stores and updates current street navigation state shared between dialog and map interactions. |
| `OverlayManager` | `OverlayManager.java` | No | Manages creation, refresh, visibility, and teardown of plugin-owned map overlay layers. |
| `OverviewManager` | `OverviewManager.java` | No | Coordinates overview dialogs and keeps their data synchronized with current plugin state. |
| `PostcodeCollector` | `PostcodeCollector.java` | No | Utility for extracting visible postcode candidates from the active dataset. |
| `PostcodeOverviewLayer` | `PostcodeOverviewLayer.java` | No | Map layer that visualizes postcode distribution and mismatches for quick QA checks. |
| `ReferenceStreetFetchService` | `ReferenceStreetFetchService.java` | No | Loads lightweight reference street geometries asynchronously with caching and debounce support. |
| `ReferenceStreetFetchService.ReferenceStreetContext` | `ReferenceStreetFetchService.java` | No | Immutable load context containing selected street and local dataset geometry anchors. |
| `ReferenceStreetLayer` | `ReferenceStreetLayer.java` | No | Read-only overlay layer that renders fetched reference street geometry in a distinct style. |
| `SingleBuildingSplitService` | `SingleBuildingSplitService.java` | No | Performs rollback-safe line splits within exactly one building geometry. |
| `SingleBuildingSplitService.RingPaths` | `SingleBuildingSplitService.java` | No | Two directed boundary paths between split nodes used to create the result polygons. |
| `SingleSplitResult` | `SingleSplitResult.java` | No | Result object for a single line split execution, including message and produced ways. |
| `SplitCommandBuilder` | `SplitCommandBuilder.java` | No | Builds command sequences for split operations, including node preparation and tag preservation. |
| `SplitCommandBuilder.PreparedNodeCommands` | `SplitCommandBuilder.java` | No | Bundle returned from node preparation containing new split nodes, updated ring, and commands. |
| `SplitContext` | `SplitContext.java` | No | Lightweight context values that split services propagate into newly created building parts. |
| `StreetCompletenessHeuristic` | `StreetCompletenessHeuristic.java` | No | Estimates whether a street is likely incomplete to decide when reference street loading is useful. |
| `StreetHouseNumberCountCollector` | `StreetHouseNumberCountCollector.java` | No | Collects per-street address counts and completeness indicators across the current dataset. |
| `StreetHouseNumberCountDialog` | `StreetHouseNumberCountDialog.java` | No | Dialog that lists streets with address counts, selection shortcuts, and rescan controls. |
| `StreetHouseNumberCountRow` | `StreetHouseNumberCountRow.java` | No | Row model for per-street house-number counts, including duplicate marker information. |
| `StreetModeController` | `StreetModeController.java` | Yes | Orchestrates Street Mode state, dialog synchronization, overlays, and split/address operations. |
| `StreetModeController.AddressSelection` | `StreetModeController.java` | Yes | Immutable current address selection transferred from dialog to map mode. |
| `StreetModeController.ReferenceLoadKey` | `StreetModeController.java` | Yes | Cache/load key combining dataset identity and normalized street name. |
| `StreetModeController.SplitTargetScan` | `StreetModeController.java` | Yes | Scan result describing whether a temporary split line targets exactly one building. |
| `StreetNameCollector` | `StreetNameCollector.java` | No | Utility for collecting unique street names from highway ways in the current dataset. |
| `StreetSelectionDialog` | `StreetSelectionDialog.java` | No | Main configuration dialog where users pick street/address settings before working in map mode. |
| `TerraceSplitRequest` | `TerraceSplitRequest.java` | No | Input object for row-house splitting that currently carries the requested part count. |
| `TerraceSplitResult` | `TerraceSplitResult.java` | No | Result object for row-house split execution with status message and resulting ways. |
| `TerraceSplitService` | `TerraceSplitService.java` | No | Splits row-house buildings into configured parts based on click position and geometry orientation. |
| `TerraceSplitService.Bounds` | `TerraceSplitService.java` | No | Axis-aligned latitude/longitude bounds used for fallback terrace splitting. |
| `TerraceSplitService.SplitLine` | `TerraceSplitService.java` | No | Concrete split line in projected coordinates for one ratio step. |
| `TerraceSplitService.SplitOrientation` | `TerraceSplitService.java` | No | Orientation model that derives deterministic split lines along the main building axis. |
| `TerraceSplitService.Vector2D` | `TerraceSplitService.java` | No | Small 2D vector helper used for orientation and projection calculations. |

> Maintenance note: Update this table when adding new classes or changing class comments.

# HouseNumberClick

JOSM plugin for fast house-number mapping on buildings.

## What's New in 1.1.0

- New `Show street house number counts` window with per-street counts and click-to-zoom.
- Sortable street-count table (default: count descending; header click toggles sorting).
- Extended street workflow visuals: overlay, odd/even connection lines, duplicate highlighting, and overview-based zoom.
- Input/shortcut behavior hardened so street navigation shortcuts do not interfere with text editing.

## What's New in 1.1.1

- Improved `L`, `+`, and `-` shortcut reliability when dialogs are open.
- Prevented shortcut actions while typing in text fields.
- Checkbox interactions now return focus to the map when Street Mode is active.

## What's New in 1.1.2

- Corrected duplicate marker logic in house-number overview (`xN` now reflects exact repeated values only).
- Duplicate overview entries now zoom to the full base-number group.
- Street-count list now marks streets with exact duplicate house numbers as `N (dup)`.

## What's New in 1.1.3

- Street dialog UX polish with clearer labels and compact section spacing.
- Building overview addressed color changed to green.
- Overlay connection lines made thicker for better readability.
- Overlay refresh now happens immediately after successful address assignment.

## Demo
![HouseNumberClick demo](docs/images/housenumberclick-demo.gif)

## Core Features

- Opens a working dialog with `Street`, `Postcode`, optional `Building type`, `House number`, and increment (`-2`, `-1`, `+1`, `+2`).
- Left-click on a building applies address tags (`addr:street`, optional `addr:postcode`, optional `addr:housenumber`) and optionally sets `building=*`.
- House number can auto-advance after successful apply, including letter suffix handling (`12a -> 12b`).
- `Ctrl` + left-click reads existing address values from a building; if no building is hit, nearby street name can be picked.
- Conflict warning protects overwriting existing address values (street/postcode).
- Optional `Zoom to selected street` zooms to all mapped house numbers of the currently selected street.

## Map Mode Shortcuts

- `+` / `-`: change current house number component.
- `L`: toggle letter suffix (`12 <-> 12a`).
- `Esc`: leave/pause Street Mode.
- Left/right street navigation does not trigger while typing in text fields.

## Optional Visual Tools

- `Show house number layer`: overlay of house numbers for the selected street.
- `Show connection lines`: connect mapped numbers in sorted order; `Separate even and odd connection lines` draws parity-specific paths.
- Duplicate house numbers are highlighted in the overlay.
- `Show house number overview`: odd/even table including gap markers (`•` for missing base numbers); click a cell to zoom to the mapped object.
- `Show street house number counts`: list of streets with known house-number counts; click a row to zoom to that street.
- Street-count table supports sorting by `Street` and `Count`.
- `Show Overview`: building-only overview layer with color coding:
  - green = building has `addr:housenumber` on the building object,
  - subtle yellow/ochre = multipolygon building relation has no `addr:housenumber`, but at least one `outer` way has one (misplaced tag warning),
  - dark gray = no house number found.

## Optional Integration

- Integrates with [`BuildingSplitter`](https://github.com/olileitner/buildingsplitter) when installed.

## Usage

1. Start <img src="images/housenumberclick.svg" alt="HouseNumberClick icon" width="18" /> `HouseNumberClick` in JOSM.
2. Select street and optional postcode/building type/house number.
3. Click buildings to apply addresses.
4. Use optional shortcuts and overview windows as needed.

![HouseNumberClick dialog](docs/images/housenumberclick-dialog.png)


## Build and Test

Prerequisite for compile/dist:
- `buildingsplitter.jar` at `~/.josm/plugins/buildingsplitter.jar`
  or custom path via `-Dbuildingsplitter.jar=/path/to/buildingsplitter.jar`

```bash
ant compile
ant test
ant dist
```

## Local Installation

```bash
mkdir -p ~/.josm/plugins
cp dist/HouseNumberClick.jar ~/.josm/plugins/
```

## License

GNU General Public License v2. See `LICENSE`.

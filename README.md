# HouseNumberClick

JOSM plugin for fast house-number mapping on buildings.

## What's New in 1.1.4

- Street dialog wording is now more consistent and clearer across all display options.
- Updated option names in the UI and docs (for example `Auto-zoom to selected street`, `Show house number labels`, `Show all street counts`).
- Improved README usage guidance with the plugin icon and explicit house-number auto-increment note.

## Demo
![HouseNumberClick demo](docs/images/housenumberclick-demo.gif)

## Core Features

- Opens a working dialog with `Street`, `Postcode`, optional `Building type`, `House number`, and increment (`-2`, `-1`, `+1`, `+2`).
- Left-click on a building applies address tags (`addr:street`, optional `addr:postcode`, optional `addr:housenumber`) and optionally sets `building=*`.
- House number can auto-advance after successful apply, including letter suffix handling (`12a -> 12b`).
- `Ctrl` + left-click reads existing address values from a building; if no building is hit, nearby street name can be picked.
- Conflict warning protects overwriting existing address values (street/postcode).
- Optional `Auto-zoom to selected street` zooms to all mapped house numbers of the currently selected street.

## Map Mode Shortcuts

- `+` / `-`: change current house number component.
- `L`: toggle letter suffix (`12 <-> 12a`).
- `Esc`: leave/pause Street Mode.
- Left/right street navigation does not trigger while typing in text fields.

## Optional Visual Tools

- `Show house number labels`: overlay of house numbers for the selected street.
- `Show connection lines`: connect mapped numbers in sorted order; `Separate even / odd` draws parity-specific paths.
- Duplicate house numbers are highlighted in the overlay.
- `Show overview panel (selected street)`: odd/even table including gap markers (`•` for missing base numbers); click a cell to zoom to the mapped object.
- `Show all street counts`: list of streets with known house-number counts; click a row to zoom to that street.
- Street-count table supports sorting by `Street` and `Count`.
- `Show overview` / `Hide overview`: building-only overview layer with color coding:
  - green = building has `addr:housenumber` on the building object,
  - subtle yellow/ochre = multipolygon building relation has no `addr:housenumber`, but at least one `outer` way has one (misplaced tag warning),
  - dark gray = no house number found.

## Optional Integration

- Integrates with [`BuildingSplitter`](https://github.com/olileitner/buildingsplitter) when installed.

## Usage

1. Start <img src="images/housenumberclick.svg" alt="HouseNumberClick icon" width="18" /> `HouseNumberClick` in JOSM.
2. Select street and optional postcode/building type/house number.
3. Click buildings to apply addresses. The house number increments automatically after each successful click.
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

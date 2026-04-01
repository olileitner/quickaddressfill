# QuickAddressFill

JOSM plugin for quickly applying address tags to buildings.

## Features

- Opens a dialog with `Postcode`, `Street`, optional `Building type`, `House number`, and increment selection (`-2`, `-1`, `+1`, `+2`).
- Shows mode state in the dialog: `Active` or `Paused`, including `Continue working` when paused.
- Activates a map mode where a left-click on a building sets the following tags:
  - `addr:street`
  - `addr:postcode` (if filled)
  - `building` (if building type is filled)
  - `addr:housenumber` (if filled)
- Building type is single-use: if filled, it is applied on the next successful building click and then cleared in the dialog.
- Shows an overwrite warning for `addr:street` and `addr:postcode`.
- Automatically increments the house number after successfully applying tags, using the selected increment.
- For house numbers containing letters (for example `12a`), the letter part is incremented (`12a` -> `12b`) instead of the numeric part.
- If a letter-based house number is entered while `+2` or a negative increment is selected, the dialog automatically switches increment back to `+1`.
- `SPACE` also increments the house number without clicking a building (for skipped/missing buildings).
- `-` decreases the house number by one.
- `L` toggles a letter suffix on the house number (`12` <-> `12a`) for quick switching between numeric and lettered values.
- `Ctrl` + left-click on a building reads `addr:street`, `addr:postcode`, and `addr:housenumber` into the dialog (building type is not imported by pickup).
- `Ctrl` + left-click on a street (without a building hit) reads the nearby street `name` into the dialog street field and sets house number to `1`.
- Status line displays active values and updates continuously (street, postcode, house number, increment), including `QAF PAUSED` when mode is inactive.

## Dialog Hint

The dialog shows this hint below the increment buttons:

`Hint: SPACE increments, - decrements, L toggles the letter suffix (e.g. 12 <-> 12a).`

This makes the `SPACE` shortcut visible directly in the dialog.

## Usage

1. Start `Quick Address Fill` in JOSM.
2. Select a street; optionally enter postcode, building type, and a starting house number.
3. Select increment (`-2`, `-1`, `+1`, `+2`).
4. Optional: use `Ctrl` + left-click on a building to read its address data into the dialog, or `Ctrl` + left-click on a street to pick its name.
5. Left-click buildings to apply tags.
6. Optional: press `SPACE` to advance the house number without tagging a building.
7. Optional: press `-` to reduce the current house number by one.
8. Optional: press `L` to toggle a trailing `a` suffix on/off.
9. Press `ESC` to pause/exit Street Mode (you can continue from the dialog).

## Build

```bash
ant compile
ant dist
```

## Local Installation

```bash
mkdir -p ~/.josm/plugins
cp dist/QuickAddressFill.jar ~/.josm/plugins/
```










# HouseNumberClick 1.1.2 Release Notes

HouseNumberClick is a JOSM plugin for fast address tagging on buildings with street-focused editing workflows.

## Highlights Since 1.1.1

- Improved duplicate marker logic in house-number overview entries:
  - mixed variants like `1`, `1a`, `1b` are no longer treated as duplicates
  - exact repeats like `1, 1, 1a` or `1, 1a, 1a` are marked correctly
- Added duplicate marker in street-count overview (`N (dup)`) for streets containing at least one exact duplicate house number.
- Improved overview table click behavior so duplicate entries zoom to the full base-number group instead of a single representative object.
- Restored map focus after clicks in overview/count tables to keep map-mode shortcut flow uninterrupted.

## Stability and UX Improvements

- Added regression coverage for duplicate marker and grouped overview zoom behavior.

## Build Artifact

- Release artifact: `dist/HouseNumberClick.jar`


# HouseNumberClick 1.1.4 Release Notes

HouseNumberClick is a JOSM plugin for fast address tagging on buildings with street-focused editing workflows.

## Highlights Since 1.1.3

- Interaction model is now fully single-mode: `HouseNumberClickStreetMapMode` remains the only active map mode.
- Line split now runs inline in street mode via `Alt+Drag` (no temporary mode switch).
- Row-house split remains inline via right-click on a building and uses the configured `Parts` value from the dialog.
- `Alt+1..9` now quick-sets row-house `Parts` directly in Street Mode.
- Legacy split-mode controller entrypoints and split-mode compatibility paths were removed to match the new UX model.
- Street dialog option wording was aligned for clearer and more consistent terminology.
- README terminology was synchronized with current dialog labels.
- Usage documentation now includes the plugin icon and a short auto-increment note for house numbers.

## Documentation and I18N Maintenance

- Translation extraction source list (`i18n/POTFILES.in`) was updated to include all UI classes using translated strings.

## Build Artifact

- Release artifact: `dist/HouseNumberClick.jar`


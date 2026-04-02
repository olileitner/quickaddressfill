# Translation Workflow Preparation

This folder contains the project-local i18n layout for the QuickAddressFill JOSM plugin.

## Structure

- `POTFILES.in`: deterministic list of Java files used for extraction.
- `po/`: future translation catalogs (`*.po`).
- `po/templates/`: generated POT template output.
- `lang/`: generated JOSM `.lang` files to be packaged into the plugin JAR.

## Build Targets

- `ant i18n-extract`: generates `po/templates/QuickAddressFill.pot` using `xgettext`.
- `ant i18n-merge`: updates any existing `po/*.po` against the latest POT.
- `ant i18n-lang`: generates `lang/*.lang` from `po/*.po` using JOSM `i18n.pl`.
- `ant i18n`: runs `i18n-extract`, `i18n-merge`, and `i18n-lang`.

The standard `ant dist` build now packages files from `i18n/lang/` into
`data/QuickAddressFill/lang/` inside the plugin JAR when such files exist.

## First Translation Example

1. Run `ant i18n-extract`.
2. Create `po/de.po` from `po/templates/QuickAddressFill.pot`.
3. Translate entries in `po/de.po`.
4. Run `ant i18n-lang` to generate `lang/de.lang`.
5. Run `ant dist` to package `lang/de.lang` into the plugin JAR.

## Prerequisites

- `perl`
- JOSM `i18n.pl` conversion script at `i18n/i18n.pl`
  - alternatively: pass path with `-Di18n.pl=/path/to/i18n.pl`


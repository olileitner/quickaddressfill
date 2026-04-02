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

The standard `ant dist` build now packages files from `i18n/lang/` into
`data/QuickAddressFill/lang/` inside the plugin JAR when such files exist.


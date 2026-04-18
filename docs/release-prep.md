# HouseNumberClick Release Prep (PluginsSource-First)

## Scope

This checklist prepares a release for externally hosted jars (for example GitHub Releases) that can be consumed by JOSM PluginsSource.

## 1) Update Version and Notes

1. Set `plugin.version` in `build.xml`.
2. Update `RELEASE_NOTES.md` for that version.
3. Keep only one `What's New` block for the current version in `README.md`.
4. Use only non-interactive release commands (no editor prompts).

## 2) Build and Validate

```bash
cd /home/oliver/IdeaProjects/housenumberclick
ant clean
ant test
ant release-artifact
```

Expected files:

- `dist/HouseNumberClick.jar`
- `dist/HouseNumberClick-<version>.jar`

## 3) Verify Manifest Metadata

Check the built jar contains:

- `Plugin-Class`
- `Plugin-Version`
- `Plugin-Mainversion`

## 4) GitHub Release

Preferred publisher:

- Push annotated tag `v<version>` and let `.github/workflows/release.yml` create/update the release.

```bash
cd /home/oliver/IdeaProjects/housenumberclick
git tag -a v<version> -m "Release v<version>"
git push origin main
git push origin v<version>
```

Manual fallback (only when workflow automation is unavailable):

```bash
cd /home/oliver/IdeaProjects/housenumberclick
git tag -a v<version> -m "Release v<version>"
git push origin main
git push origin v<version>
gh release create v<version> dist/HouseNumberClick-<version>.jar \
  --title "HouseNumberClick v<version>" \
  --notes-file RELEASE_NOTES.md
```

Hard rule:
- If a command would open an editor, stop and rerun with explicit non-interactive flags.
- Do not publish twice for the same tag (automation + manual).

## 5) PluginsSource URL Pattern

Use the direct release asset URL:

`https://github.com/<owner>/<repo>/releases/download/v<version>/HouseNumberClick-<version>.jar`


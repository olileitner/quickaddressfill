# GitHub Release Steps (PluginsSource-First)

This repository uses externally hosted plugin jars (GitHub Releases + PluginsSource).

## 1) Preflight

```bash
cd /home/oliver/IdeaProjects/housenumberclick
git --no-pager status --short --branch
git pull --rebase
```

Checklist:

- Working tree clean
- Branch up to date
- `plugin.version` updated in `build.xml`
- Version matches intended tag (`v<version>`)
- If `po/*.po` files exist and local `i18n/i18n.pl` is missing, use `-Di18n.pl=/path/to/i18n.pl` for `ant i18n-lang`

## 2) Build + Test

```bash
cd /home/oliver/IdeaProjects/housenumberclick
ant clean
ant test
ant release-artifact
```

Expected:

- `dist/HouseNumberClick.jar`
- `dist/HouseNumberClick-<version>.jar`

## 3) Verify Artifact

```bash
cd /home/oliver/IdeaProjects/housenumberclick
ls -lh dist/HouseNumberClick-<version>.jar
unzip -p dist/HouseNumberClick-<version>.jar META-INF/MANIFEST.MF
```

Check:

- `Plugin-Version = <version>`
- `Plugin-Mainversion` correct
- `Plugin-Class` present

## 4) Tag Release

```bash
cd /home/oliver/IdeaProjects/housenumberclick
git tag -a v<version> -m "Release v<version>"
git push origin main
git push origin v<version>
```

Verify:

```bash
cd /home/oliver/IdeaProjects/housenumberclick
git --no-pager show v<version>
```

Ensure tag points to the intended release commit.

## 5) Create GitHub Release

```bash
cd /home/oliver/IdeaProjects/housenumberclick
gh release create v<version> dist/HouseNumberClick-<version>.jar \
  --title "HouseNumberClick v<version>" \
  --notes-file RELEASE_NOTES.md
```

Rules:

- No interactive prompts
- Only upload versioned jar (`dist/HouseNumberClick-<version>.jar`)
- Release notes must match version

## 6) Post-Release Verification

Check on GitHub:

- Tag exists
- Release exists
- Correct `.jar` uploaded
- Filename matches version
- Release notes correct

## 7) Verify Downloaded Artifact (Important)

Do not trust local build only; verify published artifact:

```bash
curl -L -o test.jar \
https://github.com/olileitner/housenumberclick/releases/download/v<version>/HouseNumberClick-<version>.jar

unzip -p test.jar META-INF/MANIFEST.MF
```

PluginsSource URL pattern:

`https://github.com/<owner>/<repo>/releases/download/v<version>/HouseNumberClick-<version>.jar`

## 8) Local Smoke Test (Real Release Artifact)

```bash
cd /home/oliver/IdeaProjects/housenumberclick
cp test.jar ~/.josm/plugins/HouseNumberClick.jar
```

Start JOSM and verify:

- Plugin loads without errors
- Street Mode works:
  - Left click (`apply`)
  - Ctrl+Click (`readback`)
  - Right click (`row-house split`)
  - Alt+Drag (`line split`)

## 9) Failure Conditions (Release is Invalid if)

- Version mismatch (tag vs jar vs manifest)
- Wrong artifact uploaded
- Manifest missing required fields
- Plugin fails to load in JOSM

## 10) Final Rule

If anything is unclear during release:

STOP and fix it before publishing.

A broken release is worse than a delayed release.


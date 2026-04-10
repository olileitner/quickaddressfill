# GitHub Release Steps (PluginsSource-First)

This repository is prepared for externally hosted plugin jars (for example via GitHub Releases + JOSM PluginsSource).

## 1) Preflight

- Ensure working tree is clean.
- Ensure local branch is up to date.
- Update target release version in `build.xml` (`plugin.version`) before tagging.

```bash
cd /home/oliver/IdeaProjects/housenumberclick
git --no-pager status --short --branch
git pull --rebase
```

## 2) Build + Test

```bash
cd /home/oliver/IdeaProjects/housenumberclick
ant clean
ant test
ant release-artifact
```

Expected artifacts:
- `dist/HouseNumberClick.jar`
- `dist/HouseNumberClick-<version>.jar`

Quick artifact/manifest check:

```bash
cd /home/oliver/IdeaProjects/housenumberclick
ls -lh dist/HouseNumberClick-<version>.jar
unzip -p dist/HouseNumberClick-<version>.jar META-INF/MANIFEST.MF
```

## 3) Tag the Release

```bash
cd /home/oliver/IdeaProjects/housenumberclick
git tag v<version>
git push origin v<version>
```

## 4) Create GitHub Release

1. Open GitHub repository releases page.
2. Create a new release from tag `v<version>`.
3. Title suggestion: `HouseNumberClick v<version>`.
4. Copy content from `RELEASE_NOTES.md` into the release description.
5. Upload artifact `dist/HouseNumberClick-<version>.jar`.
6. Publish release.

PluginsSource URL pattern for the uploaded asset:

`https://github.com/<owner>/<repo>/releases/download/v<version>/HouseNumberClick-<version>.jar`

## 5) Post-Release Quick Check

- Verify tag exists remotely.
- Verify release notes text and uploaded versioned jar are correct.
- Verify downloaded jar contains manifest entries:
  - `Plugin-Class`
  - `Plugin-Version`
  - `Plugin-Mainversion`

## 6) Local Smoke Test (Recommended)

```bash
cd /home/oliver/IdeaProjects/housenumberclick
cp dist/HouseNumberClick.jar ~/.josm/plugins/
```

- Start JOSM and verify plugin loads.
- Start Street Mode and test:
  - Left click (`apply`)
  - Ctrl+Click (`readback`)
  - Right click (`row-house split`)
  - Alt+Drag (`line split`)


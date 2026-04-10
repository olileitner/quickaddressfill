# JOSM PluginsSource Release Preparation

This repository is prepared for a PluginsSource-first release flow using an externally hosted jar (for example GitHub Releases).

## 1) Repository Status (Verified)

From `build.xml` in this repository:

- Metadata in jar manifest is set:
  - `Plugin-Class`
  - `Plugin-Version`
  - `Plugin-Mainversion`
- Build targets available:
  - `clean`, `compile`, `test`, `dist`, `release-artifact`, `i18n-*`
- Release artifact support:
  - `dist/HouseNumberClick.jar`
  - `dist/HouseNumberClick-<version>.jar` via `ant release-artifact`

## 2) Recommended PluginsSource-First Flow

1. Update `plugin.version` in `build.xml` before tagging/release.
2. Build and verify:

```bash
cd /home/oliver/IdeaProjects/housenumberclick
ant clean
ant test
ant release-artifact
```

   Quick verification:

```bash
ls -lh dist/HouseNumberClick-<version>.jar
unzip -p dist/HouseNumberClick-<version>.jar META-INF/MANIFEST.MF
```

3. Tag and release on GitHub:

```bash
git tag v<version>
git push origin v<version>
```

4. Upload `dist/HouseNumberClick-<version>.jar` as release asset.
5. Use the direct release asset URL for PluginsSource.

URL pattern:

`https://github.com/<owner>/<repo>/releases/download/v<version>/HouseNumberClick-<version>.jar`

## 3) Hosting Note and Optional Future Official JOSM Publish Path

This repository intentionally does not include an `ant publish` target; external artifact hosting is the intended release path.

If you later decide to use the official JOSM publish path, that must be done in the JOSM publication context/repository where `ant publish` is supported.


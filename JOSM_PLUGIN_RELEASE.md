# JOSM PluginsSource Release Preparation

This repository is prepared for a **PluginsSource-first release flow** using externally hosted artifacts (for example GitHub Releases).

## 1) Repository Status (Current Implementation)

From `build.xml` in this repository:

- Metadata is written into the jar manifest:
  - `Plugin-Class`
  - `Plugin-Version`
  - `Plugin-Mainversion`
- Build targets available:
  - `clean`, `compile`, `test`, `dist`, `release-artifact`, `i18n-*`
- Release artifacts:
  - `dist/HouseNumberClick.jar`
  - `dist/HouseNumberClick-<version>.jar` (via `ant release-artifact`)

## 2) Recommended PluginsSource-First Flow

### 1. Prepare Version

- Update `plugin.version` in `build.xml`.
- Ensure version matches intended tag (`v<version>`).

### 2. Build and Verify

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

Verify:

- `Plugin-Version` matches `<version>`
- `Plugin-Mainversion` is correct
- `Plugin-Class` manifest entry is present
- `ant test` passes

If `po/*.po` files are present and local `i18n/i18n.pl` is missing, run with explicit override:

```bash
ant -Di18n.pl=/path/to/i18n.pl i18n-lang
```

### 3. Tag and Release (Non-Interactive)

Preferred path:

- Push annotated tag `v<version>` and let `.github/workflows/release.yml` publish the release artifact.

```bash
git tag -a v<version> -m "Release v<version>"
git push origin main
git push origin v<version>
```

Manual fallback (only when automation is unavailable):

```bash
gh release create v<version> dist/HouseNumberClick-<version>.jar \
  --title "HouseNumberClick v<version>" \
  --notes-file RELEASE_NOTES.md
```

Hard rule:

- Do NOT allow interactive editor prompts.
- Always provide explicit flags.
- Never publish twice for the same tag (automation + manual).

### 4. PluginsSource URL

Use the versioned release asset:

`https://github.com/<owner>/<repo>/releases/download/v<version>/HouseNumberClick-<version>.jar`

Important:

- This URL only works if the **exact file name matches the uploaded asset**.

## 3) Design Decision: External Artifact Hosting

This repository intentionally does NOT include an `ant publish` target.

Reason:

- Artifacts are hosted externally (GitHub Releases)
- PluginsSource consumes the hosted jar

## 4) Optional Future: Official JOSM Publish Flow

If switching to official JOSM publishing:

- Must be done in the JOSM PluginsSource environment
- Requires `ant publish` support there
- This repository setup remains valid but is not sufficient alone

## 5) Critical Consistency Rules

Before any release, all of the following MUST match exactly:

- tag (`v<version>`)
- `plugin.version`
- release asset filename
- release notes

Any mismatch can break update behavior.

## 6) Doc Sync Check (Mandatory)

Before merging release-process documentation changes, verify this file stays aligned with:

- `RELEASE_STEPS.md`

At minimum, keep these topics consistent across both files:

- non-interactive release command rules
- annotated tag rule (`git tag -a ...`)
- versioned-jar-only upload rule
- i18n fallback guidance (`-Di18n.pl=/path/to/i18n.pl` when applicable)
- version/tag/asset/release-notes consistency checks


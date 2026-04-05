# JOSM Plugin Manager Release Preparation

This document captures what is currently verifiable in this repository and what is still needed for an official JOSM Plugin Manager publication.

## 1) Current Repository Status (Verified)

From `build.xml` in this repository:

- Present metadata:
  - `plugin.class`
  - `plugin.version` (now `1.1.2`)
  - `plugin.main.version` (`19481`)
- Present build targets:
  - `clean`, `compile`, `test`, `dist`, `i18n-*`
- Missing target:
  - no `publish` target in this standalone build file
- Missing property for publish workflow:
  - no `commit.message` property in this standalone build file

Conclusion:
- The repository is ready for GitHub releases.
- It is **not yet directly publish-ready** for an `ant publish`-based JOSM Plugin Manager workflow from this repository alone.

## 2) What Is Needed For Official JOSM Publication

For an official JOSM Plugin Manager release, you need the JOSM plugin publication context where `ant publish` is available.

Required preparation steps:

1. Place/sync plugin sources in the official JOSM plugin publication repository/context.
2. Use the build setup expected by that context (including `publish` target).
3. Add required publication metadata there (including commit/release message field if required by that build setup).
4. Ensure plugin metadata values are correct and consistent:
   - plugin class
   - plugin version
   - required JOSM main version
5. Run publication in that context:
   - `ant clean`
   - `ant test` (or equivalent validation target)
   - `ant dist`
   - `ant publish`

## 3) Practical Checklist Before Moving To Official Publish Context

Run locally in this repository first:

```bash
cd /home/oliver/IdeaProjects/housenumberclick
ant clean
ant test
ant dist
```

Verify artifact:
- `dist/HouseNumberClick.jar`

Verify manifest fields in built jar:
- `Plugin-Class`
- `Plugin-Version`
- `Plugin-Mainversion`

## 4) GitHub Release vs Official JOSM Release

GitHub release (already supported here):
- tag + release notes + upload `dist/HouseNumberClick.jar`

Official JOSM Plugin Manager release:
- requires JOSM publication repository/context and `ant publish`
- updates plugin availability in JOSM Plugin Manager, not only GitHub assets

## 5) Recommended Next Step

Use `RELEASE_STEPS.md` for GitHub release `v1.1.2`, then prepare migration/synchronization into the official JOSM plugin publication workflow to execute `ant publish` there.


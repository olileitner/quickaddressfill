# GitHub Release Steps (v1.1.2)

This file documents the release flow for the next GitHub patch release after `1.1.1`.

## Why 1.1.2

`1.1.2` is a patch release focused on duplicate-marker correctness and grouped overview zoom behavior.

## 1) Preflight

- Ensure working tree is clean.
- Ensure local branch is up to date.

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
ant dist
```

Expected artifact:
- `dist/HouseNumberClick.jar`

## 3) Tag the Release

```bash
cd /home/oliver/IdeaProjects/housenumberclick
git tag v1.1.2
git push origin v1.1.2
```

## 4) Create GitHub Release

1. Open GitHub repository releases page.
2. Create a new release from tag `v1.1.2`.
3. Title suggestion: `HouseNumberClick v1.1.2`.
4. Copy content from `RELEASE_NOTES.md` into the release description.
5. Upload artifact `dist/HouseNumberClick.jar`.
6. Publish release.

## 5) Post-Release Quick Check

- Verify tag exists remotely.
- Verify release notes text and uploaded jar are correct.
- Verify downloaded jar contains manifest entries:
  - `Plugin-Class`
  - `Plugin-Version`
  - `Plugin-Mainversion`


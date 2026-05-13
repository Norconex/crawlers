# Release Changelogs

Changelogs are created on **release branches only** and are preserved in git history at each release tag.
They are not kept in the main branch to avoid duplication.

## Workflow Overview

1. Create `changelog-{VERSION}.md` on the release branch before release.
2. The release workflow validates the file exists and has content.
3. After release, the changelog is permanently recorded in git history at that tag.
4. Delete the changelog file from the release branch before merging back to main.
5. Next release: Create a fresh changelog file on the next release branch.

## File Naming Convention

Changelog files **must** be named with the version they document:

```
changelog-{VERSION}.md
```

Example: `changelog-4.0.0.md`, `changelog-4.0.1.md`

This naming convention:

- Prevents accidental reuse of old changelogs
- Makes the release workflow able to validate existence
- Ties each changelog to exactly one release version

## Best Practices

1. **Create on release branch only** — Add the changelog to the release branch, not main.
2. **Create before triggering release** — File must exist before running the release workflow.
3. **Version-specific content** — Each changelog documents only changes in that specific version.
4. **Delete after successful release** — Remove the file from the release branch before merging back to main.
5. **User-focused language** — Write for end users, not developers. Focus on what changed and why they should care.
6. **Organized sections** — Group changes by category (see template below).

## Content Structure

Changelogs should follow this structure:

```markdown
# Version X.Y.Z

Release Date: YYYY-MM-DD

## Added

- New feature 1
- New feature 2

## Improved

- Improvement to existing feature

## Fixed

- Bug fix 1
- Bug fix 2

## Deprecated

- Deprecated API/option (if applicable)

## Removed

- Removed API/option (if applicable)

## Breaking Changes

- Breaking change 1 (if applicable)
- Include migration guidance if possible

## Upgrade Notes

- Special instructions for upgrading to this version (if applicable)
```

## Release Workflow Integration

The release workflow (`.github/workflows/release.yaml`) enforces:

1. **File existence check** — Release fails if `changelog-{VERSION}.md` does not exist.
2. **Content validation** — Release fails if the changelog file is empty or contains only whitespace.

This ensures every release has documented changes and prevents shipping empty changelogs.

## After Release: Cleanup

After a successful release:

1. The changelog file is recorded in git history at that release's tag.
   - Example: `v4.0.1` commit contains `changelogs/changelog-4.0.1.md`
   - Users can view it via `git show v4.0.1:changelogs/changelog-4.0.1.md` if needed
   - It's also accessible in GitHub at that tag

2. **Delete the changelog file** from the release branch before merging back to main.
   - This prevents accumulation and duplication in the main branch
   - Keeps the codebase clean

3. When the next release branch is created, create a fresh `changelog-X.Y.Z.md` file.
   - Use `TEMPLATE.md` as a reference
   - This prevents mistakes and ensures each release has its own file

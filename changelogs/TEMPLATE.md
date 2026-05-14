# Version X.Y.Z

Release Date: YYYY-MM-DD

## Added

- Describe new features
- Include configuration options if relevant
- Example: "Added support for parallel processing in web crawler with `maxThreads` configuration"

## Improved

- Describe enhancements to existing functionality
- Include behavior changes if any
- Example: "Improved filtering performance by 30% through optimized regex compilation"

## Fixed

- Describe bug fixes
- Reference issue numbers if applicable
- Example: "Fixed issue #123: URL encoding in query parameters"

## Deprecated

- List APIs, configuration options, or features marked as deprecated
- Indicate what users should use instead
- Example: "Deprecated `useOldParser` in favor of `parserVersion: 2`"

## Removed

- List APIs, configuration options, or features that have been removed
- Only applicable for major version changes or later patch versions
- Example: "Removed support for Java 11; Java 21+ required"

## Breaking Changes

- Describe any breaking changes
- **Include migration guidance** for affected users
- Example: "Configuration property `maxConnections` renamed to `connectionPool.maxSize`. Update your configs accordingly."

## Upgrade Notes

- Special instructions for upgrading to this version
- Database migration steps (if applicable)
- Configuration changes required
- Plugin compatibility notes
- Example: "All existing crawl profiles must be re-validated due to internal schema changes. Use the migration tool: `./migrate-profiles.sh`"

---

**Note:** Keep language user-focused. Avoid internal technical jargon where possible.
Focus on "what changed and why users should care" rather than implementation details.

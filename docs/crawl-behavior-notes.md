# Crawl Behavior Notes

This note captures crawl behavior rules and debugging findings established while investigating the `feature/hazelcast` branch. It is intended as an internal reference first, but parts of it may later be suitable for product documentation.

## Core semantics

- `ProcessingStatus.PROCESSED` means a crawl entry was finalized for the run. It does not mean the document was necessarily fetched, downloaded, parsed, or imported.
- `ProcessingOutcome` carries the semantic result of handling a crawl entry, such as `PREMATURE`, `REDIRECT`, `NEW`, `UNMODIFIED`, `REJECTED`, or `DELETED`.
- When debugging recrawl behavior, distinguish lifecycle status from actual network work. The relevant question is often whether a URL reached fetch/import, not whether it ended as `PROCESSED`.

## Web pipeline rules

- `RecrawlableResolverStage` is part of the normal web importer pipeline and runs before fetch stages for non-orphan entries.
- `CrawlProcessStep` attaches the baseline crawl entry as `previousCrawlEntry` when the crawl is incremental.
- `RecrawlableResolverStage` uses that `previousCrawlEntry` to decide whether a URL should be processed again.

## Sitemap behavior

- Web crawler defaults include `GenericSitemapResolver`, `GenericSitemapLocator`, and `GenericRecrawlableResolver`.
- `WebCrawlTest` does not disable sitemap handling. The test extension only provides a temp work directory and a zero-delay resolver.
- `startReferencesSitemaps` are actively bootstrapped by the web crawl driver.
- `SitemapResolutionStage` is also part of the normal queue pipeline unless sitemap resolver or locator is explicitly set to `null`.

## Redirect and canonical behavior

- The special redirect second-pass requeue logic is intentional. It exists to support flows such as redirect/auth bounce behavior and canonical/redirect loops.
- Removing or over-tightening that exception can break valid scenarios such as `CanonicalRedirectLoopTest`.
- When a redirect target is requeued for a second pass, the requeue operation must preserve the replacement crawl-entry state, including updated redirect trail context.
- Requeueing by reviving a stale tracked entry can lose redirect-trail information and prevent canonical loop detection from firing.

## Finalization rule for incomplete outcomes

- For outcomes that are not new or modified, missing crawl-entry fields from the previous crawl entry must be copied onto `currentCrawlEntry` during finalization.
- Copying previous fields onto the wrapper `CrawlDocContext` instead of onto `currentCrawlEntry` is incorrect and causes metadata loss across runs.
- The fields that matter here include sitemap metadata and redirect-related fields that later recrawl decisions depend on.

## Findings from recent regressions

### `NonRecrawlablesRedirected`

- The failure on Run #3 was not caused by sitemap support being disabled.
- Runtime evidence showed sitemap resolution events and premature-rejection events were both occurring.
- The real issue was loss of prior crawl-entry data after premature finalization, which caused later runs to lose the sitemap metadata needed to suppress fetch.
- Fix: preserve previous crawl-entry fields by copying them onto `currentCrawlEntry` during finalization.

### `CanonicalRedirectLoopTest`

- The remaining canonical-loop failure after the first fix was caused by requeueing a stale tracked redirect target.
- That stale entry lacked the updated redirect trail, so the second pass treated the canonical URL as non-canonical again instead of recognizing the redirect/canonical loop.
- Fix: requeue the replacement crawl entry state rather than resurrecting the existing tracked entry unchanged.

## Validation guidance

- For cross-module web regressions that depend on current `crawler/core` code, prefer the reactor path:

```powershell
mvn --% -pl crawler/web -am -Dtest=<tests> -Dsurefire.failIfNoSpecifiedTests=false test
```

- This avoids validating `crawler/web` against stale installed artifacts from `crawler/core`.

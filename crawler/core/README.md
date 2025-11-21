crawler-core
==============

Crawler-related code shared between different crawler implementations

Website: https://opensource.norconex.com/crawlers/core/

## Infinispan presets (cluster profiles)

The core crawler uses Infinispan for clustering and cache storage.
Most users will only need to choose a high-level preset in
`InfinispanClusterConfig`:

- `CLUSTER` (default)
  - Cluster-capable profile, suitable for one or more nodes.
  - Uses the `cache/infinispan-cluster.xml` configuration.
  - Global state enabled, RocksDB persistence for ledgers and
    counters.
  - Recommended for production and durable, restartable crawls.

- `STANDALONE`
  - Single-node profile with similar persistence semantics as the
    cluster profile, but without multi-node clustering.
  - Uses the `cache/infinispan-standalone.xml` configuration.
  - Good when you run the crawler on a single machine but still
    want persistent state.

- `STANDALONE_MEMORY`
  - Lightweight, single-node, memory-centric profile.
  - Uses the `cache/infinispan-standalone-memory.xml` configuration.
  - Caches are local and cluster-wide global state is not persisted.
  - Useful for quick experiments and short-lived runs where you
    do not need to resume a crawl, but be mindful of heap usage.

Example (Java):

```java
var clusterConfig = new InfinispanClusterConfig()
        .setPreset(InfinispanClusterConfig.Preset.STANDALONE_MEMORY);
```

For most real crawls, prefer `CLUSTER` or `STANDALONE`. Use
`STANDALONE_MEMORY` when you explicitly want a fast, ephemeral
single-node setup.

## Development Setup

### Eclipse IDE Setup for ProtoStream

This project uses Infinispan ProtoStream for serialization, which requires annotation processing to generate schema files during compilation.

**For Eclipse users:**

The project includes a `.factorypath` file in version control that configures Eclipse annotation processing for ProtoStream. This ensures all Eclipse users can generate the required `CrawlerProtoSchemaImpl` class without manual configuration.

**Setup steps:**
1. Import the project as a Maven project
2. Eclipse should automatically use the `.factorypath` configuration
3. Right-click project → Properties → Java Compiler → Annotation Processing
4. Verify "Enable annotation processing" is checked
5. Generated source directory should be: `target/generated-sources/annotations`
6. After a clean build, the `CrawlerProtoSchemaImpl.java` file should appear in `target/generated-sources/annotations`

**Suppressing unused import warnings in generated code:**
The ProtoStream annotation processor may generate code with unused imports, which causes Eclipse warnings. To suppress these warnings for generated code only:

1. Right-click project → Properties → Java Build Path → Source tab
2. Look for `target/generated-sources/annotations` in the source folders list
3. If not present, click "Add Folder..." and add `target/generated-sources/annotations`
4. Expand the `target/generated-sources/annotations` entry
5. Click on "Ignore optional compile problems"
6. Check the box for "Unused imports"
7. Click "Apply and Close"

This will suppress unused import warnings only for generated code while preserving them for your hand-written code.

**If annotation processing doesn't work:**
- Right-click project → Maven → Reload Projects  
- Project → Clean → Clean project and rebuild
- Check that the `.factorypath` file is present in the project root
- Ensure annotation processing is enabled in project properties

**Why is .factorypath checked in?**
Despite Eclipse m2e 2.x claiming built-in annotation processing support, it doesn't work reliably with complex annotation processors like ProtoStream. The `.factorypath` file ensures consistent annotation processing configuration across all Eclipse installations.

**Other IDEs:**
IntelliJ IDEA, VS Code, and other IDEs work automatically via the Maven `annotationProcessorPaths` configuration - no additional setup required.
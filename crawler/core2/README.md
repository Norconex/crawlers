crawler-core
==============

Crawler-related code shared between different crawler implementations

Website: https://opensource.norconex.com/crawlers/core/

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
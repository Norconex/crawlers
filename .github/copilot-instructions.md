# Norconex Crawlers - Copilot Instructions

## Repository Overview

This is a Java-based Maven mono-repository containing the **Norconex Crawler v4 stack** - a comprehensive suite of web and filesystem crawlers with pluggable committers for various data stores. The crawler framework is designed to be highly configurable, extensible, and capable of handling complex crawling scenarios including document transformation, filtering, and routing to multiple backends.

**Repository Type:** Multi-module Maven project (Java 17, Maven 3.8.8+)  
**Size:** ~15 modules, ~350 Java files  
**Target Runtimes:** Java 17+, Command-line applications, Embeddable libraries  
**Key Technologies:** Maven, Lombok, JaCoCo, JUnit 5, AssertJ, Jackson, Apache Commons

## High-Level Architecture

### Core Modules
- **crawler/core** - Shared crawler functionality and APIs
- **crawler/web** - Web crawling implementation 
- **crawler/fs** - File system crawling implementation
- **importer** - Document parsing and transformation engine

### Committer Modules (Data Output)
- **committer/core** - Base committer functionality
- **committer/elasticsearch** - Elasticsearch integration
- **committer/solr** - Apache Solr integration  
- **committer/sql** - SQL database integration
- **committer/neo4j** - Neo4j graph database integration
- **committer/amazoncloudsearch** - Amazon CloudSearch integration
- **committer/azurecognitivesearch** - Azure Cognitive Search integration
- **committer/apachekafka** - Apache Kafka integration

### Utility Modules  
- **tools/config-converter** - Configuration migration utilities

## Build and Validation Commands

### Prerequisites
**Always ensure Java 17+ and Maven 3.8.8+ are installed before building.**

### Bootstrap
```bash
# Clone and setup (no additional bootstrap required)
cd /path/to/crawlers
mvn --version  # Verify Maven 3.8.8+
java -version  # Verify Java 17+
```

### Build Commands
```bash
# Compile single module (RECOMMENDED for development)
mvn clean compile -pl committer/core

# Compile multiple related modules
mvn clean compile -pl committer/core,committer/solr

# Full reactor build (all modules)
mvn clean compile

# Full package with distribution (single module)
mvn clean package -pl committer/core -Dmaven.javadoc.skip=true

# Build with code coverage (activate JaCoCo profile)
mvn clean test -pl committer/core -Pjacoco
```

### Test Commands  
```bash
# Run tests for single module (FASTEST)
mvn test -pl committer/core

# Run tests with coverage reporting
mvn clean test -pl committer/core -Pjacoco

# Run specific test class
mvn test -pl committer/core -Dtest=AbstractBatchCommitterTest
```

### Validation Commands
```bash
# Format validation (Eclipse-based formatter)
mvn formatter:validate -pl committer/core

# Format fix  
mvn formatter:format -pl committer/core

# Editor config validation
mvn editorconfig:check -pl committer/core

# Dependency updates check
mvn versions:display-dependency-updates -pl committer/core
```

### Build Timings
- Single module compile: ~10-20 seconds
- Single module test: ~15-30 seconds  
- Single module package: ~20-60 seconds
- Full reactor build: ~90-120 seconds (may fail at importer due to dependency issues)

### Known Build Issues & Workarounds

1. **Importer Dependency Issues**: `edu.ucar:jj2000:jar:5.4` artifact may fail to resolve
   - **Workaround**: Skip importer module with `-pl '!importer'` or use `-Dmaven.test.skip=true`

2. **EditorConfig Warnings**: XML files may show indent warnings  
   - **Expected behavior**: Warnings don't fail the build

3. **Memory Requirements**: Use `-Xmx3g` for large builds
   - **Example**: `mvn clean package -pl crawler/web -DargLine="-Xmx3g"`

## Project Layout and Architecture

### Root Directory Structure
```
├── .editorconfig           # Code formatting rules (4 spaces for Java)
├── .github/workflows/      # CI/CD pipelines (Maven-based)
├── lombok.config          # Lombok configuration
├── norconex-formatter.xml # Eclipse formatter configuration  
├── pom.xml               # Parent POM with shared dependencies
├── sonar-project.properties # SonarCloud configuration
├── assembly/             # Maven assembly descriptors
├── crawler/              # Crawler implementations
├── committer/            # Data output implementations
├── importer/             # Document processing
└── tools/                # Utility tools
```

### Key Configuration Files
- **pom.xml** - Maven parent POM, Java 17, dependency versions
- **.editorconfig** - 4-space indentation, LF line endings
- **lombok.config** - Lombok settings (log field name, etc.)
- **norconex-formatter.xml** - Eclipse Java formatter settings

### Test Structure  
- Tests use **JUnit 5** with **AssertJ** assertions
- Test naming: `*Test.java` classes in `src/test/java`
- Test resources in `src/test/resources`
- Mock objects use **Mockito**

### CI/CD Pipeline (`.github/workflows/maven-ci-cd.yaml`)
- Runs on: Ubuntu latest, Java 17, Maven 3.9.10
- Triggers: Push to main/release branches, PRs  
- Steps: Install Maven → Build → Test → SonarCloud analysis → Deploy snapshots
- **Important**: CI builds use `-Pjacoco` profile for code coverage

### Dependencies to Note
- **Norconex Commons Lang** 3.0.0-SNAPSHOT (Norconex utility library)
- **Apache Commons** suite (IO, Lang3, Collections4, Compress, etc.)
- **Jackson** 2.17.2 (JSON/XML/YAML processing)
- **Log4J** 2.24.3 (Logging framework)
- **JUnit 5** + **AssertJ** (Testing)
- **Lombok** 1.18.34 (Code generation)

### Generated Code Handling
- **Lombok** generates getters/setters/builders automatically
- **Delombok** goal runs during build to generate source for JavaDoc
- Generated sources in `target/generated-sources/`

## Key Development Guidelines

### Code Style
- **Always run formatter before committing**: `mvn formatter:format`
- **4-space indentation** for Java (enforced by editorconfig)
- **LF line endings** (cross-platform compatibility) 
- **Use Lombok annotations** (@Data, @Builder, etc.) consistently

### Testing
- **Write tests for new functionality** using JUnit 5 + AssertJ
- **Use @CrawlTest annotation** for crawler-specific test setup
- **Mock external dependencies** with Mockito
- **Test module isolation**: Always use `-pl module/path` for testing

### Module-Specific Development
- **Module-specific builds recommended**: Use `-pl committer/core` pattern for faster iteration
- **Understand dependencies**: Check each module's POM for specific requirements
- **Build incrementally**: Test module changes before full builds
- **Skip problematic modules**: Use `-pl '!importer'` to exclude importer if needed

## Essential Commands Reference

```bash
# Quick development cycle (single module)
mvn clean compile test -pl committer/core

# Full reactor build (all modules, may fail at importer)
mvn clean compile

# Build excluding importer module
mvn clean compile -pl '!importer'

# Full package with validation (single module)  
mvn clean package -pl committer/core -Pjacoco

# Format code before commit
mvn formatter:format editorconfig:format -pl committer/core

# Check for dependency updates
mvn versions:display-dependency-updates -pl committer/core

# Validate formatting without changes
mvn formatter:validate editorconfig:check -pl committer/core
```

## Trust These Instructions

These instructions are validated against the current codebase state. Only search for additional information if you encounter errors not covered here or if these instructions prove incomplete. The build patterns documented here are tested and work reliably for individual module development.


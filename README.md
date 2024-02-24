# Norconex Crawlers

Norconex web and filesystem crawlers are full-featured crawlers (or spider) that can manipulate and store collected data in a repository of your choice (e.g., a search engine). They are very flexible, powerful, easy to extend, and portable. They can be used command-line with file-based configuration on any OS or embedded into Java applications using well-documented APIs.

Visit the website for binary downloads and documentation:
https://opensource.norconex.com/collectors/http/

# Are you on the right branch?

This branch holds version 4 code, which is still in development.

**For the latest stable release of Norconex Web Crawler, use the [version 3 branch](https://github.com/Norconex/collector-http/tree/3.x-branch).**

# UPCOMING: Crawler V4 Stack

As of Feb 24, 2024, the default `main` branch holds code for the upcoming version 4 crawler stack. It is now a mono-repo containing all Norconex crawler-related projects previously maintained in their own repos. All projects in this mono report will now be released simultaneously and share the same version number.

Until v4 is officially released, this branch should not be considered stable.  

## Structure

All projects in this repository share the same Maven group id:

    com.norconex.crawler

The directory structure roughly matches Maven module names:

```
Folder             artifactId
------------------------------------------------------
crawler/
  core/            nx-crawler-core
  web/             nx-crawler-web
  fs/              nx-crawler-fs
importer/          nx-importer
committer/
  core/            nx-committer-core
  solr/            nx-committer-solr
  idol/            nx-committer-idol
  ...
```

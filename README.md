# UPCOMING: Crawler V4 Stack

**Are you on the right branch?**

This branch is an experimental/development one for upcoming version 4, 
which merges a few projects into this repo.

This branch is currently highly unstable and production use is not 
recommended.  Once it reaches a stable state, we'll update this message.  

In the meantime, [please use the stable "master" branch](https://github.com/Norconex/collector-http/tree/master) 
(version 3.x release).

## Structure

-- Delete me --

All projects in this repository share the same Maven group id:

    com.norconex.crawler

Directory structure roughly matches Maven module names:

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

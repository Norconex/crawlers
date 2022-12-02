# crawler-v4-stack

Our private experiments for V4 until it reaches a certain stability, 
then we'll make it public, merging it with existing project.

## Structure

As much as it makes sense, the directory structure matches our Maven
module names and their group and artifact IDs:

```
Folder             groupId[:artifactId]
------------------------------------------------------
crawler/           com.norconex.crawler
  core/            com.norconex.crawler   : nx-crawler-core
  web/             com.norconex.crawler   : nx-crawler-web
  filesystem/      com.norconex.crawler   : nx-crawler-filesystem
importer/          com.norconex.importer  : nx-importer
committer/         com.norconex.committer
  core/            com.norconex.committer : nx-committer-core
  solr/            com.norconex.committer : nx-committer-solr
  idol/            com.norconex.committer : nx-committer-idol
  ...
  
# Not sure about this one:
kafka-connector/   com.norconex.???       : nx-crawler-web-kafka???

  
```

## Questions/not sure

* Kafka directory location/group/artifact.
* Introducing the `nx-*` prefix.  I think it is easier to group/identify our 
  artifacts/jar that way.  Used to be `norconex-` but `nx-` makes it shorter.
* Shall all our artifacts share the same group id since it is all the same
  "project"?  Importer can really live on its own though... bit deal
  if its group id would be `com.norconex.crawler`?


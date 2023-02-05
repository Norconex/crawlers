# crawler-v4-stack

Our private experiments for V4 until it reaches a certain stability, 
then we'll make it public, merging it with existing project.

## Structure

All projects in this repository share the same Maven group id:

    com.norconex.crawler

As much as it makes sense, the directory structure matches our Maven
module names and their artifact IDs:

```
Folder             artifactId
------------------------------------------------------
crawler/
  core/            nx-crawler-core
  web/             nx-crawler-web
  filesystem/      nx-crawler-filesystem
importer/          nx-importer
committer/
  core/            nx-committer-core
  solr/            nx-committer-solr
  idol/            nx-committer-idol
  ...
  
# Not sure about this one:
kafka-connector/   com.norconex.???       : nx-crawler-web-kafka???

  
```

## Questions/not sure

* Kafka directory location/group/artifact.
* Introducing the `nx-*` prefix.  I think it is easier to group/identify our 
  artifacts/jar that way.  Used to be `norconex-` but `nx-` makes it shorter.


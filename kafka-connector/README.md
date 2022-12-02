# Kafka Connector

## The plan (so far):

Ship with 2 Kafka connectors:
* Web **crawler** SOURCE connector 
* **Committer** SINK adapter connector


## Questions

* Is this the best location for Kafka stuff?  I feel we need a top-level
  folder, like "distribution", "cluster", "cloud", "add-ons", "integration",
  etc.  That top level will be everything we build "around" the crawler (not 
  dependencies to the crawler).
  



To document
--------------
- by default crawler looks for other nodes.
- pass -standalone for a local run (faster startup, predefined config)

# Infinispan configs:

Use velocity to make them mainly the same?


crawlRunId resolution logic:
==============================

Convert all configuration File/Path instances to String so we don't have
to concern ourselves with OS path differences? (like, setting /blah on windows
being converted to \blah or even c:\blah)




Create a small config with free-form XML/JSON/Yaml in it to test 
JDBC H2 configuration.

- make stop calls idempotent and have cli start a node that just puts stop in an admin cache and have all nodes listening to that cache.
-  all nodes will be a combination of: check on startup, listen, an slow polling.
- maybe: have the option to pass a reason on command line when stopping, storing that reason.
- make stop polling interval configurable


RANDOM:

crawlerId
clusterRunId


//TODO for each type of cluster ID

//       run: new distributed memory ID, then store it in persistent cache
//   session: new distributed memory ID, and check if previous run as completed, else, use the previous ID for the session id
//   crawler: there is alreay a crawler id.

/*


    "A crawl session consists of one or more runs. Each run represents a single launch of the crawler, whether it is the initial start or a resume after a pause."

IDEAS:

    •  crawler: Core crawling logic and orchestration
    •  cache: Caching abstractions and implementations
    •  fetch: HTTP or resource fetching utilities
    •  parse: Content parsing and extraction
    •  model: Data models (e.g., Page, Link)
    •  storage: Persistence and data storage
    •  config: Configuration management
    •  util: General utilities and helpers
    •  extension: Extension points and plugin interfaces
    •  exception: Custom exceptions


*/
//MAYBE: have a CrawlerClient for all commands and keep Crawler just
// for crawling (have import/export, cleaning, etc, done by other classes)?



To consider for testing: 

```xml
  <!-- JGroups stack matching your working programmatic configuration -->
  <jgroups>
    <stack name="test_stack">
      <!-- SHARED_LOOPBACK: Suitable for local testing or single-machine clusters -->
      
      <SHARED_LOOPBACK bind_addr="localhost"/>
      
      <!-- PING and MERGE3: Handle discovery and cluster merging -->
      <PING/>
      <MERGE3 max_interval="30000" min_interval="10000"/>
      
      <!-- FD_SOCK and FD_ALL3: Provide failure detection -->
      <FD_SOCK start_port="57800"/>
      <FD_ALL3 timeout="12000" interval="3000"/>
      
      <!-- VERIFY_SUSPECT: Confirms suspected node failures -->
      <VERIFY_SUSPECT timeout="1500"/>
      
      <!-- NAKACK2 and UNICAST3: Handle reliable multicast and unicast messaging -->
      <pbcast.NAKACK2 use_mcast_xmit="false"/>
      <UNICAST3/>
      
      <!-- STABLE: Ensures garbage collection of old messages -->
      <pbcast.STABLE desired_avg_gossip="50000"/>
      
      <!-- GMS: Manages group membership -->
      <pbcast.GMS print_local_addr="true" join_timeout="3000"/>
      
      <!-- UFC and MFC: Handle flow control -->
      <UFC max_credits="20000000" min_threshold="0.4"/>
      <MFC max_credits="20000000" min_threshold="0.4"/>
      
      <!-- FRAG2: Handles message fragmentation -->
      <FRAG2 frag_size="60000"/>
    </stack>
  </jgroups>
```

TODO: Investigate connection pooling strategy for larger Hazelcast clusters
- Current JDBC map store pool size is per-node; with N nodes, total connections = N * maximumPoolSize
- For indefinite scaling, consider centralized connection pooling or optimizing concurrent DB operations during startup/shutdown
- Monitor connection usage in multi-node tests and production to avoid database overload

TODO: Consider optional CP/lock-based coordinator election (future)
- Keep current membership-based coordinator logic as default for backward compatibility
- Add opt-in leader election using Hazelcast CP/distributed lock for deterministic failover behavior
- Evaluate consistency vs availability trade-offs under network partitions before enabling by default

TODO: Make Hazelcast map-store initial-mode configurable
- Use LAZY for fresh (initial) crawls to improve startup performance
- Use EAGER for resume/incremental crawls to ensure all persisted state is loaded upfront
- Add configuration option to toggle based on crawl type

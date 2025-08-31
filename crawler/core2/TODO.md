crawlRunId resolution logic:
==============================

Upon being created, an infinispan cluster will store in an ephemeral cache
(crawlerRunCache) a computeOrGet id.  Then the coordinator will compare 
that one with the one in crawl-session cache and if different will know
if we are resuming or not.  In either case, it updates the crawlSessionState
in the crawlSessionCache.



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
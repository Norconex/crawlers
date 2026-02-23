About your observations. Overall they are spot on, and a result of my inexperience with clustering in general and Hazelcast. I feel quite a bit is more complex than is should be as a result. My challenge is to make it a good and easy to cluster solution, while:

- making the whole persistence model replaceable (not tightly coupled with Hazelcast)
- being able to run standalone and clustered, with the standalone version completely bypassing some of the cluster-specific concepts, for increased performance.
- have it as easy / transparent for the average user to run either standalone or clustered.
- should work standalone by default without the user worrying about persistence.
- Has to be as performant as possible. Crawling thousands of URLs in very short time can easily create bottlenecks if the clustering is not done right.

The good news is I am willing to scrap whatever is not optimal for the "right" way to do things. Even if that means a change of paradigm.  Here are my comments on all of your points:

**1. Two sources of truth for queue depth**

I made two constructs because a queue and a map handle two concerns. One needs to act as a fifo queue and the other a key/value store.  I would love to have only a single source of truth but how would you combine these two requirements into one (fifo and key-value) efficiently?

**2. Static instanceRegistry in HazelcastCacheManager**

The singleton instance was an attempt to improve performence by reusing instances and also be able to test crawl "resumes" when the state is in memory only.  If you find a way to eliminate the singleton, great.

**3. synchronized + distributed concurrency mismatch**

I agree, I just was not sure how to do it. Can you make it so the synchronized keyworkd is no longer needed?

**4. addCoordinatorChangeListener() mutates state as a side effect**

Agreed. Please do it proper.

**5. No enforcement of coordinator-only operations**

Great finding. Please fix.

**6. CrawlEntryLedger fires events directly**

How will other nodes know of peristence changes if not done by the data layer?  Are we forcing every writer to the data layer to also publish events? It seemed more efficient to have them listen on the datalayer for changes.  I am totally willing to change that though if you have a better model.

**7. QueryFilter semantics differ between implementations**

You can refactor this one so it makes more sense.  If not mistaken, I came up with QueryFilter as a tentative to abstract and simplify some query operations to a minimum that any data store implementation could accomodate. If you have a better way (maybe tied to your more flexible, single-source of truth), then change it.

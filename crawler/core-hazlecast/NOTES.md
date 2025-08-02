# Hazelcast Implementation Notes

## Configuration Tips

1. The default Hazelcast configuration enables multicast discovery, which works well in development environments
2. For production, you may want to disable multicast and use TCP-IP or other discovery mechanisms
3. You can provide a custom XML configuration file with advanced settings

## Data Persistence

To configure persistence with Hazelcast:

```xml
<hazelcast>
    <persistence enabled="true">
        <base-dir>/path/to/persistence-directory</base-dir>
    </persistence>
</hazelcast>
```

## Cache Management

### Manually Clearing All Caches

```java
HazelcastCluster cluster = ...;
cluster.getCacheManager().clearAll();
```

Or you can access and clear individual caches:

```java
CacheManager cacheManager = cluster.getCacheManager();
for (String cacheName : cacheManager.getCache("cacheName", Object.class).getKeys()) {
    Cache<?> cache = cacheManager.getCache(cacheName, Object.class);
    if (cache != null) {
        cache.clear();
    }
}
```

## API Design Recommendations

When exposing cache functionality to your application:

- Avoid exposing raw Hazelcast operations to users of your API
- Create high-level methods on service or manager classes to abstract away the cache details
- Consider providing domain-specific methods like:

```java
public void addDocumentToQueue(Document doc) {
    documentCache.put(doc.getId(), doc);
}

public Optional<Document> getDocumentFromQueue(String id) {
    return documentCache.get(id);
}
```
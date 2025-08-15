# TODO:

- Make sure to eliminate the concept of run "once". We are now executing
  pipeline steps and they are all assumed to be run once per session.
  
- Make sure each keys in caches follow this pattern:
  crawlerId:sessionId:whateverContext:actualKey

- Coordinator will store the current step in a cache.
- Workers are listening for that cache and execute when it changes
  (either stopped or a new node).
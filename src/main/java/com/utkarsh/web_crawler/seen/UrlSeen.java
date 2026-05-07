package com.utkarsh.web_crawler.seen;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 9-10: in-memory implementation of the "URL Seen?" component. A real
 * crawler would back this with a Bloom filter + persistent store; for our
 * single-process, single-crawl use case a {@link ConcurrentHashMap}-backed
 * set is plenty.
 */
public class UrlSeen {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    /** @return {@code true} if this URL is new (and should be enqueued), {@code false} if it was already seen. */
    public boolean markIfAbsent(String url) {
        return seen.add(url);
    }

    public int size() {
        return seen.size();
    }
}

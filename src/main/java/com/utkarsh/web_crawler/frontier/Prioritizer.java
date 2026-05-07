package com.utkarsh.web_crawler.frontier;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Computes the priority of a URL. A real crawler would look at PageRank,
 * traffic, freshness, etc. This implementation uses cheap structural heuristics
 * which still produce sensible ordering: shallow paths beat deep ones, and the
 * seed host beats foreign hosts.
 */
@Component
public class Prioritizer {

    private static final Logger LOG = LoggerFactory.getLogger(Prioritizer.class);

    public Priority computePriority(String url, String seedHost) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String path = uri.getPath() == null ? "/" : uri.getPath();
            int slashes = (int) path.chars().filter(c -> c == '/').count();

            Priority p;
            if (host != null && host.equalsIgnoreCase(seedHost) && slashes <= 1) {
                p = Priority.HIGH;
            } else if (host != null && host.equalsIgnoreCase(seedHost) && slashes <= 3) {
                p = Priority.MEDIUM;
            } else {
                p = Priority.LOW;
            }
            LOG.trace("Prioritizer: {} -> {}", url, p);
            return p;
        } catch (IllegalArgumentException e) {
            LOG.trace("Prioritizer: malformed URL '{}', defaulting to LOW", url);
            return Priority.LOW;
        }
    }
}

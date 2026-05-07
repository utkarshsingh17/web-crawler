package com.utkarsh.web_crawler.frontier;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapping table from host -> back queue index. Once a host is bound to a back
 * queue index it stays there for the lifetime of the crawl, guaranteeing that
 * all URLs from the same host are processed by the same worker thread (the
 * politeness invariant).
 */
public class HostMappingTable {

    private static final Logger LOG = LoggerFactory.getLogger(HostMappingTable.class);

    private final ConcurrentMap<String, Integer> hostToQueue = new ConcurrentHashMap<>();
    private final int numQueues;

    public HostMappingTable(int numQueues) {
        this.numQueues = numQueues;
    }

    public int queueIndexFor(String url) {
        String host = hostOf(url);
        return hostToQueue.computeIfAbsent(host, h -> {
            int idx = Math.floorMod(h.hashCode(), numQueues);
            LOG.debug("HostMappingTable: host '{}' -> back queue b{}", h, idx + 1);
            return idx;
        });
    }

    public int knownHosts() {
        return hostToQueue.size();
    }

    private static String hostOf(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "unknown" : h.toLowerCase();
        } catch (IllegalArgumentException e) {
            return "unknown";
        }
    }
}

package com.utkarsh.web_crawler.frontier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Back module of the URL frontier: one FIFO queue per worker thread. Combined
 * with {@link HostMappingTable}, this guarantees that requests to the same
 * host are serialized through one worker, which is how politeness is enforced.
 */
public class BackQueues {

    private static final Logger LOG = LoggerFactory.getLogger(BackQueues.class);

    private final List<LinkedBlockingQueue<UrlEntry>> queues;
    private final HostMappingTable mappingTable;

    public BackQueues(int numQueues, HostMappingTable mappingTable) {
        this.queues = new ArrayList<>(numQueues);
        for (int i = 0; i < numQueues; i++) {
            queues.add(new LinkedBlockingQueue<>());
        }
        this.mappingTable = mappingTable;
    }

    /** Back queue router: routes a URL to the queue that owns its host. */
    public void route(UrlEntry entry) {
        int idx = mappingTable.queueIndexFor(entry.url());
        queues.get(idx).offer(entry);
        LOG.debug("BackQueueRouter: {} -> b{} (depth={}, priority={})",
                entry.url(), idx + 1, entry.depth(), entry.priority());
    }

    public LinkedBlockingQueue<UrlEntry> queue(int index) {
        return queues.get(index);
    }

    public int numQueues() {
        return queues.size();
    }

    public boolean allEmpty() {
        return queues.stream().allMatch(LinkedBlockingQueue::isEmpty);
    }

    public int totalSize() {
        return queues.stream().mapToInt(LinkedBlockingQueue::size).sum();
    }
}

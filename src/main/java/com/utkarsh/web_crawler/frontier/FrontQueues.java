package com.utkarsh.web_crawler.frontier;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Front module of the URL frontier. One FIFO queue per priority level. The
 * {@link #poll()} method (the "front queue selector") biases selection
 * towards higher-priority queues.
 */
public class FrontQueues {

    private static final Logger LOG = LoggerFactory.getLogger(FrontQueues.class);

    private final Map<Priority, LinkedBlockingQueue<UrlEntry>> queues = new EnumMap<>(Priority.class);

    public FrontQueues() {
        for (Priority p : Priority.values()) {
            queues.put(p, new LinkedBlockingQueue<>());
        }
    }

    public void offer(UrlEntry entry) {
        queues.get(entry.priority()).offer(entry);
        LOG.debug("FrontQueue[{}] <- {}", entry.priority(), entry.url());
    }

    /**
     * Front queue selector: pick a non-empty queue with a probability biased
     * by {@link Priority#weight()}. Falls back to round-robin lookup if the
     * randomly chosen bucket is empty.
     */
    public UrlEntry poll() {
        double r = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (Priority p : Priority.values()) {
            cumulative += p.weight();
            if (r <= cumulative) {
                UrlEntry e = queues.get(p).poll();
                if (e != null) {
                    return e;
                }
                break;
            }
        }
        for (Priority p : Priority.values()) {
            UrlEntry e = queues.get(p).poll();
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return queues.values().stream().allMatch(LinkedBlockingQueue::isEmpty);
    }

    public int size() {
        return queues.values().stream().mapToInt(LinkedBlockingQueue::size).sum();
    }
}

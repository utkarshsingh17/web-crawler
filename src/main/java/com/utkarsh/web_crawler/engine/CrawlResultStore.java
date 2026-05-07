package com.utkarsh.web_crawler.engine;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Aggregates the four buckets of links that the REST response surfaces.
 * Backed by lock-free, sorted, thread-safe sets so any worker can write
 * concurrently without coordination.
 */
public class CrawlResultStore {

    private final Set<String> internalLinks = new ConcurrentSkipListSet<>();
    private final Set<String> externalLinks = new ConcurrentSkipListSet<>();
    private final Set<String> staticResources = new ConcurrentSkipListSet<>();
    private final Set<String> otherResources = new ConcurrentSkipListSet<>();

    public void addInternal(String url)  { internalLinks.add(url); }
    public void addExternal(String url)  { externalLinks.add(url); }
    public void addStatic(String url)    { staticResources.add(url); }
    public void addOther(String url)     { otherResources.add(url); }

    public Set<String> internalLinks()    { return internalLinks; }
    public Set<String> externalLinks()    { return externalLinks; }
    public Set<String> staticResources()  { return staticResources; }
    public Set<String> otherResources()   { return otherResources; }
}

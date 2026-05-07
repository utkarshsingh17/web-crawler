package com.utkarsh.web_crawler.engine;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.utkarsh.web_crawler.config.CrawlerProperties;
import com.utkarsh.web_crawler.downloader.CrawlerWorker;
import com.utkarsh.web_crawler.downloader.HtmlDownloader;
import com.utkarsh.web_crawler.downloader.HtmlDownloader.DownloadResult;
import com.utkarsh.web_crawler.dto.CrawlResponse;
import com.utkarsh.web_crawler.extractor.LinkExtractor;
import com.utkarsh.web_crawler.extractor.LinkExtractor.ExtractedLinks;
import com.utkarsh.web_crawler.filter.UrlFilter;
import com.utkarsh.web_crawler.frontier.BackQueues;
import com.utkarsh.web_crawler.frontier.FrontQueues;
import com.utkarsh.web_crawler.frontier.HostMappingTable;
import com.utkarsh.web_crawler.frontier.Prioritizer;
import com.utkarsh.web_crawler.parser.ContentParser;
import com.utkarsh.web_crawler.seen.ContentSeen;
import com.utkarsh.web_crawler.seen.UrlSeen;
import com.utkarsh.web_crawler.util.LoggerMessages;

/**
 * Orchestrator. The public entry point is {@link #crawl(String)} which picks
 * either the BFS pipeline (default, multi-threaded, frontier + worker pool)
 * or the DFS variant (single-threaded recursive).
 *
 * <p>To switch traversal strategy, edit the dispatch in {@link #crawl(String)}.
 */
@Component
public class CrawlerEngine {

    private static final Logger LOG = LoggerFactory.getLogger(CrawlerEngine.class);

    private final CrawlerProperties props;
    private final HtmlDownloader downloader;
    private final ContentParser parser;
    private final LinkExtractor linkExtractor;
    private final UrlFilter urlFilter;
    private final Prioritizer prioritizer;

    public CrawlerEngine(CrawlerProperties props,
                         HtmlDownloader downloader,
                         ContentParser parser,
                         LinkExtractor linkExtractor,
                         UrlFilter urlFilter,
                         Prioritizer prioritizer) {
        this.props = props;
        this.downloader = downloader;
        this.parser = parser;
        this.linkExtractor = linkExtractor;
        this.urlFilter = urlFilter;
        this.prioritizer = prioritizer;
    }

    /**
     * Public entry point. Toggles between BFS and DFS traversal.
     *
     * <p>BFS (default) goes through the URL Frontier + back queues + N
     * worker threads with politeness delay. DFS is a single-threaded
     * recursive walk kept for comparison/teaching.
     */
    public CrawlResponse crawl(String seedUrl) {
        // ─── traversal strategy ─────────────────────────────────────────────
        return crawlBfs(seedUrl);
        // To use DFS instead, comment the line above and uncomment the line below:
        // return crawlDfs(seedUrl);
    }

    // =========================================================================
    // BFS — multi-threaded, queue-based, politeness-aware
    // =========================================================================

    /**
     * Breadth-first crawl. Each URL is enqueued in the URL Frontier and
     * processed by a worker that's pinned to the host's back queue. This is
     * the textbook architecture (Figure 4 / Figure 8 in the spec).
     */
    public CrawlResponse crawlBfs(String seedUrl) {
        long start = System.currentTimeMillis();
        LOG.info("=== BFS Crawl starting === seed={} maxPages={} maxDepth={} backQueues={}",
                seedUrl, props.getMaxPages(), props.getMaxDepth(), props.getBackQueues());

        FrontQueues front = new FrontQueues();
        HostMappingTable mapping = new HostMappingTable(props.getBackQueues());
        BackQueues back = new BackQueues(props.getBackQueues(), mapping);
        UrlSeen urlSeen = new UrlSeen();
        ContentSeen contentSeen = new ContentSeen();

        CrawlContext ctx = new CrawlContext(
                seedUrl, props, front, back, urlSeen, contentSeen,
                downloader, parser, linkExtractor, urlFilter, prioritizer);

        int totalThreads = 1 + props.getBackQueues();
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads, runnable -> {
            Thread t = new Thread(runnable);
            t.setDaemon(true);
            return t;
        });

        // Seed the frontier BEFORE starting workers, otherwise a worker may
        // poll an empty queue, find pending=0, and prematurely finish the crawl.
        ctx.submitSeed();

        List<Runnable> tasks = new ArrayList<>(totalThreads);
        tasks.add(new QueueRouter(ctx));
        for (int i = 0; i < props.getBackQueues(); i++) {
            tasks.add(new CrawlerWorker(i, back.queue(i), ctx));
        }
        tasks.forEach(pool::execute);

        boolean drained;
        try {
            drained = ctx.doneLatch().await(props.getRequestCompletionTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ctx.abort();
            drained = false;
        }

        if (!drained) {
            LOG.warn("Crawl did not drain within {} ms; returning partial result", props.getRequestCompletionTimeoutMs());
            ctx.abort();
        }

        pool.shutdownNow();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - start;
        CrawlResultStore r = ctx.results();
        LOG.info("=== BFS Crawl finished === pages={} internal={} external={} static={} other={} hosts={} elapsed={}ms",
                ctx.processedCount(),
                r.internalLinks().size(), r.externalLinks().size(),
                r.staticResources().size(), r.otherResources().size(),
                mapping.knownHosts(), elapsed);

        return new CrawlResponse(
                LoggerMessages.CRAWL_SUCCESS,
                r.internalLinks(),
                r.externalLinks(),
                r.staticResources(),
                r.otherResources());
    }

    // =========================================================================
    // DFS — single-threaded recursive (legacy-style). Kept for comparison.
    // =========================================================================

    /**
     * Depth-first crawl. Walks the link graph by recursing into each newly
     * discovered internal link before moving on. This bypasses the URL
     * Frontier, the back queues, the worker pool, and the politeness delay.
     *
     * <p>Disadvantages compared to BFS:
     * <ul>
     *   <li>Single-threaded — cannot parallelize across hosts.</li>
     *   <li>Stack depth grows with the depth of the link graph; deep sites
     *       can throw {@link StackOverflowError}.</li>
     *   <li>No politeness delay — hammers the same host with back-to-back
     *       requests, which can get the crawler rate-limited or banned.</li>
     *   <li>No prioritization — visits links in document order.</li>
     * </ul>
     *
     * <p>Kept here for didactic comparison. The URL-seen check still
     * prevents infinite loops.
     */
    public CrawlResponse crawlDfs(String seedUrl) {
        long start = System.currentTimeMillis();
        LOG.info("=== DFS Crawl starting === seed={} maxPages={} maxDepth={}",
                seedUrl, props.getMaxPages(), props.getMaxDepth());

        DfsState state = new DfsState();
        String seedHost = hostOf(seedUrl);
        dfsVisit(seedUrl, seedHost, 0, state);

        long elapsed = System.currentTimeMillis() - start;
        LOG.info("=== DFS Crawl finished === pages={} internal={} external={} static={} other={} elapsed={}ms",
                state.processed,
                state.internal.size(), state.external.size(),
                state.statics.size(), state.other.size(), elapsed);

        return new CrawlResponse(
                LoggerMessages.CRAWL_SUCCESS,
                state.internal, state.external, state.statics, state.other);
    }

    private void dfsVisit(String url, String seedHost, int depth, DfsState state) {
        if (state.processed >= props.getMaxPages()) return;
        if (depth > props.getMaxDepth()) return;
        if (!state.visited.add(url)) return;
        if (!urlFilter.isCrawlable(url)) return;

        LOG.info("[DFS] depth={} fetching: {}", depth, url);
        DownloadResult dr = downloader.download(url);
        if (!dr.ok()) return;

        Document doc = parser.parse(url, dr.html());
        if (doc == null) return;

        state.internal.add(url);
        state.processed++;
        LOG.debug("[DFS] page #{} accepted: {}", state.processed, url);

        ExtractedLinks links = linkExtractor.extract(doc);
        for (String img : links.images()) state.statics.add(img);

        for (String href : links.anchors()) {
            if (!StringUtils.hasText(href) || href.contains("#")) continue;
            if (urlFilter.isResource(href)) {
                state.other.add(href);
                continue;
            }
            String h = hostOf(href);
            if (seedHost != null && seedHost.equalsIgnoreCase(h)) {
                dfsVisit(href, seedHost, depth + 1, state); // recurse → DFS
            } else {
                state.external.add(href);
            }
        }
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Per-crawl state for the DFS variant. Single-threaded, so plain Sets are safe. */
    private static final class DfsState {
        final Set<String> visited  = new HashSet<>();
        final Set<String> internal = new TreeSet<>();
        final Set<String> external = new TreeSet<>();
        final Set<String> statics  = new TreeSet<>();
        final Set<String> other    = new TreeSet<>();
        int processed = 0;
    }
}

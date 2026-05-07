package com.utkarsh.web_crawler.engine;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.utkarsh.web_crawler.config.CrawlerProperties;
import com.utkarsh.web_crawler.downloader.HtmlDownloader;
import com.utkarsh.web_crawler.downloader.HtmlDownloader.DownloadResult;
import com.utkarsh.web_crawler.extractor.LinkExtractor;
import com.utkarsh.web_crawler.extractor.LinkExtractor.ExtractedLinks;
import com.utkarsh.web_crawler.filter.UrlFilter;
import com.utkarsh.web_crawler.frontier.BackQueues;
import com.utkarsh.web_crawler.frontier.FrontQueues;
import com.utkarsh.web_crawler.frontier.Prioritizer;
import com.utkarsh.web_crawler.frontier.Priority;
import com.utkarsh.web_crawler.frontier.UrlEntry;
import com.utkarsh.web_crawler.parser.ContentParser;
import com.utkarsh.web_crawler.seen.ContentSeen;
import com.utkarsh.web_crawler.seen.UrlSeen;

/**
 * Per-crawl state. Built once per request, shared by every worker thread and
 * the queue router. Encapsulates the workflow steps 4-11 in
 * {@link #processUrl(UrlEntry)}.
 */
public class CrawlContext {

    private static final Logger LOG = LoggerFactory.getLogger(CrawlContext.class);

    private final String seedUrl;
    private final String seedHost;
    private final CrawlerProperties properties;

    private final FrontQueues frontQueues;
    private final BackQueues backQueues;

    private final UrlSeen urlSeen;
    private final ContentSeen contentSeen;
    private final CrawlResultStore results = new CrawlResultStore();

    private final HtmlDownloader downloader;
    private final ContentParser parser;
    private final LinkExtractor linkExtractor;
    private final UrlFilter urlFilter;
    private final Prioritizer prioritizer;

    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final CountDownLatch doneLatch = new CountDownLatch(1);

    public CrawlContext(String seedUrl,
                        CrawlerProperties properties,
                        FrontQueues frontQueues,
                        BackQueues backQueues,
                        UrlSeen urlSeen,
                        ContentSeen contentSeen,
                        HtmlDownloader downloader,
                        ContentParser parser,
                        LinkExtractor linkExtractor,
                        UrlFilter urlFilter,
                        Prioritizer prioritizer) {
        this.seedUrl = seedUrl;
        this.seedHost = hostOf(seedUrl);
        this.properties = properties;
        this.frontQueues = frontQueues;
        this.backQueues = backQueues;
        this.urlSeen = urlSeen;
        this.contentSeen = contentSeen;
        this.downloader = downloader;
        this.parser = parser;
        this.linkExtractor = linkExtractor;
        this.urlFilter = urlFilter;
        this.prioritizer = prioritizer;
    }

    public CrawlerProperties properties() { return properties; }
    public CrawlResultStore results()     { return results; }
    public boolean isFinished()           { return finished.get(); }
    public int processedCount()           { return processed.get(); }
    public CountDownLatch doneLatch()     { return doneLatch; }
    public FrontQueues frontQueues()      { return frontQueues; }
    public BackQueues backQueues()        { return backQueues; }

    /** Step 1: seed URL → URL frontier. Must be called before starting workers. */
    public void submitSeed() {
        LOG.info("[Step 1] Adding seed URL to frontier: {}", seedUrl);
        offerToFrontier(seedUrl, 0);
        started.set(true);
    }

    /**
     * Step 11: a freshly-discovered URL is added to the frontier (front
     * queues, prioritized by the prioritizer).
     */
    public void offerToFrontier(String url, int depth) {
        if (depth > properties.getMaxDepth()) {
            LOG.trace("Frontier: dropping {} (depth {} > max {})", url, depth, properties.getMaxDepth());
            return;
        }
        if (!urlSeen.markIfAbsent(url)) {
            LOG.trace("[Step 10] UrlSeen: already processed {}", url);
            return;
        }
        Priority p = prioritizer.computePriority(url, seedHost);
        UrlEntry entry = new UrlEntry(url, depth, p);
        pending.incrementAndGet();
        frontQueues.offer(entry);
    }

    /**
     * Steps 4-11 for a single URL pulled off a back queue.
     * Called by {@link com.utkarsh.web_crawler.downloader.CrawlerWorker}.
     */
    public void processUrl(UrlEntry entry) {
        if (finished.get()) return;
        if (processed.get() >= properties.getMaxPages()) {
            LOG.debug("Reached max-pages cap, skipping {}", entry.url());
            return;
        }

        LOG.info("[Step 2-3] Worker fetching: {} (depth={}, priority={})",
                entry.url(), entry.depth(), entry.priority());

        DownloadResult dr = downloader.download(entry.url());
        if (!dr.ok()) {
            return;
        }

        Document doc = parser.parse(entry.url(), dr.html());
        if (doc == null) {
            LOG.debug("[Step 4] ContentParser rejected {}", entry.url());
            return;
        }

        if (!contentSeen.markIfAbsent(entry.url(), dr.html())) {
            LOG.info("[Step 5-6] ContentSeen drop: {}", entry.url());
            return;
        }

        int count = processed.incrementAndGet();
        results.addInternal(entry.url());
        LOG.info("[Step 6] Page #{} accepted: {}", count, entry.url());

        ExtractedLinks links = linkExtractor.extract(doc);

        for (String img : links.images()) {
            results.addStatic(img);
        }

        for (String href : links.anchors()) {
            classifyAndMaybeEnqueue(href, entry.depth() + 1);
        }
    }

    /** Steps 7-11 applied to one extracted anchor. */
    private void classifyAndMaybeEnqueue(String href, int childDepth) {
        if (urlFilter.isResource(href)) {
            results.addOther(href);
            LOG.trace("[Step 8] Resource extension -> other: {}", href);
            return;
        }

        boolean internal = isInternal(href);
        if (!internal) {
            results.addExternal(href);
            LOG.trace("[Step 8] External link recorded: {}", href);
            return;
        }

        if (!urlFilter.isCrawlable(href)) {
            LOG.trace("[Step 8] Filter dropped internal link: {}", href);
            return;
        }

        LOG.debug("[Step 9-11] New internal link queued: {}", href);
        offerToFrontier(href, childDepth);
    }

    /** Called by worker after each URL (success or failure). */
    public void markUrlComplete() {
        int remaining = pending.decrementAndGet();
        if (remaining <= 0 || processed.get() >= properties.getMaxPages()) {
            checkCompletion();
        }
    }

    /** Called periodically (and after each completion) to detect a drained crawl. */
    public void checkCompletion() {
        if (finished.get()) return;
        // Guard against the startup race: workers may poll empty queues
        // before the request thread has finished enqueuing the seed.
        if (!started.get()) return;

        boolean hardCap = processed.get() >= properties.getMaxPages();
        boolean drained = pending.get() <= 0 && frontQueues.isEmpty() && backQueues.allEmpty();

        if (hardCap || drained) {
            if (finished.compareAndSet(false, true)) {
                LOG.info("Crawl finishing (processed={}, pending={}, frontQ={}, backQ={}, hardCap={})",
                        processed.get(), pending.get(),
                        frontQueues.size(), backQueues.totalSize(), hardCap);
                doneLatch.countDown();
            }
        }
    }

    public void abort() {
        if (finished.compareAndSet(false, true)) {
            LOG.info("Crawl aborted by engine");
            doneLatch.countDown();
        }
    }

    private boolean isInternal(String url) {
        String h = hostOf(url);
        return seedHost != null && seedHost.equalsIgnoreCase(h);
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

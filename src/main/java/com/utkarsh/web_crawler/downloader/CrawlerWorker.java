package com.utkarsh.web_crawler.downloader;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.utkarsh.web_crawler.engine.CrawlContext;
import com.utkarsh.web_crawler.frontier.UrlEntry;

/**
 * One worker per back queue. The worker:
 * <ol>
 *   <li>polls its dedicated back queue</li>
 *   <li>sleeps the configured politeness delay since its last fetch</li>
 *   <li>asks the engine to process the URL (download → parse → extract → enqueue)</li>
 * </ol>
 * Because each host hashes deterministically to one back queue, only one
 * in-flight request per host is ever issued — that's the politeness invariant.
 */
public class CrawlerWorker implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CrawlerWorker.class);

    private final int queueIndex;
    private final LinkedBlockingQueue<UrlEntry> backQueue;
    private final CrawlContext ctx;

    private long lastFetchEpochMs = 0L;
    private int localProcessed = 0;

    public CrawlerWorker(int queueIndex, LinkedBlockingQueue<UrlEntry> backQueue, CrawlContext ctx) {
        this.queueIndex = queueIndex;
        this.backQueue = backQueue;
        this.ctx = ctx;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("crawler-worker-b" + (queueIndex + 1));
        LOG.info("Worker thread b{} started", queueIndex + 1);

        while (!ctx.isFinished()) {
            UrlEntry entry;
            try {
                entry = backQueue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (entry == null) {
                ctx.checkCompletion();
                continue;
            }

            try {
                applyPolitenessDelay();
                ctx.processUrl(entry);
                localProcessed++;
            } catch (Throwable t) {
                LOG.warn("Worker b{} error on {}: {}", queueIndex + 1, entry.url(), t.toString());
            } finally {
                ctx.markUrlComplete();
            }
        }

        LOG.info("Worker thread b{} stopped (handled {} URLs locally; crawl total {})",
                queueIndex + 1, localProcessed, ctx.processedCount());
    }

    private void applyPolitenessDelay() {
        long delay = ctx.properties().getPolitenessDelayMs();
        long elapsed = System.currentTimeMillis() - lastFetchEpochMs;
        if (lastFetchEpochMs > 0 && elapsed < delay) {
            try {
                long sleep = delay - elapsed;
                LOG.debug("Worker b{} politeness sleep {} ms", queueIndex + 1, sleep);
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastFetchEpochMs = System.currentTimeMillis();
    }
}

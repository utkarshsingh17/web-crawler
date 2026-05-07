package com.utkarsh.web_crawler.engine;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.utkarsh.web_crawler.frontier.UrlEntry;

/**
 * Background thread that drains the front queues into the back queues. It
 * sits between the prioritization module (front) and the politeness module
 * (back) of the URL frontier (Figure 8 in the spec).
 */
public class QueueRouter implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(QueueRouter.class);

    private final CrawlContext ctx;

    public QueueRouter(CrawlContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("crawler-queue-router");
        LOG.info("QueueRouter started");

        while (!ctx.isFinished()) {
            UrlEntry entry = ctx.frontQueues().poll();
            if (entry == null) {
                ctx.checkCompletion();
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            ctx.backQueues().route(entry);
        }
        LOG.info("QueueRouter stopped");
    }
}

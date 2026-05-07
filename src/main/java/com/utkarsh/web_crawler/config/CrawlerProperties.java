package com.utkarsh.web_crawler.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    /** Maximum number of pages to download in a single crawl. */
    private int maxPages = 200;

    /** Maximum link depth from the seed URL. */
    private int maxDepth = 5;

    /** Politeness delay between two fetches against the same back queue. */
    private long politenessDelayMs = 500;

    /** Per-request timeout for the HTML downloader. */
    private int requestTimeoutMs = 10_000;

    /** Number of back queues (= number of downloader worker threads). */
    private int backQueues = 4;

    /** User-Agent string sent by the downloader. */
    private String userAgent = "WebCrawler/1.0 (+spring-boot)";

    /** Hard cap on how long an HTTP request will block waiting for the crawl. */
    private long requestCompletionTimeoutMs = 60_000;

    /** Hosts that the URL filter will refuse to enqueue. */
    private List<String> blacklistHosts = List.of();

    /** File extensions that should be classified as "other" resources rather than crawled. */
    private List<String> resourceExtensions = List.of(
            "css", "js", "gif", "jpg", "jpeg", "png", "svg", "ico", "mp3", "mp4",
            "zip", "gz", "tar", "pdf", "xls", "xlsx", "doc", "docx", "ppt", "pptx");
}

package com.utkarsh.web_crawler.downloader;

import java.io.IOException;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.utkarsh.web_crawler.config.CrawlerProperties;

/**
 * Step 3: HTML downloader. Wraps jsoup's HTTP client and surfaces a single
 * {@link DownloadResult} object. The DNS resolver step from the diagram is
 * handled implicitly by the JDK / OS resolver.
 */
@Component
public class HtmlDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(HtmlDownloader.class);

    private final CrawlerProperties props;

    public HtmlDownloader(CrawlerProperties props) {
        this.props = props;
    }

    public DownloadResult download(String url) {
        long start = System.currentTimeMillis();
        try {
            Connection.Response resp = Jsoup.connect(url)
                    .userAgent(props.getUserAgent())
                    .timeout(props.getRequestTimeoutMs())
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(false)
                    .execute();

            int status = resp.statusCode();
            long elapsed = System.currentTimeMillis() - start;

            if (status >= 400) {
                LOG.info("HtmlDownloader: {} -> HTTP {} ({} ms)", url, status, elapsed);
                return DownloadResult.failure(url, status);
            }

            Document doc = resp.parse();
            String html = doc.outerHtml();
            LOG.info("HtmlDownloader: {} -> HTTP {} ({} bytes, {} ms)",
                    url, status, html.length(), elapsed);
            return DownloadResult.success(url, status, html);
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            LOG.warn("HtmlDownloader: {} failed after {} ms - {}", url, elapsed, e.getMessage());
            return DownloadResult.failure(url, -1);
        }
    }

    public record DownloadResult(String url, int status, String html, boolean ok) {
        static DownloadResult success(String url, int status, String html) {
            return new DownloadResult(url, status, html, true);
        }

        static DownloadResult failure(String url, int status) {
            return new DownloadResult(url, status, null, false);
        }
    }
}

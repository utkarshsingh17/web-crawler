package com.utkarsh.web_crawler.util;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.utkarsh.web_crawler.dto.CrawlResponse;

@Component
public class CrawlingUtils {

    private static final Logger LOG = LoggerFactory.getLogger(CrawlingUtils.class);

    private static final Pattern STATIC_RESOURCE_FILTER = Pattern.compile(
            ".*(\\.(css|js|gif|jpg|png|mp3|mp4|zip|gz|pdf|xls|xlsx|doc|docx))$",
            Pattern.CASE_INSENSITIVE);

    public CrawlResponse crawlResource(String link) {
        CrawlState state = new CrawlState();
        collectLinks(link, link, state);
        return new CrawlResponse(
                LoggerMessages.CRAWL_SUCCESS,
                state.internalLinks,
                state.externalLinks,
                state.staticResources,
                state.otherResources);
    }

    /**
     * Recursively walks links discovered in the page, classifying each one as
     * internal/external/static/other. A URL-seen test guarantees we never
     * fetch the same page twice.
     */
    private void collectLinks(String rootUrl, String currentUrl, CrawlState state) {
        if (!state.visited.add(currentUrl)) {
            return;
        }

        Document doc;
        try {
            doc = Jsoup.connect(currentUrl).get();
        } catch (IOException e) {
            LOG.warn("Failed to fetch {}: {}", currentUrl, e.getMessage());
            return;
        }

        for (Element element : doc.select("a")) {
            String nextUrl = element.absUrl("href");

            if (!StringUtils.hasText(nextUrl) || nextUrl.contains("#")) {
                continue;
            }

            if (STATIC_RESOURCE_FILTER.matcher(nextUrl).matches()) {
                state.otherResources.add(nextUrl);
                continue;
            }

            if (nextUrl.startsWith(rootUrl)) {
                state.internalLinks.add(nextUrl);
                collectLinks(rootUrl, nextUrl, state);
            } else {
                state.externalLinks.add(nextUrl);
            }
        }

        Elements staticElements = doc.select("img");
        for (Element element : staticElements) {
            String src = element.absUrl("src");
            if (StringUtils.hasText(src)) {
                state.staticResources.add(src);
            }
        }
    }

    /**
     * Per-request mutable container so concurrent or sequential crawls don't
     * leak results into each other (the legacy implementation kept these as
     * fields on the singleton bean).
     */
    private static final class CrawlState {
        final Set<String> visited = new HashSet<>();
        final Set<String> internalLinks = new TreeSet<>();
        final Set<String> externalLinks = new TreeSet<>();
        final Set<String> staticResources = new TreeSet<>();
        final Set<String> otherResources = new TreeSet<>();
    }
}

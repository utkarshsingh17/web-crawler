package com.utkarsh.web_crawler.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Step 4: parses raw HTML into a jsoup {@link Document} and rejects malformed
 * pages. Jsoup's parser is permissive, so "malformed" here means empty body or
 * non-HTML content.
 */
@Component
public class ContentParser {

    private static final Logger LOG = LoggerFactory.getLogger(ContentParser.class);

    public Document parse(String url, String html) {
        if (!StringUtils.hasText(html)) {
            LOG.debug("ContentParser: empty body for {}", url);
            return null;
        }
        Document doc = Jsoup.parse(html, url);
        if (doc.body() == null || doc.body().children().isEmpty()) {
            LOG.debug("ContentParser: no body content for {}", url);
            return null;
        }
        LOG.debug("ContentParser: parsed {} ({} bytes)", url, html.length());
        return doc;
    }
}

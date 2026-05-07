package com.utkarsh.web_crawler.extractor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Step 7: pulls all anchor (&lt;a href&gt;) and image (&lt;img src&gt;)
 * absolute URLs out of a parsed page.
 */
@Component
public class LinkExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(LinkExtractor.class);

    public ExtractedLinks extract(Document doc) {
        Set<String> anchors = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            String abs = a.absUrl("href");
            if (StringUtils.hasText(abs)) anchors.add(abs);
        }

        Set<String> images = new LinkedHashSet<>();
        for (Element img : doc.select("img[src]")) {
            String abs = img.absUrl("src");
            if (StringUtils.hasText(abs)) images.add(abs);
        }

        LOG.debug("LinkExtractor: {} -> {} anchors, {} images",
                doc.location(), anchors.size(), images.size());
        return new ExtractedLinks(new ArrayList<>(anchors), new ArrayList<>(images));
    }

    public record ExtractedLinks(List<String> anchors, List<String> images) {
    }
}

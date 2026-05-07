package com.utkarsh.web_crawler.filter;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.utkarsh.web_crawler.config.CrawlerProperties;

/**
 * Step 8 in the workflow: drops URLs that should not be crawled.
 * <ul>
 *   <li>blank or fragment-only links</li>
 *   <li>non-http(s) schemes</li>
 *   <li>blacklisted hosts</li>
 *   <li>resource extensions (.pdf, .css, …) — those are reported but not crawled</li>
 * </ul>
 */
@Component
public class UrlFilter {

    private static final Logger LOG = LoggerFactory.getLogger(UrlFilter.class);

    private final Set<String> blacklist;
    private final Set<String> resourceExtensions;

    public UrlFilter(CrawlerProperties props) {
        this.blacklist = new HashSet<>(lower(props.getBlacklistHosts()));
        this.resourceExtensions = new HashSet<>(lower(props.getResourceExtensions()));
    }

    public boolean isCrawlable(String url) {
        if (!StringUtils.hasText(url)) return reject(url, "blank");
        if (url.contains("#")) return reject(url, "fragment");

        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return reject(url, "non-http(s)");
        }

        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return reject(url, "no-host");
            if (blacklist.contains(host.toLowerCase())) return reject(url, "blacklisted-host");
        } catch (IllegalArgumentException e) {
            return reject(url, "malformed");
        }

        if (isResource(url)) return reject(url, "resource-extension");
        return true;
    }

    /** Test purely on URL pattern: is this a static/binary resource? */
    public boolean isResource(String url) {
        int dot = url.lastIndexOf('.');
        if (dot < 0) return false;
        int q = url.indexOf('?', dot);
        String ext = (q < 0 ? url.substring(dot + 1) : url.substring(dot + 1, q)).toLowerCase();
        return resourceExtensions.contains(ext);
    }

    private boolean reject(String url, String reason) {
        LOG.trace("UrlFilter rejected {} ({})", url, reason);
        return false;
    }

    private static Collection<String> lower(Collection<String> in) {
        Set<String> out = new HashSet<>();
        if (in != null) for (String s : in) if (StringUtils.hasText(s)) out.add(s.toLowerCase());
        return out;
    }
}

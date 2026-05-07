package com.utkarsh.web_crawler.seen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Steps 5-6: drops pages whose content (not URL) was already processed. We
 * hash the rendered HTML with SHA-256 and treat hash collisions as duplicates.
 */
public class ContentSeen {

    private static final Logger LOG = LoggerFactory.getLogger(ContentSeen.class);

    private final Set<String> hashes = ConcurrentHashMap.newKeySet();

    /** @return {@code true} if the content is new, {@code false} if a duplicate was found. */
    public boolean markIfAbsent(String url, String html) {
        if (html == null || html.isEmpty()) return true;
        String hash = sha256(html);
        boolean isNew = hashes.add(hash);
        if (!isNew) {
            LOG.debug("ContentSeen: duplicate content at {} (hash={})", url, hash);
        }
        return isNew;
    }

    public int size() {
        return hashes.size();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.utkarsh.web_crawler.util;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UrlValidation {

    // Validates http(s) URLs with optional www, port, path, query and fragment.
    // Mirrors the client-side regex in static/js/unity.js so client and server
    // agree on what a "valid" link looks like.
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?"
                    + "((([a-z\\d]([a-z\\d-]*[a-z\\d])*)\\.?)+[a-z]{2,}"
                    + "|((\\d{1,3}\\.){3}\\d{1,3}))"
                    + "(:\\d+)?(/[-a-z\\d%_.~+]*)*"
                    + "(\\?[;&a-z\\d%_.~+=-]*)?"
                    + "(#[-a-z\\d_]*)?$",
            Pattern.CASE_INSENSITIVE);

    public boolean isValid(String link) {
        if (!StringUtils.hasText(link)) {
            return false;
        }
        return URL_PATTERN.matcher(link).matches();
    }
}

package com.utkarsh.web_crawler.service;

import org.springframework.stereotype.Service;

import com.utkarsh.web_crawler.dto.CrawlResponse;
import com.utkarsh.web_crawler.util.CrawlingUtils;

@Service
public class CrawlServiceImpl implements ICrawlService {

    private final CrawlingUtils crawlingUtils;

    public CrawlServiceImpl(CrawlingUtils crawlingUtils) {
        this.crawlingUtils = crawlingUtils;
    }

    @Override
    public CrawlResponse crawlResource(String link) {
        return crawlingUtils.crawlResource(link);
    }
}

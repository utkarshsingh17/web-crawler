package com.utkarsh.web_crawler.service;

import org.springframework.stereotype.Service;

import com.utkarsh.web_crawler.dto.CrawlResponse;
import com.utkarsh.web_crawler.engine.CrawlerEngine;

@Service
public class CrawlServiceImpl implements ICrawlService {

    private final CrawlerEngine engine;

    public CrawlServiceImpl(CrawlerEngine engine) {
        this.engine = engine;
    }

    @Override
    public CrawlResponse crawlResource(String link) {
        return engine.crawl(link);
    }
}

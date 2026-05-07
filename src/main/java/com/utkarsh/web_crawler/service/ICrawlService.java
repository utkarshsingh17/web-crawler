package com.utkarsh.web_crawler.service;

import com.utkarsh.web_crawler.dto.CrawlResponse;

public interface ICrawlService {

    CrawlResponse crawlResource(String link);
}

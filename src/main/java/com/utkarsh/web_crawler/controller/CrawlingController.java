package com.utkarsh.web_crawler.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.utkarsh.web_crawler.dto.CrawlRequest;
import com.utkarsh.web_crawler.dto.CrawlResponse;
import com.utkarsh.web_crawler.service.ICrawlService;
import com.utkarsh.web_crawler.util.LoggerMessages;
import com.utkarsh.web_crawler.util.RestEndpointMapper;
import com.utkarsh.web_crawler.util.UrlValidation;

@RestController
public class CrawlingController {

    private static final Logger LOG = LoggerFactory.getLogger(CrawlingController.class);

    private final ICrawlService crawlService;
    private final UrlValidation urlValidation;

    public CrawlingController(ICrawlService crawlService, UrlValidation urlValidation) {
        this.crawlService = crawlService;
        this.urlValidation = urlValidation;
    }

    @PostMapping(RestEndpointMapper.CRAWL)
    public ResponseEntity<CrawlResponse> crawlResource(@RequestBody CrawlRequest crawlRequest) {
        String link = crawlRequest.getLink();
        LOG.info("CrawlingController received request for link: {}", link);

        if (!urlValidation.isValid(link)) {
            LOG.info("CrawlingController rejected invalid link: {}", link);
            return new ResponseEntity<>(new CrawlResponse(LoggerMessages.INVALID_URL), HttpStatus.BAD_REQUEST);
        }

        CrawlResponse response = crawlService.crawlResource(link);
        LOG.info("CrawlingController: {}", LoggerMessages.CRAWL_SUCCESS);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

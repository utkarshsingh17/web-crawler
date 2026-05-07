# web-crawler

A Spring Boot REST API that, given a starting URL, walks the page graph and
returns the discovered internal links, external links, static (image) resources
and other downloadable resources (pdf, css, js, archives, …).

This project is a port of the legacy `spring-boot-crawler` (Spring Boot 2.1.3 /
Java 8) onto a modern stack:

- Spring Boot 4.0.6
- Java 21
- Spring MVC (`spring-boot-starter-webmvc`)
- jsoup 1.18.x for HTML parsing
- Lombok for DTO boilerplate

## Running

```bash
./mvnw spring-boot:run
```

The app listens on `http://localhost:8081`.

## REST API

`POST /crawl`

Request:

```json
{ "link": "https://example.com" }
```

Response (`200 OK`):

```json
{
  "message": "Crawling Operation Successful",
  "internalResources": ["..."],
  "externalResources": ["..."],
  "staticResources": ["..."],
  "otherResources": ["..."]
}
```

`400 Bad Request` for malformed links:

```json
{ "message": "Invalid URL Operation Failed" }
```

Example with `curl`:

```bash
curl -X POST http://localhost:8081/crawl \
  -H 'Content-Type: application/json' \
  -d '{"link":"https://example.com"}'
```

## Project layout

```
com.utkarsh.web_crawler
├── WebCrawlerApplication      // @SpringBootApplication entry point
├── controller.CrawlingController
├── service.ICrawlService / CrawlServiceImpl
├── util.CrawlingUtils         // jsoup-based recursive crawler
├── util.UrlValidation         // URL regex check
├── util.RestEndpointMapper    // endpoint constants
├── util.LoggerMessages        // log/response strings
├── util.ResponseMessages
├── dto.CrawlRequest
└── dto.CrawlResponse
```

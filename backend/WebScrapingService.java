package com.example.chatbot_backend;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class WebScrapingService {

    // =====================================================
    // Logger
    // =====================================================
    private static final Logger log = LoggerFactory.getLogger(WebScrapingService.class);

    // =====================================================
    // Configuration Constants
    // =====================================================
    private static final String DUCKDUCKGO_URL = "https://html.duckduckgo.com/html/?q=";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_RESULTS = 10;
    private static final String RESULT_PREFIX = "- ";

    // =====================================================
    // User Agents
    // =====================================================
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64)",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
    };

    // =====================================================
    // DuckDuckGo Result Selectors
    // =====================================================
    private static final String[] EXTRA_SELECTORS = {
            ".result__extras",
            ".result__url",
            ".result__snippet",
            ".result__body",
            ".result__title a",
            ".result__a"
    };

    // =====================================================
    // Public API
    // =====================================================
    public List<String> searchAndScrape(String query) {

        List<String> results = new ArrayList<>();

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = DUCKDUCKGO_URL + encodedQuery;

            log.info("Attempting to scrape search results from: {}", searchUrl);

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(getRandomUserAgent())
                    .timeout(TIMEOUT_MS)
                    .get();

            Elements resultElements = doc.select("div.result, div.web-result");
            log.debug("Found {} potential result elements.", resultElements.size());

            extractResults(resultElements, results);

            if (results.isEmpty()) {
                log.warn("No snippets extracted for query: {}", query);
            }

        } catch (IOException e) {
            log.error("IOException during scraping for query '{}': {}", query, e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during scraping for query '{}'", query, e);
        }

        return results;
    }

    // =====================================================
    // Internal Helpers
    // =====================================================
    private void extractResults(Elements elements, List<String> results) {

        int count = 0;

        for (Element result : elements) {
            if (count >= MAX_RESULTS) {
                break;
            }

            String extracted = extractFirstAvailable(result, EXTRA_SELECTORS);
            if (extracted != null) {
                results.add(RESULT_PREFIX + extracted);
                log.debug("Extracted snippet: {}", extracted);
                count++;
            } else {
                log.debug("Skipped result with no valid text");
            }
        }
    }

    private String extractFirstAvailable(Element result, String... selectors) {
        for (String selector : selectors) {
            Element el = result.selectFirst(selector);
            if (el != null && !el.text().trim().isEmpty()) {
                return el.text().trim();
            }
        }
        return null;
    }

    private String getRandomUserAgent() {
        return USER_AGENTS[(int) (Math.random() * USER_AGENTS.length)];
    }
}

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

    private static final Logger log = LoggerFactory.getLogger(WebScrapingService.class);
    private static final String DUCKDUCKGO_URL = "https://html.duckduckgo.com/html/?q=";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36";
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RESULTS = 10;

    // --- New elements for enhancement ---
    private static final String[] EXTRA_SELECTORS = {
            ".result__extras",
            ".result__url",
            ".result__snippet",
            ".result__body",
            ".result__title a",
            ".result__a"
    };

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64)",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
    };
    // --- End new elements ---

    public List<String> searchAndScrape(String query) {
        List<String> results = new ArrayList<>();
        try {
            // Encode the query for the URL
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = DUCKDUCKGO_URL + encodedQuery;
            log.info("Attempting to scrape search results from: {}", searchUrl);

            // Fetch the HTML document using Jsoup
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(getRandomUserAgent()) // Updated to use random user-agent
                    .timeout(TIMEOUT_MS)
                    .get();

            Elements resultElements = doc.select("div.result, div.web-result"); 

            log.debug("Found {} potential result elements.", resultElements.size());

            int count = 0;
            for (Element result : resultElements) {
                if (count >= MAX_RESULTS) {
                    break;
                }

                // Use helper to extract first available element text
                String extracted = extractFirstAvailable(result, EXTRA_SELECTORS);
                if (extracted != null) {
                    results.add("- " + extracted);
                    log.debug("Extracted: {}", extracted);
                    count++;
                } else {
                    log.debug("No valid text found in this result element");
                }
            }

            if (results.isEmpty()) {
                log.warn("No snippets extracted from search results page for query: {}", query);
            }

        } catch (IOException e) {
            log.error("IOException during Jsoup connection or parsing for query '{}': {}", query, e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during web scraping for query '{}': {}", query, e.getMessage(), e);

        }

        return results;
    }

    // --- Helper method to extract first available selector ---
    private String extractFirstAvailable(Element result, String... selectors) {
        for (String selector : selectors) {
            Element el = result.selectFirst(selector);
            if (el != null && !el.text().trim().isEmpty()) {
                return el.text().trim();
            }
        }
        return null;
    }

    // --- Helper method to get random user-agent ---
    private String getRandomUserAgent() {
        return USER_AGENTS[(int) (Math.random() * USER_AGENTS.length)];
    }
}

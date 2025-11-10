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


    public List<String> searchAndScrape(String query) {
        List<String> results = new ArrayList<>();
        try {
            // Encode the query for the URL
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = DUCKDUCKGO_URL + encodedQuery;
            log.info("Attempting to scrape search results from: {}", searchUrl);

            // Fetch the HTML document using Jsoup
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT) // Set user agent to avoid basic blocks
                    .timeout(TIMEOUT_MS)   // Set connection timeout
                    .get();                // Execute the GET request

            Elements resultElements = doc.select("div.result, div.web-result"); 

            log.debug("Found {} potential result elements.", resultElements.size());

            int count = 0;
            for (Element result : resultElements) {
                if (count >= MAX_RESULTS) {
                    break;
                }

                Element snippetElement = result.selectFirst(".result__snippet, .result__body"); 
                if (snippetElement != null) {
                    String snippetText = snippetElement.text().trim(); 
                    if (!snippetText.isEmpty()) {
                        results.add("- " + snippetText); 
                        log.debug("Extracted snippet: {}", snippetText);
                        count++;
                    }
                } else {

                     Element titleElement = result.selectFirst(".result__title a, .result__a");
                     if (titleElement != null) {
                         String titleText = titleElement.text().trim();
                         if(!titleText.isEmpty()) {
                             results.add("- " + titleText + " (Title only)");
                             log.debug("Extracted title as fallback: {}", titleText);
                             count++;
                         }
                     }
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
}
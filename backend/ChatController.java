package com.example.chatbot_backend;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class ChatController {

    // =====================================================
    // Logger
    // =====================================================
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // =====================================================
    // Dependencies
    // =====================================================
    @Autowired
    private WebScrapingService webScrapingService;

    // =====================================================
    // Configuration
    // =====================================================
    @Value("${ollama.api.url:http://localhost:11434/api/generate}")
    private String ollamaApiUrl;

    @Value("${ollama.model.name:gemma3}")
    private String ollamaModelName;

    // =====================================================
    // Constants (Design only)
    // =====================================================
    private static final int MAX_PROMPT_LENGTH = 8000;
    private static final int PROMPT_MIN_WORDS = 100;
    private static final int PROMPT_MAX_WORDS = 600;

    private static final String PROMPT_TEMPLATE =
            "these are the following search results i got:\n\"\"\"\n%s\n\"\"\"\n\n" +
            "Answer my original question directly within %d to %d words: %s";

    // =====================================================
    // Search Heuristic Keywords
    // =====================================================
    private static final List<String> SEARCH_KEYWORDS = Arrays.asList(
            "upcoming","web","search","find","breaking",
            "multiplied","add","sub","subtract","sum",
            "adding","multiply","divide","divided","/",
            "latest","current","today","live","update",
            "emergency","alert","trending","headline",
            "recent","news","developing","real-time",
            "urgent","immediate","now","global","world",
            "election","weather","stock","finance","sports",
            "results","polls","technology","market","viral",
            "scoop","yesterday","price of","who is",
            "what is the capital of","define",
            "tell me about","google","bing","summary of",
            "what happened","public data","net worth",
            "population","explain briefly","short note",
            "meaning of"
    );

    // =====================================================
    // API Endpoint
    // =====================================================
    @PostMapping("/api/chat")
    public Map<String, String> handleChatMessage(@RequestBody Map<String, String> payload) {

        String userMessage = payload.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            log.warn("Received empty message payload.");
            return Map.of("error", "Message not provided in the request.");
        }

        userMessage = normalizeMessage(userMessage);
        log.info("Received user message: {}", userMessage);

        String finalPrompt = userMessage;

        if (shouldSearchWeb(userMessage)) {
            log.info("Heuristic triggered: Searching web for query '{}'", userMessage);

            List<String> searchSnippets = webScrapingService.searchAndScrape(userMessage);

            if (searchSnippets != null && !searchSnippets.isEmpty()) {

                String formattedResults = formatScrapedResults(searchSnippets);
                finalPrompt = buildSearchPrompt(formattedResults, userMessage);

                log.info("Generated prompt with scraped web results.");
                log.debug("Prompt snippet: {}",
                        finalPrompt.substring(0, Math.min(finalPrompt.length(), 180)) + "...");
            } else {
                log.warn("Web scraping returned no snippets for query: {}", userMessage);
            }
        } else {
            log.info("Heuristic not triggered. Sending original message to LLM.");
        }

        String safePrompt = enforcePromptLimit(finalPrompt);
        String botResponse = getOllamaResponse(safePrompt);

        return Map.of("response", botResponse);
    }

    // =====================================================
    // Heuristic & Formatting Helpers
    // =====================================================
    private boolean shouldSearchWeb(String message) {
        String lower = message.toLowerCase();
        return SEARCH_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private String formatScrapedResults(List<String> snippets) {
        return String.join("\n", snippets);
    }

    private String buildSearchPrompt(String results, String question) {
        return String.format(
                PROMPT_TEMPLATE,
                results,
                PROMPT_MIN_WORDS,
                PROMPT_MAX_WORDS,
                question
        );
    }

    private String normalizeMessage(String msg) {
        return msg.replaceAll("\\s+", " ").trim();
    }

    private String enforcePromptLimit(String prompt) {
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            log.warn("Prompt exceeded max length. Trimming.");
            return prompt.substring(0, MAX_PROMPT_LENGTH);
        }
        return prompt;
    }

    // =====================================================
    // Ollama API Integration
    // =====================================================
    private String getOllamaResponse(String prompt) {

        StringBuilder finalResponse = new StringBuilder();

        try {
            URL url = new URL(ollamaApiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            JSONObject requestPayload = new JSONObject();
            requestPayload.put("model", ollamaModelName);
            requestPayload.put("prompt", prompt);
            requestPayload.put("stream", false);

            log.debug("Sending prompt to Ollama. Model: {}, URL: {}", ollamaModelName, ollamaApiUrl);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestPayload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            log.debug("Ollama API response code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                    String responseBody = reader.lines().collect(Collectors.joining("\n"));
                    if (!responseBody.trim().isEmpty()) {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        finalResponse.append(jsonResponse.optString("response", ""));
                    }
                }
            } else {
                return readErrorResponse(conn, responseCode);
            }

            conn.disconnect();

            String output = finalResponse.toString().trim();
            log.info("Received Ollama response length: {}", output.length());

            return output.isEmpty()
                    ? "No valid response received from Ollama."
                    : output;

        } catch (Exception e) {
            log.error("Error calling Ollama API: {}", e.getMessage(), e);
            return "Error calling Ollama API: " + e.getMessage();
        }
    }

    private String readErrorResponse(HttpURLConnection conn, int code) {
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {

            String errorBody = errorReader.lines().collect(Collectors.joining("\n"));
            log.error("Error response from Ollama ({}): {}", code, errorBody);
            return "Error from Ollama API: " + code + " - " + errorBody;

        } catch (Exception e) {
            log.error("Could not read error stream from Ollama: {}", e.getMessage());
            return "Error from Ollama API: " + code;
        }
    }
}

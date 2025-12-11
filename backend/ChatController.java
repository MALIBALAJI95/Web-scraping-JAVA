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

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private WebScrapingService webScrapingService;

    @Value("${ollama.api.url:http://localhost:11434/api/generate}")
    private String ollamaApiUrl;

    @Value("${ollama.model.name:gemma3}")
    private String ollamaModelName;

    // --- NEW ENHANCED KEYWORDS ---
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

            // NEW additions
            "tell me about", "google", "bing", "summary of",
            "what happened", "public data", "net worth", "population",
            "explain briefly", "short note", "meaning of"
    );
    // --- END NEW KEYWORDS ---

    @PostMapping("/api/chat")
    public Map<String, String> handleChatMessage(@RequestBody Map<String, String> payload) {
        String userMessage = payload.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            log.warn("Received empty message payload.");
            return Map.of("error", "Message not provided in the request.");
        }

        userMessage = normalizeMessage(userMessage);  // NEW normalization
        log.info("Received user message: {}", userMessage);

        String finalPrompt = userMessage;

        if (shouldSearchWeb(userMessage)) {
            log.info("Heuristic triggered: Searching web for query '{}'", userMessage);

            List<String> searchSnippets = webScrapingService.searchAndScrape(userMessage);

            if (searchSnippets != null && !searchSnippets.isEmpty()) {

                String formattedResults = formatScrapedResults(searchSnippets);
                finalPrompt = String.format(
                        "these are the following search results i got:\n\"\"\"\n%s\n\"\"\"\n\nAnswer my original question directly within 100 to 600 words: %s",
                        formattedResults,
                        userMessage
                );
                log.info("Generated prompt with scraped web results.");
                log.debug("Prompt snippet: {}", finalPrompt.substring(0, Math.min(finalPrompt.length(), 180)) + "...");

            } else {
                log.warn("Web scraping failed or returned no snippets for query: {}", userMessage);
            }
        } else {
            log.info("Heuristic not triggered. Sending original message to LLM.");
        }

        String safePrompt = enforcePromptLimit(finalPrompt); // NEW safety
        String botResponse = getOllamaResponse(safePrompt);

        return Map.of("response", botResponse);
    }

    private boolean shouldSearchWeb(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase();
        return SEARCH_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private String formatScrapedResults(List<String> snippets) {
        return String.join("\n", snippets);
    }

    // --- NEW: Normalize message ---
    private String normalizeMessage(String msg) {
        return msg.replaceAll("\\s+", " ").trim();
    }

    // --- NEW: Prevent extremely long prompts ---
    private String enforcePromptLimit(String prompt) {
        int maxLength = 8000;
        if (prompt.length() > maxLength) {
            log.warn("Prompt exceeded limit. Trimming...");
            return prompt.substring(0, maxLength);
        }
        return prompt;
    }

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
                byte[] input = requestPayload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input);
            }

            int responseCode = conn.getResponseCode();
            log.debug("Ollama API response code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                    String responseBody = reader.lines().collect(Collectors.joining("\n"));
                    if (!responseBody.trim().isEmpty()) {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String responseText = jsonResponse.optString("response", "");
                        finalResponse.append(responseText);
                    }
                }
            } else {
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {

                    String errorBody = errorReader.lines().collect(Collectors.joining("\n"));
                    log.error("Error response from Ollama ({}): {}", responseCode, errorBody);
                    return "Error from Ollama API: " + responseCode + " - " + errorBody;

                } catch (Exception ioEx) {
                    log.error("Could not read error stream from Ollama: {}", ioEx.getMessage());
                    return "Error from Ollama API: " + responseCode;
                }
            }

            conn.disconnect();

            String output = finalResponse.toString().trim();
            log.info("Received Ollama response length: {}", output.length());

            return output.isEmpty() ? "No valid response received from Ollama." : output;

        } catch (Exception e) {
            log.error("Error calling Ollama API: {}", e.getMessage(), e);
            return "Error calling Ollama API: " + e.getMessage();
        }
    }
}

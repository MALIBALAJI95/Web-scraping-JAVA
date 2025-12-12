package com.example.chatbot_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@SpringBootApplication
public class ChatbotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotBackendApplication.class, args);
        System.out.println("[Chatbot] Application started successfully.");
        printEnvironmentInfo();
        registerShutdownHook();
    }

    // --- NEW: Display basic environment info ---
    private static void printEnvironmentInfo() {
        String javaVersion = System.getProperty("java.version");
        String os = System.getProperty("os.name");
        System.out.println("[Chatbot] Java Version: " + javaVersion);
        System.out.println("[Chatbot] Operating System: " + os);
    }

    // --- NEW: Basic shutdown notifier ---
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Chatbot] Application is shutting down...");
        }));
    }

    // Optional lifecycle annotations (Spring managed)
    @PostConstruct
    public void onStartup() {
        System.out.println("[Chatbot] Initialization phase complete.");
    }

    @PreDestroy
    public void onShutdown() {
        System.out.println("[Chatbot] PreDestroy: Cleaning up before shutdown.");
    }
}

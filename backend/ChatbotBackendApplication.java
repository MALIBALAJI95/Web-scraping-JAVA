package com.example.chatbot_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@SpringBootApplication
public class ChatbotBackendApplication {

    // --- Design constants ---
    private static final String APP_NAME = "Chatbot Backend";
    private static final String APP_VERSION = "v1.0.0";

    public static void main(String[] args) {
        SpringApplication.run(ChatbotBackendApplication.class, args);

        printBanner();
        log(APP_NAME + " started successfully");
        printEnvironmentInfo();
        registerShutdownHook();
    }

    // --- Simple banner (design only) ---
    private static void printBanner() {
        System.out.println("======================================");
        System.out.println("   " + APP_NAME + "  " + APP_VERSION);
        System.out.println("======================================");
    }

    // --- Centralized logging (no framework added) ---
    private static void log(String message) {
        System.out.println("[Chatbot] " + message);
    }

    // --- Display basic environment info ---
    private static void printEnvironmentInfo() {
        String javaVersion = System.getProperty("java.version");
        String os = System.getProperty("os.name");

        log("Java Version: " + javaVersion);
        log("Operating System: " + os);
    }

    // --- Basic shutdown notifier ---
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                log("Application is shutting down...")
        ));
    }

    // --- Spring lifecycle hooks ---
    @PostConstruct
    public void onStartup() {
        log("Initialization phase complete");
    }

    @PreDestroy
    public void onShutdown() {
        log("PreDestroy: Cleaning up before shutdown");
    }
}

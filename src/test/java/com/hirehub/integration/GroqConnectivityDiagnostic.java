package com.hirehub.integration;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * GroqConnectivityDiagnostic — Standalone minimal connectivity test for the GroqCloud API.
 *
 * <p>Run from project root with:
 * <pre>
 *   $env:GROQ_API_KEY = "your_key_here"
 *   java -cp "target/classes;target/dependency/*" com.hirehub.util.GroqConnectivityDiagnostic
 * </pre>
 */
public class GroqConnectivityDiagnostic {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "openai/gpt-oss-20b";
    private static final String TEST_PROMPT = "Reply with exactly CONNECTED.";

    public static void main(String[] args) {
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("GROQ_API_KEY");
        }

        System.out.println("==================================================");
        System.out.println("  HireHub GroqCloud Diagnostic Runner");
        System.out.println("  Model       : " + MODEL);
        System.out.println("  Endpoint    : " + ENDPOINT);
        System.out.println("  API Key set : " + (apiKey != null && !apiKey.isBlank()));
        System.out.println("==================================================");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[ERROR] GROQ_API_KEY environment variable is not set.");
            System.err.println("Set the variable in PowerShell: $env:GROQ_API_KEY = \"your_key\"");
            System.exit(1);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        Gson gson = new Gson();

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", TEST_PROMPT);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.add("messages", messages);
        body.addProperty("max_tokens", 50);
        body.addProperty("temperature", 0.0);

        String jsonPayload = gson.toJson(body);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;

            int status = response.statusCode();
            System.out.println("HTTP Status Code: " + status + " (" + elapsed + " ms)");

            if (status == 200) {
                JsonObject root = gson.fromJson(response.body(), JsonObject.class);
                String text = root.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString().trim();
                System.out.println("[SUCCESS] Output: \"" + text + "\"");
            } else {
                System.err.println("[FAILED] Response: " + response.body());
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Request failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

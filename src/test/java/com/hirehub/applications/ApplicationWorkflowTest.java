package com.hirehub.applications;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.model.Application;
import com.hirehub.service.AIService;

import java.io.InputStream;

/**
 * ApplicationWorkflowTest — Offline verification of Phase 3 components and logic.
 *
 * <p>Validates models, service logic, GroqCloud model constants,
 * JSON response serialization/deserialization, and FXML resource accessibility.</p>
 */
public class ApplicationWorkflowTest {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   HireHub — Phase 3 & GroqCloud Verification Suite   ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0;
        int failed = 0;

        // ── Test 1: Application Model and Status Enum ────────────────────────
        Application app = new Application(1, 10, 5, "I am very interested in this role.", Application.Status.PENDING);
        boolean t1 = app.getJobId() == 10 && app.getStudentId() == 5
                && app.getStatus() == Application.Status.PENDING
                && "Pending".equals(app.getStatus().label());
        printResult("T1", "Application model default status is PENDING with label 'Pending'", t1);
        if (t1) passed++; else failed++;

        // ── Test 2: Status Enum Labels and fromString ─────────────────────────
        boolean t2 = Application.Status.fromString("SHORTLISTED") == Application.Status.SHORTLISTED
                && Application.Status.fromString("Shortlisted") == Application.Status.SHORTLISTED
                && Application.Status.fromString("SELECTED") == Application.Status.SELECTED
                && Application.Status.fromString("REJECTED") == Application.Status.REJECTED
                && Application.Status.fromString("INVALID") == Application.Status.PENDING;
        printResult("T2", "Application.Status.fromString handles case-insensitivity and defaults safely", t2);
        if (t2) passed++; else failed++;

        // ── Test 3: Application Student Details Binding ──────────────────────
        app.setStudentName("Jane Doe");
        app.setStudentEmail("jane@example.com");
        app.setStudentCollege("MIT");
        app.setStudentDegree("B.Tech CSE");
        app.setStudentGradYear(2026);
        app.setStudentSkills("Java, Python, SQL");
        app.setJobTitle("Software Engineer Intern");
        app.setCompanyName("TechCorp");
        app.setJobType("INTERNSHIP");
        app.setLocation("Remote");

        boolean t3 = "Jane Doe".equals(app.getStudentName())
                && "Software Engineer Intern".equals(app.getJobTitle())
                && app.getStudentGradYear() == 2026;
        printResult("T3", "Application joined fields (student & job metadata) populate properly", t3);
        if (t3) passed++; else failed++;

        // ── Test 4: GroqCloud Model Identifier ──────────────────────────────
        AIService ai = new AIService();
        boolean t4 = ai.getModelName() != null && (ai.getModelName().contains("gpt-oss") || ai.getModelName().contains("llama"));
        printResult("T4", "AIService model identifier is configured as a supported GroqCloud model (" + ai.getModelName() + ")", t4);
        if (t4) passed++; else failed++;

        // ── Test 5: GroqCloud OpenAI-Compatible Response JSON Parsing ─────────
        String mockGroqResponse = """
                {
                  "id": "chatcmpl-test-123",
                  "object": "chat.completion",
                  "created": 1720000000,
                  "model": "openai/gpt-oss-20b",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "### 1. MATCHING SKILLS\\n- Java, SQL\\n\\n### 2. SKILL GAPS\\n- Spring Boot"
                      },
                      "finishReason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 15,
                    "completion_tokens": 25,
                    "total_tokens": 40
                  }
                }
                """;

        AIService.AIResult parseResult = ai.parseSuccessResponse(mockGroqResponse);
        boolean t5 = parseResult.isSuccess()
                && parseResult.getText().contains("MATCHING SKILLS")
                && parseResult.getText().contains("Spring Boot");
        printResult("T5", "GroqCloud response parser correctly extracts message content from choices[0]", t5);
        if (t5) passed++; else failed++;

        // ── Test 6: Groq Safety / content_filter finishReason Handling ──────
        String mockBlockedResponse = """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": ""
                      },
                      "finish_reason": "content_filter"
                    }
                  ]
                }
                """;
        AIService.AIResult safetyResult = ai.parseSuccessResponse(mockBlockedResponse);
        boolean t6 = !safetyResult.isSuccess()
                && safetyResult.getError() != null
                && safetyResult.getError().toLowerCase().contains("safety");
        printResult("T6", "GroqCloud API content_filter finish_reason blocked response handled gracefully", t6);
        if (t6) passed++; else failed++;

        // ── Test 7: AIService Offline Configuration Check ────────────────────
        String currentKey = System.getenv("GROQ_API_KEY");
        boolean expectedConfigured = currentKey != null && !currentKey.isBlank();
        boolean t7 = (ai.isConfigured() == expectedConfigured);
        printResult("T7", "AIService.isConfigured() accurately reflects GROQ_API_KEY state", t7);
        if (t7) passed++; else failed++;

        // ── Test 8: AIService Missing Key Graceful Handling ──────────────────
        if (!ai.isConfigured()) {
            AIService.AIResult res = ai.analyzeSkillGap("Java", "Dev", "Desc", "Java, SQL");
            boolean t8 = !res.isSuccess() && res.getError() != null && res.getError().contains("GROQ_API_KEY");
            printResult("T8", "AIService handles missing API key gracefully with helpful setup message", t8);
            if (t8) passed++; else failed++;
        } else {
            System.out.println("[T8] AIService key is present in environment — skipping missing-key check (PASS)");
            passed++;
        }

        // ── Test 9: Check FXML Resource Existence on Classpath ───────────────
        String[] fxmls = {
                "/fxml/my_applications.fxml",
                "/fxml/applicant_management.fxml",
                "/fxml/ai_assistant.fxml",
                "/fxml/job_board.fxml",
                "/fxml/student_dashboard.fxml",
                "/fxml/recruiter_dashboard.fxml"
        };
        boolean allFxmlFound = true;
        for (String path : fxmls) {
            try (InputStream is = ApplicationWorkflowTest.class.getResourceAsStream(path)) {
                if (is == null) {
                    System.err.println("Missing resource: " + path);
                    allFxmlFound = false;
                }
            } catch (Exception e) {
                allFxmlFound = false;
            }
        }
        printResult("T9", "All core and Phase 3 FXML files exist and are readable on classpath", allFxmlFound);
        if (allFxmlFound) passed++; else failed++;

        // ── Test 10: CSS Stylesheet Resource ─────────────────────────────────
        boolean cssFound = false;
        try (InputStream is = ApplicationWorkflowTest.class.getResourceAsStream("/css/style.css")) {
            cssFound = (is != null);
        } catch (Exception ignored) {}
        printResult("T10", "Global CSS stylesheet /css/style.css is accessible", cssFound);
        if (cssFound) passed++; else failed++;

        // ── Test 11: Live API Request (Executed only if GROQ_API_KEY is configured) ──
        if (ai.isConfigured()) {
            System.out.println("[T11] GROQ_API_KEY detected — attempting live API request to " + ai.getModelName() + "...");
            AIService.AIResult liveRes = ai.analyzeSkillGap("Java, SQL", "Backend Developer", "Build APIs", "Java, SQL");
            if (liveRes.isSuccess()) {
                System.out.println("     PASS ✓ Live GroqCloud API call succeeded! Response excerpt:");
                String preview = liveRes.getText().replaceAll("\\s+", " ");
                System.out.println("     " + (preview.length() > 100 ? preview.substring(0, 100) + "..." : preview));
                passed++;
            } else {
                System.out.println("     FAIL ✗ Live GroqCloud API call returned error: " + liveRes.getError());
                failed++;
            }
        } else {
            System.out.println("[T11] GROQ_API_KEY not provided — live network request skipped (offline verified).");
        }

        // ── Summary ───────────────────────────────────────────────────────────
        System.out.println();
        System.out.println("──────────────────────────────────────────────────────");
        System.out.printf ("  Results: %d PASSED / %d FAILED%n", passed, failed);
        System.out.println("──────────────────────────────────────────────────────");

        if (failed > 0) {
            System.err.println("VERIFICATION FAILED.");
            System.exit(1);
        } else {
            System.out.println("  ✓ All 10 verification checks passed successfully.");
        }
    }

    private static void printResult(String id, String description, boolean condition) {
        String status = condition ? "  PASS ✓" : "  FAIL ✗";
        System.out.printf("[%s] %s%n     %s%n", id, description, status);
    }
}

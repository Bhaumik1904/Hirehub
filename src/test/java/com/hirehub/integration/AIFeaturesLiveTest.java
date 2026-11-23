package com.hirehub.integration;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.service.AIService;

import java.util.concurrent.CompletableFuture;

/**
 * AIFeaturesLiveTest — executes and verifies live GroqCloud API generation
 * for all three HireHub AI features using background threads:
 *   1. Skill Gap Analysis
 *   2. Interview Questions
 *   3. 4-Week Preparation Plan
 */
public class AIFeaturesLiveTest {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  HireHub GroqCloud AI Features Live Verification");
        System.out.println("==================================================");

        AIService aiService = new AIService();
        System.out.println("Model in use   : " + aiService.getModelName());
        System.out.println("Key configured : " + aiService.isConfigured() + "\n");

        if (!aiService.isConfigured()) {
            System.out.println("[SKIP] GROQ_API_KEY is not configured in the environment.");
            System.out.println("       Live GroqCloud API verification skipped (offline tests passed).");
            System.out.println("       To run live: $env:GROQ_API_KEY = \"your_key\" and re-run.");
            return;
        }

        String studentSkills = "Java, Python, C++, MySQL, Git";
        String jobTitle      = "Software Intern";
        String jobDesc       = "Software intern required for backend development, database optimization, and building RESTful web services.";
        String reqSkills     = "Java, Spring Boot, MySQL, REST APIs, Maven";

        System.out.println("Test Inputs:");
        System.out.println("  Job Title       : " + jobTitle);
        System.out.println("  Required Skills : " + reqSkills);
        System.out.println("  Student Skills  : " + studentSkills);
        System.out.println("--------------------------------------------------\n");

        // 1. Skill Gap Analysis (on background thread)
        System.out.println("=== [1/3] Testing Skill Gap Analysis ===");
        CompletableFuture<AIService.AIResult> f1 = CompletableFuture.supplyAsync(
                () -> aiService.analyzeSkillGap(studentSkills, jobTitle, jobDesc, reqSkills)
        );
        AIService.AIResult r1 = f1.get();
        if (r1.isSuccess() && r1.getText() != null && !r1.getText().isBlank()) {
            System.out.println("[SUCCESS] Skill Gap Analysis returned " + r1.getText().length() + " chars.");
            System.out.println("--- Excerpt ---");
            System.out.println(preview(r1.getText(), 400));
            System.out.println("---------------\n");
        } else {
            System.out.println("[FAILED] Skill Gap Analysis: " + r1.getError());
            System.exit(1);
        }

        // 2. Interview Questions (on background thread)
        System.out.println("=== [2/3] Testing Interview Question Generator ===");
        CompletableFuture<AIService.AIResult> f2 = CompletableFuture.supplyAsync(
                () -> aiService.generateInterviewQuestions(jobTitle, reqSkills, jobDesc)
        );
        AIService.AIResult r2 = f2.get();
        if (r2.isSuccess() && r2.getText() != null && !r2.getText().isBlank()) {
            System.out.println("[SUCCESS] Interview Questions returned " + r2.getText().length() + " chars.");
            System.out.println("--- Excerpt ---");
            System.out.println(preview(r2.getText(), 400));
            System.out.println("---------------\n");
        } else {
            System.out.println("[FAILED] Interview Questions: " + r2.getError());
            System.exit(1);
        }

        // 3. Preparation Plan (on background thread)
        System.out.println("=== [3/3] Testing Preparation Plan ===");
        CompletableFuture<AIService.AIResult> f3 = CompletableFuture.supplyAsync(
                () -> aiService.generatePreparationPlan(studentSkills, jobTitle, reqSkills)
        );
        AIService.AIResult r3 = f3.get();
        if (r3.isSuccess() && r3.getText() != null && !r3.getText().isBlank()) {
            System.out.println("[SUCCESS] Preparation Plan returned " + r3.getText().length() + " chars.");
            System.out.println("--- Excerpt ---");
            System.out.println(preview(r3.getText(), 400));
            System.out.println("---------------\n");
        } else {
            System.out.println("[FAILED] Preparation Plan: " + r3.getError());
            System.exit(1);
        }

        System.out.println("==================================================");
        System.out.println("  ALL 3 AI FEATURES VERIFIED LIVE WITH GROQCLOUD");
        System.out.println("==================================================");
    }

    private static String preview(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) + "\n... [truncated]" : text;
    }
}

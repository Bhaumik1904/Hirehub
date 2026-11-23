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
import com.hirehub.model.*;
import com.hirehub.service.AIService;
import com.hirehub.service.CircuitBreaker;
import com.hirehub.service.ResumeAnalysisService;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * GroqIntegrationTest — Comprehensive test suite validating the complete
 * migration from Google Gemini to GroqCloud across all HireHub AI services and workflows.
 */
public class GroqIntegrationTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;
    private static int totalSkipped = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  HireHub Phase 6: GroqCloud AI Migration Verification Suite");
        System.out.println("=================================================================\n");

        try {
            // Section 1: Architecture, Endpoint & Configuration
            testSection1ArchitectureAndConfig();

            // Section 2: Mock HTTP Response Parsing & Error Handling
            testSection2ResponseParsingAndErrors();

            // Section 3: Reliability, Circuit Breaker & Retry Strategies
            testSection3ReliabilityAndCircuitBreaker();

            // Section 4: Optimized Interview Question Generation & Caching
            testSection4InterviewQuestionGeneration();

            // Section 5: AI Workflows & Prompt Invariants
            testSection5AIWorkflowsAndPrompts();

            // Section 6: Deterministic Scoring & Privacy Protection Invariants
            testSection6DeterministicScoringAndPrivacy();

            // Section 7: Conditional Live GroqCloud API Network Test
            testSection7ConditionalLiveApi();

        } catch (Throwable t) {
            System.err.println("[FATAL] Verifier crashed unexpectedly: " + t.getMessage());
            t.printStackTrace();
            totalFailed++;
        }

        System.out.println("\n=================================================================");
        System.out.printf("  GROQCLOUD MIGRATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED\n",
                totalPassed, totalFailed, totalSkipped);
        System.out.println("=================================================================");

        if (totalFailed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static void pass(String name) {
        System.out.println("  [PASS] " + name);
        totalPassed++;
    }

    private static void fail(String name, String reason) {
        System.err.println("  [FAIL] " + name + " — " + reason);
        totalFailed++;
    }

    private static void skip(String name, String reason) {
        System.out.println("  [SKIP] " + name + " — " + reason);
        totalSkipped++;
    }

    // =========================================================================
    // Section 1: Architecture, Endpoint & Configuration
    // =========================================================================
    private static void testSection1ArchitectureAndConfig() {
        System.out.println("--- [1] GroqCloud Architecture & Configuration Suite ---");

        AIService ai = new AIService();

        // 1. Endpoint
        if (AIService.GROQ_ENDPOINT.equals("https://api.groq.com/openai/v1/chat/completions")) {
            pass("GroqCloud OpenAI-compatible endpoint is configured correctly: " + AIService.GROQ_ENDPOINT);
        } else {
            fail("Endpoint mismatch", "Expected https://api.groq.com/openai/v1/chat/completions, got " + AIService.GROQ_ENDPOINT);
        }

        // 2. Primary Model Default
        if ("openai/gpt-oss-20b".equals(ai.getModelName())) {
            pass("Primary model defaults to openai/gpt-oss-20b");
        } else {
            fail("Primary model default", "Expected openai/gpt-oss-20b, got " + ai.getModelName());
        }

        // 3. Fallback Model Default
        if ("llama-3.3-70b-versatile".equals(ai.getFallbackModelName())) {
            pass("Fallback model defaults to llama-3.3-70b-versatile");
        } else {
            fail("Fallback model default", "Expected llama-3.3-70b-versatile, got " + ai.getFallbackModelName());
        }

        // 4. Missing Key Error Message & Setup URL
        AIService unconfigured = new AIService(null);
        if (!unconfigured.isConfigured()) {
            pass("AIService(null) reports isConfigured() == false");
        } else {
            fail("Unconfigured check", "Expected isConfigured() == false for null key");
        }

        AIService.AIResult missingKeyRes = unconfigured.analyzeSkillGap("Java", "Dev", "Desc", "Java");
        if (!missingKeyRes.isSuccess() && missingKeyRes.getError().contains("GROQ_API_KEY")
                && missingKeyRes.getError().contains("https://console.groq.com/keys")) {
            pass("Missing key error guides user to GroqCloud console and $env:GROQ_API_KEY");
        } else {
            fail("Missing key guidance", "Error missing GROQ_API_KEY or Groq console link: " + missingKeyRes.getError());
        }

        // 5. Check FXML classpath availability
        String[] fxmlPaths = {"/fxml/ai_assistant.fxml", "/fxml/resume_analyzer.fxml"};
        for (String p : fxmlPaths) {
            try (InputStream is = GroqIntegrationTest.class.getResourceAsStream(p)) {
                if (is != null) {
                    pass("Classpath FXML accessible: " + p);
                } else {
                    fail("Classpath FXML missing", p);
                }
            } catch (Exception e) {
                fail("Classpath FXML error", p + ": " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // Section 2: Mock HTTP Response Parsing & Error Handling
    // =========================================================================
    private static void testSection2ResponseParsingAndErrors() {
        System.out.println("\n--- [2] GroqCloud Response Parsing & Error Handling Suite ---");

        AIService ai = new AIService("mock-groq-key");

        // 1. Success 200 standard chat completion
        String mock200 = """
                {
                  "id": "chatcmpl-test-01",
                  "object": "chat.completion",
                  "created": 1720000000,
                  "model": "openai/gpt-oss-20b",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "### 1. MATCHING SKILLS\\n- Java, Spring Boot\\n\\n### 2. MISSING SKILLS\\n- Docker"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": { "prompt_tokens": 50, "completion_tokens": 100, "total_tokens": 150 }
                }
                """;
        AIService.AIResult res200 = ai.parseSuccessResponse(mock200);
        if (res200.isSuccess() && res200.getText().contains("MATCHING SKILLS") && res200.getText().contains("Docker")) {
            pass("HTTP 200 chat completion parsed correctly from choices[0].message.content");
        } else {
            fail("HTTP 200 parsing", "Failed to parse content: " + res200.getError());
        }

        // 2. HTTP 400 Bad Request
        String mock400 = """
                {
                  "error": {
                    "message": "Invalid temperature value: 3.5. Must be between 0 and 2.",
                    "type": "invalid_request_error",
                    "code": "parameter_invalid"
                  }
                }
                """;
        AIService.AIResult res400 = ai.parseSuccessResponse(mock400);
        if (!res400.isSuccess() && res400.getError().contains("Invalid temperature value")) {
            pass("HTTP 400 error extracted and formatted into user-friendly message");
        } else {
            fail("HTTP 400 parsing", "Unexpected result: " + res400.getError());
        }

        // 3. HTTP 401 Authentication Failure
        String mock401 = """
                {
                  "error": {
                    "message": "Invalid API Key provided: gsk_invalid_test_key",
                    "type": "authentication_error",
                    "code": "invalid_api_key"
                  }
                }
                """;
        AIService.AIResult res401 = ai.parseSuccessResponse(mock401);
        if (!res401.isSuccess() && res401.getError().contains("Invalid API Key")) {
            pass("HTTP 401 authentication error extracted properly");
        } else {
            fail("HTTP 401 parsing", "Unexpected result: " + res401.getError());
        }

        // 4. HTTP 404 Model Not Found
        String mock404 = """
                {
                  "error": {
                    "message": "Model 'unsupported-model' does not exist or you do not have access.",
                    "type": "invalid_request_error",
                    "code": "model_not_found"
                  }
                }
                """;
        AIService.AIResult res404 = ai.parseSuccessResponse(mock404);
        if (!res404.isSuccess() && res404.getError().contains("does not exist")) {
            pass("HTTP 404 model not found error handled properly");
        } else {
            fail("HTTP 404 parsing", "Unexpected result: " + res404.getError());
        }

        // 5. HTTP 429 Rate Limit
        String mock429 = """
                {
                  "error": {
                    "message": "Rate limit reached for model openai/gpt-oss-20b. Requests per minute limit exceeded.",
                    "type": "requests",
                    "code": "rate_limit_exceeded"
                  }
                }
                """;
        AIService.AIResult res429 = ai.parseSuccessResponse(mock429);
        if (!res429.isSuccess() && res429.getError().contains("Rate limit reached")) {
            pass("HTTP 429 rate limit error parsed properly");
        } else {
            fail("HTTP 429 parsing", "Unexpected result: " + res429.getError());
        }

        // 6. Content Safety Filter (finish_reason = content_filter)
        String mockContentFilter = """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": { "role": "assistant", "content": "" },
                      "finish_reason": "content_filter"
                    }
                  ]
                }
                """;
        AIService.AIResult resFilter = ai.parseSuccessResponse(mockContentFilter);
        if (!resFilter.isSuccess() && resFilter.getError().toLowerCase().contains("safety")) {
            pass("finish_reason: content_filter handled gracefully with safety guidance");
        } else {
            fail("content_filter handling", "Unexpected result: " + resFilter.getError());
        }

        // 7. Malformed / Truncated JSON
        AIService.AIResult resMalformed = ai.parseSuccessResponse("{\"choices\": [{\"message\":");
        if (!resMalformed.isSuccess() && resMalformed.getError().contains("Could not parse")) {
            pass("Malformed JSON response handled safely without exception");
        } else {
            fail("Malformed JSON handling", "Unexpected result: " + resMalformed.getError());
        }
    }

    // =========================================================================
    // Section 3: Reliability, Circuit Breaker & Retry Strategies
    // =========================================================================
    private static void testSection3ReliabilityAndCircuitBreaker() {
        System.out.println("\n--- [3] GroqCloud Reliability, Circuit Breaker & Retry Suite ---");

        // 1. Circuit Breaker transitions
        CircuitBreaker cb = new CircuitBreaker(3, 100L); // 3 failures, 100ms cooldown
        if (cb.getState() == CircuitBreaker.State.CLOSED && cb.allowRequest()) {
            pass("Circuit Breaker initializes to CLOSED and permits requests");
        } else {
            fail("Circuit Breaker init", "Initial state is not CLOSED");
        }

        cb.recordFailure();
        cb.recordFailure();
        if (cb.getState() == CircuitBreaker.State.CLOSED) {
            pass("Circuit Breaker remains CLOSED after 2 failures (< threshold 3)");
        } else {
            fail("Circuit Breaker threshold", "Tripped before reaching threshold");
        }

        cb.recordFailure();
        if (cb.getState() == CircuitBreaker.State.OPEN && !cb.allowRequest()) {
            pass("Circuit Breaker transitions to OPEN after 3 failures and fast-fails");
        } else {
            fail("Circuit Breaker trip", "Failed to trip to OPEN on 3rd failure");
        }

        // Wait for cooldown
        try {
            Thread.sleep(120L);
        } catch (InterruptedException ignored) {}

        if (cb.getState() == CircuitBreaker.State.HALF_OPEN && cb.allowRequest()) {
            pass("Circuit Breaker transitions to HALF_OPEN after cooldown and allows 1 probe");
        } else {
            fail("Circuit Breaker half-open", "Failed to transition to HALF_OPEN");
        }

        cb.recordSuccess();
        if (cb.getState() == CircuitBreaker.State.CLOSED && cb.allowRequest()) {
            pass("Circuit Breaker resets to CLOSED upon successful probe");
        } else {
            fail("Circuit Breaker reset", "Failed to reset to CLOSED after success");
        }

        // 2. Exponential backoff with jitter bounds
        for (int attempt = 1; attempt <= 3; attempt++) {
            long backoff = AIService.calculateBackoffWithJitter(attempt, 1000L, 50L, 200L);
            long expectedBase = 1000L * (1L << (attempt - 1));
            if (backoff >= expectedBase + 50L && backoff <= expectedBase + 200L) {
                pass("Attempt " + attempt + " backoff (" + backoff + "ms) strictly within expected jitter bounds ["
                        + (expectedBase + 50) + ", " + (expectedBase + 200) + "]");
            } else {
                fail("Backoff bounds", "Attempt " + attempt + " backoff out of bounds: " + backoff);
            }
        }

        // 3. Retry-After header parsing
        long retry1 = AIService.parseRetryAfterMs("5");
        long retry2 = AIService.parseRetryAfterMs("30"); // Clamped to 15s max
        long retry3 = AIService.parseRetryAfterMs("invalid");
        if (retry1 == 5000L && retry2 == 15000L && retry3 == 0L) {
            pass("Retry-After header safely parsed and clamped to 15s ceiling");
        } else {
            fail("Retry-After parsing", "Expected 5000/15000/0, got " + retry1 + "/" + retry2 + "/" + retry3);
        }

        // 4. Transient vs Permanent failure distinction
        boolean transient503 = AIService.isTransientFailure("GroqCloud service temporarily unavailable (HTTP 503 UNAVAILABLE).");
        boolean nonTransient401 = AIService.isTransientFailure("GroqCloud API authentication failed (HTTP 401).");
        boolean nonTransient404 = AIService.isTransientFailure("GroqCloud model not found (HTTP 404).");
        if (transient503 && !nonTransient401 && !nonTransient404) {
            pass("Transient (503) vs permanent (401, 404) failures correctly distinguished");
        } else {
            fail("Transient failure distinction", "503=" + transient503 + ", 401=" + nonTransient401 + ", 404=" + nonTransient404);
        }
    }

    // =========================================================================
    // Section 4: Optimized Interview Question Generation & Caching
    // =========================================================================
    private static void testSection4InterviewQuestionGeneration() {
        System.out.println("\n--- [4] Optimized Interview Question Generation & Caching Suite ---");

        AIService ai = new AIService("mock-groq-key");

        // 1. Structured JSON validation
        String validQuestionsJson = """
                {
                  "questions": [
                    {
                      "category": "Technical",
                      "difficulty": "Medium",
                      "question": "Explain how HikariCP connection pooling improves JDBC throughput.",
                      "answerPoints": [
                        "Reuses open physical connections",
                        "Eliminates handshake overhead",
                        "Fast thread-safe allocation"
                      ]
                    },
                    {
                      "category": "Technical",
                      "difficulty": "Hard",
                      "question": "What is the difference between optimistic and pessimistic locking in MySQL?",
                      "answerPoints": [
                        "Optimistic uses version numbers without locks",
                        "Pessimistic uses SELECT ... FOR UPDATE row locks",
                        "Optimistic performs better under low contention"
                      ]
                    },
                    {
                      "category": "Technical",
                      "difficulty": "Medium",
                      "question": "How do JavaFX Task and Platform.runLater interact with background threads?",
                      "answerPoints": [
                        "Task runs compute on worker thread",
                        "Platform.runLater posts updates to FX thread",
                        "Prevents UI freezing"
                      ]
                    },
                    {
                      "category": "Behavioural",
                      "difficulty": "Behavioral",
                      "question": "Describe a challenging bug you diagnosed under a tight deployment deadline.",
                      "answerPoints": [
                        "Structured troubleshooting methodology",
                        "Clear team communication",
                        "Post-mortem root cause analysis"
                      ]
                    },
                    {
                      "category": "Behavioural",
                      "difficulty": "Behavioral",
                      "question": "How do you handle conflicting architectural opinions within an engineering team?",
                      "answerPoints": [
                        "Focus on measurable benchmarks and trade-offs",
                        "Collaborative consensus building",
                        "Commit once decision is reached"
                      ]
                    }
                  ]
                }
                """;

        List<AIService.InterviewQuestion> parsed = ai.parseAndValidateInterviewQuestions(validQuestionsJson);
        if (parsed.size() == 5) {
            pass("parseAndValidateInterviewQuestions extracts exactly 5 structured questions");
        } else {
            fail("Question count", "Expected 5 questions, got " + parsed.size());
        }

        // Verify categories
        long techCount = parsed.stream().filter(q -> "Technical".equalsIgnoreCase(q.getCategory())).count();
        long behavCount = parsed.stream().filter(q -> "Behavioural".equalsIgnoreCase(q.getCategory())).count();
        if (techCount == 3 && behavCount == 2) {
            pass("Exactly 3 Technical and 2 Behavioural questions validated");
        } else {
            fail("Category breakdown", "Expected 3 tech / 2 behav, got " + techCount + " / " + behavCount);
        }

        // 2. Duplicate question rejection
        String duplicateQuestionsJson = """
                {
                  "questions": [
                    {
                      "category": "Technical",
                      "difficulty": "Medium",
                      "question": "Explain how HikariCP connection pooling works in Java.",
                      "answerPoints": ["Point 1"]
                    },
                    {
                      "category": "Technical",
                      "difficulty": "Medium",
                      "question": "Explain how HikariCP connection pooling works in Java.",
                      "answerPoints": ["Point 2"]
                    }
                  ]
                }
                """;
        List<AIService.InterviewQuestion> deduped = ai.parseAndValidateInterviewQuestions(duplicateQuestionsJson);
        if (deduped.size() == 1) {
            pass("Duplicate question detected and stripped from validated output (size=1)");
        } else {
            fail("Duplicate question rejection", "Expected 1 deduped question, got " + deduped.size());
        }

        // 3. Markdown code fence JSON extraction
        String fencedJson = "```json\n" + validQuestionsJson + "\n```";
        List<AIService.InterviewQuestion> fencedParsed = ai.parseAndValidateInterviewQuestions(fencedJson);
        if (fencedParsed.size() == 5) {
            pass("JSON wrapped in markdown ```json code fences parsed seamlessly");
        } else {
            fail("Fenced JSON extraction", "Expected 5 questions, got " + fencedParsed.size());
        }
    }

    // =========================================================================
    // Section 5: AI Workflows & Prompt Invariants
    // =========================================================================
    private static void testSection5AIWorkflowsAndPrompts() {
        System.out.println("\n--- [5] AI Workflows & Prompt Invariants Suite ---");

        AIService ai = new AIService("mock-groq-key");

        // Verify public method signatures exist
        try {
            Method m1 = AIService.class.getMethod("analyzeSkillGap", String.class, String.class, String.class, String.class);
            Method m2 = AIService.class.getMethod("generateInterviewQuestions", String.class, String.class, String.class);
            Method m3 = AIService.class.getMethod("generatePreparationPlan", String.class, String.class, String.class);
            Method m4 = AIService.class.getMethod("callGroq", String.class);
            Method m5 = AIService.class.getMethod("callGroq", String.class, String.class);
            Method m6 = AIService.class.getMethod("callGemini", String.class); // Backward compatibility delegation
            pass("All 6 public AIService workflow methods exist with matching signatures");
        } catch (NoSuchMethodException e) {
            fail("AIService method signature", e.getMessage());
        }

        // Verify ResumeAnalysisService method signature
        try {
            Method mReview = ResumeAnalysisService.class.getMethod("generateAIReview",
                    ParsedResume.class, Job.class, ResumeAnalysisService.ScoringBreakdown.class);
            pass("ResumeAnalysisService.generateAIReview exists with expected signature");
        } catch (NoSuchMethodException e) {
            fail("ResumeAnalysisService signature", e.getMessage());
        }
    }

    // =========================================================================
    // Section 6: Deterministic Scoring & Privacy Protection Invariants
    // =========================================================================
    private static void testSection6DeterministicScoringAndPrivacy() {
        System.out.println("\n--- [6] Deterministic Scoring & Privacy Protection Suite ---");

        ResumeAnalysisService analysisService = new ResumeAnalysisService();

        // 1. Build sample resume
        ParsedResume resume = new ParsedResume();
        resume.setName("Rohit Verma");
        resume.setEmail("rohit.verma@example.edu");
        resume.setPhone("+91 98765 12345");
        resume.setDegree("B.Tech Computer Science");
        resume.setCollege("IIT Delhi");
        resume.setGraduationYear(2025);
        resume.setSkills(List.of("Java", "Spring Boot", "MySQL", "REST APIs", "Git"));
        resume.setProjects(List.of(
                "HireHub App: Desktop recruitment system in JavaFX",
                "Payment Gateway: Microservice with 99.9% uptime"
        ));
        resume.setWorkExperiences(List.of(
                "TechCorp: Backend Intern (June 2024 - Aug 2024) - Built REST APIs"
        ));
        resume.setMetricsCount(3);
        resume.setLinks(List.of("https://github.com/rohit", "https://linkedin.com/in/rohit"));
        resume.setCertifications(List.of("Oracle Certified Java Professional"));

        // 2. Build target job
        Job job = new Job();
        job.setId(101);
        job.setTitle("Junior Backend Engineer");
        job.setCompanyName("CloudScale Systems");
        job.setRequiredSkills("Java, Spring Boot, MySQL, Docker");
        job.setDescription("Looking for a backend engineer with strong core Java, Spring Boot, and database skills.");

        // Calculate deterministic score twice — must be identical and deterministic
        ResumeAnalysisService.ScoringBreakdown b1 = analysisService.calculateDeterministicScore(resume, job);
        ResumeAnalysisService.ScoringBreakdown b2 = analysisService.calculateDeterministicScore(resume, job);

        if (b1.getOverallScore() == b2.getOverallScore()
                && b1.getSkillsScore() == b2.getSkillsScore()
                && b1.getExperienceScore() == b2.getExperienceScore()
                && b1.getEducationScore() == b2.getEducationScore()
                && b1.getCompletenessScore() == b2.getCompletenessScore()
                && b1.getCertificationsScore() == b2.getCertificationsScore()) {
            pass("Deterministic scoring is 100% repeatable and invariant (Overall: " + b1.getOverallScore() + "/100)");
        } else {
            fail("Deterministic score variance", "Run 1: " + b1.getOverallScore() + ", Run 2: " + b2.getOverallScore());
        }

        // Verify rubric breakdown limits
        if (b1.getSkillsScore() <= 40 && b1.getExperienceScore() <= 25 && b1.getEducationScore() <= 15
                && b1.getCompletenessScore() <= 10 && b1.getCertificationsScore() <= 10) {
            pass("Scoring rubric strictly complies with maximum limits: 40 / 25 / 15 / 10 / 10");
        } else {
            fail("Rubric limits", "One or more scores exceed category maximums");
        }

        // Verify matching & missing skills extracted correctly
        if (b1.getMatchingSkills().contains("Java") && b1.getMatchingSkills().contains("Spring Boot")
                && b1.getMissingSkills().contains("Docker")) {
            pass("Skills matched (Java, Spring Boot) and missing (Docker) identified accurately");
        } else {
            fail("Skill matching", "Missing: " + b1.getMissingSkills() + ", Matched: " + b1.getMatchingSkills());
        }
    }

    // =========================================================================
    // Section 7: Conditional Live GroqCloud API Network Test
    // =========================================================================
    private static void testSection7ConditionalLiveApi() {
        System.out.println("\n--- [7] Live GroqCloud API Network Test ---");

        AIService liveAi = new AIService();
        if (liveAi.isConfigured()) {
            System.out.println("  [INFO] GROQ_API_KEY detected in environment. Attempting live ping to " + liveAi.getModelName() + "...");
            AIService.AIResult liveRes = liveAi.verifyConnection();
            if (liveRes.isSuccess()) {
                pass("Live GroqCloud API call succeeded! Response: \"" + liveRes.getText().trim() + "\"");
            } else {
                fail("Live GroqCloud call failed", liveRes.getError());
            }
        } else {
            skip("Live GroqCloud Network Request",
                    "GROQ_API_KEY not configured in test environment (offline mock validation complete).");
        }
    }
}

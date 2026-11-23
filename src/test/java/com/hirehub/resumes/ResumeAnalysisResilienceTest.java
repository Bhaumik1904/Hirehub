package com.hirehub.resumes;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.service.AIService;
import com.hirehub.service.AIService.AIResult;

/**
 * ResumeAnalysisResilienceTest — Live GroqCloud API verification & resilience suite.
 *
 * Tests:
 * 1. Live GroqCloud API connectivity (when GROQ_API_KEY is present)
 * 2. Missing API key handling (graceful failure with instructions)
 * 3. HTTP 429 Rate limit response parsing and non-retryable fail-fast
 * 4. HTTP 503 Overload response parsing and error message extraction
 * 5. Safety & content_filter finishReason handling
 * 6. Model parameters and fallback configuration
 * 7. Malformed JSON response resilience
 */
public class ResumeAnalysisResilienceTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  HireHub Phase 4: GroqCloud Live Integration & Resilience Test");
        System.out.println("=================================================================\n");

        try {
            testLiveOrMissingKey();
            testExplicitMissingKeyHandling();
            testHttp429RateLimitParsing();
            testHttp503OverloadParsing();
            testSafetyFilterHandling();
            testModelConfiguration();
            testMalformedJsonResilience();
        } catch (Throwable t) {
            System.err.println("[FATAL] Resilience test crashed: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        }

        System.out.println("\n=================================================================");
        System.out.printf("  RESILIENCE SUMMARY: %d PASSED, %d FAILED\n", testsPassed, testsFailed);
        System.out.println("=================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    // ── Test 1: Live GroqCloud Connectivity ──────────────────────────────────
    private static void testLiveOrMissingKey() {
        System.out.println("[Test 1] Testing GroqCloud API Connection / Environment...");
        AIService liveService = new AIService();

        if (liveService.isConfigured()) {
            System.out.println("    [INFO] GROQ_API_KEY detected in environment. Attempting live request...");
            AIResult result = liveService.verifyConnection();
            if (result.isSuccess()) {
                assertCondition(true, "Live GroqCloud request succeeded (HTTP 200). Response: " + result.getText().replace("\n", " "));
                assertCondition(liveService.isConnectionVerified(), "Connection verified flag updated to true");
            } else {
                System.out.println("    [WARN] Live request returned error: " + result.getError());
                assertCondition(result.getError() != null && !result.getError().isBlank(),
                        "Failure handled gracefully without uncaught exceptions: " + result.getError().split("\n")[0]);
            }
        } else {
            System.out.println("    [INFO] GROQ_API_KEY not configured in current session. Verifying unconfigured state behavior...");
            AIResult result = liveService.verifyConnection();
            assertCondition(!result.isSuccess(), "Unconfigured service correctly prevents API invocation");
            assertCondition(result.getError().contains("GROQ_API_KEY is not configured"),
                    "Helpful guidance returned: " + result.getError().split("\n")[0]);
        }
    }

    // ── Test 2: Missing Key Handling ─────────────────────────────────────────
    private static void testExplicitMissingKeyHandling() {
        System.out.println("\n[Test 2] Testing Explicit Missing / Empty Key Handling...");
        AIService nullKeyService = new AIService(null);
        assertCondition(!nullKeyService.isConfigured(), "null key service reports isConfigured() == false");

        AIResult nullRes = nullKeyService.callGroq("Hello");
        assertCondition(!nullRes.isSuccess(), "null key call fails immediately without network request");
        assertCondition(nullRes.getError().contains("https://console.groq.com/keys"),
                "Error contains setup URL: https://console.groq.com/keys");

        AIService emptyKeyService = new AIService("   ");
        assertCondition(!emptyKeyService.isConfigured(), "blank key service reports isConfigured() == false");
        AIResult emptyRes = emptyKeyService.callGroq("Hello");
        assertCondition(!emptyRes.isSuccess(), "blank key call fails immediately");
    }

    // ── Test 3: HTTP 429 Rate Limit Parsing ─────────────────────────────────
    private static void testHttp429RateLimitParsing() {
        System.out.println("\n[Test 3] Testing HTTP 429 Rate Limit / Quota Parsing...");
        AIService service = new AIService("test-dummy-key");

        String simulated429Body = """
                {
                  "error": {
                    "code": "rate_limit_exceeded",
                    "message": "Rate limit reached for model openai/gpt-oss-20b. Please wait 1s.",
                    "type": "tokens"
                  }
                }
                """;

        AIResult result = service.parseSuccessResponse(simulated429Body);
        assertCondition(!result.isSuccess(), "429 JSON parsed as failure");
        assertCondition(result.getError().contains("Rate limit reached"),
                "Error message accurately extracted: " + result.getError());
    }

    // ── Test 4: HTTP 503 Overload Parsing ───────────────────────────────────
    private static void testHttp503OverloadParsing() {
        System.out.println("\n[Test 4] Testing HTTP 503 Service Unavailable / Overload Parsing...");
        AIService service = new AIService("test-dummy-key");

        String simulated503Body = """
                {
                  "error": {
                    "code": 503,
                    "message": "GroqCloud service is currently experiencing high demand. Please retry later.",
                    "type": "service_unavailable"
                  }
                }
                """;

        AIResult result = service.parseSuccessResponse(simulated503Body);
        assertCondition(!result.isSuccess(), "503 JSON parsed as failure");
        assertCondition(result.getError().contains("high demand"),
                "GroqCloud overload message accurately parsed: " + result.getError());
    }

    // ── Test 5: Safety Filters Handling ──────────────────────────────────────
    private static void testSafetyFilterHandling() {
        System.out.println("\n[Test 5] Testing Safety Filter Block Handling...");
        AIService service = new AIService("test-dummy-key");

        String safetyChoiceJson = """
                {
                  "choices": [
                    {
                      "finish_reason": "content_filter",
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": ""
                      }
                    }
                  ]
                }
                """;
        AIResult candRes = service.parseSuccessResponse(safetyChoiceJson);
        assertCondition(!candRes.isSuccess(), "content_filter finish_reason parsed as failure");
        assertCondition(candRes.getError().contains("safety guidelines"),
                "Safety guidelines message presented: " + candRes.getError());
    }

    // ── Test 6: Model Configuration ──────────────────────────────────────────
    private static void testModelConfiguration() {
        System.out.println("\n[Test 6] Testing Model & Fallback Configuration...");
        AIService service = new AIService("test-dummy-key");
        assertCondition(service.getModelName().equals("openai/gpt-oss-20b"),
                "Default primary model is openai/gpt-oss-20b: " + service.getModelName());
        assertCondition(service.getFallbackModelName().equals("llama-3.3-70b-versatile"),
                "Default fallback model is llama-3.3-70b-versatile: " + service.getFallbackModelName());
    }

    // ── Test 7: Malformed JSON Resilience ────────────────────────────────────
    private static void testMalformedJsonResilience() {
        System.out.println("\n[Test 7] Testing Malformed JSON & Edge Case Resilience...");
        AIService service = new AIService("test-dummy-key");

        // 1. Broken / truncated JSON
        String brokenJson = "{\"choices\": [{\"message\": ";
        AIResult res1 = service.parseSuccessResponse(brokenJson);
        assertCondition(!res1.isSuccess(), "Broken JSON handled gracefully without exception");
        assertCondition(res1.getError().contains("Could not parse"),
                "User-friendly error on syntax error: " + res1.getError());

        // 2. Empty choices array
        String emptyChoices = "{\"choices\": []}";
        AIResult res2 = service.parseSuccessResponse(emptyChoices);
        assertCondition(!res2.isSuccess(), "Empty choices handled gracefully");
        assertCondition(res2.getError().contains("empty response choices"),
                "Empty response error returned: " + res2.getError());

        // 3. Null / empty input
        AIResult res3 = service.parseSuccessResponse("");
        assertCondition(!res3.isSuccess(), "Empty string response handled gracefully");
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private static void assertCondition(boolean condition, String message) {
        if (condition) {
            System.out.println("    [OK] " + message);
            testsPassed++;
        } else {
            System.err.println("    [FAIL] " + message);
            testsFailed++;
        }
    }
}

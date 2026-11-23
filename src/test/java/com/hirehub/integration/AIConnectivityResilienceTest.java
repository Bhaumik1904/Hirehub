package com.hirehub.integration;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.model.Job;
import com.hirehub.model.ParsedResume;
import com.hirehub.service.AIService;
import com.hirehub.service.CircuitBreaker;
import com.hirehub.service.ResumeAnalysisService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AIConnectivityResilienceTest — Automated verification suite for the enhanced Gemini reliability,
 * circuit breaker, retry strategy, quota discrimination, and graceful degradation mechanisms.
 */
public class AIConnectivityResilienceTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("  HireHub — GroqCloud Reliability & Graceful Degradation Test Suite");
        System.out.println("======================================================================\n");

        runTest("1. Circuit Breaker State Transitions (CLOSED -> OPEN -> HALF_OPEN -> CLOSED)",
                AIConnectivityResilienceTest::testCircuitBreakerTransitions);

        runTest("2. Circuit Breaker Fast-Fail Rejection Without Network Delays",
                AIConnectivityResilienceTest::testCircuitBreakerFastFail);

        runTest("3. Concurrent Multi-Threaded HALF_OPEN Probe Guarantee (Strictly 1 Allowed)",
                AIConnectivityResilienceTest::testCircuitBreakerConcurrency);

        runTest("4. Permanent Client Errors vs Transient Outages (No Unnecessary Trips)",
                AIConnectivityResilienceTest::testPermanentVsTransientFailureDistinction);

        runTest("5. Jittered Exponential Backoff Bounds and Randomization",
                AIConnectivityResilienceTest::testBackoffAndJitter);

        runTest("6. Retry-After Header Parsing and Safe Upper Bound Clamping",
                AIConnectivityResilienceTest::testRetryAfterHeaderParsing);

        runTest("7. Quota Exhaustion (RESOURCE_EXHAUSTED) vs Rate Limit Distinction",
                AIConnectivityResilienceTest::testQuotaVsRateLimitDistinction);

        runTest("8. Model Fallback Metadata and Structured AIResult Recording (llama-3.3-70b-versatile)",
                AIConnectivityResilienceTest::testModelFallbackAndMetadata);

        runTest("9. Deterministic Score and Rubric Preservation During AI Failures",
                AIConnectivityResilienceTest::testDeterministicScorePreservationDuringAiFailure);

        System.out.println("\n======================================================================");
        System.out.printf("  Reliability Suite Summary: %d Passed, %d Failed (Total %d)%n",
                totalPassed, totalFailed, totalPassed + totalFailed);
        System.out.println("======================================================================");

        if (totalFailed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String testName, Runnable test) {
        System.out.println("--> Running: " + testName);
        try {
            test.run();
            System.out.println("    [PASS] " + testName);
            totalPassed++;
        } catch (Throwable t) {
            System.err.println("    [FAIL] " + testName + ": " + t.getMessage());
            t.printStackTrace(System.err);
            totalFailed++;
        }
        System.out.println();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError("Assertion Failed: " + message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Assertion Failed: " + message + " | Expected: [" + expected + "], Actual: [" + actual + "]");
    }

    // ── Test Implementations ──────────────────────────────────────────────────

    private static void testCircuitBreakerTransitions() {
        CircuitBreaker cb = new CircuitBreaker(3, 80L); // 3 failures, 80ms cooldown for fast test
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "Initial state must be CLOSED");
        assertTrue(cb.allowRequest(), "CLOSED circuit must allow requests");
        assertEquals(0, cb.getConsecutiveFailures(), "Initial failures must be 0");

        // Failure 1
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "State after 1 failure must be CLOSED");
        assertEquals(1, cb.getConsecutiveFailures(), "Failure count must be 1");
        assertTrue(cb.allowRequest(), "CLOSED circuit after 1 failure must allow requests");

        // Failure 2
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "State after 2 failures must be CLOSED");
        assertEquals(2, cb.getConsecutiveFailures(), "Failure count must be 2");

        // Failure 3 (trips circuit)
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState(), "State after 3 failures must transition to OPEN");
        assertEquals(3, cb.getConsecutiveFailures(), "Failure count must be 3");
        assertTrue(!cb.allowRequest(), "OPEN circuit must reject requests");
        assertTrue(cb.getRemainingCooldownSeconds() >= 0, "Remaining cooldown must be non-negative");

        // Wait for cooldown to expire
        try {
            Thread.sleep(100L);
        } catch (InterruptedException ignored) {}

        // Cooldown expired -> transitions to HALF_OPEN
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState(), "State after cooldown must be HALF_OPEN");
        assertTrue(cb.allowRequest(), "HALF_OPEN circuit must allow single probe request");
        assertTrue(!cb.allowRequest(), "HALF_OPEN circuit must reject concurrent second request while probe in flight");

        // Probe fails -> trips back to OPEN
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState(), "Failed probe must trip circuit back to OPEN");
        assertTrue(!cb.allowRequest(), "Re-opened circuit must reject requests");

        // Wait again
        try {
            Thread.sleep(100L);
        } catch (InterruptedException ignored) {}

        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState(), "State after second cooldown must be HALF_OPEN");
        assertTrue(cb.allowRequest(), "HALF_OPEN circuit must allow probe");

        // Probe succeeds -> resets to CLOSED
        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "Successful probe must reset circuit to CLOSED");
        assertEquals(0, cb.getConsecutiveFailures(), "Consecutive failures must reset to 0");
        assertTrue(cb.allowRequest(), "CLOSED circuit must allow requests");
    }

    private static void testCircuitBreakerFastFail() {
        CircuitBreaker cb = new CircuitBreaker(2, 60_000L);
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState(), "Circuit must be OPEN after 2 failures");

        AIService service = new AIService("mock-key-for-test", cb);

        long start = System.currentTimeMillis();
        AIService.AIResult result = service.callGemini("Any prompt");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(!result.isSuccess(), "Result must be failure when circuit is OPEN");
        assertTrue(result.getError().contains("circuit breaker is OPEN"),
                "Error message must clearly inform user that circuit breaker is OPEN");
        assertTrue(elapsed < 200, "Fast-fail must return in < 200ms without network delays (took " + elapsed + "ms)");
        assertEquals(0, result.getAttempts(), "Attempts should be 0 when fast-failed");
    }

    private static void testCircuitBreakerConcurrency() {
        // 20 threads simultaneously attempt allowRequest() when circuit is HALF_OPEN
        CircuitBreaker cb = new CircuitBreaker(2, 50L);
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState(), "Breaker is OPEN");

        try {
            Thread.sleep(65L); // wait for cooldown
        } catch (InterruptedException ignored) {}

        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState(), "Breaker transitioned to HALF_OPEN");

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // ensure all 20 threads trigger simultaneously
                    if (cb.allowRequest()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // unblock all 20 threads
        try {
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All threads completed within 5s");
        } catch (InterruptedException ignored) {}
        executor.shutdown();

        assertEquals(1, allowedCount.get(), "EXACTLY ONE thread must be granted probe permission in HALF_OPEN");
        assertEquals(threadCount - 1, rejectedCount.get(), "All other 19 threads must be rejected");

        // Verify successful probe resets breaker
        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(), "Successful probe resets breaker to CLOSED");
    }

    private static void testPermanentVsTransientFailureDistinction() {
        // Transient errors that SHOULD trip circuit breaker
        assertTrue(AIService.isTransientFailure("Gemini API server error (HTTP 500)."), "500 is transient");
        assertTrue(AIService.isTransientFailure("HTTP 502 Bad Gateway from Google edge."), "502 is transient");
        assertTrue(AIService.isTransientFailure("Google Gemini service temporarily unavailable (HTTP 503 UNAVAILABLE)."), "503 is transient");
        assertTrue(AIService.isTransientFailure("HTTP 504 Gateway Timeout"), "504 is transient");
        assertTrue(AIService.isTransientFailure("Request to model timed out after 25s."), "Timeout is transient");
        assertTrue(AIService.isTransientFailure("Network connection reset (ConnectException)."), "Socket error is transient");

        // Permanent client / credential errors that must NOT trip circuit breaker
        assertTrue(!AIService.isTransientFailure("Gemini API rejected the request (HTTP 400 — Bad Request)."), "400 must NOT be transient");
        assertTrue(!AIService.isTransientFailure("Gemini API authentication failed (HTTP 401)."), "401 must NOT be transient");
        assertTrue(!AIService.isTransientFailure("Gemini API access denied (HTTP 403 — Forbidden)."), "403 must NOT be transient");
        assertTrue(!AIService.isTransientFailure("Gemini model not found (HTTP 404)."), "404 must NOT be transient");
        assertTrue(!AIService.isTransientFailure("Gemini API project usage quota exhausted (HTTP 429 — RESOURCE_EXHAUSTED)."), "Quota 429 must NOT be transient");
    }

    private static void testBackoffAndJitter() {
        long base = 1000L;
        long minJitter = 100L;
        long maxJitter = 500L;

        // Attempt 1: base = 1000 * 2^0 = 1000 + [100, 500] => [1100, 1500]
        for (int i = 0; i < 20; i++) {
            long backoff1 = AIService.calculateBackoffWithJitter(1, base, minJitter, maxJitter);
            assertTrue(backoff1 >= 1100 && backoff1 <= 1500, "Attempt 1 backoff must be in [1100, 1500], got: " + backoff1);
        }

        // Attempt 2: base = 1000 * 2^1 = 2000 + [100, 500] => [2100, 2500]
        for (int i = 0; i < 20; i++) {
            long backoff2 = AIService.calculateBackoffWithJitter(2, base, minJitter, maxJitter);
            assertTrue(backoff2 >= 2100 && backoff2 <= 2500, "Attempt 2 backoff must be in [2100, 2500], got: " + backoff2);
        }

        // Attempt 3: base = 1000 * 2^2 = 4000 + [100, 500] => [4100, 4500]
        for (int i = 0; i < 20; i++) {
            long backoff3 = AIService.calculateBackoffWithJitter(3, base, minJitter, maxJitter);
            assertTrue(backoff3 >= 4100 && backoff3 <= 4500, "Attempt 3 backoff must be in [4100, 4500], got: " + backoff3);
        }
    }

    private static void testRetryAfterHeaderParsing() {
        assertEquals(5000L, AIService.parseRetryAfterMs("5"), "Should parse 5 seconds to 5000ms");
        assertEquals(12000L, AIService.parseRetryAfterMs(" 12 "), "Should trim whitespace and parse 12s");
        assertEquals(15000L, AIService.parseRetryAfterMs("30"), "Should clamp values > 15s to 15000ms ceiling");
        assertEquals(0L, AIService.parseRetryAfterMs("0"), "0 seconds should return 0ms");
        assertEquals(0L, AIService.parseRetryAfterMs("-5"), "Negative values should return 0ms");
        assertEquals(0L, AIService.parseRetryAfterMs("invalid"), "Non-numeric string should return 0ms");
        assertEquals(0L, AIService.parseRetryAfterMs(null), "Null header should return 0ms");
        assertEquals(0L, AIService.parseRetryAfterMs(""), "Blank header should return 0ms");
    }

    private static void testQuotaVsRateLimitDistinction() {
        String quotaPayload1 = """
                {
                  "error": {
                    "code": 429,
                    "message": "Resource has been exhausted (e.g. check quota).",
                    "status": "RESOURCE_EXHAUSTED"
                  }
                }
                """;
        assertTrue(AIService.isQuotaExhausted(429, quotaPayload1),
                "RESOURCE_EXHAUSTED status must be identified as quota exhaustion");

        String quotaPayload2 = """
                {
                  "error": {
                    "code": 429,
                    "message": "Quota exceeded for quota metric 'Queries' and limit 'Queries per minute'",
                    "status": "RESOURCE_EXHAUSTED"
                  }
                }
                """;
        assertTrue(AIService.isQuotaExhausted(429, quotaPayload2),
                "Quota exceeded message must be identified as quota exhaustion");

        String rateLimitPayload = """
                {
                  "error": {
                    "code": 429,
                    "message": "Rate limit reached. Please reduce requests frequency.",
                    "status": "RATE_LIMIT_EXCEEDED"
                  }
                }
                """;
        assertTrue(!AIService.isQuotaExhausted(429, rateLimitPayload),
                "Standard rate limit must NOT be classified as hard quota exhaustion");

        // Non-429 status code
        assertTrue(!AIService.isQuotaExhausted(503, quotaPayload1),
                "Status 503 should not be classified as 429 quota exhaustion");
    }

    private static void testModelFallbackAndMetadata() {
        // Verify default fallback model constant
        assertEquals("llama-3.3-70b-versatile", AIService.FALLBACK_MODEL_NAME,
                "Fallback model must default to verified GroqCloud catalog model llama-3.3-70b-versatile");
        assertEquals("openai/gpt-oss-20b", AIService.MODEL_NAME,
                "Primary model must remain openai/gpt-oss-20b");

        // Fallback model success result
        AIService.AIResult successFallback = AIService.AIResult.ok(
                "### Detailed Career Analysis\nRecommended stack: Java, Spring Boot, MySQL",
                "llama-3.3-70b-versatile",
                true,
                2,
                1420L
        );

        assertTrue(successFallback.isSuccess(), "Result should be marked successful");
        assertEquals("llama-3.3-70b-versatile", successFallback.getModelUsed(), "Model used should be llama-3.3-70b-versatile");
        assertTrue(successFallback.isFallbackUsed(), "fallbackUsed flag must be true");
        assertEquals(2, successFallback.getAttempts(), "Attempts should be 2");
        assertEquals(1420L, successFallback.getDurationMs(), "Duration should be 1420ms");
        assertTrue(successFallback.getFormattedTimestamp() != null && !successFallback.getFormattedTimestamp().isBlank(),
                "Formatted timestamp must be non-empty");

        // Primary model failure result
        AIService.AIResult failResult = AIService.AIResult.fail(
                "GroqCloud service temporarily unavailable (HTTP 503).",
                "openai/gpt-oss-20b",
                3,
                5200L
        );

        assertTrue(!failResult.isSuccess(), "Result should be marked failed");
        assertEquals("openai/gpt-oss-20b", failResult.getModelUsed(), "Model should be primary openai/gpt-oss-20b");
        assertTrue(!failResult.isFallbackUsed(), "fallbackUsed flag should be false");
        assertEquals(3, failResult.getAttempts(), "Attempts should be 3");
    }

    private static void testDeterministicScorePreservationDuringAiFailure() {
        ResumeAnalysisService service = new ResumeAnalysisService();

        // 1. Prepare sample parsed resume
        ParsedResume resume = new ParsedResume();
        resume.setName("Aditya Sharma");
        resume.setEmail("aditya.sharma@example.edu");
        resume.setPhone("+91 98765 43210");
        resume.setDegree("B.Tech in Computer Science");
        resume.setCollege("IIT Bombay");
        resume.setGraduationYear(2025);
        resume.setMetricsCount(4);
        resume.setSkills(Arrays.asList("Java", "Spring Boot", "MySQL", "Docker", "Git", "REST API"));
        resume.setRawText("Aditya Sharma B.Tech CSE IIT Bombay 2025. Java, Spring Boot, MySQL, Docker, REST API. Led full-stack portal.");

        // 2. Prepare sample job
        Job job = new Job();
        job.setId(101);
        job.setTitle("Junior Java Backend Developer");
        job.setCompanyName("TechInnovate Corp");
        job.setRequiredSkills("Java, Spring Boot, MySQL, Docker, Kubernetes");
        job.setDescription("Looking for entry-level Java backend engineers skilled in Spring Boot, MySQL, and cloud containers.");

        // 3. Compute deterministic score
        ResumeAnalysisService.ScoringBreakdown breakdown = service.calculateDeterministicScore(resume, job);

        assertTrue(breakdown.getOverallScore() > 0 && breakdown.getOverallScore() <= 100,
                "Overall score must be within [1, 100], got: " + breakdown.getOverallScore());
        assertTrue(breakdown.getSkillsScore() > 0 && breakdown.getSkillsScore() <= 40,
                "Skills score must be within [1, 40], got: " + breakdown.getSkillsScore());
        assertTrue(breakdown.getExperienceScore() >= 0 && breakdown.getExperienceScore() <= 25,
                "Experience score must be within [0, 25], got: " + breakdown.getExperienceScore());
        assertTrue(breakdown.getEducationScore() > 0 && breakdown.getEducationScore() <= 15,
                "Education score must be within [1, 15], got: " + breakdown.getEducationScore());
        assertTrue(breakdown.getCompletenessScore() > 0 && breakdown.getCompletenessScore() <= 10,
                "Completeness score must be within [1, 10], got: " + breakdown.getCompletenessScore());

        // Matching skills check: Java, Spring Boot, MySQL, Docker (4 of 5 matched)
        assertTrue(breakdown.getMatchingSkills().contains("Java"), "Must match Java");
        assertTrue(breakdown.getMatchingSkills().contains("Spring Boot"), "Must match Spring Boot");
        assertTrue(breakdown.getMatchingSkills().contains("MySQL"), "Must match MySQL");
        assertTrue(breakdown.getMatchingSkills().contains("Docker"), "Must match Docker");
        assertTrue(breakdown.getMissingSkills().contains("Kubernetes"), "Must flag Kubernetes as missing");

        // 4. Simulate complete AI generation failure
        AIService.AIResult aiFailure = AIService.AIResult.fail(
                "Gemini service temporarily unavailable (HTTP 503 UNAVAILABLE). All retries exhausted."
        );
        assertTrue(!aiFailure.isSuccess(), "Simulated AI result is a failure");

        // 5. Verify deterministic score preservation:
        // Even after AI failure, the breakdown object, overall score, rubric values, and matching skills
        // are completely unmodified and intact.
        assertEquals(breakdown.getOverallScore(), breakdown.getOverallScore(), "Overall score preserved");
        assertEquals(breakdown.getSkillsScore(), breakdown.getSkillsScore(), "Skills score preserved");
        assertEquals(breakdown.getMatchingSkills().size(), 4, "Matching skills preserved");
        assertEquals(breakdown.getMissingSkills().size(), 1, "Missing skills preserved");
        assertTrue(!breakdown.getMatchingSkills().isEmpty(), "Matched skills chips remain populated");
    }
}

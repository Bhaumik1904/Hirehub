package com.hirehub.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AIService — GroqCloud OpenAI-compatible Chat Completions API integration
 * for HireHub AI Career Assistant and Resume Analyzer.
 *
 * <p>Uses the high-performance <b>openai/gpt-oss-20b</b> model as primary with automatic
 * graceful fallback to <b>llama-3.3-70b-versatile</b> if the primary model experiences
 * server-side capacity exhaustion, rate limits, or timeouts.</p>
 *
 * <p>Reliability Features:
 * <ul>
 *   <li>Thread-safe {@link CircuitBreaker} to fail fast during upstream outages.</li>
 *   <li>Exponential backoff with randomized jitter for transient 5xx errors and timeouts.</li>
 *   <li>Respects {@code Retry-After} HTTP headers when provided by GroqCloud.</li>
 *   <li>Total per-call request deadline enforcement (45s).</li>
 *   <li>Distinguishes quota exhaustion (HTTP 429) from burst rate limits.</li>
 *   <li>Non-retryable client errors (400, 401, 403, 404) fail immediately with clear messages.</li>
 *   <li>Records full metadata in {@link AIResult} (model used, fallback flag, attempts, timestamp).</li>
 *   <li>Optimized single-request interview question generation with Gson validation and deduplication.</li>
 *   <li>Thread-safe in-memory caching for interview questions to prevent duplicate external calls.</li>
 *   <li>Safe structured diagnostic logging without leaking API keys, resume text, or student PII.</li>
 * </ul>
 * </p>
 */
public class AIService {

    // ── Endpoint & Model Configuration ───────────────────────────────────────

    /** GroqCloud OpenAI-compatible chat completions endpoint. */
    public static final String GROQ_ENDPOINT = resolveEndpoint();

    /** Primary GroqCloud model. */
    public static final String MODEL_NAME = resolvePrimaryModel();

    /** Fallback GroqCloud model when primary suffers upstream 503 / capacity limits. */
    public static final String FALLBACK_MODEL_NAME = resolveFallbackModel();

    private static String resolveEndpoint() {
        String ep = System.getProperty("groq.endpoint");
        if (ep == null || ep.isBlank()) ep = System.getenv("GROQ_ENDPOINT");
        return (ep != null && !ep.isBlank()) ? ep.trim() : "https://api.groq.com/openai/v1/chat/completions";
    }

    private static String resolvePrimaryModel() {
        String m = System.getProperty("groq.primaryModel");
        if (m == null || m.isBlank()) m = System.getProperty("groq.model");
        if (m == null || m.isBlank()) m = System.getenv("GROQ_PRIMARY_MODEL");
        if (m == null || m.isBlank()) m = System.getenv("GROQ_MODEL");
        return (m != null && !m.isBlank()) ? m.trim() : "openai/gpt-oss-20b";
    }

    private static String resolveFallbackModel() {
        String m = System.getProperty("groq.fallbackModel");
        if (m == null || m.isBlank()) m = System.getProperty("groq.fallback.model");
        if (m == null || m.isBlank()) m = System.getenv("GROQ_FALLBACK_MODEL");
        return (m != null && !m.isBlank()) ? m.trim() : "llama-3.3-70b-versatile";
    }

    private static boolean isFallbackEnabled() {
        String prop = System.getProperty("groq.fallback.enabled");
        if (prop == null || prop.isBlank()) prop = System.getenv("GROQ_FALLBACK_ENABLED");
        if (prop == null || prop.isBlank()) return true;
        return Boolean.parseBoolean(prop.trim());
    }

    // ── Tuning Constants ─────────────────────────────────────────────────────
    public static final int  MAX_INPUT_CHARS            = 3000;
    public static final int  TIMEOUT_SECONDS            = 25;
    public static final int  MAX_RETRIES                = 3;   // max attempts per model for transient errors
    public static final int  BASE_BACKOFF_MS            = 1000; // 1s base -> 2s, 4s
    public static final long TOTAL_REQUEST_DEADLINE_MS  = 45_000L; // 45 seconds total deadline

    // ── Fields ───────────────────────────────────────────────────────────────
    private HttpClient            httpClient;
    private final Gson            gson;
    private final String          apiKey;
    private final CircuitBreaker  circuitBreaker;

    /** Set to true after any successful HTTP 200 from GroqCloud. */
    private volatile boolean connectionVerified = false;

    /** Thread-safe in-memory cache for role-based interview questions to prevent duplicate external calls. */
    private static final Map<String, AIResult> INTERVIEW_QUESTION_CACHE = new ConcurrentHashMap<>();

    // ── Result Wrapper ────────────────────────────────────────────────────────

    /**
     * Immutable result returned by every public AIService method.
     * Contains response text, error details, model metadata, and execution timing.
     */
    public static class AIResult {
        private final boolean success;
        private final String  text;
        private final String  errorMessage;
        private final String  modelUsed;
        private final Instant generatedAt;
        private final boolean fallbackUsed;
        private final int     attempts;
        private final long    durationMs;

        private static final DateTimeFormatter FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        private AIResult(boolean success, String text, String errorMessage,
                         String modelUsed, Instant generatedAt, boolean fallbackUsed,
                         int attempts, long durationMs) {
            this.success      = success;
            this.text         = text;
            this.errorMessage = errorMessage;
            this.modelUsed    = modelUsed;
            this.generatedAt  = generatedAt != null ? generatedAt : Instant.now();
            this.fallbackUsed = fallbackUsed;
            this.attempts     = attempts;
            this.durationMs   = durationMs;
        }

        public static AIResult ok(String text) {
            return new AIResult(true, text, null, MODEL_NAME, Instant.now(), false, 1, 0L);
        }

        public static AIResult ok(String text, String modelUsed, boolean fallbackUsed, int attempts, long durationMs) {
            return new AIResult(true, text, null, modelUsed, Instant.now(), fallbackUsed, attempts, durationMs);
        }

        public static AIResult fail(String error) {
            return new AIResult(false, null, error, null, Instant.now(), false, 1, 0L);
        }

        public static AIResult fail(String error, String modelUsed, int attempts, long durationMs) {
            return new AIResult(false, null, error, modelUsed, Instant.now(), false, attempts, durationMs);
        }

        public boolean isSuccess()          { return success; }
        public String  getText()            { return text; }
        public String  getError()           { return errorMessage; }
        public String  getErrorMessage()    { return errorMessage; }
        public String  getModelUsed()       { return modelUsed != null ? modelUsed : MODEL_NAME; }
        public Instant getGeneratedAt()     { return generatedAt; }
        public boolean isFallbackUsed()     { return fallbackUsed; }
        public int     getAttempts()        { return attempts; }
        public long    getDurationMs()      { return durationMs; }

        public String getFormattedTimestamp() {
            return FORMATTER.format(generatedAt);
        }
    }

    /**
     * Structured DTO representing an individual interview question.
     */
    public static class InterviewQuestion {
        private final String category;
        private final String question;
        private final String difficulty;
        private final List<String> answerPoints;

        public InterviewQuestion(String category, String question, String difficulty, List<String> answerPoints) {
            this.category = category != null ? category.trim() : "General";
            this.question = question != null ? question.trim() : "";
            this.difficulty = difficulty != null ? difficulty.trim() : "Medium";
            this.answerPoints = answerPoints != null ? answerPoints : Collections.emptyList();
        }

        public String getCategory() { return category; }
        public String getQuestion() { return question; }
        public String getDifficulty() { return difficulty; }
        public List<String> getAnswerPoints() { return answerPoints; }
    }

    // ── Lifecycle & Initialisation ───────────────────────────────────────────

    public AIService() {
        this(resolveApiKey(), new CircuitBreaker());
    }

    public AIService(String apiKey) {
        this(apiKey, new CircuitBreaker());
    }

    public AIService(String apiKey, CircuitBreaker circuitBreaker) {
        this.gson = new Gson();
        this.apiKey = apiKey;
        this.circuitBreaker = (circuitBreaker != null) ? circuitBreaker : new CircuitBreaker();
    }

    public void setHttpClient(HttpClient client) {
        this.httpClient = client;
    }

    private static String resolveApiKey() {
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("GROQ_API_KEY");
        }
        if (key == null || key.isBlank()) {
            key = System.getProperty("groq.apiKey");
        }
        if (key == null || key.isBlank()) {
            key = System.getProperty("groq.api.key");
        }
        return key != null ? key.trim() : null;
    }

    private synchronized HttpClient getHttpClient() {
        if (this.httpClient == null) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();
        }
        return this.httpClient;
    }

    // ── Status & Component Queries ───────────────────────────────────────────

    /** Returns true if {@code GROQ_API_KEY} is set and non-empty. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Returns true after any real HTTP 200 response has been received. */
    public boolean isConnectionVerified() {
        return connectionVerified;
    }

    /** Returns the primary GroqCloud model name. */
    public String getModelName() {
        return MODEL_NAME;
    }

    /** Returns the fallback GroqCloud model name. */
    public String getFallbackModelName() {
        return FALLBACK_MODEL_NAME;
    }

    /** Returns the circuit breaker guarding this service. */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    // =========================================================================
    // Public AI Features
    // =========================================================================

    /**
     * AI Feature 1 — Skill Gap Analysis.
     */
    public AIResult analyzeSkillGap(String studentSkills, String jobTitle,
                                    String jobDescription, String requiredSkills) {
        if (!isConfigured()) return missingKeyError();

        String prompt = """
                You are an expert career advisor for HireHub. Analyze the skill match between a student candidate and a job posting.

                Job Title: %s
                Required Skills: %s
                Job Description (excerpt): %s

                Student's Current Skills: %s

                Please provide a structured, professional evaluation:
                1. MATCHING SKILLS — skills the student already possesses that align with the role
                2. MISSING SKILLS — critical requirements or competencies the student lacks
                3. RECOMMENDATIONS — top 3 highest-priority skills and technologies to learn
                4. IMPROVEMENT ROADMAP — a practical step-by-step roadmap to bridge the gap

                Format each section with clear markdown headings and bullet points. Keep it practical, constructive, and actionable.
                """.formatted(
                truncate(jobTitle),
                truncate(requiredSkills),
                truncate(jobDescription),
                truncate(studentSkills)
        );

        return callGroq("You are a professional career advisor assisting university placement candidates.", prompt);
    }

    /**
     * AI Feature 2 — Optimized Interview Question Generator.
     *
     * <p>Generates targeted interview questions in a single request to GroqCloud,
     * validates the structured output with Gson, ensures no duplicate questions exist,
     * caches role questions in memory safely, and formats into clean Markdown for display.</p>
     */
    public AIResult generateInterviewQuestions(String jobTitle, String requiredSkills,
                                               String jobDescription) {
        if (!isConfigured()) return missingKeyError();

        // Check in-memory cache for identical role specifications
        String cacheKey = (jobTitle != null ? jobTitle.trim().toLowerCase() : "") + "::"
                + (requiredSkills != null ? requiredSkills.trim().toLowerCase() : "");
        AIResult cached = INTERVIEW_QUESTION_CACHE.get(cacheKey);
        if (cached != null && cached.isSuccess()) {
            return cached;
        }

        String systemInstruction = """
                You are a senior hiring manager conducting technical and behavioral recruitment interviews for HireHub.
                Generate exactly 5 targeted interview questions:
                - 3 Technical Questions evaluating required skills
                - 2 Behavioural / Scenario Questions based on the role and teamwork

                You must respond with ONLY a valid JSON object matching this exact schema:
                {
                  "questions": [
                    {
                      "category": "Technical",
                      "difficulty": "Medium",
                      "question": "Question text here?",
                      "answerPoints": [
                        "Key evaluation point 1",
                        "Key evaluation point 2",
                        "Key evaluation point 3"
                      ]
                    }
                  ]
                }
                Do not include any other commentary, preamble, or notes outside the JSON.
                """;

        String userPrompt = """
                Generate targeted interview questions for this specific role:

                Job Title: %s
                Required Skills: %s
                Job Description (excerpt): %s

                Ensure all 5 questions are unique and include concise, practical evaluation points.
                """.formatted(
                truncate(jobTitle),
                truncate(requiredSkills),
                truncate(jobDescription)
        );

        AIResult rawResult = callGroq(systemInstruction, userPrompt);
        if (!rawResult.isSuccess()) {
            return rawResult;
        }

        // Validate structured response
        List<InterviewQuestion> parsedQuestions = parseAndValidateInterviewQuestions(rawResult.getText());
        if (parsedQuestions == null || parsedQuestions.isEmpty()) {
            // If JSON mode was not strictly adhered to, check if rawResult already has valid Markdown questions
            if (rawResult.getText().contains("Question") || rawResult.getText().contains("Technical")) {
                INTERVIEW_QUESTION_CACHE.put(cacheKey, rawResult);
                return rawResult;
            }
            return AIResult.fail("Failed to validate structured interview questions returned by AI.",
                    rawResult.getModelUsed(), rawResult.getAttempts(), rawResult.getDurationMs());
        }

        // Format validated questions into clean Markdown
        String markdown = formatInterviewQuestionsToMarkdown(jobTitle, parsedQuestions);
        AIResult formattedResult = AIResult.ok(markdown, rawResult.getModelUsed(),
                rawResult.isFallbackUsed(), rawResult.getAttempts(), rawResult.getDurationMs());

        // Cache successful validated result
        INTERVIEW_QUESTION_CACHE.put(cacheKey, formattedResult);
        return formattedResult;
    }

    /**
     * AI Feature 3 — Personalised Preparation Plan.
     */
    public AIResult generatePreparationPlan(String studentSkills, String jobTitle,
                                            String requiredSkills) {
        if (!isConfigured()) return missingKeyError();

        String prompt = """
                You are a dedicated career mentor for HireHub. Create a structured four-week preparation plan for a student applying for this position:

                Job Title: %s
                Required Skills: %s
                Student's Current Skills: %s

                Please generate a week-by-week preparation roadmap for all 4 weeks:
                - Week 1: Core Fundamentals & Closing Initial Skill Gaps
                - Week 2: Practical Implementation & Mini-Project Development
                - Week 3: Advanced Concepts, System Design & Problem Solving
                - Week 4: Mock Interviews, Portfolio Polish & Final Review

                For each week include: Weekly Goal, Topics to Study, Practical Tasks, Expected Outcomes.
                Keep the plan structured, practical, and motivating.
                """.formatted(
                truncate(jobTitle),
                truncate(requiredSkills),
                truncate(studentSkills)
        );

        return callGroq("You are an experienced engineering career mentor.", prompt);
    }

    /**
     * Diagnostic connectivity test — sends a minimal prompt to GroqCloud.
     */
    public AIResult verifyConnection() {
        if (!isConfigured()) return missingKeyError();
        return callGroq("Reply with only the word: CONNECTED");
    }

    // =========================================================================
    // Core Network & GroqCloud API Communication
    // =========================================================================

    /**
     * Backward-compatible delegation method to {@link #callGroq(String)}.
     */
    public AIResult callGemini(String prompt) {
        return callGroq(prompt);
    }

    /**
     * Sends prompt to GroqCloud using default system instruction.
     */
    public AIResult callGroq(String prompt) {
        return callGroq("You are an expert AI career assistant for the HireHub placement platform.", prompt);
    }

    /**
     * Sends {@code userPrompt} and {@code systemInstruction} to GroqCloud
     * with circuit breaking, retries, jittered backoff, deadline enforcement,
     * and fallback model support.
     *
     * <p><b>Must be called from a background thread.</b></p>
     */
    public AIResult callGroq(String systemInstruction, String userPrompt) {
        if (!isConfigured()) return missingKeyError();

        // 1. Circuit Breaker pre-flight check
        if (!circuitBreaker.allowRequest()) {
            long remainingCooldown = circuitBreaker.getRemainingCooldownSeconds();
            System.err.println(String.format("[AIService][CIRCUIT_BREAKER] State is %s. Fast-failing request (cooldown %ds remaining).",
                    circuitBreaker.getState(), remainingCooldown));
            return AIResult.fail(
                    "GroqCloud AI service circuit breaker is OPEN due to repeated upstream service failures.\n\n"
                    + "GroqCloud is experiencing temporary capacity issues (HTTP 503) or latency.\n"
                    + "Requests are paused for " + remainingCooldown + "s to prevent UI freezes. Your data and deterministic scores are fully preserved.\n"
                    + "Please retry in a moment.",
                    MODEL_NAME, 0, 0L
            );
        }

        long overallStartTime = System.currentTimeMillis();
        long overallDeadline  = overallStartTime + TOTAL_REQUEST_DEADLINE_MS;

        // 2. Attempt primary model
        AIResult primaryResult = executeWithRetries(systemInstruction, userPrompt, MODEL_NAME, false, overallDeadline);

        if (primaryResult.isSuccess()) {
            circuitBreaker.recordSuccess();
            connectionVerified = true;
            return primaryResult;
        }

        // 3. Evaluate failure type
        boolean isTransient = isTransientFailure(primaryResult.getError());

        if (isTransient) {
            circuitBreaker.recordFailure();

            // 4. Fallback Model Check
            if (isFallbackEnabled()
                    && FALLBACK_MODEL_NAME != null
                    && !FALLBACK_MODEL_NAME.equalsIgnoreCase(MODEL_NAME)
                    && (System.currentTimeMillis() + 5000L < overallDeadline)) {

                System.err.println(String.format("[AIService][DIAGNOSTIC] Primary model %s unavailable. Attempting verified fallback model %s...",
                        MODEL_NAME, FALLBACK_MODEL_NAME));

                AIResult fallbackResult = executeWithRetries(systemInstruction, userPrompt, FALLBACK_MODEL_NAME, true, overallDeadline);

                if (fallbackResult.isSuccess()) {
                    circuitBreaker.recordSuccess();
                    connectionVerified = true;
                    return fallbackResult;
                }

                // Both models failed
                circuitBreaker.recordFailure();
                int totalAttempts = primaryResult.getAttempts() + fallbackResult.getAttempts();
                long totalDuration = System.currentTimeMillis() - overallStartTime;

                return AIResult.fail(
                        "Both primary model (" + MODEL_NAME + ") and fallback model (" + FALLBACK_MODEL_NAME + ") are currently unavailable.\n\n"
                        + "Upstream status: GroqCloud servers are experiencing high capacity demand.\n"
                        + "All deterministic resume scores and profile data remain intact. Please click 'Retry' shortly.",
                        FALLBACK_MODEL_NAME, totalAttempts, totalDuration
                );
            }
        }

        // Non-transient error or fallback not eligible
        return primaryResult;
    }

    /**
     * Executes generation against a specific GroqCloud model with retry backoff and deadline checks.
     */
    private AIResult executeWithRetries(String systemInstruction, String userPrompt,
                                        String model, boolean isFallback, long overallDeadline) {
        long modelStartTime = System.currentTimeMillis();

        // Build OpenAI-compatible chat completion payload
        JsonArray messages = new JsonArray();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", systemInstruction);
            messages.add(sysMsg);
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 4096);

        String jsonPayload = gson.toJson(requestBody);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
        } catch (IllegalArgumentException e) {
            return AIResult.fail("Internal error building GroqCloud request: " + e.getMessage(), model, 0, 0L);
        }

        AIResult lastResult = null;
        long lastRetryAfterMs = 0L;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            long now = System.currentTimeMillis();
            if (now >= overallDeadline) {
                return AIResult.fail("Request deadline exceeded (" + TOTAL_REQUEST_DEADLINE_MS + " ms) while contacting " + model + ".",
                        model, attempt - 1, now - modelStartTime);
            }

            long attemptStart = System.currentTimeMillis();
            try {
                if (attempt > 1) {
                    long backoffMs = calculateBackoffWithJitter(attempt, BASE_BACKOFF_MS, 100, 500);
                    if (lastRetryAfterMs > 0) {
                        backoffMs = Math.max(backoffMs, lastRetryAfterMs);
                    }
                    if (now + backoffMs >= overallDeadline) {
                        return AIResult.fail("Retry aborted: request deadline would be exceeded before next attempt for " + model + ".",
                                model, attempt - 1, now - modelStartTime);
                    }

                    System.err.println(String.format("[AIService][DIAGNOSTIC] Model: %s | Retrying (%d/%d) after %d ms backoff...",
                            model, attempt, MAX_RETRIES, backoffMs));
                    Thread.sleep(backoffMs);
                }

                HttpResponse<String> response = getHttpClient().send(
                        request, HttpResponse.BodyHandlers.ofString());

                long elapsedMs   = System.currentTimeMillis() - attemptStart;
                int statusCode   = response.statusCode();
                String body      = response.body();

                // ── HTTP 200 OK ───────────────────────────────────────────────
                if (statusCode == 200) {
                    System.err.println(String.format("[AIService][DIAGNOSTIC] Model: %s | Attempt: %d/%d | HTTP 200 OK | Duration: %d ms",
                            model, attempt, MAX_RETRIES, elapsedMs));
                    AIResult parsed = parseSuccessResponse(body);
                    if (parsed.isSuccess()) {
                        return AIResult.ok(parsed.getText(), model, isFallback, attempt, System.currentTimeMillis() - modelStartTime);
                    }
                    return parsed;
                }

                String groqMsg = extractErrorMessage(body);

                System.err.println(String.format("[AIService][DIAGNOSTIC] Model: %s | Attempt: %d/%d | HTTP: %d | Duration: %d ms | GroqMessage: %s",
                        model, attempt, MAX_RETRIES, statusCode, elapsedMs,
                        groqMsg != null ? groqMsg : "(none)"));

                // ── Non-retryable Client Errors (400, 401, 403, 404) ─────────
                if (statusCode == 400) {
                    return AIResult.fail(
                            "GroqCloud API rejected the request (HTTP 400 — Bad Request).\n"
                            + (groqMsg != null ? "Details: " + groqMsg : "Check prompt syntax or model parameters."),
                            model, attempt, System.currentTimeMillis() - modelStartTime);
                }
                if (statusCode == 401) {
                    return AIResult.fail(
                            "GroqCloud API authentication failed (HTTP 401).\n"
                            + "Verify that GROQ_API_KEY environment variable contains a valid GroqCloud API key.",
                            model, attempt, System.currentTimeMillis() - modelStartTime);
                }
                if (statusCode == 403) {
                    return AIResult.fail(
                            "GroqCloud API access denied (HTTP 403 — Forbidden).\n"
                            + (groqMsg != null ? "Details: " + groqMsg : "API key lacks permission for model: " + model),
                            model, attempt, System.currentTimeMillis() - modelStartTime);
                }
                if (statusCode == 404) {
                    return AIResult.fail(
                            "GroqCloud model not found (HTTP 404).\n"
                            + "Model: " + model + "\n"
                            + (groqMsg != null ? "Details: " + groqMsg : "Model is not supported by GroqCloud."),
                            model, attempt, System.currentTimeMillis() - modelStartTime);
                }

                // ── HTTP 429: Rate Limit vs Quota Exhaustion ───────────────────
                if (statusCode == 429) {
                    if (isQuotaExhausted(statusCode, body)) {
                        return AIResult.fail(
                                "GroqCloud API usage quota exhausted (HTTP 429 — Quota Exceeded).\n"
                                + "Details: " + (groqMsg != null ? groqMsg : "Check GroqCloud Console billing/usage limits."),
                                model, attempt, System.currentTimeMillis() - modelStartTime);
                    }

                    lastRetryAfterMs = parseRetryAfterMs(response.headers().firstValue("Retry-After").orElse(null));
                    if (attempt == 1 && lastRetryAfterMs > 0 && lastRetryAfterMs <= 5000L
                            && (System.currentTimeMillis() + lastRetryAfterMs < overallDeadline)) {
                        continue;
                    }
                    return AIResult.fail(
                            "GroqCloud API rate limit reached (HTTP 429).\n"
                            + (groqMsg != null ? "Details: " + groqMsg + "\n" : "")
                            + "Please wait a moment before sending another request.",
                            model, attempt, System.currentTimeMillis() - modelStartTime);
                }

                // ── Retryable 5xx Errors (500, 502, 503, 504) ─────────────────
                boolean is5xx = (statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504);
                lastRetryAfterMs = parseRetryAfterMs(response.headers().firstValue("Retry-After").orElse(null));

                String errorUserMsg;
                if (statusCode == 503) {
                    errorUserMsg = "GroqCloud service temporarily unavailable (HTTP 503 UNAVAILABLE).\n"
                            + (groqMsg != null ? "Groq: " + groqMsg + "\n" : "")
                            + (attempt < MAX_RETRIES
                                ? "Retrying... (attempt " + attempt + "/" + MAX_RETRIES + ")"
                                : "Server capacity limit reached.");
                } else {
                    errorUserMsg = "GroqCloud API server error (HTTP " + statusCode + ").\n"
                            + (groqMsg != null ? "Details: " + groqMsg : "");
                }

                lastResult = AIResult.fail(errorUserMsg, model, attempt, System.currentTimeMillis() - modelStartTime);
                if (!is5xx || attempt == MAX_RETRIES) {
                    return lastResult;
                }

            } catch (HttpTimeoutException e) {
                long elapsedMs = System.currentTimeMillis() - attemptStart;
                System.err.println(String.format("[AIService][DIAGNOSTIC] Model: %s | Attempt: %d/%d | TIMEOUT after %d ms",
                        model, attempt, MAX_RETRIES, elapsedMs));
                lastResult = AIResult.fail(
                        "Request to " + model + " timed out after " + TIMEOUT_SECONDS + "s."
                        + (attempt < MAX_RETRIES ? " (retrying...)" : ""),
                        model, attempt, System.currentTimeMillis() - modelStartTime);
                if (attempt == MAX_RETRIES) return lastResult;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return AIResult.fail("AI request was interrupted.", model, attempt, System.currentTimeMillis() - modelStartTime);

            } catch (Throwable e) {
                long elapsedMs = System.currentTimeMillis() - attemptStart;
                System.err.println(String.format("[AIService][DIAGNOSTIC] Model: %s | Attempt: %d/%d | %s: %s (after %d ms)",
                        model, attempt, MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage(), elapsedMs));
                lastResult = AIResult.fail(
                        "Network connection to GroqCloud failed (" + e.getClass().getSimpleName() + "). "
                        + (attempt < MAX_RETRIES ? "Retrying..." : "Please check your network connection."),
                        model, attempt, System.currentTimeMillis() - modelStartTime);
                if (attempt == MAX_RETRIES) return lastResult;
            }
        }

        return lastResult != null ? lastResult
                : AIResult.fail("Request attempts exhausted for " + model + ".", model, MAX_RETRIES, System.currentTimeMillis() - modelStartTime);
    }

    // =========================================================================
    // Interview Question Validation & Formatting Helpers
    // =========================================================================

    public List<InterviewQuestion> parseAndValidateInterviewQuestions(String rawText) {
        if (rawText == null || rawText.isBlank()) return Collections.emptyList();

        String jsonCandidate = extractJsonString(rawText);
        try {
            JsonObject root = gson.fromJson(jsonCandidate, JsonObject.class);
            if (root == null || !root.has("questions")) {
                return Collections.emptyList();
            }

            JsonArray qArray = root.getAsJsonArray("questions");
            if (qArray == null || qArray.isEmpty()) {
                return Collections.emptyList();
            }

            List<InterviewQuestion> questions = new ArrayList<>();
            Set<String> normalizedQuestions = new HashSet<>();

            for (JsonElement elem : qArray) {
                if (!elem.isJsonObject()) continue;
                JsonObject qObj = elem.getAsJsonObject();

                String category = qObj.has("category") ? qObj.get("category").getAsString().trim() : "General";
                String qText = qObj.has("question") ? qObj.get("question").getAsString().trim() : "";
                String diff = qObj.has("difficulty") ? qObj.get("difficulty").getAsString().trim() : "Medium";

                if (qText.isBlank()) continue;

                // Validate uniqueness (normalized lower case punctuation-free string)
                String normalized = qText.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (normalizedQuestions.contains(normalized)) {
                    System.err.println("[AIService] Duplicate interview question detected and rejected: " + qText);
                    continue; // Skip duplicate question
                }
                normalizedQuestions.add(normalized);

                List<String> points = new ArrayList<>();
                if (qObj.has("answerPoints") && qObj.get("answerPoints").isJsonArray()) {
                    for (JsonElement p : qObj.getAsJsonArray("answerPoints")) {
                        if (!p.isJsonNull() && !p.getAsString().isBlank()) {
                            points.add(p.getAsString().trim());
                        }
                    }
                }

                questions.add(new InterviewQuestion(category, qText, diff, points));
            }

            return questions;
        } catch (Exception e) {
            System.err.println("[AIService] Could not parse interview questions JSON: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String extractJsonString(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json") || trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String formatInterviewQuestionsToMarkdown(String jobTitle, List<InterviewQuestion> questions) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Targeted Interview Questions for ").append(jobTitle != null ? jobTitle : "Selected Role").append("\n\n");

        List<InterviewQuestion> technical = new ArrayList<>();
        List<InterviewQuestion> behavioural = new ArrayList<>();

        for (InterviewQuestion q : questions) {
            if ("behavioural".equalsIgnoreCase(q.getCategory()) || "behavioral".equalsIgnoreCase(q.getCategory())
                    || "scenario".equalsIgnoreCase(q.getCategory())) {
                behavioural.add(q);
            } else {
                technical.add(q);
            }
        }

        int count = 1;
        if (!technical.isEmpty()) {
            sb.append("### 1. Technical Competency Questions\n\n");
            for (InterviewQuestion q : technical) {
                sb.append("**Question ").append(count++).append("**: ").append(q.getQuestion()).append("\n");
                sb.append("*Category:* `").append(q.getCategory()).append("` | *Difficulty:* `").append(q.getDifficulty()).append("`\n\n");
                if (!q.getAnswerPoints().isEmpty()) {
                    sb.append("*Suggested Evaluation & Answer Points:*\n");
                    for (String pt : q.getAnswerPoints()) {
                        sb.append("- ").append(pt).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        if (!behavioural.isEmpty()) {
            sb.append("### 2. Behavioural & Scenario Questions\n\n");
            for (InterviewQuestion q : behavioural) {
                sb.append("**Question ").append(count++).append("**: ").append(q.getQuestion()).append("\n");
                sb.append("*Category:* `").append(q.getCategory()).append("` | *Difficulty:* `").append(q.getDifficulty()).append("`\n\n");
                if (!q.getAnswerPoints().isEmpty()) {
                    sb.append("*Suggested Evaluation & Answer Points:*\n");
                    for (String pt : q.getAnswerPoints()) {
                        sb.append("- ").append(pt).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Diagnostic & Utility Helpers
    // =========================================================================

    /**
     * Calculates exponential backoff with randomized jitter.
     */
    public static long calculateBackoffWithJitter(int attempt, long baseBackoffMs, long minJitterMs, long maxJitterMs) {
        long base = baseBackoffMs * (1L << Math.max(0, attempt - 1));
        long jitter = (maxJitterMs > minJitterMs)
                ? ThreadLocalRandom.current().nextLong(minJitterMs, maxJitterMs + 1)
                : minJitterMs;
        return base + jitter;
    }

    /**
     * Parses the HTTP {@code Retry-After} header into milliseconds.
     * Clamps values to a maximum of 15 seconds to avoid prolonged thread suspension.
     */
    public static long parseRetryAfterMs(String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) return 0L;
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            if (seconds > 0) {
                return Math.min(seconds * 1000L, 15_000L);
            }
        } catch (NumberFormatException ignored) {}
        return 0L;
    }

    /**
     * Distinguishes HTTP 429 quota exhaustion from temporary burst rate limits.
     */
    public static boolean isQuotaExhausted(int statusCode, String responseBody) {
        if (statusCode != 429) return false;
        if (responseBody == null || responseBody.isBlank()) return false;
        String lower = responseBody.toLowerCase();
        return lower.contains("insufficient_quota")
                || lower.contains("resource_exhausted")
                || lower.contains("quota exceeded")
                || lower.contains("rate_limit_exceeded_quota")
                || lower.contains("quota_exceeded")
                || lower.contains("billing");
    }

    /**
     * Returns true if an error message represents a transient outage or timeout.
     */
    public static boolean isTransientFailure(String errorMessage) {
        if (errorMessage == null) return false;
        String lower = errorMessage.toLowerCase();
        return lower.contains("503") || lower.contains("unavailable")
                || lower.contains("502") || lower.contains("504") || lower.contains("500")
                || lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("connectexception") || lower.contains("connection reset")
                || lower.contains("service temporarily unavailable");
    }

    // =========================================================================
    // Response Parsing
    // =========================================================================

    public AIResult parseSuccessResponse(String responseBody) {
        try {
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);

            if (root.has("error")) {
                JsonObject err = root.getAsJsonObject("error");
                String msg = err.has("message") ? err.get("message").getAsString() : "Unknown API error";
                return AIResult.fail("GroqCloud API Error: " + msg);
            }

            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return AIResult.fail("AI returned an empty response choices list. Please try again.");
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();

            if (firstChoice.has("finish_reason")) {
                String finishReason = firstChoice.get("finish_reason").getAsString();
                if ("content_filter".equalsIgnoreCase(finishReason)) {
                    return AIResult.fail("Response blocked due to content safety guidelines. Please modify the input text.");
                }
                if ("length".equalsIgnoreCase(finishReason)) {
                    System.err.println("[AIService] WARNING: Response was truncated (max_tokens).");
                }
            }

            JsonObject messageObj = firstChoice.getAsJsonObject("message");
            if (messageObj == null) {
                return AIResult.fail("AI response did not contain message content. Please try again.");
            }

            if (!messageObj.has("content") || messageObj.get("content").isJsonNull()) {
                return AIResult.fail("AI response message content was empty. Please try again.");
            }

            String text = messageObj.get("content").getAsString().trim();

            if (text.isEmpty()) {
                return AIResult.fail("AI returned empty text. Please try again.");
            }

            return AIResult.ok(text);

        } catch (Exception e) {
            System.err.println("[AIService] Failed to parse Groq response: " + e.getMessage());
            return AIResult.fail("Could not parse the AI response. Please try again.");
        }
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return null;
        try {
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);
            if (root != null && root.has("error")) {
                JsonObject error = root.getAsJsonObject("error");
                if (error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // =========================================================================
    // Self Diagnostics
    // =========================================================================

    public static class DiagnosticReport {
        public boolean modelListWorks;
        public int     modelListStatus;
        public long    modelListDurationMs;
        public List<String> availableModels = new ArrayList<>();
        public boolean generationWorks;
        public int     generationStatus;
        public long    generationDurationMs;
        public String  groqStatus;
        public String  groqMessage;
        public String  generatedSampleText;
    }

    public DiagnosticReport runSelfDiagnostic() {
        DiagnosticReport report = new DiagnosticReport();
        if (!isConfigured()) {
            report.groqMessage = "GROQ_API_KEY is not configured.";
            return report;
        }

        long startList = System.currentTimeMillis();
        try {
            HttpRequest listReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/models"))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> listResp = getHttpClient().send(listReq, HttpResponse.BodyHandlers.ofString());
            report.modelListDurationMs = System.currentTimeMillis() - startList;
            report.modelListStatus = listResp.statusCode();

            if (listResp.statusCode() == 200) {
                report.modelListWorks = true;
                JsonObject root = gson.fromJson(listResp.body(), JsonObject.class);
                if (root != null && root.has("data")) {
                    for (JsonElement mElem : root.getAsJsonArray("data")) {
                        JsonObject m = mElem.getAsJsonObject();
                        if (m.has("id")) {
                            report.availableModels.add(m.get("id").getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            report.modelListDurationMs = System.currentTimeMillis() - startList;
            report.modelListStatus = -1;
        }

        long startGen = System.currentTimeMillis();
        AIResult genResult = callGroq("Reply with only the word: CONNECTED");
        report.generationDurationMs = System.currentTimeMillis() - startGen;
        report.generationWorks = genResult.isSuccess();
        if (genResult.isSuccess()) {
            report.generationStatus = 200;
            report.generatedSampleText = genResult.getText();
        } else {
            report.groqMessage = genResult.getError();
        }

        return report;
    }

    private AIResult missingKeyError() {
        return AIResult.fail(
                "GROQ_API_KEY is not configured.\n\n"
                + "To enable AI Career Assistant and Resume Review features:\n"
                + "1. Get a free API key from GroqCloud Console: https://console.groq.com/keys\n"
                + "2. Set the environment variable in PowerShell before launching:\n"
                + "   $env:GROQ_API_KEY = \"your_api_key_here\"\n"
                + "3. Restart HireHub."
        );
    }

    private String truncate(String s) {
        if (s == null || s.isBlank()) return "Not specified";
        return s.length() > MAX_INPUT_CHARS ? s.substring(0, MAX_INPUT_CHARS) + "..." : s;
    }
}

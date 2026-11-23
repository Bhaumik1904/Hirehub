package com.hirehub.service;

import com.hirehub.dao.JobDAO;
import com.hirehub.dao.ResumeAnalysisDAO;
import com.hirehub.model.Job;
import com.hirehub.model.ParsedResume;
import com.hirehub.model.ResumeAnalysis;
import com.hirehub.model.User;
import com.hirehub.util.SessionManager;

import java.sql.SQLException;
import java.util.*;

/**
 * ResumeAnalysisService — Implements deterministic fair scoring and coordinates
 * GroqCloud AI evaluations for both job-specific compatibility and general resume quality.
 */
public class ResumeAnalysisService {

    private final ResumeAnalysisDAO analysisDAO = new ResumeAnalysisDAO();
    private final JobDAO            jobDAO      = new JobDAO();
    private final AIService         aiService   = new AIService();

    // ── Alias mapping for technical skills ──────────────────────────────────
    private static final Map<String, String> SKILL_ALIASES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        SKILL_ALIASES.put("js", "JavaScript");
        SKILL_ALIASES.put("javascript", "JavaScript");
        SKILL_ALIASES.put("ts", "TypeScript");
        SKILL_ALIASES.put("typescript", "TypeScript");
        SKILL_ALIASES.put("py", "Python");
        SKILL_ALIASES.put("python", "Python");
        SKILL_ALIASES.put("react", "React");
        SKILL_ALIASES.put("reactjs", "React");
        SKILL_ALIASES.put("react.js", "React");
        SKILL_ALIASES.put("node", "Node.js");
        SKILL_ALIASES.put("nodejs", "Node.js");
        SKILL_ALIASES.put("node.js", "Node.js");
        SKILL_ALIASES.put("k8s", "Kubernetes");
        SKILL_ALIASES.put("kubernetes", "Kubernetes");
        SKILL_ALIASES.put("postgres", "PostgreSQL");
        SKILL_ALIASES.put("postgresql", "PostgreSQL");
        SKILL_ALIASES.put("mongo", "MongoDB");
        SKILL_ALIASES.put("mongodb", "MongoDB");
        SKILL_ALIASES.put("aws", "AWS");
        SKILL_ALIASES.put("amazon web services", "AWS");
        SKILL_ALIASES.put("gcp", "GCP");
        SKILL_ALIASES.put("google cloud", "GCP");
        SKILL_ALIASES.put("google cloud platform", "GCP");
        SKILL_ALIASES.put("springboot", "Spring Boot");
        SKILL_ALIASES.put("spring boot", "Spring Boot");
        SKILL_ALIASES.put("ml", "Machine Learning");
        SKILL_ALIASES.put("machine learning", "Machine Learning");
        SKILL_ALIASES.put("ai", "Generative AI");
        SKILL_ALIASES.put("genai", "Generative AI");
        SKILL_ALIASES.put("generative ai", "Generative AI");
        SKILL_ALIASES.put("ci/cd", "CI/CD");
        SKILL_ALIASES.put("cicd", "CI/CD");
    }

    // ── Scoring Breakdown Data Holder ───────────────────────────────────────

    public static class ScoringBreakdown {
        private final int          overallScore;
        private final int          skillsScore;         // max 40
        private final int          experienceScore;     // max 25
        private final int          educationScore;      // max 15
        private final int          completenessScore;   // max 10
        private final int          certificationsScore; // max 10
        private final List<String> matchingSkills;
        private final List<String> missingSkills;
        private final String       skillsExplanation;
        private final String       experienceExplanation;
        private final String       educationExplanation;
        private final String       completenessExplanation;
        private final String       certificationsExplanation;
        private final boolean      isGeneral;
        private final String       evaluationConfidence;
        private final List<String> unevaluatedCriteria;
        private final List<String> extractedSummary;

        public ScoringBreakdown(int overallScore, int skillsScore, int experienceScore,
                                int educationScore, int completenessScore, int certificationsScore,
                                List<String> matchingSkills, List<String> missingSkills,
                                String skillsExplanation, String experienceExplanation,
                                String educationExplanation, String completenessExplanation,
                                String certificationsExplanation, boolean isGeneral,
                                String evaluationConfidence, List<String> unevaluatedCriteria,
                                List<String> extractedSummary) {
            this.overallScore               = overallScore;
            this.skillsScore                = skillsScore;
            this.experienceScore            = experienceScore;
            this.educationScore             = educationScore;
            this.completenessScore          = completenessScore;
            this.certificationsScore        = certificationsScore;
            this.matchingSkills             = matchingSkills;
            this.missingSkills              = missingSkills;
            this.skillsExplanation          = skillsExplanation;
            this.experienceExplanation      = experienceExplanation;
            this.educationExplanation       = educationExplanation;
            this.completenessExplanation    = completenessExplanation;
            this.certificationsExplanation  = certificationsExplanation;
            this.isGeneral                  = isGeneral;
            this.evaluationConfidence       = evaluationConfidence;
            this.unevaluatedCriteria        = unevaluatedCriteria != null ? unevaluatedCriteria : Collections.emptyList();
            this.extractedSummary           = extractedSummary != null ? extractedSummary : Collections.emptyList();
        }

        public int getOverallScore()                 { return overallScore; }
        public int getSkillsScore()                  { return skillsScore; }
        public int getExperienceScore()              { return experienceScore; }
        public int getEducationScore()               { return educationScore; }
        public int getCompletenessScore()            { return completenessScore; }
        public int getCertificationsScore()          { return certificationsScore; }
        public List<String> getMatchingSkills()      { return matchingSkills; }
        public List<String> getMissingSkills()       { return missingSkills; }
        public String getSkillsExplanation()         { return skillsExplanation; }
        public String getExperienceExplanation()     { return experienceExplanation; }
        public String getEducationExplanation()      { return educationExplanation; }
        public String getCompletenessExplanation()   { return completenessExplanation; }
        public String getCertificationsExplanation() { return certificationsExplanation; }
        public boolean isGeneral()                   { return isGeneral; }
        public String getEvaluationConfidence()      { return evaluationConfidence; }
        public List<String> getUnevaluatedCriteria() { return unevaluatedCriteria; }
        public List<String> getExtractedSummary()    { return extractedSummary; }
    }

    // ── Deterministic Scoring Engine ────────────────────────────────────────

    /**
     * Calculates the deterministic 5-part compatibility score (0-100).
     *
     * <p>If {@code job == null}, performs a General Resume Quality &amp; Readiness evaluation.
     * If {@code job != null}, performs a role-specific job alignment audit.
     *
     * <p>Requirements:
     * - Missing resume info is treated as unknown/unevaluated, not a deficiency.
     * - Distinguishes extracted information from unverified listed claims.
     * - Clear transparency on criteria that could not be evaluated.
     */
    public ScoringBreakdown calculateDeterministicScore(ParsedResume resume, Job job) {
        if (resume == null) {
            return new ScoringBreakdown(0, 0, 0, 0, 0, 0,
                    Collections.emptyList(), Collections.emptyList(),
                    "Resume data unavailable", "", "", "", "",
                    job == null, "LOW", List.of("No resume data"), Collections.emptyList());
        }

        List<String> unevaluated = new ArrayList<>();
        List<String> extractedInfo = new ArrayList<>();

        // Candidate extracted skills
        Set<String> candidateSkills = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (resume.getSkills() != null) {
            for (String s : resume.getSkills()) {
                candidateSkills.add(canonicalSkill(s));
            }
        }
        extractedInfo.add(candidateSkills.size() + " technical skills extracted");

        // ── 1. Skills Evaluation (40 pts) ───────────────────────────────────
        int skillsScore;
        String skillsExp;
        List<String> matching = new ArrayList<>();
        List<String> missing  = new ArrayList<>();

        if (job == null) {
            // General Resume Analysis: Breadth of technical profile
            int count = candidateSkills.size();
            if (count >= 8)      skillsScore = 40;
            else if (count >= 5) skillsScore = 32;
            else if (count >= 3) skillsScore = 24;
            else if (count >= 1) skillsScore = 14;
            else {
                skillsScore = 0;
                unevaluated.add("Technical skills: None clearly detected in resume text");
            }
            skillsExp = String.format("General technical breadth: %d skills detected across modern technology stacks.", count);
            matching.addAll(candidateSkills);
        } else {
            // Job-Specific Compatibility
            List<String> requiredList = parseSkillList(job.getRequiredSkills());
            if (requiredList.isEmpty()) {
                int matched = 0;
                String desc = job.getDescription() != null ? job.getDescription().toLowerCase() : "";
                for (String cSkill : candidateSkills) {
                    if (desc.contains(cSkill.toLowerCase())) {
                        matching.add(cSkill);
                        matched++;
                    }
                }
                skillsScore = Math.min(40, Math.max(16, matched * 8));
                skillsExp = String.format("Job has no explicit skill list; matched %d skills from description.", matched);
                unevaluated.add("Explicit required skills omitted by recruiter; evaluated against job description context.");
            } else {
                for (String req : requiredList) {
                    String canon = canonicalSkill(req);
                    boolean found = candidateSkills.contains(canon);
                    if (!found) {
                        for (String cSkill : candidateSkills) {
                            if (cSkill.equalsIgnoreCase(canon) || cSkill.toLowerCase().contains(canon.toLowerCase()) || canon.toLowerCase().contains(cSkill.toLowerCase())) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (found) {
                        matching.add(req);
                    } else {
                        missing.add(req);
                    }
                }
                double skillRatio = requiredList.isEmpty() ? 0.0 : (double) matching.size() / requiredList.size();
                skillsScore = (int) Math.round(skillRatio * 40.0);
                skillsExp = String.format("%d of %d required skills matched (%.0f%% match rate).",
                        matching.size(), requiredList.size(), skillRatio * 100.0);
            }
        }

        // ── 2. Projects & Experience Evaluation (25 pts) ────────────────────
        int expScore = 0;
        int projCount = (resume.getProjects() != null) ? resume.getProjects().size() : 0;
        int workCount = (resume.getWorkExperiences() != null) ? resume.getWorkExperiences().size() : 0;
        int metrics   = resume.getMetricsCount();

        extractedInfo.add(projCount + " project item(s)");
        extractedInfo.add(workCount + " work/internship entry(ies)");

        // Projects presence (max 10)
        if (projCount >= 2)      expScore += 10;
        else if (projCount == 1) expScore += 6;
        else unevaluated.add("Projects: No distinct projects section detected (treated as unevaluated).");

        // Experience presence (max 10)
        if (workCount >= 1)      expScore += 10;
        else if (projCount >= 3) expScore += 5; // compensating strong projects for freshers
        else unevaluated.add("Work experience / internships: Not detected on resume (treated as unstated).");

        // Quantifiable metrics / scale indicators (max 5)
        if (metrics >= 2)      expScore += 5;
        else if (metrics == 1) expScore += 3;
        else unevaluated.add("Quantifiable impact metrics: None detected in project/experience descriptions.");

        String expExp = String.format("Detected %d project(s), %d work/internship item(s), and %d quantifiable metric(s).",
                projCount, workCount, metrics);

        // ── 3. Education & Academic Profile Evaluation (15 pts) ─────────────
        int eduScore = 0;
        boolean hasDegree = resume.getDegree() != null && !resume.getDegree().isBlank();
        boolean hasCollege = resume.getCollege() != null && !resume.getCollege().isBlank();

        if (hasDegree && hasCollege) {
            eduScore += 8;
            extractedInfo.add("Degree & College: " + resume.getDegree() + " at " + resume.getCollege());
        } else if (hasDegree || hasCollege) {
            eduScore += 5;
            if (!hasCollege) unevaluated.add("College / University institution not stated.");
            if (!hasDegree)  unevaluated.add("Exact degree specification omitted.");
        } else {
            unevaluated.add("Academic background not clearly detected from resume.");
        }

        int gradYear = resume.getGraduationYear();
        if (gradYear >= 2020 && gradYear <= 2030) {
            eduScore += 7;
            extractedInfo.add("Graduation Year: " + gradYear);
        } else if (gradYear > 0) {
            eduScore += 4;
            extractedInfo.add("Graduation Year: " + gradYear);
        } else {
            // Missing graduation year: neutral score (4) instead of penalizing student
            eduScore += 4;
            unevaluated.add("Graduation year: Unspecified in resume (evaluated as neutral).");
        }

        String eduExp = String.format("Degree: %s | College: %s | Graduation Year: %s.",
                hasDegree ? resume.getDegree() : "Unstated",
                hasCollege ? resume.getCollege() : "Unstated",
                gradYear > 0 ? String.valueOf(gradYear) : "Unstated");

        // ── 4. Resume Completeness & Clarity (10 pts) ───────────────────────
        int compScore = 0;
        boolean hasEmail = resume.getEmail() != null && !resume.getEmail().isBlank();
        boolean hasPhone = resume.getPhone() != null && !resume.getPhone().isBlank();

        if (hasEmail) compScore += 2; else unevaluated.add("Email address not found.");
        if (hasPhone) compScore += 2; else unevaluated.add("Phone number not found.");

        int linksCount = (resume.getLinks() != null) ? resume.getLinks().size() : 0;
        if (linksCount >= 2)      compScore += 3;
        else if (linksCount == 1) compScore += 2;
        else unevaluated.add("Online profile links (GitHub / LinkedIn): Not found.");

        int sectionsPresent = 0;
        if (candidateSkills.size() > 0) sectionsPresent++;
        if (projCount > 0)              sectionsPresent++;
        if (hasDegree || hasCollege)    sectionsPresent++;
        if (sectionsPresent >= 3) compScore += 3;
        else compScore += sectionsPresent;

        String compExp = String.format("Contact details %s, %d profile link(s), and %d core sections detected.",
                (hasEmail && hasPhone) ? "complete" : "partially present", linksCount, sectionsPresent);

        // ── 5. Detected Certifications & Listed Credentials (10 pts) ────────
        // Terminology: "Detected certifications / listed credentials" (never labelled "verified")
        int certScore = 0;
        int certCount = (resume.getCertifications() != null) ? resume.getCertifications().size() : 0;
        if (certCount >= 2)      certScore += 10;
        else if (certCount == 1) certScore += 6;
        else {
            String raw = resume.getRawText() != null ? resume.getRawText().toLowerCase() : "";
            if (raw.contains("certified") || raw.contains("certification") || raw.contains("coursera") || raw.contains("udemy")) {
                certScore += 4;
                certCount = 1;
            } else {
                unevaluated.add("Certifications: No listed credentials detected.");
            }
        }

        String certExp = String.format("Found %d detected certification(s) / listed credential(s). Note: Listed credentials have not been independently verified.", certCount);

        // Overall score clamped between 0 and 100
        int overall = Math.min(100, Math.max(0, skillsScore + expScore + eduScore + compScore + certScore));

        // Evaluation confidence heuristic
        String confidence = "HIGH";
        if (unevaluated.size() >= 4) {
            confidence = "LOW (Significant data omitted from resume)";
        } else if (unevaluated.size() >= 2) {
            confidence = "MODERATE (Some criteria could not be evaluated)";
        }

        return new ScoringBreakdown(
                overall, skillsScore, expScore, eduScore, compScore, certScore,
                matching, missing,
                skillsExp, expExp, eduExp, compExp, certExp,
                job == null, confidence, unevaluated, extractedInfo
        );
    }

    // ── GroqCloud AI Deep Review Generation ─────────────────────────────────

    /**
     * Generates an AI evaluation of the resume using GroqCloud via {@link AIService}.
     * Supports both general resume analysis (when {@code job == null}) and job-specific analysis.
     *
     * <p><b>Must be executed on a background worker thread.</b></p>
     */
    public AIService.AIResult generateAIReview(ParsedResume parsedResume, Job job, ScoringBreakdown breakdown) {
        if (!aiService.isConfigured()) {
            return AIService.AIResult.fail("GROQ_API_KEY is not configured. Please set the environment variable and restart.");
        }

        String prompt = (job != null)
                ? buildJobSpecificAIPrompt(parsedResume, job, breakdown)
                : buildGeneralResumeAIPrompt(parsedResume, breakdown);

        return aiService.callGroq(prompt);
    }

    private String buildJobSpecificAIPrompt(ParsedResume resume, Job job, ScoringBreakdown breakdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a Senior Technical Recruiter and Career Mentor for HireHub.\n");
        sb.append("Perform an insightful, fair evaluation of the candidate's resume for the target job posting.\n\n");

        sb.append("### TARGET JOB DETAILS\n");
        sb.append("Title: ").append(job.getTitle()).append("\n");
        sb.append("Company: ").append(job.getCompanyName()).append("\n");
        sb.append("Required Skills: ").append(job.getRequiredSkills() != null ? job.getRequiredSkills() : "Not specified").append("\n");
        sb.append("Description Excerpt: ").append(truncate(job.getDescription(), 800)).append("\n\n");

        sb.append("### CANDIDATE RESUME SUMMARY\n");
        sb.append("Candidate Name: ").append(resume.getName()).append("\n");
        sb.append("College: ").append(resume.getCollege() != null ? resume.getCollege() : "Unstated").append("\n");
        sb.append("Degree: ").append(resume.getDegree() != null ? resume.getDegree() : "Unstated").append("\n");
        sb.append("Graduation Year: ").append(resume.getGraduationYear() > 0 ? resume.getGraduationYear() : "Unstated").append("\n");
        sb.append("Extracted Skills: ").append(resume.getSkillsCsv()).append("\n");
        sb.append("Projects Count: ").append(resume.getProjects().size()).append("\n");
        sb.append("Work/Internships Count: ").append(resume.getWorkExperiences().size()).append("\n");
        sb.append("Listed Credentials: ").append(resume.getCertifications().size()).append(" (detected from text, unverified)\n\n");

        sb.append("### DETERMINISTIC COMPATIBILITY AUDIT (Calculated by HireHub deterministic engine)\n");
        sb.append("- Overall Compatibility Score: ").append(breakdown.getOverallScore()).append("/100 (Evaluation Confidence: ").append(breakdown.getEvaluationConfidence()).append(")\n");
        sb.append("- Required Skills: ").append(breakdown.getSkillsScore()).append("/40 (Matched: ")
                .append(String.join(", ", breakdown.getMatchingSkills()))
                .append("; Missing: ").append(String.join(", ", breakdown.getMissingSkills())).append(")\n");
        sb.append("- Projects & Experience: ").append(breakdown.getExperienceScore()).append("/25\n");
        sb.append("- Academic Profile: ").append(breakdown.getEducationScore()).append("/15\n");
        sb.append("- Completeness & Contact: ").append(breakdown.getCompletenessScore()).append("/10\n");
        sb.append("- Listed Credentials: ").append(breakdown.getCertificationsScore()).append("/10\n");

        if (!breakdown.getUnevaluatedCriteria().isEmpty()) {
            sb.append("- Unevaluated / Unstated Criteria: ").append(String.join("; ", breakdown.getUnevaluatedCriteria())).append("\n");
        }
        sb.append("\n");

        if (resume.getRawText() != null && !resume.getRawText().isBlank()) {
            sb.append("### RESUME EXCERPT\n");
            sb.append(truncate(resume.getRawText(), 1800)).append("\n\n");
        }

        sb.append("### INSTRUCTIONS FOR YOUR REPORT\n");
        sb.append("Please provide an actionable report using clean Markdown headings:\n\n");
        sb.append("## 1. Executive Summary & Role Match\n");
        sb.append("Provide a balanced 3-4 sentence appraisal of how the candidate fits ").append(job.getTitle()).append(" at ").append(job.getCompanyName()).append(". Do not assume missing information means a student lacks capability.\n\n");
        sb.append("## 2. ATS & Resume Quality Evaluation\n");
        sb.append("- Formatting, structural flow, and ATS readability.\n");
        sb.append("- Use of quantifiable metrics and active action verbs.\n\n");
        sb.append("## 3. Skills Gap & Practical Bridging Strategy\n");
        sb.append("- Missing skills: ").append(String.join(", ", breakdown.getMissingSkills())).append(".\n");
        sb.append("- Provide realistic resources or mini-projects to learn these efficiently.\n\n");
        sb.append("## 4. High-Impact Project Enhancements (Google X-Y-Z Formula)\n");
        sb.append("Rewrite 2-3 project bullet points using Google's formula: *Accomplished [X], as measured by [Y], by doing [Z]*. Show Before & After.\n\n");
        sb.append("## 5. Prioritized 30-Day Preparation Roadmap\n");
        sb.append("- Weeks 1-2: Core gap closure and resume revisions.\n");
        sb.append("- Weeks 3-4: Portfolio enhancements and targeted interview practice.\n\n");
        sb.append("Maintain an encouraging, highly professional tone.");

        return sb.toString();
    }

    private String buildGeneralResumeAIPrompt(ParsedResume resume, ScoringBreakdown breakdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a Senior Technical Recruiter and Career Mentor for HireHub.\n");
        sb.append("Perform a comprehensive, fair general audit of the student candidate's resume.\n\n");

        sb.append("### CANDIDATE RESUME SUMMARY\n");
        sb.append("Candidate Name: ").append(resume.getName()).append("\n");
        sb.append("College: ").append(resume.getCollege() != null ? resume.getCollege() : "Unstated").append("\n");
        sb.append("Degree: ").append(resume.getDegree() != null ? resume.getDegree() : "Unstated").append("\n");
        sb.append("Graduation Year: ").append(resume.getGraduationYear() > 0 ? resume.getGraduationYear() : "Unstated").append("\n");
        sb.append("Extracted Skills: ").append(resume.getSkillsCsv()).append("\n");
        sb.append("Projects Count: ").append(resume.getProjects().size()).append("\n");
        sb.append("Work/Internships Count: ").append(resume.getWorkExperiences().size()).append("\n");
        sb.append("Listed Credentials: ").append(resume.getCertifications().size()).append(" (detected from text, unverified)\n\n");

        sb.append("### DETERMINISTIC QUALITY AUDIT (Calculated by HireHub engine)\n");
        sb.append("- Overall Resume Quality Score: ").append(breakdown.getOverallScore()).append("/100 (Evaluation Confidence: ").append(breakdown.getEvaluationConfidence()).append(")\n");
        sb.append("- Technical Breadth: ").append(breakdown.getSkillsScore()).append("/40\n");
        sb.append("- Projects & Experience: ").append(breakdown.getExperienceScore()).append("/25\n");
        sb.append("- Academic Profile: ").append(breakdown.getEducationScore()).append("/15\n");
        sb.append("- Completeness & Presentation: ").append(breakdown.getCompletenessScore()).append("/10\n");
        sb.append("- Listed Credentials: ").append(breakdown.getCertificationsScore()).append("/10\n");

        if (!breakdown.getUnevaluatedCriteria().isEmpty()) {
            sb.append("- Unevaluated / Unstated Criteria: ").append(String.join("; ", breakdown.getUnevaluatedCriteria())).append("\n");
        }
        sb.append("\n");

        if (resume.getRawText() != null && !resume.getRawText().isBlank()) {
            sb.append("### RESUME EXCERPT\n");
            sb.append(truncate(resume.getRawText(), 1800)).append("\n\n");
        }

        sb.append("### INSTRUCTIONS FOR YOUR REPORT\n");
        sb.append("Please provide an actionable report using clean Markdown headings:\n\n");
        sb.append("## 1. Executive Summary & Overall Impression\n");
        sb.append("Summarize the candidate's core strengths, primary technical identity, and competitive advantages.\n\n");
        sb.append("## 2. ATS & Formatting Critique\n");
        sb.append("Evaluate layout, readability, contact visibility, and section hierarchy.\n\n");
        sb.append("## 3. Technical Profile & Suggested Extensions\n");
        sb.append("Assess the detected skill set. Recommend adjacent technologies or industry-standard tools to complement their current stack.\n\n");
        sb.append("## 4. Google X-Y-Z Bullet Point Optimizations\n");
        sb.append("Rewrite 2 to 3 bullet points using Google's formula: *Accomplished [X], as measured by [Y], by doing [Z]*. Show Before & After.\n\n");
        sb.append("## 5. Strategic 30-Day Career Roadmap\n");
        sb.append("Outline concrete steps to elevate this resume for competitive internships and entry-level engineering placements.\n");

        return sb.toString();
    }

    private boolean isAuthorizedStudent(int studentId) {
        if (studentId <= 0) return false;
        User current = SessionManager.getInstance().getCurrentUser();
        return current != null
                && current.getRole() == User.Role.STUDENT
                && current.getId() == studentId;
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    /**
     * Saves a completed analysis to the database. Supports nullable {@code jobId} and {@code resumeId}.
     * Requires an authenticated STUDENT session matching {@code studentId}.
     */
    public ResumeAnalysis saveAnalysis(Integer resumeId, Integer jobId, int studentId,
                                       ScoringBreakdown breakdown, String fullAiText) throws SQLException {
        if (studentId <= 0) {
            throw new IllegalArgumentException("Invalid student ID: " + studentId);
        }
        if (!isAuthorizedStudent(studentId)) {
            throw new SecurityException("Unauthorized: Session does not match student ID " + studentId);
        }
        ResumeAnalysis analysis = new ResumeAnalysis(
                resumeId, jobId, studentId,
                breakdown.getOverallScore(),
                breakdown.getSkillsScore(),
                breakdown.getExperienceScore(),
                breakdown.getEducationScore(),
                breakdown.getCompletenessScore(),
                breakdown.getCertificationsScore(),
                String.join(", ", breakdown.getMatchingSkills()),
                String.join(", ", breakdown.getMissingSkills()),
                fullAiText, // stored in analysis_summary
                breakdown.getSkillsExplanation() + " | " + breakdown.getExperienceExplanation(),
                breakdown.getCompletenessExplanation()
        );

        analysisDAO.insert(analysis);
        return analysis;
    }

    /**
     * Admin-only save for analysis record.
     */
    public ResumeAnalysis saveAnalysisForAdmin(Integer resumeId, Integer jobId, int studentId,
                                              ScoringBreakdown breakdown, String fullAiText) throws SQLException {
        if (studentId <= 0) {
            throw new IllegalArgumentException("Invalid student ID: " + studentId);
        }
        if (!SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            throw new SecurityException("Unauthorized: Admin role required.");
        }
        ResumeAnalysis analysis = new ResumeAnalysis(
                resumeId, jobId, studentId,
                breakdown.getOverallScore(),
                breakdown.getSkillsScore(),
                breakdown.getExperienceScore(),
                breakdown.getEducationScore(),
                breakdown.getCompletenessScore(),
                breakdown.getCertificationsScore(),
                String.join(", ", breakdown.getMatchingSkills()),
                String.join(", ", breakdown.getMissingSkills()),
                fullAiText,
                breakdown.getSkillsExplanation() + " | " + breakdown.getExperienceExplanation(),
                breakdown.getCompletenessExplanation()
        );

        analysisDAO.insert(analysis);
        return analysis;
    }

    /**
     * Retrieves previous analyses for a student.
     * Requires an authenticated STUDENT session matching {@code studentId}.
     */
    public List<ResumeAnalysis> getStudentAnalysisHistory(int studentId, int limit) {
        if (studentId <= 0 || !isAuthorizedStudent(studentId)) {
            return Collections.emptyList();
        }
        try {
            return analysisDAO.findRecentByStudentId(studentId, limit);
        } catch (SQLException e) {
            System.err.println("[ResumeAnalysisService] Error fetching history: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Admin-only retrieval of student analysis history.
     */
    public List<ResumeAnalysis> getStudentAnalysisHistoryForAdmin(int studentId, int limit) {
        if (studentId <= 0 || !SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return Collections.emptyList();
        }
        try {
            return analysisDAO.findRecentByStudentId(studentId, limit);
        } catch (SQLException e) {
            System.err.println("[ResumeAnalysisService] Error fetching history for admin: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Utility Methods ─────────────────────────────────────────────────────

    private List<String> parseSkillList(String csv) {
        List<String> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) return list;
        String[] tokens = csv.split("[,;\\n]+");
        for (String t : tokens) {
            String tr = t.trim();
            if (!tr.isEmpty()) {
                list.add(tr);
            }
        }
        return list;
    }

    public static String canonicalSkill(String skill) {
        if (skill == null) return "";
        String trimmed = skill.trim();
        return SKILL_ALIASES.getOrDefault(trimmed, trimmed);
    }

    private String truncate(String s, int maxChars) {
        if (s == null) return "";
        return s.length() > maxChars ? s.substring(0, maxChars) + "..." : s;
    }
}

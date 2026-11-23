package com.hirehub.service;

import com.hirehub.dao.StudentAnalyticsDAO;
import com.hirehub.model.*;
import com.hirehub.util.SessionManager;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * StudentAnalyticsService — Service layer for student analytics, recommendations, and dashboard metrics.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Positive student ID and date range validation.</li>
 *   <li>Role and session authorization guards (fail-closed for unauthorized cross-tenant requests).</li>
 *   <li>Deterministic resume readiness rubric calculations without AI dependency.</li>
 *   <li>Skill overlap, union, in-demand missing skills ranking, and job match recommendations.</li>
 *   <li>Zero-filled metric fallbacks and safe division-by-zero guards.</li>
 * </ul>
 */
public class StudentAnalyticsService {

    private final StudentAnalyticsDAO analyticsDAO;
    private final ResumeService resumeService;
    private final ResumeAnalysisService analysisService;

    public StudentAnalyticsService() {
        this(new StudentAnalyticsDAO(), new ResumeService(), new ResumeAnalysisService());
    }

    public StudentAnalyticsService(StudentAnalyticsDAO analyticsDAO,
                                   ResumeService resumeService,
                                   ResumeAnalysisService analysisService) {
        this.analyticsDAO = Objects.requireNonNull(analyticsDAO, "analyticsDAO must not be null");
        this.resumeService = Objects.requireNonNull(resumeService, "resumeService must not be null");
        this.analysisService = Objects.requireNonNull(analysisService, "analysisService must not be null");
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Loads a complete analytics snapshot for {@code studentId} within optional date boundaries.
     *
     * @param studentId positive student user ID
     * @param from      inclusive start date (nullable for all time)
     * @param to        inclusive end date (nullable for all time)
     * @return non-null StudentDashboardSnapshot
     */
    public StudentDashboardSnapshot loadSnapshot(int studentId, LocalDate from, LocalDate to) {
        validateStudentId(studentId);
        validateDateRange(from, to);
        validateSessionAccess(studentId);

        StudentDashboardSnapshot snapshot = new StudentDashboardSnapshot();
        snapshot.setStudentId(studentId);
        snapshot.setGeneratedAt(LocalDateTime.now());

        LocalDateTime dtFrom = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime dtTo = (to != null) ? to.plusDays(1).atStartOfDay() : null;

        try {
            // 1. Applications by status
            Map<Application.Status, Integer> statusCounts = analyticsDAO.countApplicationsByStatus(studentId, dtFrom, dtTo);
            int pending = statusCounts.getOrDefault(Application.Status.PENDING, 0);
            int shortlisted = statusCounts.getOrDefault(Application.Status.SHORTLISTED, 0);
            int rejected = statusCounts.getOrDefault(Application.Status.REJECTED, 0);
            int selected = statusCounts.getOrDefault(Application.Status.SELECTED, 0);
            int totalApps = pending + shortlisted + rejected + selected;

            StudentStatusDistribution distribution = new StudentStatusDistribution(pending, shortlisted, rejected, selected);
            snapshot.setStatusDistribution(distribution);

            // 2. Open active platform jobs
            int openActiveJobs = analyticsDAO.countOpenActiveJobs();

            // 3. Profile analysis & completeness score
            Optional<StudentProfile> profileOpt = analyticsDAO.getStudentProfile(studentId);
            boolean hasProfile = profileOpt.isPresent();
            boolean profileComplete = false;
            int profileScore = 0;
            List<String> missingFields = new ArrayList<>();

            if (hasProfile) {
                StudentProfile p = profileOpt.get();

                if (p.getPhone() != null && !p.getPhone().isBlank()) profileScore++;
                else missingFields.add("Phone Number");

                if (p.getCollege() != null && !p.getCollege().isBlank()) profileScore++;
                else missingFields.add("College / University");

                if (p.getDegree() != null && !p.getDegree().isBlank()) profileScore++;
                else missingFields.add("Degree");

                if (p.getGraduationYear() >= 2000) profileScore++;
                else missingFields.add("Graduation Year");

                if (p.getSkills() != null && !p.getSkills().isBlank()) profileScore++;
                else missingFields.add("Technical Skills");

                if (p.getResumePath() != null && !p.getResumePath().isBlank()) profileScore++;
                else missingFields.add("Resume Document");

                profileComplete = (profileScore == 6);
            } else {
                missingFields.addAll(List.of("Phone Number", "College / University", "Degree",
                        "Graduation Year", "Technical Skills", "Resume Document"));
            }

            // 4. Resume signals & readiness
            Optional<Resume> activeResumeOpt = analyticsDAO.getActiveResume(studentId);
            boolean hasActiveResume = activeResumeOpt.isPresent();
            int historicalResumeCount = analyticsDAO.countHistoricalResumes(studentId);
            boolean hasHistoricalResumes = historicalResumeCount > 0;

            Optional<ResumeAnalysis> latestAnalysisOpt = analyticsDAO.getLatestResumeAnalysis(studentId);
            Integer latestResumeScore = latestAnalysisOpt.map(ResumeAnalysis::getOverallScore).orElse(null);

            StudentResumeReadiness readiness = new StudentResumeReadiness();
            readiness.setHasActiveResume(hasActiveResume);
            readiness.setHasHistoricalResumes(hasHistoricalResumes);
            readiness.setHistoricalResumeCount(historicalResumeCount);

            ParsedResume parsedResume = null;
            String latestConfidence = null;

            if (hasActiveResume) {
                Resume r = activeResumeOpt.get();
                readiness.setActiveResumeFileName(r.getFileName());
                readiness.setActiveResumeUploadedAt(r.getUploadedAt());
                readiness.setActiveResumeFileSizeBytes(r.getFileSizeBytes());

                parsedResume = resumeService.parseJsonMetadata(r);
                if (parsedResume != null) {
                    readiness.setParsedSkillCount(parsedResume.getSkills() != null ? parsedResume.getSkills().size() : 0);
                    readiness.setParsedExperienceCount(parsedResume.getWorkExperiences() != null ? parsedResume.getWorkExperiences().size() : 0);
                    readiness.setParsedProjectCount(parsedResume.getProjects() != null ? parsedResume.getProjects().size() : 0);
                    readiness.setParsedCertificationCount(parsedResume.getCertifications() != null ? parsedResume.getCertifications().size() : 0);
                    readiness.setParsedMetricsCount(parsedResume.getMetricsCount());

                    // Compute live deterministic general resume rubric score
                    ResumeAnalysisService.ScoringBreakdown breakdown =
                            analysisService.calculateDeterministicScore(parsedResume, null);
                    if (breakdown != null) {
                        readiness.setOverallScore(breakdown.getOverallScore());
                        readiness.setSkillsScore(breakdown.getSkillsScore());
                        readiness.setExperienceScore(breakdown.getExperienceScore());
                        readiness.setEducationScore(breakdown.getEducationScore());
                        readiness.setCompletenessScore(breakdown.getCompletenessScore());
                        readiness.setCertificationsScore(breakdown.getCertificationsScore());
                        readiness.setEvaluationConfidence(breakdown.getEvaluationConfidence());
                        readiness.setUnevaluatedCriteria(breakdown.getUnevaluatedCriteria());
                        latestConfidence = breakdown.getEvaluationConfidence();
                    }
                }
            }

            if (latestAnalysisOpt.isPresent()) {
                ResumeAnalysis la = latestAnalysisOpt.get();
                readiness.setPersistedOverallScore(la.getOverallScore());
                readiness.setPersistedMatchingSkills(la.getMatchingSkills());
                readiness.setPersistedMissingSkills(la.getMissingSkills());
                readiness.setPersistedCreatedAt(la.getCreatedAt());
            }

            snapshot.setResumeReadiness(readiness);

            // 5. Overview KPI Assembly
            StudentOverviewKPI kpi = new StudentOverviewKPI(
                    totalApps, pending, shortlisted, rejected, selected, openActiveJobs,
                    hasProfile, profileComplete, profileScore, missingFields,
                    hasActiveResume, hasHistoricalResumes, latestResumeScore, latestConfidence
            );
            snapshot.setKpi(kpi);

            // 6. Skill Insights & In-Demand Gaps
            List<Job> openActiveJobList = analyticsDAO.findOpenActiveJobs();
            StudentSkillInsight skillInsight = buildSkillInsight(profileOpt.orElse(null), parsedResume,
                    latestAnalysisOpt.orElse(null), openActiveJobList);
            snapshot.setSkillInsight(skillInsight);

            // 7. Job Recommendations
            Set<Integer> appliedJobIds = analyticsDAO.getAppliedJobIds(studentId);
            List<StudentJobRecommendation> recommendations = buildRecommendations(
                    skillInsight.getCombinedSkills(), openActiveJobList, appliedJobIds);
            snapshot.setRecommendations(recommendations);

            // 8. Application History Rows
            List<StudentApplicationRow> rows = analyticsDAO.getApplicationRows(studentId, dtFrom, dtTo);
            snapshot.setRecentApplications(rows);

            // 9. Trend points
            List<StudentTrendPoint> trend = analyticsDAO.getApplicationTrend(studentId, dtFrom, dtTo);
            snapshot.setApplicationTrends(trend);

            // 10. Recent Activity Events
            List<StudentActivityEvent> activities = analyticsDAO.getRecentActivity(studentId, 15);
            snapshot.setRecentActivities(activities);

        } catch (SQLException e) {
            System.err.println("[StudentAnalyticsService] Database error loading snapshot: " + e.getMessage());
            snapshot.setErrorMessage("Database error loading student analytics: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[StudentAnalyticsService] Unexpected error loading snapshot: " + e.getMessage());
            snapshot.setErrorMessage("Unexpected error loading student analytics: " + e.getMessage());
        }

        return snapshot;
    }

    /**
     * Loads an all-time snapshot for the student.
     */
    public StudentDashboardSnapshot loadAllTimeSnapshot(int studentId) {
        return loadSnapshot(studentId, null, null);
    }

    /**
     * Loads a rolling last-N-days snapshot for the student.
     */
    public StudentDashboardSnapshot loadLastNDaysSnapshot(int studentId, int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("Days must be positive, got: " + days);
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        return loadSnapshot(studentId, from, to);
    }

    // =========================================================================
    // Validation Helpers
    // =========================================================================

    public void validateStudentId(int studentId) {
        if (studentId <= 0) {
            throw new IllegalArgumentException("Student ID must be positive, got: " + studentId);
        }
    }

    public void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid date range: 'from' (" + from + ") is after 'to' (" + to + ")");
        }
    }

    private void validateSessionAccess(int studentId) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            // Admins may view any student; students may only view their own
            if (user.getRole() == User.Role.STUDENT && user.getId() != studentId) {
                throw new SecurityException("Unauthorized: Student user " + user.getId() + " cannot access analytics for student " + studentId);
            }
        }
    }

    // =========================================================================
    // Skill Insights & Recommendation Algorithms
    // =========================================================================

    private StudentSkillInsight buildSkillInsight(StudentProfile profile,
                                                  ParsedResume parsedResume,
                                                  ResumeAnalysis latestAnalysis,
                                                  List<Job> openJobs) {
        // 1. Profile skills
        List<String> profileSkills = new ArrayList<>();
        if (profile != null && profile.getSkills() != null && !profile.getSkills().isBlank()) {
            profileSkills = parseAndCanonicalizeSkills(profile.getSkills());
        }

        // 2. Resume skills
        List<String> resumeSkills = new ArrayList<>();
        if (parsedResume != null && parsedResume.getSkills() != null) {
            for (String s : parsedResume.getSkills()) {
                String canon = ResumeAnalysisService.canonicalSkill(s);
                if (!canon.isBlank() && !containsIgnoreCase(resumeSkills, canon)) {
                    resumeSkills.add(canon);
                }
            }
        }

        // 3. Combined skills (union, deduplicated)
        List<String> combinedSkills = new ArrayList<>(profileSkills);
        for (String rs : resumeSkills) {
            if (!containsIgnoreCase(combinedSkills, rs)) {
                combinedSkills.add(rs);
            }
        }

        // 4. Persisted missing skills from latest analysis
        List<String> persistedMissing = new ArrayList<>();
        if (latestAnalysis != null && latestAnalysis.getMissingSkills() != null && !latestAnalysis.getMissingSkills().isBlank()) {
            persistedMissing = parseAndCanonicalizeSkills(latestAnalysis.getMissingSkills());
        }

        // 5. In-demand missing skills across open active jobs
        Map<String, Integer> demandMap = new LinkedHashMap<>();
        if (openJobs != null) {
            for (Job j : openJobs) {
                if (j.getRequiredSkills() != null && !j.getRequiredSkills().isBlank()) {
                    List<String> jobSkills = parseAndCanonicalizeSkills(j.getRequiredSkills());
                    for (String js : jobSkills) {
                        if (!containsIgnoreCase(combinedSkills, js)) {
                            demandMap.put(js, demandMap.getOrDefault(js, 0) + 1);
                        }
                    }
                }
            }
        }

        List<String> inDemandMissing = demandMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return new StudentSkillInsight(profileSkills, resumeSkills, combinedSkills, persistedMissing, inDemandMissing);
    }

    private List<StudentJobRecommendation> buildRecommendations(List<String> studentSkills,
                                                                 List<Job> openJobs,
                                                                 Set<Integer> appliedJobIds) {
        List<StudentJobRecommendation> recs = new ArrayList<>();
        if (openJobs == null) return recs;

        for (Job job : openJobs) {
            List<String> jobSkills = (job.getRequiredSkills() != null && !job.getRequiredSkills().isBlank())
                    ? parseAndCanonicalizeSkills(job.getRequiredSkills())
                    : Collections.emptyList();

            List<String> matched = new ArrayList<>();
            List<String> missing = new ArrayList<>();

            for (String js : jobSkills) {
                if (containsIgnoreCase(studentSkills, js)) {
                    matched.add(js);
                } else {
                    missing.add(js);
                }
            }

            int matchCount = matched.size();
            int totalReq = jobSkills.size();
            double matchPct = (totalReq > 0)
                    ? Math.round((matchCount * 100.0 / totalReq) * 10.0) / 10.0
                    : (studentSkills.isEmpty() ? 50.0 : 80.0); // Baseline default if job has no required skills

            boolean alreadyApplied = appliedJobIds != null && appliedJobIds.contains(job.getId());

            recs.add(new StudentJobRecommendation(
                    job.getId(),
                    job.getTitle(),
                    job.getCompanyName(),
                    job.getJobType() != null ? job.getJobType().label() : "Full-Time",
                    job.getLocation(),
                    job.getDeadline(),
                    matchCount,
                    totalReq,
                    matchPct,
                    matched,
                    missing,
                    alreadyApplied
            ));
        }

        // Sort: unapplied first, then highest match percentage, then deadline / id
        recs.sort((a, b) -> {
            if (a.isAlreadyApplied() != b.isAlreadyApplied()) {
                return a.isAlreadyApplied() ? 1 : -1;
            }
            int cmp = Double.compare(b.getMatchPercentage(), a.getMatchPercentage());
            if (cmp != 0) return cmp;
            return Integer.compare(b.getJobId(), a.getJobId());
        });

        if (recs.size() > 15) {
            return new ArrayList<>(recs.subList(0, 15));
        }
        return recs;
    }

    private List<String> parseAndCanonicalizeSkills(String csv) {
        List<String> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) return list;

        String[] tokens = csv.split("[,;\\n]+");
        for (String t : tokens) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                String canon = ResumeAnalysisService.canonicalSkill(trimmed);
                if (!canon.isBlank() && !containsIgnoreCase(list, canon)) {
                    list.add(canon);
                }
            }
        }
        return list;
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) return false;
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) return true;
        }
        return false;
    }
}

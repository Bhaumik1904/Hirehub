# HIREHUB — PHASE 6 STEP 4.2B.2.1: STUDENT JOB VISIBILITY SECURITY CORRECTION REPORT
**Verification & Completion Date:** November 14, 2026  
**Target Environment:** Java 21 | JavaFX 21 | MySQL 8 (`hirehub_db`) | HikariCP 5.1.0  
**Phase:** Phase 6 Step 4.2B.2.1 (Student Job Visibility Security Correction)  
**Status:** **100% COMPLETE & VERIFIED** (119 Java Sources Compiled Cleanly, 270/270 DAO Verifier Tests Passed, 856/856 Cumulative Regression Tests Passed, Strict Scope Boundary Adhered)

---

## Executive Summary

Phase 6 Step 4.2B.2.1 addresses and resolves a critical security vulnerability identified in the initial Step 4.2B.2 student-facing job discovery queries. Previously, `searchApprovedActive()` and `findOpenApprovedActiveJobs()` in `JobDAO` filtered solely on job lifecycle status (`status = 'ACTIVE'`) and moderation status (`moderation_status = 'APPROVED'`). However, they did not inspect the owning recruiter's account status or profile verification status.

Without joining `users` and `recruiter_profiles`, jobs posted by unverified, pending, rejected, suspended, or deactivated recruiters could potentially be displayed to students.

Step 4.2B.2.1 corrects this at the data access layer by enforcing a complete, fail-closed multi-table join and predicate chain across `jobs`, `users`, and `recruiter_profiles`.

All 15 security scenarios specified in the mandate have been implemented and verified against live MySQL:
- Only jobs with active recruiter accounts (`users.is_active = TRUE`), role `RECRUITER`, verified recruiter profiles (`recruiter_profiles.verification_status = 'VERIFIED'`), active job lifecycle status (`jobs.status = 'ACTIVE'`), approved moderation status (`jobs.moderation_status = 'APPROVED'`), and valid non-expired deadlines are visible to students.
- All 12 negative scenarios (unverified, pending, rejected, suspended, or deactivated recruiters; unprofiled recruiters; unapproved jobs; non-active jobs; expired deadlines) fail closed and remain strictly invisible to students.
- Recruiter-owned queries and admin review queries remain completely uninhibited.

---

## 1. Root Cause Analysis

In Phase 6 Step 4.2B.2, `JobDAO` introduced:
```sql
SELECT ... FROM jobs 
WHERE status='ACTIVE' AND moderation_status='APPROVED' 
  AND (title LIKE ? OR company_name LIKE ?) ...
```
and
```sql
SELECT ... FROM jobs 
WHERE status='ACTIVE' AND moderation_status='APPROVED' 
  AND (deadline IS NULL OR deadline >= CURDATE()) ...
```

### Vulnerability Mechanics
1. **Recruiter Account Deactivation Bypass:** If an administrator deactivated a recruiter account in `users` (`is_active = FALSE`), that recruiter's approved active jobs continued to appear in student discovery.
2. **Recruiter Verification Bypass:** If a recruiter profile was `UNVERIFIED`, `PENDING`, `REJECTED`, or `SUSPENDED`, or if a recruiter had no profile record in `recruiter_profiles`, any active approved job remained discoverable.
3. **Deadlines in Search:** `searchApprovedActive` previously lacked deadline filtering (`(deadline IS NULL OR deadline >= CURDATE())`), creating inconsistent deadline semantics between search and recommendation lists.

---

## 2. Files Modified

In strict accordance with the allowable modification scope:
1. `src/main/java/com/hirehub/dao/JobDAO.java`
   - Added prefixed column list `JOB_COLS_PREFIXED` (`j.id, j.recruiter_id, ...`) to avoid column label collision during relational joins.
   - Updated `SQL_SEARCH_APPROVED_ACTIVE` with inner joins on `users u` and `recruiter_profiles rp` and fail-closed predicates.
   - Updated `SQL_FIND_OPEN_APPROVED_ACTIVE` with identical joins and predicates.
   - Preserved `public List<Job> searchApprovedActive(String keyword, String location, String jobType)` while adding a paginated overload `public List<Job> searchApprovedActive(String keyword, String location, String jobType, int limit, int offset)`.
   - Preserved `public List<Job> findOpenApprovedActiveJobs()`.
   - Kept all legacy methods (`searchActive`, `findOpenActiveJobs`, `findByRecruiterId`, `findByIdAndRecruiter`, `updateForRecruiter`) unchanged.

2. `src/main/java/com/hirehub/util/Phase6Step4_2B2DAOVerifier.java`
   - Added architecture reflection check for the paginated `searchApprovedActive` overload.
   - Added Section 3: `runStudentJobVisibilitySecuritySuite()` containing 98 new assertions testing all 15 visibility matrix scenarios, keyword evasion resistance, pagination boundaries, recruiter-owned queries, admin queries, duplicate prevention, and fixture teardown with baseline verification.

**Zero modifications were made to:**
- Models or Enums (`Job`, `RecruiterProfile`, `User`, `JobModerationStatus`, `RecruiterVerificationStatus`, `AdminAuditEntityType`)
- Other DAOs (`RecruiterDAO`, `AdminUserDAO`, `AdminModerationDAO`, `StudentDAO`, etc.)
- Any Service classes (`JobService`, `RecruiterService`, `AdminModerationService`, etc.)
- Controllers, FXML files, or CSS stylesheets
- Migration scripts, schema definitions, or database connection configurations.

---

## 3. Exact SQL Visibility Conditions & Joins

### 3.1 Schema Projection & Aliases
```sql
SELECT 
    j.id, j.recruiter_id, j.title, j.company_name, j.description, j.required_skills, 
    j.location, j.job_type, j.salary_range, j.eligibility, j.deadline, j.status, 
    j.moderation_status, j.moderation_reason, j.moderated_at, j.moderated_by, j.created_at
FROM jobs j
JOIN users u 
    ON u.id = j.recruiter_id
JOIN recruiter_profiles rp 
    ON rp.user_id = u.id
WHERE j.status = 'ACTIVE'
  AND j.moderation_status = 'APPROVED'
  AND u.is_active = TRUE
  AND u.role = 'RECRUITER'
  AND rp.verification_status = 'VERIFIED'
  AND (j.deadline IS NULL OR j.deadline >= CURDATE())
```

### 3.2 Dynamic Search Query (`SQL_SEARCH_APPROVED_ACTIVE`)
```sql
SELECT j.id, j.recruiter_id, j.title, j.company_name, j.description, j.required_skills, 
       j.location, j.job_type, j.salary_range, j.eligibility, j.deadline, j.status, 
       j.moderation_status, j.moderation_reason, j.moderated_at, j.moderated_by, j.created_at 
FROM jobs j 
JOIN users u ON u.id = j.recruiter_id 
JOIN recruiter_profiles rp ON rp.user_id = u.id 
WHERE j.status = 'ACTIVE' 
  AND j.moderation_status = 'APPROVED' 
  AND u.is_active = TRUE 
  AND u.role = 'RECRUITER' 
  AND rp.verification_status = 'VERIFIED' 
  AND (j.deadline IS NULL OR j.deadline >= CURDATE()) 
  AND (j.title LIKE ? OR j.company_name LIKE ?) 
  AND (? = '' OR j.location LIKE ?) 
  AND (? = '' OR j.job_type = ?) 
ORDER BY j.created_at DESC, j.id DESC
```

### 3.3 Open Jobs Query (`SQL_FIND_OPEN_APPROVED_ACTIVE`)
```sql
SELECT j.id, j.recruiter_id, j.title, j.company_name, j.description, j.required_skills, 
       j.location, j.job_type, j.salary_range, j.eligibility, j.deadline, j.status, 
       j.moderation_status, j.moderation_reason, j.moderated_at, j.moderated_by, j.created_at 
FROM jobs j 
JOIN users u ON u.id = j.recruiter_id 
JOIN recruiter_profiles rp ON rp.user_id = u.id 
WHERE j.status = 'ACTIVE' 
  AND j.moderation_status = 'APPROVED' 
  AND u.is_active = TRUE 
  AND u.role = 'RECRUITER' 
  AND rp.verification_status = 'VERIFIED' 
  AND (j.deadline IS NULL OR j.deadline >= CURDATE()) 
ORDER BY j.created_at DESC, j.id DESC
```

---

## 4. Joins and Relationship Verification

1. **`jobs.recruiter_id` -> `users.id`:**
   Foreign key `fk_jobs_recruiter` guarantees referential integrity to `users.id` (primary key).
2. **`recruiter_profiles.user_id` -> `users.id`:**
   Foreign key `fk_rp_user` with `UNIQUE KEY uq_recruiter_user (user_id)` guarantees at most 1 profile per user.
3. **Fail-Closed Semantics via Inner Joins:**
   Because an `INNER JOIN` is employed:
   - If a recruiter has no entry in `recruiter_profiles`, 0 rows are returned.
   - If a recruiter account is missing or corrupted in `users`, 0 rows are returned.
   - If `u.is_active` is FALSE, 0 rows are returned.
   - If `u.role != 'RECRUITER'`, 0 rows are returned.
   - If `rp.verification_status != 'VERIFIED'`, 0 rows are returned.
   - No fallback or NULL-coalescing is used; missing or non-matching status strictly defaults to hidden.
4. **Duplicate Prevention:**
   Because `u.id` is PK and `rp.user_id` is UNIQUE, the join is strictly 1:1:1. It cannot multiply rows or produce duplicate job entries.

---

## 5. Security Test Cases & Execution Results

All tests were executed against the live MySQL database (`hirehub_db`) using isolated fixtures prefixed with `DAO4_2B2_TEST_`.

### 5.1 The 15 Matrix Scenarios

| Scenario # | Test Scenario Description | Expected Student Visibility | Actual Live DB Result | Status |
|---|---|:---:|:---:|:---:|
| **S01** | Active recruiter, verified profile, active approved job, valid future deadline | **Visible** | Returned in open jobs and search | **PASS** |
| **S02** | Recruiter profile is `UNVERIFIED` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S03** | Recruiter profile is `PENDING` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S04** | Recruiter profile is `REJECTED` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S05** | Recruiter profile is `SUSPENDED` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S06** | Recruiter account is deactivated (`is_active = FALSE`) | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S07** | Recruiter account active, but has no recruiter profile in DB | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S08** | Job moderation is `PENDING` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S09** | Job moderation is `REJECTED` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S10** | Job moderation is `SUSPENDED` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S11** | Job lifecycle status is `DRAFT` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S12** | Job lifecycle status is `CLOSED` | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S13** | Job deadline expired (`CURDATE() - 1 day`) | **Hidden** | Excluded from open jobs and search | **PASS** |
| **S14** | Job deadline is today (`CURDATE()`) | **Visible** | Returned in open jobs and search | **PASS** |
| **S15** | Job deadline is `NULL` (no deadline set) | **Visible** | Returned in open jobs and search | **PASS** |

### 5.2 Additional Security Verifications
1. **Keyword Search Evasion Resistance:**
   Exact keyword searches matching hidden job titles (`JOB_S02` through `JOB_S13`) were executed against `searchApprovedActive(title, "", "")`. Every single query returned **0 results**. Exact title searches for visible jobs (`JOB_S01`, `JOB_S14`, `JOB_S15`) each returned **exactly 1 result**.
2. **Pagination Safety:**
   `searchApprovedActive(PREFIX, "", "", 2, 0)` returned the first 2 visible jobs. Page 2 (offset 2) returned the remaining 1 visible job. Page 3 (offset 4) returned 0 jobs. Hidden jobs were never leaked across page boundaries.
3. **Recruiter-Owned Management Queries Intact:**
   Recruiter A calling `findByRecruiterId(recAId)` successfully retrieved all 9 jobs owned by Recruiter A (including `DRAFT`, `CLOSED`, `PENDING`, `REJECTED`, `SUSPENDED`, and expired jobs). `findByIdAndRecruiter` confirmed Recruiter A can view and manage their own unapproved and inactive jobs.
4. **Admin Search & Moderation Queries Intact:**
   `searchJobsForAdmin` and `countJobsForAdmin` returned all 15 test jobs without filtering out unapproved or unverified records. Filtering by `PENDING`, `REJECTED`, `SUSPENDED`, `DRAFT`, and `CLOSED` worked as expected.
5. **Duplicate Prevention:**
   Distinct count matching confirmed zero duplicates in both `searchApprovedActive` and `findOpenApprovedActiveJobs`.

---

## 6. Regression Verification Across All 11 Suites

All test suites were executed to verify that no legacy functionality or verifier assumptions were broken:

| Test Suite | Verifier Main Class | Passed | Failed | Skipped | Status |
|---|---|:---:|:---:|:---:|:---:|
| **Phase 6 Step 4.2B.2 & 4.2B.2.1** | `com.hirehub.util.Phase6Step4_2B2DAOVerifier` | **270** | 0 | 0 | **PASS** |
| **Phase 6 Step 4.1 Admin Moderation** | `com.hirehub.util.Phase6Step4_1AdminUserManagementVerifier` | **115** | 0 | 0 | **PASS** |
| **Phase 6 Step 4.2A Migration** | `com.hirehub.util.Phase6Step4_2AMigrationVerifier` | **73** | 0 | 0 | **PASS** |
| **Phase 6 Step 3 Connection Pool** | `com.hirehub.util.Phase6Step3ConnectionPoolVerifier` | **96** | 0 | 0 | **PASS** |
| **Phase 6 Step 2 Resume Snapshot** | `com.hirehub.util.Phase6Step2ResumeSnapshotVerifier` | **67** | 0 | 0 | **PASS** |
| **Phase 6 Step 1 Recruiter Security** | `com.hirehub.util.Phase6Step1RecruiterSecurityVerifier` | **60** | 0 | 0 | **PASS** |
| **Phase 5 Step 4B Student Analytics** | `com.hirehub.util.Phase5Step4BStudentAnalyticsVerifier` | **97** | 0 | 0 | **PASS** |
| **Phase 5 Step 3A Recruiter Security** | `com.hirehub.util.Phase5Step3ASecurityVerifier` | **36** | 0 | 0 | **PASS** |
| **Phase 5 Step 3C Recruiter Dashboard** | `com.hirehub.util.Phase5Step3CRecruiterDashboardVerifier` | **21** | 0 | 0 | **PASS** |
| **Recruiter Applicant Management** | `com.hirehub.util.RecruiterApplicantVerifier` | **15** | 0 | 0 | **PASS** |
| **Admin Login Path** | `com.hirehub.util.AdminLoginVerifier` | **6** | 0 | 0 | **PASS** |
| **CUMULATIVE TOTAL** | **11 Suites** | **856** | **0** | **0** | **100% PASS** |

---

## 7. Database Baseline Row Counts

Database row counts in `hirehub_db` were verified before tests and immediately after fixture teardown:

| Table Name | Pre-Test Baseline | Post-Test Verified | Match Status |
|---|:---:|:---:|:---:|
| `users` | 3 | 3 | **MATCH** |
| `recruiter_profiles` | 1 | 1 | **MATCH** |
| `jobs` | 1 | 1 | **MATCH** |
| `admin_audit_logs` | 0 | 0 | **MATCH** |
| `applications` | 1 | 1 | **MATCH** |
| `resumes` | 3 | 3 | **MATCH** |
| `resume_analyses` | 1 | 1 | **MATCH** |
| `student_profiles` | 1 | 1 | **MATCH** |

Zero test data leaked into production records.

---

## 8. Confirmation of Recruiter & Admin Functionality

- **Recruiter Job Management:**
  Recruiters retain full access to their own jobs via `findByRecruiterId`, `findByIdAndRecruiter`, `updateForRecruiter`, `updateStatusForRecruiter`, and `deleteForRecruiter`. None of the student visibility predicates affect recruiter workflows.
- **Admin Review & Moderation:**
  Administrators retain unhindered visibility to all jobs across any lifecycle status (`ACTIVE`, `DRAFT`, `CLOSED`) and moderation status (`PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`) via `searchJobsForAdmin` and `countJobsForAdmin`.
- **Legacy Backward Compatibility:**
  `findOpenActiveJobs()` and `searchActive()` continue to filter solely on `status = 'ACTIVE'`, preserving full backward compatibility for internal reporting and legacy caller chains.

---

## 9. Scope & Code Change Verification

Only two files were modified in Step 4.2B.2.1:
1. `src/main/java/com/hirehub/dao/JobDAO.java`
2. `src/main/java/com/hirehub/util/Phase6Step4_2B2DAOVerifier.java`

Zero modifications were made to service classes, controllers, FXML, CSS, models, enums, migrations, or database infrastructure.

---

## 10. Unresolved Concerns

None. The student job discovery queries fail closed under all edge conditions, all 15 scenario tests pass, and all 856 cumulative regression tests pass cleanly.

---

## 11. STOP & Next Steps

Phase 6 Step 4.2B.2.1 is **100% complete and verified**.

In adherence with the prompt's final instruction:
- **No service-layer authorization code has been modified.**
- **No controllers or UI workflows have been modified.**
- **Standing by for user approval before proceeding to Phase 6 Step 4.2C: Service-Layer Security & Authorization.**


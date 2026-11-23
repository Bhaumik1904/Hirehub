# HireHub – Phase 5 Step 4B.1: Student Service & DAO Security Closure Report

**Location**: `D:\HireHub`  
**Date**: November 12, 2026  
**Status**: COMPLETE (All 315 Verifier Tests Passing Across 7 Suites)

---

## 1. Executive Summary



During the Phase 5 Step 4B verification, a targeted security gap was identified: while UI controllers had fail-closed authentication checks, student-facing services and DAOs relied heavily on client/controller guards or non-positive integer checks (`studentId <= 0`) rather than validating session identity and SQL ownership predicates. Consequently, an authenticated student (or any caller directly referencing a service instance) could manipulate or inspect another student's profile, resume, analysis records, or application history by simply supplying another student's positive ID.

In **Phase 5 Step 4B.1**, we completed a comprehensive security audit and implemented service-level session authorization, atomic SQL ownership predicates, and clean administrative separation:
1. **Service-Level Authorization Enforced**: Student-facing methods now strictly enforce that an authenticated session exists, that the session user has `User.Role.STUDENT`, and that `sessionUser.getId() == studentId`. Unauthorized callers fail closed (returning empty Optionals, empty collections, failed `ServiceResult`s, or throwing `SecurityException`).
2. **Administrative Separation (`*ForAdmin`)**: Rather than allowing administrators to silently bypass general student APIs, explicitly named and narrowly scoped Admin methods (`getProfileForAdmin`, `saveProfileForAdmin`, `getActiveResumeForAdmin`, `getAllResumesForAdmin`, `deleteResumeForAdmin`, `saveAnalysisForAdmin`, `getStudentAnalysisHistoryForAdmin`, `getStudentApplicationsForAdmin`) were created, requiring `SessionManager.getInstance().hasRole(User.Role.ADMIN)`.
3. **Atomic DAO Ownership Predicates**: Scoped queries (`WHERE id = ? AND student_id = ?`) were introduced and verified on mutations and lookups (`ResumeDAO.findByIdForStudent`, `deleteForStudent`, `updateForStudent`; `ResumeAnalysisDAO.findByIdForStudent`, `deleteForStudent`). Legacy unscoped DAO methods were annotated with `@Deprecated` and documented.
4. **Zero-Regression Verification**: 7 verification suites were run against offline logic and live MySQL database fixtures, totaling **315 passed checks with 0 failures**. All temporary database fixtures were 100% cleaned up, leaving baseline row counts restored.

---

## 2. Caller & Call Path Audit

A full inspection was conducted across all student-facing services and DAOs:

| Method | Callers | Vulnerability Before Step 4B.1 | Resolution in Step 4B.1 |
| :--- | :--- | :--- | :--- |
| `StudentService.getProfile(int userId)` | `StudentDashboardController`, `AIAssistantController`, Verifiers | Allowed reading any student's profile by ID if caller knew the positive integer ID. | Enforced `isAuthorizedStudent(userId)`. Returns `Optional.empty()` on unauthorized calls. Added `getProfileForAdmin`. |
| `StudentService.saveProfile(int userId, ...)` | `StudentDashboardController`, Verifiers | Allowed overwriting another student's profile fields by ID. | Enforced `isAuthorizedStudent(userId)`. Returns `ServiceResult.fail()` on unauthorized calls. Added `saveProfileForAdmin`. |
| `ApplicationService.apply(int studentId, int jobId, String)` | `JobBoardController`, Verifiers | Allowed submitting applications on behalf of another student ID. | Enforced `isAuthorizedStudent(studentId)`. Fails safely with `ServiceResult.fail()`. |
| `ApplicationService.hasApplied(int studentId, int jobId)` | `JobBoardController`, Verifiers | Allowed probing whether any student applied to any job. | Enforced `isAuthorizedStudent(studentId)`. Returns `false` on unauthorized calls. |
| `ApplicationService.getStudentApplications(int studentId)` | `MyApplicationsController`, Verifiers | Allowed viewing all applications and statuses belonging to another student. | Enforced `isAuthorizedStudent(studentId)`. Returns empty list. Added `getStudentApplicationsForAdmin`. |
| `ResumeService.uploadAndProcessResume(int studentId, File)` | `ResumeAnalyzerController`, Verifiers | Allowed storing and attaching resumes to any student ID. | Enforced `isAuthorizedStudent(studentId)`. Returns `UploadResult.fail()` on unauthorized calls. |
| `ResumeService.getActiveResume(int studentId)` | `ResumeAnalyzerController`, `StudentAnalyticsService`, Verifiers | Allowed viewing metadata and parsed content of any student's active resume. | Enforced `isAuthorizedStudent(studentId)`. Returns `Optional.empty()`. Added `getActiveResumeForAdmin`. |
| `ResumeService.getAllResumes(int studentId)` | `ResumeAnalyzerController`, Verifiers | Allowed enumerating historical resumes for any student ID. | Enforced `isAuthorizedStudent(studentId)`. Returns empty list. Added `getAllResumesForAdmin`. |
| `ResumeService.syncWithStudentProfile(int studentId, ParsedResume)` | `ResumeAnalyzerController`, Verifiers | Allowed mutating another student's profile data with extracted resume text. | Enforced `isAuthorizedStudent(studentId)`. Returns `false` on unauthorized calls. |
| `ResumeService.deleteResume(int resumeId, int studentId)` | `ResumeAnalyzerController`, Verifiers | Prior to 4B, relied on unscoped `delete(resumeId)`. In 4B, was scoped by ID, but did not check session caller identity. | Enforced `isAuthorizedStudent(studentId)` AND atomic `resumeDAO.deleteForStudent(resumeId, studentId)`. Added `deleteResumeForAdmin`. |
| `ResumeAnalysisService.saveAnalysis(...)` | `ResumeAnalyzerController`, Verifiers | Allowed persisting AI analysis rows tied to another student's account. | Enforced `isAuthorizedStudent(studentId)`. Throws `SecurityException` on mismatch. Added `saveAnalysisForAdmin`. |
| `ResumeAnalysisService.getStudentAnalysisHistory(int studentId, int limit)` | `ResumeAnalyzerController`, Verifiers | Allowed inspecting score history and AI critiques of another student. | Enforced `isAuthorizedStudent(studentId)`. Returns empty list. Added `getStudentAnalysisHistoryForAdmin`. |
| `ResumeDAO.findById(int id)` | `Phase4RealStudentE2ETest` | Unscoped lookup by primary key without owner check. | Annotated `@Deprecated`. Replaced in student workflows with `findByIdForStudent(id, studentId)`. |
| `ResumeDAO.update(Resume r)` | Unused in production code | Unscoped update without owner predicate. | Annotated `@Deprecated`. Added atomic `updateForStudent(Resume r, int studentId)`. |
| `ResumeDAO.delete(int id)` | Tests only | Unscoped deletion by primary key. | Annotated `@Deprecated`. Replaced in student workflows with `deleteForStudent(id, studentId)`. |
| `ResumeAnalysisDAO.findById(int id)` | Tests only | Unscoped analysis lookup. | Annotated `@Deprecated`. Replaced in student workflows with `findByIdForStudent(id, studentId)`. |
| `ResumeAnalysisDAO.delete(int id)` | Tests only | Unscoped analysis deletion. | Annotated `@Deprecated`. Replaced in student workflows with `deleteForStudent(id, studentId)`. |

---

## 3. Files Modified & Added

### Modified Existing Files
1. `D:\HireHub\src\main\java\com\hirehub\dao\ResumeDAO.java`:
   - Added `SQL_UPDATE_FOR_STUDENT = "UPDATE resumes SET ... WHERE id = ? AND student_id = ?"`.
   - Added `updateForStudent(Resume resume, int studentId)`.
   - Annotated legacy unscoped methods with `@Deprecated`: `findById(int)`, `update(Resume)`, `delete(int)`.
2. `D:\HireHub\src\main\java\com\hirehub\dao\ResumeAnalysisDAO.java`:
   - Annotated legacy unscoped methods with `@Deprecated`: `findById(int)`, `delete(int)`.
3. `D:\HireHub\src\main\java\com\hirehub\service\StudentService.java`:
   - Added `isAuthorizedStudent(int userId)` helper.
   - Guarded `getProfile(userId)` and `saveProfile(userId, ...)`.
   - Added `getProfileForAdmin(int userId)` and `saveProfileForAdmin(int userId, ...)`.
4. `D:\HireHub\src\main\java\com\hirehub\service\ResumeService.java`:
   - Added `isAuthorizedStudent(int studentId)` helper.
   - Guarded `uploadAndProcessResume`, `getActiveResume`, `getAllResumes`, `syncWithStudentProfile`, `deleteResume`.
   - Added `getActiveResumeForAdmin(int studentId)`, `getAllResumesForAdmin(int studentId)`, and `deleteResumeForAdmin(int resumeId, int studentId)`.
5. `D:\HireHub\src\main\java\com\hirehub\service\ResumeAnalysisService.java`:
   - Added `isAuthorizedStudent(int studentId)` helper.
   - Guarded `saveAnalysis` (throws `SecurityException` on session mismatch) and `getStudentAnalysisHistory`.
   - Added `saveAnalysisForAdmin` and `getStudentAnalysisHistoryForAdmin`.
6. `D:\HireHub\src\main\java\com\hirehub\service\ApplicationService.java`:
   - Added `isAuthorizedStudent(int studentId)` helper.
   - Guarded student-facing operations: `apply`, `hasApplied`, `getStudentApplications`.
   - Added `getStudentApplicationsForAdmin(int studentId)`.
   - Left all recruiter-facing methods (`*ForRecruiter`) strictly untouched.
7. `D:\HireHub\src\main\java\com\hirehub\util\Phase5Step3ASecurityVerifier.java`:
   - Wrapped `SessionManager.getInstance().login(student)` around student application submissions and inquiries to align with enforced service authorization.

### New Test Suite
1. `D:\HireHub\src\main\java\com\hirehub\util\Phase5Step4B1SecurityVerifier.java`:
   - 97 automated tests covering offline authorization checks, reflection deprecation verification, and live MySQL tenant isolation tests.

---

## 4. Verification & Test Results

All test suites were executed cleanly using `verify_phase4.ps1`:

### Suite 1: Phase 5 Step 4B.1 Student Security Closure Verifier (`Phase5Step4B1SecurityVerifier`)
```
=================================================================
HireHub Phase 5 Step 4B.1: Student Service & DAO Security Closure
=================================================================
--- [1] Running Offline Service Authorization & DAO Deprecation Suite ---
  [PASS] 12 Unauthenticated service calls fail safe
  [PASS] 12 Cross-student spoofed calls fail safe
  [PASS] 8 Cross-role recruiter calls fail safe
  [PASS] 9 Admin silent bypass attempts fail safe
  [PASS] 8 Non-admin calls to *ForAdmin methods fail safe
  [PASS] 10 DAO @Deprecated and scoped method contracts verified
--- [2] Running Live Database Tenant Isolation & Integrity Suite ---
  [PASS] 7 Legitimate student operations succeed
  [PASS] 10 Cross-student operations blocked and DB rows untouched
  [PASS] 5 Direct DAO atomic predicates block cross-tenant lookups & mutations
  [PASS] 8 Admin *ForAdmin operations succeed
  [PASS] 2 Student self-deletion operations succeed
  [PASS] 6 Database table restoration checks (100% baseline match)
-----------------------------------------------------------------
SECURITY CLOSURE SUMMARY: 97 PASSED, 0 FAILED, 0 SKIPPED
=================================================================
```

### Suite 2: Step 4B Student Security & Analytics Verifier (`Phase5Step4BStudentAnalyticsVerifier`)
```
-----------------------------------------------------------------
VERIFICATION SUMMARY: 97 PASSED, 0 FAILED, 0 SKIPPED
=================================================================
```

### Suite 3: Step 3A Recruiter Security Hardening Verifier (`Phase5Step3ASecurityVerifier`)
```
=================================================================
  STEP 3A SECURITY SUMMARY: 36 PASSED, 0 FAILED, 0 SKIPPED
=================================================================
```

### Suite 4: Step 3B Recruiter Analytics Backend Verifier (`Phase5RecruiterAnalyticsVerifier`)
```
-----------------------------------------------------------------
VERIFICATION SUMMARY: 50 PASSED, 0 FAILED, 0 SKIPPED
=================================================================
```

### Suite 5: Step 1 Admin Analytics Verifier (`Phase5AnalyticsVerifier`)
```
=================================================================
  VERIFICATION SUMMARY: 14 PASSED, 0 FAILED, 0 SKIPPED
=================================================================
```

### Suite 6: Recruiter Applicant Management Verifier (`RecruiterApplicantVerifier`)
```
-----------------------------------------------------------------
Results: 15 passed, 0 failed.
=================================================================
```

### Suite 7: Admin Login Verifier (`AdminLoginVerifier`)
```
-----------------------------------------------------------------
Results: 6 PASSED / 0 FAILED
=================================================================
```

### Total Test Tally
| Suite | Passed | Failed | Skipped | Status |
| :--- | :---: | :---: | :---: | :---: |
| `Phase5Step4B1SecurityVerifier` | 97 | 0 | 0 | **PASS** |
| `Phase5Step4BStudentAnalyticsVerifier` | 97 | 0 | 0 | **PASS** |
| `Phase5Step3ASecurityVerifier` | 36 | 0 | 0 | **PASS** |
| `Phase5RecruiterAnalyticsVerifier` | 50 | 0 | 0 | **PASS** |
| `Phase5AnalyticsVerifier` | 14 | 0 | 0 | **PASS** |
| `RecruiterApplicantVerifier` | 15 | 0 | 0 | **PASS** |
| `AdminLoginVerifier` | 6 | 0 | 0 | **PASS** |
| **Total** | **315** | **0** | **0** | **100% GREEN** |

---

## 5. Live MySQL Database Restoration

Every live database test suite cleans up all created fixtures in a strict `try-finally` block. Baseline table row counts were audited before and after test execution:

| Table | Baseline Rows | Final Rows After Suite Execution | Status |
| :--- | :---: | :---: | :---: |
| `users` | 3 | 3 | Restored |
| `jobs` | 1 | 1 | Restored |
| `applications` | 1 | 1 | Restored |
| `resumes` | 3 | 3 | Restored |
| `resume_analyses` | 1 | 1 | Restored |
| `student_profiles` | 1 | 1 | Restored |

---

## 6. Remaining Limitations & Boundaries Preserved

1. **Scope Adherence**:
   - No FXML or CSS files were modified.
   - Step 4C (UI integration) was not initiated.
   - No database schemas were altered.
   - No Recruiter or Admin dashboards were modified.
   - No Step 3A recruiter security hardening was altered or weakened.
2. **Legacy DAO Methods**:
   - Unscoped DAO methods (`ResumeDAO.findById`, `ResumeDAO.delete`, `ResumeAnalysisDAO.findById`, `ResumeAnalysisDAO.delete`) remain present with `@Deprecated` annotations for legacy/test harness compatibility, but are guaranteed to be uncalled by any student-facing service paths.
3. **Admin Service Capabilities**:
   - Explicit Admin methods are restricted exclusively to `User.Role.ADMIN` and fail closed for all other roles or unauthenticated sessions.


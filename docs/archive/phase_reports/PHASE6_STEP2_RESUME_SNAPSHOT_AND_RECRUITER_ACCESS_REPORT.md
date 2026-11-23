# HireHub Phase 6 Step 2: Application Resume Snapshot + Secure Recruiter Resume Access (GAP-05 + GAP-01) Report

**Date:** 2026-11-13  
**Status:** COMPLETE & VERIFIED  
**Verifier:** `com.hirehub.util.Phase6Step2ResumeSnapshotVerifier` (67/67 PASSED)  
**Regression Suites:** 193/193 PASSED across Phase 6 Step 1, Phase 5 Step 4B.1, and Phase 5 Step 3A  
**Database Row Baselines:** 100% restored (users=3, recruiter_profiles=1, jobs=1, applications=1, resumes=3, resume_analyses=1, student_profiles=1)

---

## 1. Executive Summary

Phase 6 Step 2 closes two critical gaps identified in the HireHub Product Readiness & Gap Audit:

1. **GAP-05 (Application Resume Snapshot):** Previously, job applications in HireHub had no snapshot of the resume attached at the time of application submission. If a student subsequently uploaded, modified, or replaced their active resume, recruiters would see the newer resume rather than the historical artifact submitted for that job opening. Furthermore, resumes could be deleted even if they were part of submitted applications.  
   **Closure:** Added `applications.resume_id` referencing `resumes.id` with `ON DELETE RESTRICT ON UPDATE CASCADE`. When a student applies, their active resume is atomically snapshotted into the application record. Subsequent active resume changes leave historical applications completely unaltered. Resumes referenced by applications are strictly protected against deletion both in the service layer and via MySQL database constraints.

2. **GAP-01 (Secure Recruiter Resume Access):** Recruiters required a secure, authenticated, and isolated mechanism to view and download applicant resumes without exposing arbitrary filesystem access or leaking resumes across recruiters.  
   **Closure:** Implemented atomic SQL join authorization in `ResumeDAO` and `ApplicationService.getResumeForApplication(int applicationId)`, ensuring recruiters can access resumes *only* for applications submitted to jobs they own (`INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ?`). Hardened path resolution in `ResumeService.resolveAndValidateResumeFile(String storedPath)` to reject directory traversal (`..`), path escaping, and files outside `data/resumes/`. Legacy applications with `resume_id = NULL` are handled gracefully with a clear "Resume not available" status. Integrated "View Resume" and "Download Resume" controls into the recruiter applicant management interface.

---

## 2. Architecture & Implementation Details

### 2.1 Database Schema Migration (GAP-05)

- **Migration File:** `database/phase6_step2_migration.sql` (applied and recorded in `database/ensure_all_tables.sql`).
- Added column and foreign key to `applications`:
  ```sql
  ALTER TABLE applications
      ADD COLUMN resume_id INT NULL DEFAULT NULL AFTER student_id,
      ADD KEY idx_app_resume (resume_id),
      ADD CONSTRAINT fk_app_resume
          FOREIGN KEY (resume_id) REFERENCES resumes (id)
          ON DELETE RESTRICT ON UPDATE CASCADE;
  ```
- **Constraint Behavior:** `ON DELETE RESTRICT` guarantees at the storage engine level that no referenced resume row can be dropped while an application record points to it (yielding MySQL error 1451 on rogue delete attempts).

### 2.2 Model & DAO Layer

- **`Application.java`:** Added `private Integer resumeId`, constructors, and `getResumeId()` / `setResumeId(Integer)`.
- **`ApplicationDAO.java`:**
  - Updated `SQL_INSERT` to include `resume_id`.
  - Added overloaded `insert(int jobId, int studentId, String coverLetter, Integer resumeId)`.
  - Maintained backward-compatible `insert(jobId, studentId, coverLetter)` setting `resume_id = null`.
  - Updated `SQL_FIND_BY_ID`, `SQL_FIND_BY_STUDENT`, `SQL_FIND_BY_JOB`, `SQL_FIND_BY_ID_AND_RECRUITER`, and `SQL_FIND_BY_JOB_AND_RECRUITER` to include `a.resume_id`.
  - Updated `mapRow()` to extract `resume_id` (handling SQL `NULL`).
  - Added `isResumeReferenced(int resumeId)` for fast reference existence checks.
- **`ResumeDAO.java`:**
  - Added `findResumeForApplicationAndRecruiter(int applicationId, int recruiterId)`:
    ```sql
    SELECT r.id, r.student_id, r.file_name, r.stored_path, r.file_size_bytes, r.file_hash,
           r.parsed_text, r.parsed_json, r.is_active, r.uploaded_at, r.updated_at
    FROM applications a
    INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ?
    INNER JOIN resumes r ON r.id = a.resume_id AND r.student_id = a.student_id
    WHERE a.id = ?
    ```
  - Added `findResumeForApplicationForAdmin(int applicationId)`.
  - Added `isReferencedByAnyApplication(int resumeId)`.

### 2.3 Service Layer Hardening

- **`ResumeService.java`:**
  - Added `resolveAndValidateResumeFile(String storedPath)`:
    - Rejects `null`, empty, or blank paths.
    - Fast-rejects directory traversal tokens (`..`).
    - Verifies file exists, is regular, and readable.
    - Obtains canonical `toRealPath()` and verifies it resides strictly within `getStorageDirectory().toRealPath()`.
  - Added `isResumeReferencedByApplication(int resumeId)`.
  - Hardened `deleteResume(int resumeId, int studentId)` and `deleteResumeForAdmin(int resumeId, int studentId)`:
    - Pre-checks `if (isResumeReferencedByApplication(resumeId)) return false;`
    - Prevents deleting physical files from disk or rows from MySQL when attached to applications.
- **`ApplicationService.java`:**
  - Added result type `ResumeAccessResult(boolean success, String message, Resume resume, File file)`.
  - Updated `apply(studentId, jobId, coverLetter)`:
    - Queries active resume via `resumeDAO.findActiveByStudentId(studentId)`.
    - Atomically captures `activeResume.getId()` into `applications.resume_id`.
    - Preserves application submission when student has no uploaded resume (`resume_id = NULL`).
  - Added overloaded `apply(studentId, jobId, coverLetter, requestedResumeId)` with student ownership verification.
  - Implemented `getResumeForApplication(int applicationId)`:
    - Requires active session with `RECRUITER` role.
    - Validates application belongs to recruiter's job via `applicationDAO.findByIdAndRecruiter(applicationId, recruiterId)`.
    - Checks for legacy applications (`resume_id == null`), returning friendly `"Resume not available for this application."`.
    - Retrieves resume entity atomically joined in SQL.
    - Resolves and validates physical file on disk within `data/resumes`.
  - Added `getResumeForApplicationForAdmin(int applicationId)` requiring `ADMIN` role.
  - Added `getResumeForApplicationForStudent(int applicationId, int studentId)`.

### 2.4 Recruiter UI Integration (GAP-01)

- **`applicant_management.fxml`:**
  - Added `viewResumeBtn` (`"📎 View Resume"`) to the table action bar.
  - Added `detailResumeStatus` label and `detailDownloadResumeBtn` (`"📥 Download Resume"`) to the applicant detail panel.
- **`ApplicantManagementController.java`:**
  - Injected `@FXML private Button viewResumeBtn;`, `@FXML private Label detailResumeStatus;`, and `@FXML private Button detailDownloadResumeBtn;`.
  - In `showDetail(Application app)`:
    - When `resumeId == null || resumeId <= 0`: Disables buttons and displays `"Resume not available (legacy application)"`.
    - When `resumeId > 0`: Enables buttons and displays `"Attached (Snapshot #<id>)"`.
  - Implemented `handleViewResume(ActionEvent)`:
    - Executes asynchronous task calling `applicationService.getResumeForApplication()`.
    - Opens the PDF using `java.awt.Desktop.getDesktop().open(file)`.
    - Gracefully falls back to download prompt if desktop opening is not supported in the OS environment.
  - Implemented `handleDownloadResume(ActionEvent)`:
    - Presents JavaFX `FileChooser` with candidate name and default filename to let recruiter save the resume locally.
- **`module-info.java`:** Added `requires java.desktop;` to permit `java.awt.Desktop` integration.

---

## 3. Files Modified and Created

| File | Type | Changes |
|---|---|---|
| `database/phase6_step2_migration.sql` | Created | DDL adding `resume_id` and foreign key constraint with `ON DELETE RESTRICT`. |
| `database/ensure_all_tables.sql` | Modified | Updated canonical table definitions to include `resume_id` and foreign key. |
| `src/main/java/com/hirehub/model/Application.java` | Modified | Added `resumeId` field, constructors, getters, and setters. |
| `src/main/java/com/hirehub/dao/ApplicationDAO.java` | Modified | Updated SQL statements, overloaded `insert`, `mapRow`, and added `isResumeReferenced`. |
| `src/main/java/com/hirehub/dao/ResumeDAO.java` | Modified | Added `findResumeForApplicationAndRecruiter`, `findResumeForApplicationForAdmin`, and `isReferencedByAnyApplication`. |
| `src/main/java/com/hirehub/service/ResumeService.java` | Modified | Added `resolveAndValidateResumeFile`, `isResumeReferencedByApplication`, and deletion guards. |
| `src/main/java/com/hirehub/service/ApplicationService.java` | Modified | Added `ResumeAccessResult`, snapshot logic in `apply`, and recruiter/admin resume access methods. |
| `src/main/resources/fxml/applicant_management.fxml` | Modified | Added View Resume button and detail panel resume status & download button. |
| `src/main/java/com/hirehub/controller/ApplicantManagementController.java` | Modified | Implemented FXML bindings, selection state updates, view, download, and fallback handlers. |
| `src/main/java/module-info.java` | Modified | Added `requires java.desktop;` for system desktop integration. |
| `src/main/java/com/hirehub/util/Phase6Step2ResumeSnapshotVerifier.java` | Created | Comprehensive automated verifier covering offline guards, snapshotting, access, and deletion. |

---

## 4. Verification & Test Results

### 4.1 Step 2 Dedicated Test Suite

Executed via `verify_phase4.ps1 -MainClass com.hirehub.util.Phase6Step2ResumeSnapshotVerifier`:

```
=================================================================
HireHub Phase 6 Step 2: Resume Snapshot & Secure Recruiter Access
=================================================================

--- [1] Running Offline Architecture, Path Traversal & Security Suite ---
  [PASS] Method exists: Application.getResumeId
  [PASS] Method exists: Application.setResumeId
  [PASS] Method exists: ApplicationDAO.insert
  [PASS] Method exists: ApplicationDAO.isResumeReferenced
  [PASS] Method exists: ResumeDAO.findResumeForApplicationAndRecruiter
  [PASS] Method exists: ResumeDAO.findResumeForApplicationForAdmin
  [PASS] Method exists: ResumeDAO.isReferencedByAnyApplication
  [PASS] Method exists: ResumeService.resolveAndValidateResumeFile
  [PASS] Method exists: ResumeService.isResumeReferencedByApplication
  [PASS] Method exists: ApplicationService.getResumeForApplication
  [PASS] Method exists: ApplicationService.getResumeForApplicationForAdmin
  [PASS] Method exists: ApplicationService.getResumeForApplicationForStudent
  [PASS] Path validation: null path returns empty
  [PASS] Path validation: empty path returns empty
  [PASS] Path validation: blank path returns empty
  [PASS] Path validation: '..' traversal rejected
  [PASS] Path validation: Windows root traversal rejected
  [PASS] Path validation: Non-existent file in storage dir returns empty
  [PASS] Path validation: Existing file outside storage dir (pom.xml) rejected
  [PASS] Path validation: Valid file inside data/resumes accepted
  [PASS] Path validation: File exists on disk
  [PASS] Unauth: getResumeForApplication fails safe
  [PASS] Unauth: getResumeForApplicationForAdmin fails safe
  [PASS] Student cannot call getResumeForApplication
  [PASS] Student blocked with unauthorized message
  [PASS] Recruiter cannot call getResumeForApplicationForAdmin
  [PASS] Recruiter blocked from admin endpoint
  [PASS] Admin cannot call standard recruiter getResumeForApplication
  [PASS] Admin blocked from recruiter endpoint without recruiter role
  [PASS] Invalid ID: 0 rejected
  [PASS] Invalid ID: negative rejected

--- [2] Running Live Database Tenant Isolation & Snapshot Suite ---
  [PASS] Student 1 application to Job 1 succeeds
  [PASS] Application 1 exists in DB
  [PASS] GAP-05: Application 1 snapshotted Resume A id (64)
  [PASS] Active resume is now Resume B
  [PASS] Application 1 still present
  [PASS] GAP-05: Historical Application 1 resume_id remained Resume A after active resume switched (64)
  [PASS] Student 1 application to Job 2 succeeds
  [PASS] Application 2 exists in DB
  [PASS] GAP-05: Application 2 snapshotted Resume B id (65)
  [PASS] GAP-01: Recruiter 1 successfully accesses resume for own job application
  [PASS] Recruiter 1 retrieved resume entity
  [PASS] Recruiter 1 received exact snapshot Resume A (64)
  [PASS] Recruiter 1 retrieved file handle
  [PASS] Recruiter 1 file exists on disk
  [PASS] GAP-01: Cross-recruiter access blocked (Recruiter 1 cannot access Application 2 resume)
  [PASS] Blocked message indicates unauthorized or not found
  [PASS] GAP-01: Recruiter 2 successfully accesses resume for own job application
  [PASS] Recruiter 2 received exact snapshot Resume B (65)
  [PASS] GAP-01: Cross-recruiter access blocked (Recruiter 2 cannot access Application 1 resume)
  [PASS] Legacy application access returns failure
  [PASS] Legacy application returns clear message (Resume not available for this application.)
  [PASS] GAP-05: Referenced Resume A deletion blocked in Service
  [PASS] Resume A still exists in DB after blocked deletion
  [PASS] Resume A file still exists on disk after blocked deletion
  [PASS] GAP-05: Admin cannot delete referenced resume
  [PASS] GAP-05: Direct SQL DELETE on referenced resume blocked by MySQL foreign key constraint (1451)
  [PASS] GAP-05: Unreferenced Resume D can be deleted successfully
  [PASS] Resume D row is gone from DB
  [PASS] Resume D file is removed from disk
  Cleaning up test fixtures and files...
  [PASS] Users table fully restored (3)
  [PASS] Recruiter profiles table fully restored (1)
  [PASS] Jobs table fully restored (1)
  [PASS] Applications table fully restored (1)
  [PASS] Resumes table fully restored (3)
  [PASS] Resume analyses table fully restored (1)
  [PASS] Student profiles table fully restored (1)

-----------------------------------------------------------------
STEP 2 VERIFICATION SUMMARY: 67 PASSED, 0 FAILED, 0 SKIPPED
=================================================================
```

### 4.2 Full Regression Suite

All existing verifiers executed and passed with zero errors:

| Verifier Class | Target Scope | Result | Status |
|---|---|---|---|
| `Phase6Step2ResumeSnapshotVerifier` | Phase 6 Step 2 (GAP-05 + GAP-01) | **67 / 67 PASSED** | ✅ GREEN |
| `Phase6Step1RecruiterSecurityVerifier` | Phase 6 Step 1 (GAP-03) | **60 / 60 PASSED** | ✅ GREEN |
| `Phase5Step4B1SecurityVerifier` | Phase 5 Step 4B.1 Student Security | **97 / 97 PASSED** | ✅ GREEN |
| `Phase5Step3ASecurityVerifier` | Phase 5 Step 3A Recruiter Hardening | **36 / 36 PASSED** | ✅ GREEN |
| **Total Automated Tests** | | **260 / 260 PASSED** | ✅ 100% |

### 4.3 Database Row Count Integrity

Pre-test and post-test table row counts on live MySQL (`hirehub_db`):
- `users`: 3
- `recruiter_profiles`: 1
- `jobs`: 1
- `applications`: 1
- `resumes`: 3
- `resume_analyses`: 1
- `student_profiles`: 1

---

## 5. Conclusion

Phase 6 Step 2 (GAP-05 + GAP-01) is fully implemented, verified, and regression-tested.
- Students' submitted applications permanently snapshot their active resume.
- Updating or replacing an active resume never affects historical applications.
- Resumes referenced by applications cannot be deleted by students or admins.
- Recruiters can securely inspect and download resumes only for applications submitted to their own jobs.
- Path traversal and arbitrary file reads are strictly rejected.
- Legacy applications without resumes are handled without errors.
- The build compiles cleanly with `build.ps1` and runs with `run.ps1`.


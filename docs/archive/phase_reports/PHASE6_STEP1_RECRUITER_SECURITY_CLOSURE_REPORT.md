# HireHub — Phase 6 Step 1: Recruiter Service Authorization Closure (GAP-03) Report

**Date:** November 12, 2026  
**Project:** HireHub (`D:\HireHub`)  
**Scope:** Recruiter Service-Layer Authorization Hardening & DAO Audit  
**Status:** COMPLETE (All Tests Passing, Zero Regressions)

---

## 1. Executive Summary

During the Phase 6 Product Readiness & Gap Audit, **GAP-03** identified a critical security disparity between student-facing services and recruiter-facing services:
- While `StudentService` and `ApplicationService` were hardened in Phase 5 Step 4B.1 to validate session user ID and role (`isAuthorizedStudent`), `RecruiterService` (`getProfile`, `saveProfile`) lacked service-layer identity enforcement.
- Relying solely on controller-level checks leaves the service layer vulnerable to cross-tenant profile reads or mutations if an attacker or buggy caller passes a different `userId`.

In this step, **HireHub Phase 6 Step 1**, we audited the entire `RecruiterService` and `RecruiterDAO` architecture, enforced fail-closed session-level authorization on all recruiter-facing operations, introduced explicit `*ForAdmin` operations, and verified tenant isolation with an automated 60-test verification suite (`Phase6Step1RecruiterSecurityVerifier`).

---

## 2. Call Path & Architecture Audit

### 2.1 Call Paths Inspected

1. **`RecruiterDashboardController.java`**:
   - `loadProfileAsync()` (line 1419): Invokes `recruiterService.getProfile(recruiterId)`. Now protected both at the UI controller layer (`ensureRecruiterAuthorized()`) and fail-closed at the service layer (`isAuthorizedRecruiter(userId)`).
   - `handleSaveProfile()` (line 1472): Invokes `recruiterService.saveProfile(recruiterId, ...)`. The recruiter ID is extracted directly from `SessionManager.getInstance().getCurrentUser().getId()`, and the service layer confirms session role `RECRUITER` and matching ID.
2. **`RecruiterDAO.java`**:
   - `findByUserId(int userId)`: Bound by `WHERE user_id=?`.
   - `update(RecruiterProfile p)`: Bound by `WHERE user_id=?`.
   - `insert(RecruiterProfile p)`: Persists `user_id`.
   - `saveOrUpdate(RecruiterProfile p)`: Checks `existsByUserId` before delegating to `update` or `insert`.
   - `recruiter_profiles` table: Possesses a database-level `UNIQUE KEY uq_recruiter_user (user_id)` constraint, preventing duplicate or disjoint profiles.
   - Added `deleteByUserId(int userId)`: Bound by `WHERE user_id=?` for clean atomic profile teardown.
3. **`JobService.java` & `ApplicationService.java`**:
   - Both were previously hardened during Phase 5 Step 3A with recruiter scoping (`*ForRecruiter` methods, atomic SQL predicates `WHERE j.id = ? AND j.recruiter_id = ?`).

---

## 3. Implementation Details

### 3.1 `RecruiterService.java`

1. **Constructor Overload for DI / Testing**:
   ```java
   public RecruiterService() {
       this(new RecruiterDAO());
   }

   public RecruiterService(RecruiterDAO recruiterDAO) {
       this.recruiterDAO = Objects.requireNonNullElseGet(recruiterDAO, RecruiterDAO::new);
   }
   ```
2. **Fail-Closed Session Authorization Helper**:
   ```java
   private boolean isAuthorizedRecruiter(int userId) {
       if (userId <= 0) return false;
       User current = SessionManager.getInstance().getCurrentUser();
       return current != null
               && current.getRole() == User.Role.RECRUITER
               && current.getId() == userId;
   }
   ```
3. **Protected Profile Read (`getProfile`)**:
   - Validates `userId > 0` and `isAuthorizedRecruiter(userId)`.
   - Returns `Optional.empty()` immediately if unauthenticated, non-recruiter, or mismatched recruiter ID.
4. **Protected Profile Mutation (`saveProfile`)**:
   - Validates `userId > 0`: returns `ServiceResult.fail("Invalid recruiter user ID.")`.
   - Validates `isAuthorizedRecruiter(userId)`: returns `ServiceResult.fail("Unauthorized: Session does not match recruiter ID " + userId)`.
   - Validates required inputs (`companyName` non-blank).
   - Prevents cross-recruiter profile overwrites.
5. **Explicit Admin Access Separation**:
   - `public Optional<RecruiterProfile> getProfileForAdmin(int userId)`: Requires `SessionManager.getInstance().hasRole(User.Role.ADMIN)`.
   - `public ServiceResult saveProfileForAdmin(...)`: Requires `SessionManager.getInstance().hasRole(User.Role.ADMIN)`.
   - Admin callers attempting to call generic recruiter methods (`getProfile`, `saveProfile`) are rejected fail-closed to prevent accidental recruiter session masquerading.

### 3.2 `RecruiterDAO.java`

- Added `deleteByUserId(int userId)` method for atomic, scoped profile deletion:
  ```java
  public boolean deleteByUserId(int userId) throws SQLException {
      String sql = "DELETE FROM recruiter_profiles WHERE user_id=?";
      try (Connection conn = DatabaseConnection.getConnection();
           PreparedStatement stmt = conn.prepareStatement(sql)) {
          stmt.setInt(1, userId);
          return stmt.executeUpdate() > 0;
      }
  }
  ```

---

## 4. Verification & Testing

### 4.1 Verification Suite: `Phase6Step1RecruiterSecurityVerifier`

Created `src/main/java/com/hirehub/util/Phase6Step1RecruiterSecurityVerifier.java` containing 60 comprehensive test cases divided into Offline and Live Database suites.

#### Test Results:
```
=================================================================
HireHub Phase 6 Step 1: Recruiter Service Authorization Closure
=================================================================

--- [1] Running Offline Recruiter Service Authorization & DAO Audit Suite ---
  [PASS] Unauthenticated: RecruiterService.getProfile returns empty
  [PASS] Unauthenticated: RecruiterService.saveProfile returns fail
  [PASS] Unauthenticated: RecruiterService.getProfileForAdmin returns empty
  [PASS] Unauthenticated: RecruiterService.saveProfileForAdmin returns fail
  [PASS] Cross-Recruiter: Recruiter A cannot read Recruiter B profile
  [PASS] Cross-Recruiter: Recruiter A cannot save Recruiter B profile
  [PASS] Cross-Role: Student cannot read recruiter profile
  [PASS] Cross-Role: Student cannot save recruiter profile
  [PASS] Cross-Role: Student cannot call getProfileForAdmin
  [PASS] Cross-Role: Student cannot call saveProfileForAdmin
  [PASS] Admin Separation: Admin cannot silently read recruiter profile via getProfile
  [PASS] Admin Separation: Admin cannot silently save recruiter profile via saveProfile
  [PASS] Non-Admin: Recruiter cannot call RecruiterService.getProfileForAdmin
  [PASS] Non-Admin: Recruiter cannot call RecruiterService.saveProfileForAdmin
  [PASS] Invalid ID: RecruiterService.getProfile with ID 0 returns empty
  [PASS] Invalid ID: RecruiterService.getProfile with negative ID returns empty
  [PASS] Invalid ID: RecruiterService.saveProfile with ID 0 returns fail
  [PASS] Invalid ID: RecruiterService.saveProfile with negative ID returns fail
  [PASS] Invalid Input: RecruiterService.saveProfile with null companyName returns fail
  [PASS] Invalid Input: RecruiterService.saveProfile with empty companyName returns fail
  [PASS] Invalid ID: RecruiterService.getProfileForAdmin with ID 0 returns empty
  [PASS] Invalid ID: RecruiterService.getProfileForAdmin with negative ID returns empty
  [PASS] Invalid ID: RecruiterService.saveProfileForAdmin with ID 0 returns fail
  [PASS] Invalid ID: RecruiterService.saveProfileForAdmin with negative ID returns fail
  [PASS] Invalid Input: RecruiterService.saveProfileForAdmin with blank companyName returns fail
  [PASS] RecruiterService.getProfile(int) exists
  [PASS] RecruiterService.saveProfile(int, String, String, String, String) exists
  [PASS] RecruiterService.getProfileForAdmin(int) exists
  [PASS] RecruiterService.saveProfileForAdmin(int, String, String, String, String) exists
  [PASS] RecruiterDAO.findByUserId(int) exists
  [PASS] RecruiterDAO.saveOrUpdate(RecruiterProfile) exists
  [PASS] RecruiterDAO.update(RecruiterProfile) exists
  [PASS] RecruiterDAO.insert(RecruiterProfile) exists
  [PASS] RecruiterDAO.existsByUserId(int) exists
  [PASS] RecruiterDAO.deleteByUserId(int) exists

--- [2] Running Live Database Tenant Isolation & Integrity Suite ---
  [PASS] Legitimate: Recruiter A reads own profile
  [PASS] Legitimate: Recruiter A updates own profile
  [PASS] Legitimate: Recruiter A update persisted to DB
  [PASS] Isolation: Recruiter A cannot read Recruiter B profile via service
  [PASS] Isolation: Recruiter A cannot overwrite Recruiter B profile via service
  [PASS] Isolation: Recruiter B profile in database completely untouched
  [PASS] Cross-Role: Student cannot read Recruiter A profile
  [PASS] Cross-Role: Student cannot modify Recruiter A profile
  [PASS] Cross-Role: Student cannot call getProfileForAdmin
  [PASS] Cross-Role: Student cannot call saveProfileForAdmin
  [PASS] Cross-Role: Recruiter A profile in DB untouched after Student attempt
  [PASS] Admin Separation: Admin cannot silently read recruiter profile via getProfile
  [PASS] Admin Separation: Admin cannot silently modify recruiter profile via saveProfile
  [PASS] Admin Explicit: Admin reads recruiter profile via getProfileForAdmin
  [PASS] Admin Explicit: Admin updates recruiter profile via saveProfileForAdmin
  [PASS] Admin Explicit: Recruiter A profile in DB reflects Admin update
  [PASS] DAO: findByUserId for nonexistent user returns empty
  [PASS] DAO: existsByUserId for nonexistent user returns false
  Cleaning up test fixtures...
  [PASS] Users table fully restored (3 == 3)
  [PASS] Recruiter profiles table fully restored (1 == 1)
  [PASS] Jobs table fully restored (1 == 1)
  [PASS] Applications table fully restored (1 == 1)
  [PASS] Resumes table fully restored (3 == 3)
  [PASS] Resume analyses table fully restored (1 == 1)
  [PASS] Student profiles table fully restored (1 == 1)

-----------------------------------------------------------------
SECURITY CLOSURE SUMMARY: 60 PASSED, 0 FAILED, 0 SKIPPED
=================================================================
```

---

## 5. Regression Suite Results

To ensure zero regressions across the codebase, existing verifiers were executed against the updated codebase:

| Verifier Class | Focus Area | Result |
| :--- | :--- | :--- |
| `Phase6Step1RecruiterSecurityVerifier` | Recruiter Service Authorization & Tenant Isolation | **60 / 60 PASSED** (100%) |
| `Phase5Step4B1SecurityVerifier` | Student Service & DAO Security Closure | **97 / 97 PASSED** (100%) |
| `Phase5Step3ASecurityVerifier` | Recruiter Hardening & Job/Application Scoping | **36 / 36 PASSED** (100%) |
| `Phase5Step3CRecruiterDashboardVerifier` | Recruiter Intelligence Dashboard UI & Integration | **21 / 21 PASSED** (100%) |

**All baseline database tables were verified and 100% restored after all runs:**
- `users`: 3 rows
- `recruiter_profiles`: 1 row
- `jobs`: 1 row
- `applications`: 1 row
- `resumes`: 3 rows
- `resume_analyses`: 1 row
- `student_profiles`: 1 row

---

## 6. Files Modified and Created

1. **`src/main/java/com/hirehub/service/RecruiterService.java`** (Modified)
   - Added session-level role verification (`isAuthorizedRecruiter`).
   - Protected `getProfile` and `saveProfile`.
   - Added `getProfileForAdmin` and `saveProfileForAdmin`.
   - Added overloaded constructor for `RecruiterDAO` injection.
2. **`src/main/java/com/hirehub/dao/RecruiterDAO.java`** (Modified)
   - Added `deleteByUserId(int userId)` for atomic scoped deletions.
3. **`src/main/java/com/hirehub/util/Phase6Step1RecruiterSecurityVerifier.java`** (Created)
   - 60-test verifier testing offline authorization, role bounds, cross-tenant isolation, admin explicit operations, and live database cleanup.
4. **`PHASE6_STEP1_RECRUITER_SECURITY_CLOSURE_REPORT.md`** (Created)
   - Complete architectural and verification report.

---

## 7. Conclusion

GAP-03 is now completely resolved. Recruiter service-layer operations are strictly bound to the authenticated recruiter session, preventing cross-tenant reads or updates regardless of caller or parameter tampering. The service architecture maintains perfect symmetry with `StudentService`, with clean separation for explicit Admin operations.


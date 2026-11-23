# PHASE 6 STEP 4.2C.2.2 — Atomic Approved Job Re-Moderation and Failure Safety

**Date:** 2026-11-19  
**Status:** ✅ COMPLETE — All verifiers pass, database baseline restored.

---

## 1. Root Cause: Non-Atomic Persistence

Prior to this step, `updateJob()` and `updateJobForRecruiter()` issued two independent database operations:

1. `jobDAO.updateForRecruiter(...)` — updated substantive content columns
2. `jobDAO.updateModerationStatus(...)` — reset moderation to PENDING

If operation **2** failed (network fault, constraint violation, timeout), the job's substantive content would be persisted while its moderation status remained `APPROVED` — a correctness and security failure.

---

## 2. Transaction Design

### 2.1 Single-Method Atomic Operation

All recruiter-scoped job updates now flow exclusively through:

```
JobDAO.updateJobForRecruiterAtomic(Job job, int recruiterId)
```

`JobService.updateJobForRecruiter()` delegates entirely to this method.  
`JobService.updateJob()` builds the Job entity and delegates to `updateJobForRecruiter()`.  
The old split-call path (`updateForRecruiter` + `updateModerationStatus`) is **not** called for recruiter edits.

### 2.2 SQL Operations Within One Transaction

| Step | Operation | SQL |
|------|-----------|-----|
| 1 | Acquire exclusive row lock + verify ownership | `SELECT … FROM jobs WHERE id = ? AND recruiter_id = ? FOR UPDATE` |
| 2 | Evaluate substantive change under lock | In-JVM comparison against locked row |
| 3 | Lifecycle activation guard under lock | APPROVED check + deadline check |
| 4 | (Injection point: `CONTENT_UPDATE`) | — |
| 5 | Update content columns | `UPDATE jobs SET title=?, … WHERE id=? AND recruiter_id=?` |
| 6 | (Injection point: `MODERATION_UPDATE`) | — |
| 7 | (If APPROVED + substantive) Reset moderation | `UPDATE jobs SET moderation_status='PENDING', moderation_reason='Content modified by recruiter — pending re-approval', moderated_at=NULL, moderated_by=NULL WHERE id=? AND recruiter_id=? AND moderation_status='APPROVED'` |
| 8 | Commit | `conn.commit()` |

If any step throws `SQLException`, `conn.rollback()` is called in the `catch` block, and the original `autoCommit` state is restored in `finally`.

### 2.3 Guarantees

- **Ownership** verified at the SQL `WHERE id = ? AND recruiter_id = ?` level (not just service-level).
- **Exclusive row lock** (`FOR UPDATE`) prevents concurrent updates from racing between the read and the write steps within the same transaction.
- **Moderation guard predicate** `AND moderation_status = 'APPROVED'` on step 7 ensures that if a concurrent session changed the status between our content write and moderation reset, step 7 returns 0 rows and the entire transaction rolls back.
- **No partial state**: commit is a single atomic `conn.commit()` that covers both the content write and the moderation reset.

---

## 3. Race-Condition Protection

### 3.1 Row-Level Locking

```sql
SELECT … FROM jobs WHERE id = ? AND recruiter_id = ? FOR UPDATE
```

This acquires an **InnoDB exclusive row lock** at transaction start. Any concurrent `UPDATE` on the same row blocks until this transaction commits or rolls back.

### 3.2 Ownership at Mutation Boundary

Both the content update and the moderation reset carry `AND recruiter_id = ?`, ensuring that even if a locking layer were bypassed, the update would still not affect another recruiter's row.

### 3.3 Concurrent Moderation Guard

The moderation reset SQL carries `AND moderation_status = 'APPROVED'`. Scenario:

- Concurrent process A: admin rejects the job → `moderation_status = 'REJECTED'`
- This transaction (step 7): `UPDATE … AND moderation_status = 'APPROVED'` → 0 rows affected → rollback

Result: the content update is also rolled back; the job retains its original content and `REJECTED` status.

### 3.4 Tenant Isolation Preserved

No change to any existing admin, student, or cross-recruiter SQL predicates.

---

## 4. Failure-Injection Seam

A thread-safe, volatile seam is provided in `JobDAO`:

```java
private static volatile String failureInjectionPoint = null;

public static void setFailureInjectionPoint(String point) { ... }
public static String getFailureInjectionPoint() { ... }
```

Injection points checked inside `updateJobForRecruiterAtomic`:

| Point name | Location |
|------------|----------|
| `CONTENT_UPDATE` | Before the content `UPDATE` statement executes |
| `MODERATION_UPDATE` | Before the moderation `UPDATE` statement executes |

Injecting at either point causes a `SQLException`, which triggers `conn.rollback()`. The seam is `null` by default and **only** active during tests that explicitly set it. Production behavior is unaffected.

---

## 5. Behavior Preservation

| Behavior | Mechanism |
|----------|-----------|
| No-op updates preserve APPROVED | `isSubstantiveChange()` returns false → `reModerationNeeded = false` → moderation block skipped |
| Whitespace-only differences are no-ops | `isDifferentString()` trims both sides before comparison |
| Substantive changes reset APPROVED → PENDING | `reModerationNeeded = true` → step 7 executes |
| DRAFT/ACTIVE/CLOSED lifecycle preserved | Only `status` column from recruiter's submission is written; moderation reset does not touch `status` |
| Recruiter cannot control moderation fields | `Job` moderation setters on the passed entity are ignored; moderation is set exclusively by step 7 SQL |
| Existing moderation audit history intact | No `INSERT` into `admin_audit_logs` during recruiter edits |
| Admin re-approval via `AdminModerationService` | Unchanged path through `AdminModerationDAO.updateJobModerationWithAudit()` |
| Student visibility | Controlled by `searchApprovedActive` / `findOpenApprovedActiveJobs` SQL predicates — unchanged |

---

## 6. Added `JobUpdateResult.activationForbidden()`

The existing `updateJobForRecruiterAtomic()` already referenced `JobUpdateResult.activationForbidden(String)` but this factory method was absent, causing a compilation error. Added:

```java
public static JobUpdateResult activationForbidden(String message) {
    return new JobUpdateResult(Status.VALIDATION_FAILED, message, false, null);
}
```

This completes the `JobUpdateResult` API and unblocks compilation.

---

## 7. Failure-Injection Test Results

All 10 scenarios verified with real MySQL transactions (live integration tests):

| # | Scenario | Result |
|---|----------|--------|
| T1 | Substantive edit commits content + PENDING atomically | ✅ PASS (7 assertions) |
| T2 | CONTENT_UPDATE failure leaves original data unchanged | ✅ PASS (4 assertions) |
| T3 | MODERATION_UPDATE failure rolls back content update | ✅ PASS (3 assertions) |
| T4 | Failed transaction creates no fabricated audit entry | ✅ PASS (1 assertion) |
| T5 | Concurrent moderation state check under row lock | ✅ PASS (3 assertions) |
| T6 | Cross-recruiter ownership rejected | ✅ PASS (3 assertions) |
| T7 | No-op update preserves APPROVED | ✅ PASS (4 assertions) |
| T8 | Lifecycle status (ACTIVE) unchanged during re-moderation | ✅ PASS (3 assertions) |
| T9 | Admin can re-approve after the transaction | ✅ PASS (4 assertions) |
| T10 | Student visibility revoked immediately after commit | ✅ PASS (3 assertions) |

**Offline architecture tests: 13/13 PASS**  
**Live integration tests: 40/40 PASS**  
**Step 4.2C.2.2 Total: 53/53 PASS, 0 FAILED, 0 SKIPPED**

---

## 8. Regression Results

| Verifier | Tests | Passed | Failed | Skipped | Exit |
|----------|-------|--------|--------|---------|------|
| Step 4.2C.2.2 (this step) | 53 | 53 | 0 | 0 | 0 |
| Step 4.2C.2.1 Approved Job Edit Audit | 74 | 74 | 0 | 0 | 0 |
| Step 4.2C.2 Recruiter Job Moderation | 149 | 149 | 0 | 0 | 0 |
| Step 4.2C.1 Admin Moderation Service | 158 | 158 | 0 | 0 | 0 |
| Step 4.2B.2 Student Visibility DAO | 270 | 270 | 0 | 0 | 0 |
| Phase 5 Step 3A Security | 36 | 36 | 0 | 0 | 0 |
| **Grand Total** | **740** | **740** | **0** | **0** | — |

---

## 9. Database Baseline Comparison

Baseline captured before each verifier, restored after teardown:

| Table | Before | After |
|-------|--------|-------|
| users | 3 | 3 |
| recruiter_profiles | 1 | 1 |
| jobs | 2 | 2 |
| admin_audit_logs | 0 | 0 |
| applications | 2 | 2 |
| resumes | 4 | 4 |
| resume_analyses | 1 | 1 |
| student_profiles | 1 | 1 |

All tables restored to exact pre-test baseline across all six verifiers.

---

## 10. Files Changed

| File | Change |
|------|--------|
| `src/main/java/com/hirehub/dao/JobDAO.java` | Added `JobUpdateResult.activationForbidden(String)` factory method |
| `src/main/java/com/hirehub/util/Phase6Step4_2C2_2AtomicJobReModerationVerifier.java` | **New** — 53-test verifier (13 offline, 40 live) |

No FXML, CSS, controllers, schema, `AdminModerationService`, student visibility SQL, or other services were modified.

---

## 11. Scope Compliance

| Item | Status |
|------|--------|
| `JobService.java` modified | No change needed (already delegates to atomic DAO) |
| `JobDAO.java` modified | ✅ Added missing `activationForbidden()` only |
| New verifier created | ✅ `Phase6Step4_2C2_2AtomicJobReModerationVerifier.java` |
| FXML/CSS | ✅ Not touched |
| Controllers | ✅ Not touched |
| Database schema / migrations | ✅ Not touched |
| Student visibility SQL | ✅ Not touched |
| `AdminModerationService` | ✅ Not touched |
| Step 4.2C.3 | ✅ Not begun |

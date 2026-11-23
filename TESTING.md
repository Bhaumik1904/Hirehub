# HireHub — Testing & Verification Catalog

This document details the automated testing architecture, execution commands, and comprehensive test suite catalog for the **HireHub** platform.

---

## 1. Testing Philosophy & Invariants

The HireHub testing framework is engineered around four core invariants:
1. **Zero Production Contamination**: All database-backed tests use dynamic prefixes (e.g., `STEP4_2A_TEST_`, `TEST_REC_`) and clean up all created rows in `finally` blocks, restoring row counts to exact baselines.
2. **Fail-Closed Security Verification**: Role-based access control, session authentication, and ownership checks are verified under hostile scenarios (e.g. unauthenticated sessions, wrong roles, foreign entity IDs).
3. **Resilience Under Network Partition**: Offline resilience and fallback tests verify that the system remains functional even if GroqCloud API returns HTTP 429/503 or network access is severed.
4. **FXML & Controller Integrity**: UI tests verify FXML tree integrity, `@FXML` field injections, and event handler attachments without requiring an active display server.

---

## 2. Test Execution (`verify.ps1`)

HireHub features a unified PowerShell test runner:

```powershell
# Display full test catalog with descriptions
.\verify.ps1 -List

# Execute the fast sanity smoke suite
.\verify.ps1 -Suite smoke

# Execute all 34 test suites across the repository
.\verify.ps1 -Suite all

# Execute all tests within a specific category
.\verify.ps1 -Category security
.\verify.ps1 -Category resumes
.\verify.ps1 -Category analytics
.\verify.ps1 -Category moderation
.\verify.ps1 -Category authentication
.\verify.ps1 -Category database
.\verify.ps1 -Category applications
.\verify.ps1 -Category integration

# Execute an individual test by class name
.\verify.ps1 -Test AdminLoginTest
.\verify.ps1 -Test ResumeAnalysisTest
```

---

## 3. Comprehensive Test Catalog (34 Test Suites)

### 3.1. Authentication (`com.hirehub.authentication`)
| Test Class | Purpose & Assertions |
| :--- | :--- |
| **`AdminLoginTest`** | Verifies BCrypt hash verification for default administrative credentials, rejection of legacy hashes, empty/null password handling, and proper role routing to `admin_dashboard.fxml`. |
| **`AuthenticationFlowTest`** | Verifies BCrypt runtime class loading, live database authentication, role resolution (`STUDENT`, `RECRUITER`, `ADMIN`), and temporary account registration/login/teardown. |

### 3.2. Database & Schema (`com.hirehub.database`)
| Test Class | Purpose & Assertions |
| :--- | :--- |
| **`DataAccessMappingTest`** | Tests entity mapping integrity between database columns and domain models (`User`, `Job`, `Application`, `Resume`). |
| **`DatabaseConnectionPoolTest`** | Tests HikariCP connection pool initialization, active connection leases, statement execution, and pool shutdown hooks. |
| **`DatabaseMigrationSchemaTest`** | Verifies column schemas, index creation, foreign key constraints (`ON DELETE SET NULL`), and idempotent re-execution safety. |

### 3.3. Security & Access Control (`com.hirehub.security`)
| Test Class | Purpose & Assertions |
| :--- | :--- |
| **`RecruiterAuthorizationTest`** | Verifies recruiter dashboard and job form fail-closed security when accessed by student, admin, or unauthenticated sessions. |
| **`RecruiterServiceSecurityTest`** | Verifies service-layer recruiter isolation, preventing recruiters from viewing or altering foreign job postings. |
| **`StudentAuthorizationTest`** | Verifies student portal controllers fail closed and redirect to login when session credentials are unauthorized. |
| **`StudentJobVisibilitySecurityTest`** | Verifies that students can only view and apply to jobs that are both `ACTIVE` and `APPROVED`. |

### 3.4. Applications & Snapshots (`com.hirehub.applications`)
| Test Class | Purpose & Assertions |
| :--- | :--- |
| **`ApplicationResumeSnapshotTest`** | Verifies that student resume metadata and physical file references are immutably captured in `application_resumes` at submission. |
| **`ApplicationWorkflowTest`** | Verifies state machine transitions (`APPLIED` → `REVIEWING` → `SHORTLISTED` → `ACCEPTED` / `REJECTED`) and prevents duplicate applications. |
| **`RecruiterApplicantManagementTest`** | Verifies candidate listing, filter operations, resume downloads, and candidate progression by recruiters. |

### 3.5. Resumes & Scoring Engine (`com.hirehub.resumes`)
| Test Class | Purpose & Assertions |
| :--- | :--- |
| **`ResumeAnalysisResilienceTest`** | Verifies error handling, HTTP 429 quota exhaustion handling, HTTP 503 service unavailable fallbacks, and safety filter responses. |
| **`ResumeAnalysisTest`** | Verifies Apache PDFBox text extraction, deterministic 5-part scoring engine, unverified credentials disclaimers, and database persistence. |
| **`ResumeAnalyzerNavigationTest`** | Verifies `resume_analyzer.fxml` view hierarchy, button event handlers, and seamless dashboard back navigation. |

### 3.6. Analytics & Dashboards (`com.hirehub.analytics`)
| Test Class | Purpose & Assertions |
| :--- | :--- |
| **`AdminAnalyticsTest`** | Verifies platform-wide KPI calculations (total placement rate, active jobs, student headcount, recruiter engagement). |
| **`RecruiterAnalyticsTest`** | Verifies hiring funnel metrics (total applicants, interview stage conversion, time-to-hire averages). |
| **`RecruiterDashboardUITest`** | Verifies recruiter dashboard FXML hierarchy, KPI card bindings, and funnel chart population. |
| **`StudentAnalyticsTest`** | Verifies student profile readiness scoring, application stage counts, and activity timelines. |
| **`StudentDashboardUITest`** | Verifies student dashboard FXML structure, KPI cards, and persistent navigation sidebar bindings. |

### 3.7. Moderation & Compliance (`com.hirehub.moderation`)
| Test Class | Purpose & Assertions |
| :--- | :--- |
| **`AdminModerationServiceTest`** | Verifies business logic for approving/rejecting jobs and verifying recruiters with mandatory audit logging. |
| **`AdminModerationUITest`** | Verifies admin moderation dashboard FXML bindings, approval queue tables, and action buttons. |
| **`AdminUserManagementTest`** | Verifies administrative account deactivation, activation, and suspension with cascade-safe audit records. |
| **`ApprovedJobModificationAuditTest`** | Verifies that modifications to already approved jobs record an audit log entry. |
| **`AtomicJobReModerationTest`** | Verifies atomic database rollback of job status to `PENDING` whenever an approved job is modified by a recruiter. |
| **`RecruiterJobModerationTest`** | Verifies recruiter notifications when job postings are approved or rejected. |
| **`RecruiterVerificationUITest`** | Verifies recruiter verification badge rendering and verification queue UI. |

### 3.8. Integration & AI (`com.hirehub.integration`)
| Test Class | Purpose & Assertions |
| :--- | :--- |
| **`AIConnectivityResilienceTest`** | Tests connection timeouts, socket disconnects, and graceful degradation when GroqCloud endpoints are unreachable. |
| **`AIFeaturesLiveTest`** | Verifies end-to-end AI career chat responses and qualitative resume evaluation against live Groq endpoints. |
| **`ClasspathDiagnosticTest`** | Inspects JVM runtime classpath, verifying all required native and third-party JAR dependencies exist. |
| **`GroqConnectivityDiagnostic`** | Low-level diagnostic utility that pings GroqCloud API endpoints and validates authentication tokens. *(Requires `GROQ_API_KEY` configured in the shell environment; used as an active diagnostic tool)*. |
| **`GroqIntegrationTest`** | Validates HTTP request headers, authorization bearer tokens, JSON payloads, and response deserialization. |
| **`StudentEndToEndWorkflowTest`** | Full student workflow: registration, profile setup, resume upload, job search, and application submission. |
| **`UIAccuracyQualityAuditTest`** | Inspects FXML and CSS definitions against Figma Make visual design tokens and color palettes. |

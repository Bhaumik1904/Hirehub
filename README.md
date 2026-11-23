# HireHub — Campus Placement & Recruitment Management Platform

[![Java](https://img.shields.io/badge/Java-21%20LTS-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21.0.5-FF8800?logo=oracle&logoColor=white)](https://openjfx.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![HikariCP](https://img.shields.io/badge/HikariCP-5.1.0-brightgreen)](https://github.com/brettwooldridge/HikariCP)
[![GroqCloud](https://img.shields.io/badge/AI-GroqCloud%20API-F55036)](https://groq.com/)
[![Architecture](https://img.shields.io/badge/Architecture-MVC%20%2B%20DAO%20%2B%20Service-blue)](ARCHITECTURE.md)

HireHub is an enterprise-grade desktop placement and recruitment application built with **Java 21**, **JavaFX 21**, **MySQL 8**, and **HikariCP**. The platform streamlines campus hiring workflows for students, corporate recruiters, and placement administration with integrated AI career guidance and deterministic resume scoring.

---

## Key Features

### 🎓 Student Workspace
- **Placement Dashboard**: Real-time KPI metrics, application pipeline stages, profile readiness indicator, and curated job recommendations.
- **Browse & Apply**: Search and filter company job openings with active moderation filtering, deadline enforcement, and instant resume attachment.
- **AI Resume Analyzer**: PDF upload, Apache PDFBox text extraction, deterministic 5-part scoring rubric (40% skills, 25% experience, 15% education, 10% completeness, 10% certifications), and qualitative feedback via GroqCloud AI.
- **AI Career Assistant**: Interactive chat providing personalized career advice, interview preparation tips, and resume improvement insights.
- **Student Profile Management**: Manage contact information, academic credentials, and core technical skills.

### 🏢 Recruiter Portal
- **Recruiter Dashboard**: Application funnel analytics, candidate pipeline tracking, and active job status summaries.
- **Job Management**: Create, edit, close, and re-publish job listings with automated moderation routing.
- **Applicant Review**: Review applicant profiles, download submitted resume snapshots, and update candidate progression stages.
- **Company Profile**: Maintain organizational identity, industry categories, and verification status badges.

### 🛡️ Admin Command Center
- **Placement Command Center**: Platform-wide KPIs covering total students, verified recruiters, active jobs, placement rates, and audit activity.
- **Job Moderation Queue**: Review, approve, or reject recruiter job postings with mandatory audit trail logging.
- **Recruiter Verification**: Audit corporate recruiter credentials before allowing job distribution.
- **User Management**: Activate, deactivate, or suspend platform accounts with cascade-safe auditing.
- **System Audit Logs**: Comprehensive historical record of all administrative moderation actions.

---

## Technology Stack

| Layer | Component | Description |
| :--- | :--- | :--- |
| **Language** | Java 21 LTS | Modular architecture with Java Platform Module System (`module-info.java`) |
| **UI Framework** | JavaFX 21.0.5 | Native desktop UI with FXML layouts and modern CSS design system |
| **Database** | MySQL 8.0 | Normalized relational schema with strict foreign key constraints |
| **Connection Pool** | HikariCP 5.1.0 | High-performance, thread-safe connection pooling |
| **Security** | BCrypt (jbcrypt 0.4) | Salted password hashing with work factor 12 |
| **Document Processing** | Apache PDFBox 2.0.31 | Structured PDF resume text extraction and metadata inspection |
| **AI Integration** | GroqCloud HTTP API | Low-latency LLM inference (`openai/gpt-oss-20b` with `llama-3.3-70b-versatile` fallback) |
| **Data Interchange** | Google Gson 2.10.1 | JSON parsing, serialization, and schema validation |
| **Architecture** | 3-Tier Layered | Clean MVC + DAO + Service Layer separation |

---

## Repository Structure

```
HireHub/
├── build.ps1                   ← Clean build script (production + test suites)
├── run.ps1                     ← Application launcher with runtime environment checks
├── verify.ps1                  ← Unified test runner (34 test suites across 8 categories)
├── verify_phase4.ps1           ← Backward-compatibility test runner delegation
├── pom.xml                     ← Maven project descriptor and dependency definitions
├── .env.example                ← Template for environment variables
├── ARCHITECTURE.md             ← Comprehensive architecture and design document
├── DATABASE.md                 ← 10-table schema, ER diagram, and migration guide
├── TESTING.md                  ← Complete testing catalog and execution guide
├── AI_INTEGRATION.md           ← GroqCloud AI integration and scoring rubric specifications
├── REPOSITORY_CLEANUP_REPORT.md ← Complete repository restructuring audit
│
├── database/                   ← Database assets and schemas
│   ├── schema/                 ← Core database definitions
│   │   ├── schema.sql          ← Base schema definition
│   │   └── ensure_all_tables.sql ← Complete 10-table idempotent DDL script
│   ├── migrations/             ← Flyway-compatible sequential SQL migrations (V1 to V6)
│   ├── seed/                   ← Initial admin and test verification seed scripts
│   └── backups/                ← Local backup storage (excluded from git)
│
├── docs/                       ← Product and architecture documentation
│   ├── academic/               ← Academic reports and presentation slides deck
│   └── archive/phase_reports/  ← Historical development phase reports
│
├── images/                     ← Figma Make UI screenshots (visual source of truth)
│
├── src/
│   ├── main/
│   │   ├── java/com/hirehub/   ← Production source code (99 classes)
│   │   │   ├── controller/     ← JavaFX UI Controllers
│   │   │   ├── dao/            ← JDBC Data Access Objects
│   │   │   ├── database/       ← Connection manager and HikariCP pool
│   │   │   ├── model/          ← Domain entities and DTOs
│   │   │   ├── service/        ← Business logic and security services
│   │   │   └── util/           ← Cross-cutting utilities (SessionManager, PasswordUtil)
│   │   └── resources/          ← Application resources
│   │       ├── css/            ← Global styling design system (style.css)
│   │       ├── fxml/           ← FXML view layouts
│   │       └── images/         ← Application branding assets
│   └── test/
│       └── java/com/hirehub/   ← Automated test suites (34 classes)
│           ├── analytics/      ← Analytics KPI calculation tests (5)
│           ├── applications/   ← Application workflow & resume snapshot tests (3)
│           ├── authentication/ ← Credential verification & admin login tests (2)
│           ├── database/       ← Connection pool & schema mapping tests (3)
│           ├── integration/    ← End-to-end workflow & Groq API tests (7)
│           ├── moderation/     ← User management & job moderation tests (7)
│           ├── resumes/        ← Resume parsing & scoring rubric tests (3)
│           └── security/       ← Role authorization & stage guard tests (4)
│
├── target/                     ← Compiled classes and test classes
└── tools/                      ← Build utilities and Maven runtime
```

---

## Getting Started

### 1. Prerequisites
- **Java Development Kit (JDK)**: Java 21 or later
- **MySQL Server**: 8.0 or later running on port 3306
- **PowerShell 5.1+** (Windows)
- *(Optional)* **GroqCloud API Key**: Required for AI Assistant and qualitative resume feedback. Get an API key at [console.groq.com/keys](https://console.groq.com/keys).

### 2. Database Initialization
Create the database and initialize all 10 tables:

```powershell
# Log into MySQL and execute the schema script
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hirehub_db;"
mysql -u root -p hirehub_db < database/schema/ensure_all_tables.sql
```

Alternatively, apply sequential versioned migrations:
```powershell
Get-ChildItem database/migrations/*.sql | Sort-Object Name | ForEach-Object {
    Get-Content $_.FullName | mysql -u root -p hirehub_db
}
```

Seed the default administrative account:
```powershell
mysql -u root -p hirehub_db < database/seed/admin_seed.sql
```
*Note: Initial administrative credentials are seeded from `admin_seed.sql`. Ensure administrator credentials are changed immediately upon first login.*

### 3. Environment Configuration
Configure your environment variables in PowerShell or create a `.env` file from `.env.example`:

```powershell
$env:DB_HOST      = "localhost"
$env:DB_PORT      = "3306"
$env:DB_NAME      = "hirehub_db"
$env:DB_USER      = "root"
$env:DB_PASS      = "your_mysql_password"
$env:GROQ_API_KEY  = "gsk_your_groq_api_key"   # Optional
```

### 4. Build and Run
Compile all production classes and test suites:
```powershell
.\build.ps1
```

Launch the HireHub desktop application:
```powershell
.\run.ps1
```

---

## Automated Testing & Verification

HireHub includes a comprehensive automated test runner supporting 34 test suites across 8 categories:

```powershell
# List all available test suites and categories
.\verify.ps1 -List

# Run the fast smoke suite
.\verify.ps1 -Suite smoke

# Run all 34 test suites (full regression)
.\verify.ps1 -Suite all

# Run tests by category (authentication, database, security, resumes, analytics, moderation, applications, integration)
.\verify.ps1 -Category security
.\verify.ps1 -Category resumes

# Run a specific single test
.\verify.ps1 -Test AdminLoginTest
```

---

## Technical Documentation Links

- [Architecture Guide (ARCHITECTURE.md)](ARCHITECTURE.md) — 3-Tier MVC architecture, HikariCP connection pooling, security model, and threading.
- [Database Guide (DATABASE.md)](DATABASE.md) — 10-table schema specification, ER diagram, migration history, and setup instructions.
- [Testing Guide (TESTING.md)](TESTING.md) — Detailed catalog of all 34 test suites, execution commands, and verification criteria.
- [AI Integration Guide (AI_INTEGRATION.md)](AI_INTEGRATION.md) — GroqCloud API resilience, 5-part scoring engine, and fallback strategies.
- [Repository Cleanup Report (REPOSITORY_CLEANUP_REPORT.md)](REPOSITORY_CLEANUP_REPORT.md) — Complete audit of the repository restructuring and file reorganization.

---

## Security & Reliability Standards

- **Zero Plaintext Passwords**: All credentials hashed with BCrypt (12 rounds) before persistence.
- **Fail-Closed Session Guarding**: Controllers verify role privileges on initialization; unauthorized attempts automatically invalidate sessions and redirect to login.
- **SQL Injection Prevention**: 100% of database interactions execute through parameterized `PreparedStatement` queries.
- **Connection Leak Protection**: Managed via HikariCP pool with auto-closing `try-with-resources` blocks and graceful shutdown hooks.
- **AI Fault Tolerance**: Graceful circuit breaking for HTTP 429/503 rate limits and network failures, ensuring core application features never crash when external APIs are offline.

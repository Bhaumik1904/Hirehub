# ============================================================
# HireHub - Unified Test & Verification Runner
# Runs individual tests, category suites, or full regression suites.
# ============================================================
param(
    [string]$Test = "",
    [string]$Category = "",
    [string]$Suite = "",
    [switch]$List = $false
)

$ErrorActionPreference = "Stop"

$javaDir = "C:\Program Files\Java\jdk-26.0.2\bin"
$java    = "$javaDir\java.exe"
$m2      = "$env:USERPROFILE\.m2\repository"
$jfxVer  = "21.0.5"
$project = "d:\HireHub"

# 1. Environment variable resolution
if (-not $env:DB_HOST) { $env:DB_HOST = "localhost" }
if (-not $env:DB_PORT) { $env:DB_PORT = "3306" }
if (-not $env:DB_NAME) { $env:DB_NAME = "hirehub_db" }
if (-not $env:DB_USER) { $env:DB_USER = "root" }

if ([string]::IsNullOrWhiteSpace($env:DB_PASS)) {
    $userDbPass = [System.Environment]::GetEnvironmentVariable("DB_PASS", "User")
    if (-not [string]::IsNullOrWhiteSpace($userDbPass)) {
        $env:DB_PASS = $userDbPass
    } else {
        $machineDbPass = [System.Environment]::GetEnvironmentVariable("DB_PASS", "Machine")
        if (-not [string]::IsNullOrWhiteSpace($machineDbPass)) {
            $env:DB_PASS = $machineDbPass
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:GROQ_API_KEY)) {
    $userKey = [System.Environment]::GetEnvironmentVariable("GROQ_API_KEY", "User")
    if (-not [string]::IsNullOrWhiteSpace($userKey)) {
        $env:GROQ_API_KEY = $userKey
    } else {
        $machineKey = [System.Environment]::GetEnvironmentVariable("GROQ_API_KEY", "Machine")
        if (-not [string]::IsNullOrWhiteSpace($machineKey)) {
            $env:GROQ_API_KEY = $machineKey
        }
    }
}

# 2. Dependency definitions
$dependencyJars = @(
    "$m2\org\openjfx\javafx-controls\$jfxVer\javafx-controls-$jfxVer-win.jar",
    "$m2\org\openjfx\javafx-fxml\$jfxVer\javafx-fxml-$jfxVer-win.jar",
    "$m2\org\openjfx\javafx-graphics\$jfxVer\javafx-graphics-$jfxVer-win.jar",
    "$m2\org\openjfx\javafx-base\$jfxVer\javafx-base-$jfxVer-win.jar",
    "$m2\org\mindrot\jbcrypt\0.4\jbcrypt-0.4.jar",
    "$m2\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar",
    "$m2\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar",
    "$m2\org\apache\pdfbox\pdfbox\2.0.31\pdfbox-2.0.31.jar",
    "$m2\org\apache\pdfbox\fontbox\2.0.31\fontbox-2.0.31.jar",
    "$m2\commons-logging\commons-logging\1.2\commons-logging-1.2.jar",
    "$m2\com\zaxxer\HikariCP\5.1.0\HikariCP-5.1.0.jar",
    "$m2\org\slf4j\slf4j-api\1.7.36\slf4j-api-1.7.36.jar",
    "$m2\org\slf4j\slf4j-simple\1.7.36\slf4j-simple-1.7.36.jar"
)

$modulePath = $dependencyJars -join ";"
$classesDir = "$project\target\classes"
$testClassesDir = "$project\target\test-classes"

if (-not (Test-Path $classesDir) -or -not (Test-Path $testClassesDir)) {
    Write-Host "[INFO] Build artifacts missing. Executing build.ps1 first..." -ForegroundColor Yellow
    & "$project\build.ps1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Build failed. Cannot run tests." -ForegroundColor Red
        exit 1
    }
}

$testClasspath = "$testClassesDir;$classesDir;$modulePath"

# 3. Test Catalog Registry (34 Test Suites)
$testCatalog = @(
    # Authentication
    [PSCustomObject]@{ Name = "AdminLoginTest"; Category = "authentication"; Class = "com.hirehub.authentication.AdminLoginTest"; Smoke = $true; Description = "BCrypt hash verification and admin role routing" },
    [PSCustomObject]@{ Name = "AuthenticationFlowTest"; Category = "authentication"; Class = "com.hirehub.authentication.AuthenticationFlowTest"; Smoke = $true; Description = "User credential validation, hashing, and role auth" },

    # Database
    [PSCustomObject]@{ Name = "DataAccessMappingTest"; Category = "database"; Class = "com.hirehub.database.DataAccessMappingTest"; Smoke = $true; Description = "DAO and entity mapping integrity" },
    [PSCustomObject]@{ Name = "DatabaseConnectionPoolTest"; Category = "database"; Class = "com.hirehub.database.DatabaseConnectionPoolTest"; Smoke = $true; Description = "HikariCP connection pool health and lifecycle" },
    [PSCustomObject]@{ Name = "DatabaseMigrationSchemaTest"; Category = "database"; Class = "com.hirehub.database.DatabaseMigrationSchemaTest"; Smoke = $false; Description = "Schema migrations and idempotent DDL execution" },

    # Security
    [PSCustomObject]@{ Name = "RecruiterAuthorizationTest"; Category = "security"; Class = "com.hirehub.security.RecruiterAuthorizationTest"; Smoke = $true; Description = "Recruiter role authorization boundaries" },
    [PSCustomObject]@{ Name = "RecruiterServiceSecurityTest"; Category = "security"; Class = "com.hirehub.security.RecruiterServiceSecurityTest"; Smoke = $true; Description = "Recruiter service-layer authorization checks" },
    [PSCustomObject]@{ Name = "StudentAuthorizationTest"; Category = "security"; Class = "com.hirehub.security.StudentAuthorizationTest"; Smoke = $true; Description = "Student access control and session guarding" },
    [PSCustomObject]@{ Name = "StudentJobVisibilitySecurityTest"; Category = "security"; Class = "com.hirehub.security.StudentJobVisibilitySecurityTest"; Smoke = $true; Description = "Job visibility filters (APPROVED vs PENDING/REJECTED)" },

    # Applications
    [PSCustomObject]@{ Name = "ApplicationResumeSnapshotTest"; Category = "applications"; Class = "com.hirehub.applications.ApplicationResumeSnapshotTest"; Smoke = $true; Description = "Resume snapshot capture on application submission" },
    [PSCustomObject]@{ Name = "ApplicationWorkflowTest"; Category = "applications"; Class = "com.hirehub.applications.ApplicationWorkflowTest"; Smoke = $true; Description = "Application lifecycle state transitions" },
    [PSCustomObject]@{ Name = "RecruiterApplicantManagementTest"; Category = "applications"; Class = "com.hirehub.applications.RecruiterApplicantManagementTest"; Smoke = $true; Description = "Recruiter application review and status updates" },

    # Resumes
    [PSCustomObject]@{ Name = "ResumeAnalysisResilienceTest"; Category = "resumes"; Class = "com.hirehub.resumes.ResumeAnalysisResilienceTest"; Smoke = $true; Description = "Resume analyzer error handling and retry resilience" },
    [PSCustomObject]@{ Name = "ResumeAnalysisTest"; Category = "resumes"; Class = "com.hirehub.resumes.ResumeAnalysisTest"; Smoke = $true; Description = "Resume text parsing, scoring rubric, and recommendations" },
    [PSCustomObject]@{ Name = "ResumeAnalyzerNavigationTest"; Category = "resumes"; Class = "com.hirehub.resumes.ResumeAnalyzerNavigationTest"; Smoke = $true; Description = "Resume analyzer UI FXML and controller navigation" },

    # Analytics
    [PSCustomObject]@{ Name = "AdminAnalyticsTest"; Category = "analytics"; Class = "com.hirehub.analytics.AdminAnalyticsTest"; Smoke = $true; Description = "Platform-wide KPI aggregation calculations" },
    [PSCustomObject]@{ Name = "RecruiterAnalyticsTest"; Category = "analytics"; Class = "com.hirehub.analytics.RecruiterAnalyticsTest"; Smoke = $true; Description = "Recruiter hiring funnel and application metrics" },
    [PSCustomObject]@{ Name = "RecruiterDashboardUITest"; Category = "analytics"; Class = "com.hirehub.analytics.RecruiterDashboardUITest"; Smoke = $true; Description = "Recruiter dashboard FXML and chart data bindings" },
    [PSCustomObject]@{ Name = "StudentAnalyticsTest"; Category = "analytics"; Class = "com.hirehub.analytics.StudentAnalyticsTest"; Smoke = $true; Description = "Student readiness score and activity calculations" },
    [PSCustomObject]@{ Name = "StudentDashboardUITest"; Category = "analytics"; Class = "com.hirehub.analytics.StudentDashboardUITest"; Smoke = $true; Description = "Student dashboard FXML and KPI card bindings" },

    # Moderation
    [PSCustomObject]@{ Name = "AdminModerationServiceTest"; Category = "moderation"; Class = "com.hirehub.moderation.AdminModerationServiceTest"; Smoke = $true; Description = "Admin moderation business logic and audit logging" },
    [PSCustomObject]@{ Name = "AdminModerationUITest"; Category = "moderation"; Class = "com.hirehub.moderation.AdminModerationUITest"; Smoke = $true; Description = "Admin moderation dashboard UI bindings" },
    [PSCustomObject]@{ Name = "AdminUserManagementTest"; Category = "moderation"; Class = "com.hirehub.moderation.AdminUserManagementTest"; Smoke = $true; Description = "Admin user suspension, activation, and deletion" },
    [PSCustomObject]@{ Name = "ApprovedJobModificationAuditTest"; Category = "moderation"; Class = "com.hirehub.moderation.ApprovedJobModificationAuditTest"; Smoke = $true; Description = "Auditing modifications to already approved jobs" },
    [PSCustomObject]@{ Name = "AtomicJobReModerationTest"; Category = "moderation"; Class = "com.hirehub.moderation.AtomicJobReModerationTest"; Smoke = $true; Description = "Atomic state rollback when job details are modified" },
    [PSCustomObject]@{ Name = "RecruiterJobModerationTest"; Category = "moderation"; Class = "com.hirehub.moderation.RecruiterJobModerationTest"; Smoke = $true; Description = "Job moderation queue transitions and notifications" },
    [PSCustomObject]@{ Name = "RecruiterVerificationUITest"; Category = "moderation"; Class = "com.hirehub.moderation.RecruiterVerificationUITest"; Smoke = $true; Description = "Recruiter verification badge and status UI" },

    # Integration
    [PSCustomObject]@{ Name = "AIConnectivityResilienceTest"; Category = "integration"; Class = "com.hirehub.integration.AIConnectivityResilienceTest"; Smoke = $true; Description = "GroqCloud API resilience, timeouts, and fallbacks" },
    [PSCustomObject]@{ Name = "AIFeaturesLiveTest"; Category = "integration"; Class = "com.hirehub.integration.AIFeaturesLiveTest"; Smoke = $false; Description = "Live end-to-end AI career assistant integration" },
    [PSCustomObject]@{ Name = "ClasspathDiagnosticTest"; Category = "integration"; Class = "com.hirehub.integration.ClasspathDiagnosticTest"; Smoke = $true; Description = "Runtime classpath and dependency verification" },
    [PSCustomObject]@{ Name = "GroqConnectivityDiagnostic"; Category = "integration"; Class = "com.hirehub.integration.GroqConnectivityDiagnostic"; Smoke = $false; Description = "Diagnostic ping to GroqCloud API endpoints" },
    [PSCustomObject]@{ Name = "GroqIntegrationTest"; Category = "integration"; Class = "com.hirehub.integration.GroqIntegrationTest"; Smoke = $false; Description = "Direct Groq HTTP request/response validation" },
    [PSCustomObject]@{ Name = "StudentEndToEndWorkflowTest"; Category = "integration"; Class = "com.hirehub.integration.StudentEndToEndWorkflowTest"; Smoke = $true; Description = "Complete student workflow from login to application" },
    [PSCustomObject]@{ Name = "UIAccuracyQualityAuditTest"; Category = "integration"; Class = "com.hirehub.integration.UIAccuracyQualityAuditTest"; Smoke = $true; Description = "Visual design system and UI accuracy verification" }
)

# 4. Handle List Command
if ($List) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  HireHub - Test Suite Catalog" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    $categories = $testCatalog | Group-Object Category
    foreach ($cat in $categories) {
        Write-Host "`n  Category: $($cat.Name.ToUpper()) ($($cat.Count) tests)" -ForegroundColor Yellow
        foreach ($t in $cat.Group) {
            Write-Host ("    - {0,-36} : {1}" -f $t.Name, $t.Description) -ForegroundColor Gray
        }
    }
    Write-Host ""
    Write-Host "Usage Examples:" -ForegroundColor Cyan
    Write-Host "  .\verify.ps1 -Test AdminLoginTest" -ForegroundColor White
    Write-Host "  .\verify.ps1 -Category security" -ForegroundColor White
    Write-Host "  .\verify.ps1 -Suite smoke" -ForegroundColor White
    Write-Host "  .\verify.ps1 -Suite all" -ForegroundColor White
    exit 0
}

# 5. Determine which tests to run
$testsToRun = @()

if (-not [string]::IsNullOrWhiteSpace($Test)) {
    # Check if exact class or short name
    $match = $testCatalog | Where-Object { $_.Name -eq $Test -or $_.Class -eq $Test }
    if ($match) {
        $testsToRun = @($match)
    } else {
        # Partial match
        $partial = $testCatalog | Where-Object { $_.Name -like "*$Test*" -or $_.Class -like "*$Test*" }
        if ($partial) {
            $testsToRun = @($partial)
        } else {
            # Maybe user passed a raw FQCN not in catalog
            $testsToRun = @([PSCustomObject]@{ Name = (Split-Path $Test -Leaf); Category = "custom"; Class = $Test; Smoke = $false; Description = "Custom test execution" })
        }
    }
} elseif (-not [string]::IsNullOrWhiteSpace($Category)) {
    $testsToRun = @($testCatalog | Where-Object { $_.Category -eq $Category.ToLower() })
    if ($testsToRun.Count -eq 0) {
        Write-Host "[ERROR] Unknown category '$Category'. Available categories:" -ForegroundColor Red
        ($testCatalog | Select-Object -ExpandProperty Category -Unique) | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
        exit 1
    }
} elseif (-not [string]::IsNullOrWhiteSpace($Suite)) {
    switch ($Suite.ToLower()) {
        "all" {
            $testsToRun = $testCatalog
        }
        "smoke" {
            $testsToRun = @($testCatalog | Where-Object { $_.Smoke -eq $true })
        }
        "fast" {
            # Non-network dependent
            $testsToRun = @($testCatalog | Where-Object { $_.Category -ne "integration" -or $_.Name -eq "ClasspathDiagnosticTest" })
        }
        default {
            Write-Host "[ERROR] Unknown suite '$Suite'. Valid options: all, smoke, fast" -ForegroundColor Red
            exit 1
        }
    }
} else {
    # Default action: run smoke suite
    Write-Host "[INFO] No specific test, category, or suite specified. Running smoke suite (fast sanity checks)." -ForegroundColor Cyan
    Write-Host "       Use '.\verify.ps1 -Suite all' to run all 34 test suites." -ForegroundColor Gray
    Write-Host "       Use '.\verify.ps1 -List' to view all available tests." -ForegroundColor Gray
    $testsToRun = @($testCatalog | Where-Object { $_.Smoke -eq $true })
}

# 6. Run selected tests
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  HireHub - Test Execution Runner" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Target Suites : $($testsToRun.Count)" -ForegroundColor White
Write-Host "  Database Host : $env:DB_HOST:$env:DB_PORT ($env:DB_NAME)" -ForegroundColor Gray
Write-Host ""

$results = @()
$totalStart = [System.Diagnostics.Stopwatch]::StartNew()

foreach ($t in $testsToRun) {
    Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "RUNNING: $($t.Name) [$($t.Category)]" -ForegroundColor Cyan
    Write-Host "CLASS  : $($t.Class)" -ForegroundColor Gray

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $exitCode = 0
    try {
        & $java `
            --module-path $modulePath `
            --add-modules javafx.controls,javafx.fxml,jbcrypt,com.google.gson,org.apache.pdfbox,com.zaxxer.hikari,org.slf4j,org.slf4j.simple `
            -cp $testClasspath `
            $t.Class

        $exitCode = $LASTEXITCODE
    } catch {
        Write-Host "Execution exception: $_" -ForegroundColor Red
        $exitCode = 1
    }
    $sw.Stop()

    $status = if ($exitCode -eq 0) { "PASS" } else { "FAIL" }
    $duration = "$([math]::Round($sw.Elapsed.TotalSeconds, 2))s"

    $results += [PSCustomObject]@{
        Name     = $t.Name
        Category = $t.Category
        Status   = $status
        Duration = $duration
        ExitCode = $exitCode
    }

    if ($exitCode -eq 0) {
        Write-Host "RESULT: PASS ($duration)" -ForegroundColor Green
    } else {
        Write-Host "RESULT: FAIL (Exit code $exitCode, $duration)" -ForegroundColor Red
    }
}

$totalStart.Stop()

# 7. Print Summary Report
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  HireHub - Test Execution Summary" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$passedCount = @($results | Where-Object { $_.Status -eq "PASS" }).Count
$failedCount = @($results | Where-Object { $_.Status -eq "FAIL" }).Count
$totalCount  = @($results).Count

foreach ($r in $results) {
    $color = if ($r.Status -eq "PASS") { "Green" } else { "Red" }
    Write-Host ("  [{0,-4}] {1,-34} ({2,-14}) {3,8}" -f $r.Status, $r.Name, $r.Category, $r.Duration) -ForegroundColor $color
}

Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray
Write-Host "  Total: $totalCount | Passed: $passedCount | Failed: $failedCount | Elapsed: $([math]::Round($totalStart.Elapsed.TotalSeconds, 2))s" -ForegroundColor $(if ($failedCount -eq 0) { "Green" } else { "Red" })
Write-Host "============================================================" -ForegroundColor Cyan

if ($failedCount -gt 0) {
    exit 1
} else {
    exit 0
}

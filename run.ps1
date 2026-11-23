# ============================================================
# HireHub Launcher - sets default environment variables
# and starts the JavaFX application with full runtime classpath.
#
# USAGE:
#   1. Set DB_PASS in your environment:
#      $env:DB_PASS = "your_mysql_password"
#   2. Optionally set GROQ_API_KEY:
#      $env:GROQ_API_KEY = "your_api_key"
#   3. Run this script:
#      .\run.ps1
# ============================================================

# Database configuration defaults
if (-not $env:DB_HOST) { $env:DB_HOST = "localhost" }
if (-not $env:DB_PORT) { $env:DB_PORT = "3306" }
if (-not $env:DB_NAME) { $env:DB_NAME = "hirehub_db" }
if (-not $env:DB_USER) { $env:DB_USER = "root" }

# Database credential validation
if ([string]::IsNullOrWhiteSpace($env:DB_PASS)) {
    # Check User or Machine registry if not in process env
    $userDbPass = [System.Environment]::GetEnvironmentVariable("DB_PASS", "User")
    if (-not [string]::IsNullOrWhiteSpace($userDbPass)) {
        $env:DB_PASS = $userDbPass
    } else {
        $machineDbPass = [System.Environment]::GetEnvironmentVariable("DB_PASS", "Machine")
        if (-not [string]::IsNullOrWhiteSpace($machineDbPass)) {
            $env:DB_PASS = $machineDbPass
        } else {
            Write-Host "[ERROR] DB_PASS environment variable is not set." -ForegroundColor Red
            Write-Host "Please set your MySQL password before running HireHub:" -ForegroundColor Yellow
            Write-Host '  $env:DB_PASS = "your_mysql_password"' -ForegroundColor Yellow
            exit 1
        }
    }
}

# Optional GroqCloud API key check
if ([string]::IsNullOrWhiteSpace($env:GROQ_API_KEY)) {
    $userKey = [System.Environment]::GetEnvironmentVariable("GROQ_API_KEY", "User")
    if (-not [string]::IsNullOrWhiteSpace($userKey)) {
        $env:GROQ_API_KEY = $userKey
    } else {
        $machineKey = [System.Environment]::GetEnvironmentVariable("GROQ_API_KEY", "Machine")
        if (-not [string]::IsNullOrWhiteSpace($machineKey)) {
            $env:GROQ_API_KEY = $machineKey
        } else {
            Write-Host "[WARN] GROQ_API_KEY environment variable is not set." -ForegroundColor Yellow
            Write-Host "       AI features will remain disabled until configured." -ForegroundColor Yellow
        }
    }
}

# Paths and dependency definitions
$java    = "C:\Program Files\Java\jdk-26.0.2\bin\java.exe"
$m2      = "$env:USERPROFILE\.m2\repository"
$jfxVer  = "21.0.5"
$project = "d:\HireHub"

# 1. JavaFX Module Path (OpenJFX native modules)
$modulePathJars = @(
    "$m2\org\openjfx\javafx-controls\$jfxVer\javafx-controls-$jfxVer-win.jar",
    "$m2\org\openjfx\javafx-fxml\$jfxVer\javafx-fxml-$jfxVer-win.jar",
    "$m2\org\openjfx\javafx-graphics\$jfxVer\javafx-graphics-$jfxVer-win.jar",
    "$m2\org\openjfx\javafx-base\$jfxVer\javafx-base-$jfxVer-win.jar"
)

# 2. Runtime Classpath Dependencies (BCrypt, MySQL Connector, Gson, PDFBox, HikariCP, SLF4J)
$classpathJars = @(
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

# 3. Verify all required JARs exist before launching
$allRequiredJars = $modulePathJars + $classpathJars
$missingJars = @()
foreach ($jar in $allRequiredJars) {
    if (-not (Test-Path $jar)) {
        $missingJars += (Split-Path $jar -Leaf)
    }
}

if ($missingJars.Count -gt 0) {
    Write-Host "[ERROR] Missing required runtime dependency JARs:" -ForegroundColor Red
    foreach ($missing in $missingJars) {
        Write-Host "  - $missing" -ForegroundColor Red
    }
    exit 1
}

# 4. Target classes directory check
$classesDir = "$project\target\classes"
if (-not (Test-Path $classesDir)) {
    Write-Host "[ERROR] Target classes directory not found: $classesDir" -ForegroundColor Red
    exit 1
}

# 5. Build module path and classpath strings
$allModules = $modulePathJars + $classpathJars
$modulePath = $allModules -join ";"
$cp = @($classesDir) + $classpathJars -join ";"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  HireHub - Starting..." -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  DB Host : $env:DB_HOST" -ForegroundColor Gray
Write-Host "  DB Port : $env:DB_PORT" -ForegroundColor Gray
Write-Host "  DB Name : $env:DB_NAME" -ForegroundColor Gray
Write-Host "  DB User : $env:DB_USER" -ForegroundColor Gray
Write-Host ""

& $java `
    --module-path $modulePath `
    --add-modules javafx.controls,javafx.fxml,jbcrypt,com.google.gson,org.apache.pdfbox,com.zaxxer.hikari,org.slf4j,org.slf4j.simple `
    -cp $cp `
    com.hirehub.Main

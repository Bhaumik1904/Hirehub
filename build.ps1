# ============================================================
# HireHub - Build Script
# Compiles production sources, test suites, and copies resources.
# ============================================================
param(
    [switch]$SkipTests = $false
)

$ErrorActionPreference = "Stop"

$javaDir = "C:\Program Files\Java\jdk-26.0.2\bin"
$javac   = "$javaDir\javac.exe"
$m2      = "$env:USERPROFILE\.m2\repository"
$jfxVer  = "21.0.5"
$project = "d:\HireHub"

# 1. Module path (JavaFX + Third-party dependencies)
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

# Verify dependencies exist
$missingDeps = @()
foreach ($jar in $dependencyJars) {
    if (-not (Test-Path $jar)) {
        $missingDeps += (Split-Path $jar -Leaf)
    }
}
if ($missingDeps.Count -gt 0) {
    Write-Host "[ERROR] Missing required build dependencies:" -ForegroundColor Red
    foreach ($m in $missingDeps) { Write-Host "  - $m" -ForegroundColor Red }
    exit 1
}

$modulePath = $dependencyJars -join ";"
$classesDir = "$project\target\classes"
$testClassesDir = "$project\target\test-classes"

if (-not (Test-Path $classesDir)) {
    New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
}
if (-not (Test-Path $testClassesDir)) {
    New-Item -ItemType Directory -Force -Path $testClassesDir | Out-Null
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  HireHub - Clean Build" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 2. Copy resources (FXML, CSS, images) into target/classes
if (Test-Path "$project\src\main\resources") {
    Copy-Item -Path "$project\src\main\resources\*" -Destination $classesDir -Recurse -Force
    Write-Host "[1/3] Resources copied to target\classes" -ForegroundColor Green
} else {
    Write-Host "[1/3] WARN: No resources found in src\main\resources" -ForegroundColor Yellow
}

# 3. Compile main application sources (modular)
$srcFiles = Get-ChildItem -Path "$project\src\main\java" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
Write-Host "[2/3] Compiling $($srcFiles.Count) production Java sources..." -ForegroundColor Cyan

& $javac `
    --module-path $modulePath `
    -d $classesDir `
    -encoding UTF-8 `
    $srcFiles

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Main application compilation failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "      Production compilation succeeded." -ForegroundColor Green

# 4. Compile test sources
if (-not $SkipTests -and (Test-Path "$project\src\test\java")) {
    $testFiles = Get-ChildItem -Path "$project\src\test\java" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
    if ($testFiles.Count -gt 0) {
        Write-Host "[3/3] Compiling $($testFiles.Count) test suite Java sources..." -ForegroundColor Cyan
        $testCp = "$classesDir;$modulePath"
        & $javac `
            -cp $testCp `
            -d $testClassesDir `
            -encoding UTF-8 `
            $testFiles

        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERROR] Test compilation failed with exit code $LASTEXITCODE" -ForegroundColor Red
            exit $LASTEXITCODE
        }
        Write-Host "      Test suite compilation succeeded." -ForegroundColor Green
    } else {
        Write-Host "[3/3] No test sources found." -ForegroundColor Gray
    }
} else {
    Write-Host "[3/3] Test compilation skipped." -ForegroundColor Gray
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  BUILD SUCCESS: All targets compiled cleanly." -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
exit 0

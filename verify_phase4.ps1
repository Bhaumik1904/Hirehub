# ============================================================
# HireHub - Phase 4 Verification Runner (Deprecated)
# Retained for backward compatibility; delegates to verify.ps1.
# ============================================================
param(
    [string]$MainClass = "ResumeAnalysisTest"
)

$project = Split-Path -Parent $MyInvocation.MyCommand.Path

# Map legacy class names to new professional test names if needed
if ($MainClass -eq "com.hirehub.util.Phase4VerificationTest" -or $MainClass -eq "Phase4VerificationTest") {
    $MainClass = "ResumeAnalysisTest"
}

Write-Host "[DEPRECATION NOTICE] 'verify_phase4.ps1' is deprecated and retained for backward compatibility." -ForegroundColor Yellow
Write-Host "                     Please use '.\verify.ps1 -Test $MainClass' instead." -ForegroundColor Yellow
Write-Host ""

& "$project\verify.ps1" -Test $MainClass
exit $LASTEXITCODE

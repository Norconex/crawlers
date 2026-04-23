param(
    [Parameter(Mandatory = $true)]
    [string]$Scenario,

    [string]$OutDir = "./results/$(Get-Date -Format 'yyyyMMdd-HHmmss')",

    [string]$BaselineDir,

    [string]$FixtureBase = "http://localhost:8181",

    [switch]$StartFixture,

    [switch]$NoFixtureCleanup
)

Write-Host "Benchmark runner"
Write-Host "  Scenario    : $Scenario"
Write-Host "  OutDir      : $OutDir"
Write-Host "  FixtureBase : $FixtureBase"
if ($BaselineDir) {
    Write-Host "  Baseline    : $BaselineDir"
}
if ($NoFixtureCleanup) {
    Write-Host "  Cleanup     : disabled (-NoFixtureCleanup)"
}

function Wait-FixtureReady {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds = 60
    )

    $healthUrl = "$BaseUrl/health"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 3 -UseBasicParsing
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300) {
                Write-Host "Fixture is ready: $healthUrl"
                return
            }
        }
        catch {
        }
        Start-Sleep -Milliseconds 500
    }

    throw "Fixture server did not become ready within $TimeoutSeconds seconds ($healthUrl)."
}

function Stop-FixturePortListeners {
    param(
        [int]$Port
    )

    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($connections) {
        $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $pids) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root

$fixtureProcess = $null
$fixturePort = 8181
try {
    if ($StartFixture) {
        Write-Host "Starting fixture server..."
        $fixtureUri = [System.Uri]$FixtureBase
        $fixturePort = if ($fixtureUri.Port -gt 0) { $fixtureUri.Port } else { 8181 }

        Stop-FixturePortListeners -Port $fixturePort
        $fixtureArgs = @(
            "-f",
            "benchmark/web-fixtures/pom.xml",
            "-DskipTests",
            "compile",
            "exec:java"
        )
        $fixtureProcess = Start-Process -FilePath "mvn" -ArgumentList $fixtureArgs -WorkingDirectory $root -WindowStyle Hidden -PassThru
        Wait-FixtureReady -BaseUrl $FixtureBase -TimeoutSeconds 90
    }

    $cmd = @(
        "-f", "benchmark/harness/pom.xml",
        "exec:java",
        "-Dexec.args=--scenario $Scenario --out $OutDir --fixtureBase $FixtureBase"
    )
    if ($BaselineDir) {
        $cmd[-1] = "$($cmd[-1]) --baseline $BaselineDir"
    }

    & mvn @cmd
    if ($LASTEXITCODE -ne 0) {
        throw "Benchmark harness failed with exit code $LASTEXITCODE"
    }
}
finally {
    if ($NoFixtureCleanup) {
        Write-Host "Skipping fixture cleanup (requested)."
    }
    else {
        if ($fixtureProcess) {
            Write-Host "Stopping fixture server process..."
            Stop-Process -Id $fixtureProcess.Id -Force -ErrorAction SilentlyContinue
        }
        if ($StartFixture) {
            Stop-FixturePortListeners -Port $fixturePort
        }
    }
    Pop-Location
}

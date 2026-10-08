[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE"
    }
}

Push-Location (Join-Path $projectRoot 'backend')
try {
    Invoke-Checked -Command 'mvn' -Arguments @('clean', 'verify')
} finally {
    Pop-Location
}

Push-Location (Join-Path $projectRoot 'frontend')
try {
    Invoke-Checked -Command 'npm' -Arguments @('ci')
    Invoke-Checked -Command 'npm' -Arguments @('run', 'lint')
    Invoke-Checked -Command 'npm' -Arguments @('run', 'format:check')
    Invoke-Checked -Command 'npm' -Arguments @('run', 'build')
} finally {
    Pop-Location
}

Write-Host 'All backend and frontend quality gates passed.' -ForegroundColor Green

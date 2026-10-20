# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
Gate a production MSP on install, patch, launch, repair and uninstall checks.
.DESCRIPTION
Uses the exact previous published MSI and newly built MSP on hosted Windows CI.
No package is published if any check fails. Reboot requests are failures here.
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted') {
    throw 'Production patch validation requires a disposable hosted Windows runner.'
}
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Output = "$Root/target/windows-production"
$Metadata = Get-Content "$Output/inputs.json" -Raw | ConvertFrom-Json
$ProductCode = '{' + $Metadata.productCode + '}'
$Registration = "HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall/$ProductCode"
$InstallDirectory = Join-Path $env:ProgramFiles 'OSCAR-UI'
$Logs = "$Root/target/installer-logs"
New-Item -ItemType Directory -Force $Logs | Out-Null
if ((Test-Path $InstallDirectory) -or (Test-Path $Registration)) {
    throw 'Existing installation; refusing lifecycle test'
}
$Msi = (Get-Item "$Output/previous.msi").FullName
$Patch = (Get-Item "$Root/dist/windows/release/*.msp").FullName
$Fixture = "$Root/src/test/resources/simple-docsis.txt"
$FixtureHash = (Get-FileHash $Fixture).Hash

# Run an installer operation and retain its log, rejecting reboot and failures.
function Invoke-Operation([string]$Name, [string[]]$Arguments) {
    Write-Host "[Production MSP] $Name"
    $Process = Start-Process msiexec.exe -ArgumentList ($Arguments + @('/qn', '/norestart', '/L*v', "`"$Logs/patch-$Name.log`"")) -PassThru
    if (!$Process.WaitForExit(300000)) {
        $Process.Kill(); throw "Timed out: $Name"
    }
    $Process.Refresh()
    if ($Process.ExitCode -ne 0) {
        throw "Installer $Name exited $($Process.ExitCode)"
    }
}

# Check target payload and registration, then launch the installed application.
function Test-Target {
    & python "$Root/scripts/build/windows-installer.py" verify --image $InstallDirectory --output $Output
    if ($LASTEXITCODE -ne 0) {
        throw 'Patched payload hash mismatch'
    }
    if ((Get-ItemProperty $Registration).DisplayVersion -ne $Metadata.version) {
        throw 'Patched version registration mismatch'
    }
    $OriginalEnvironment = @{}
    foreach ($Name in @('PATH', 'JAVA_HOME', 'QT_PLUGIN_PATH', 'QT_QPA_PLATFORM')) {
        $OriginalEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name)
    }
    try {
        $env:PATH = "$env:SystemRoot/System32;$env:SystemRoot"
        $env:JAVA_HOME = ''
        $env:QT_PLUGIN_PATH = ''
        $env:QT_QPA_PLATFORM = 'windows'
        $Process = Start-Process "$InstallDirectory/OSCAR-UI.exe" -ArgumentList @('--smoke-test', "`"$Fixture`"") -PassThru
        if (!$Process.WaitForExit(40000)) {
            $Process.Kill(); throw 'Patched launcher timed out'
        }
        $Process.Refresh()
        if ($Process.ExitCode -ne 0) {
            throw 'Patched launcher failed'
        }
    } finally {
        foreach ($Name in $OriginalEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($Name, $OriginalEnvironment[$Name])
        }
    }
}

try {
    Invoke-Operation 'baseline' @('/i', "`"$Msi`"")
    Invoke-Operation 'apply' @('/p', "`"$Patch`"", 'REINSTALL=ALL', 'REINSTALLMODE=omus')
    Test-Target
    Invoke-Operation 'repeat' @('/p', "`"$Patch`"", 'REINSTALL=ALL', 'REINSTALLMODE=omus')
    Test-Target
    Invoke-Operation 'repair' @('/fomus', $ProductCode)
    Test-Target
    Invoke-Operation 'forced-repair' @('/fa', $ProductCode)
    Test-Target
    $Chain = @(Get-Content "$Output/chain.json" -Raw | ConvertFrom-Json)
    if ($Chain.Count -gt 0) {
        Invoke-Operation 'remove-before-chain' @('/x', $ProductCode)
        $OriginalMsi = (Get-Item "$Output/chain-baseline.msi").FullName
        Invoke-Operation 'original-baseline' @('/i', "`"$OriginalMsi`"")
        $Index = 0
        foreach ($PriorPatch in $Chain) {
            $PriorPath = (Get-Item $PriorPatch).FullName
            Invoke-Operation "chain-$Index" @('/p', "`"$PriorPath`"", 'REINSTALL=ALL', 'REINSTALLMODE=omus')
            $Index++
        }
        Invoke-Operation 'chain-update' @('/p', "`"$Patch`"", 'REINSTALL=ALL', 'REINSTALLMODE=omus')
        Test-Target
        Invoke-Operation 'chain-forced-repair' @('/fa', $ProductCode)
        Test-Target
    }
} finally {
    if (Test-Path $Registration) {
        Invoke-Operation 'uninstall' @('/x', $ProductCode)
    }
}
if ((Test-Path $InstallDirectory) -or (Test-Path $Registration)) {
    throw 'Patched uninstall left content'
}
if ((Get-FileHash $Fixture).Hash -ne $FixtureHash) {
    throw 'User fixture changed'
}
'PRODUCTION_PATCH_LIFECYCLE_OK' | Set-Content "$Logs/production-patch-result.txt"

# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
Build and exercise an isolated MSP prototype using the real application image.
.DESCRIPTION
Run only on a disposable hosted Windows Actions runner after windows-release.ps1.
Uses synthetic versions and a separate product identity. Does not publish assets
or alter the production installer. Retains evidence under dist/windows/patch-prototype.
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted') {
    throw 'Patch lifecycle tests require a disposable GitHub-hosted runner.'
}
$Output = Join-Path $Root 'dist/windows/patch-prototype'
$InstallDirectory = Join-Path $env:ProgramFiles 'OSCAR-UI Patch Prototype Test'
$ProductCode = '{C86DCBBA-7394-43F0-BF23-376EBD499FE6}'
$Registration = "HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall/$ProductCode"
if ((Test-Path $InstallDirectory) -or (Test-Path $Registration) -or (Test-Path $Output)) {
    throw 'Prototype installation or output already exists; refusing to overwrite it.'
}
$Python = (Get-Command python).Source
$Helper = Join-Path $Root 'scripts/build/windows-patch-prototype.py'

# Execute a tool synchronously, logging progress and stopping on nonzero exit.
function Invoke-Checked([string]$Tool, [string[]]$Arguments) {
    Write-Host "[Patch prototype] $Tool $($Arguments -join ' ')"
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit $LASTEXITCODE" }
}

# Resolve an existing package before quoting it for msiexec. Windows Installer
# rejects mixed separators even when PowerShell and WiX accept the same path.
function Get-MsiPackageArgument([string]$PackagePath) {
    $Package = Get-Item -LiteralPath $PackagePath -ErrorAction Stop
    if ($Package.PSIsContainer) { throw "Expected a package file: $PackagePath" }
    return '"' + $Package.FullName.Replace('/', '\') + '"'
}

# Run a logged MSI operation. A reboot-required result stops this prototype;
# it must not be reported as a successfully tested launch on the new payload.
function Invoke-Installer([string]$Label, [string[]]$Arguments, [int]$Expected = 0) {
    Write-Host "[Patch prototype] Installer: $Label"
    $LogPath = Join-Path $Output "$Label.log"
    $Process = Start-Process msiexec.exe -ArgumentList ($Arguments + @('/qn', '/norestart', '/L*v', "`"$LogPath`"")) -PassThru
    if (!$Process.WaitForExit(300000)) {
        $Process.Kill()
        throw "Installer timed out at $Label; see $LogPath"
    }
    $Process.Refresh()
    if ($Process.ExitCode -ne $Expected) {
        throw "Installer $Label returned $($Process.ExitCode), expected $Expected; see $LogPath"
    }
}

# Check all packaged bytes, About's version resource, registration and launch
# using the bundled runtime with developer runtime environment removed.
function Test-InstalledStage([string]$Stage, [string]$Version) {
    Invoke-Checked $Python @($Helper, 'verify', '--image', $InstallDirectory,
        '--evidence', "$Output/evidence.json", '--stage', $Stage)
    $RegisteredVersion = (Get-ItemProperty $Registration).DisplayVersion
    Write-Host "[Patch prototype] $Stage registered version: $RegisteredVersion; expected: $Version"
    if ($RegisteredVersion -ne $Version) {
        throw "Installed apps version is $RegisteredVersion; expected $Version"
    }
    $SavedEnvironment = @{}
    foreach ($Name in @('PATH', 'JAVA_HOME', 'QT_PLUGIN_PATH', 'QT_QPA_PLATFORM')) {
        $SavedEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name)
    }
    try {
        $env:PATH = "$env:SystemRoot/System32;$env:SystemRoot"
        $env:JAVA_HOME = ''
        $env:QT_PLUGIN_PATH = ''
        $env:QT_QPA_PLATFORM = 'windows'
        $Process = Start-Process "$InstallDirectory/OSCAR-UI.exe" -WorkingDirectory $InstallDirectory `
            -ArgumentList @('--smoke-test', "`"$Fixture`"") -PassThru
        if (!$Process.WaitForExit(40000)) {
            $Process.Kill()
            throw "Launch timed out at $Stage"
        }
        $Process.Refresh()
        if ($Process.ExitCode -ne 0) { throw "Launch failed at $Stage`: $($Process.ExitCode)" }
    } finally {
        foreach ($Name in $SavedEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($Name, $SavedEnvironment[$Name])
        }
    }
}

Invoke-Checked $Python @($Helper, 'prepare', '--image', "$Root/dist/windows/app-image/OSCAR-UI", '--output', $Output)
foreach ($Stage in @('baseline', 'target')) {
    Invoke-Checked 'candle.exe' @('-nologo', '-arch', 'x64', '-out', "$Output/$Stage/product.wixobj", "$Output/$Stage/product.wxs")
    Invoke-Checked 'light.exe' @('-nologo', '-out', "$Output/$Stage/product.msi", "$Output/$Stage/product.wixobj")
}
# Retain a read-only comparison, and require its conservative identity/layout gate.
& "$Root/scripts/windows/compare-msi-baselines.ps1" -Baseline "$Output/baseline/product.msi" `
    -Target "$Output/target/product.msi" > "$Output/msi-comparison.json"
if ($LASTEXITCODE -ne 0) { throw 'MSI identity/layout comparison failed' }
Invoke-Checked 'torch.exe' @('-p', '-xi', "$Output/baseline/product.wixpdb", "$Output/target/product.wixpdb", '-out', "$Output/patch.wixmst")
Invoke-Checked 'candle.exe' @('-nologo', '-out', "$Output/patch.wixobj", "$Output/patch.wxs")
Invoke-Checked 'light.exe' @('-nologo', '-out', "$Output/patch.wixmsp", "$Output/patch.wixobj")
Invoke-Checked 'pyro.exe' @("$Output/patch.wixmsp", '-out', "$Output/prototype.msp", '-t', 'Baseline', "$Output/patch.wixmst")
Get-Item "$Output/baseline/product.msi", "$Output/target/product.msi", "$Output/prototype.msp" |
    Select-Object Name, Length | ConvertTo-Json | Set-Content "$Output/sizes.json"
$Fixture = Join-Path $Output 'user configuration.txt'
Copy-Item "$Root/src/test/resources/simple-docsis.txt" $Fixture
$FixtureHash = (Get-FileHash $Fixture).Hash
$BaselineMsi = Get-MsiPackageArgument "$Output/baseline/product.msi"
$Patch = Get-MsiPackageArgument "$Output/prototype.msp"
try {
    Invoke-Installer 'reject-missing-product' @('/p', $Patch) 1642
    Invoke-Installer 'install' @('/i', $BaselineMsi, "INSTALLDIR=`"$InstallDirectory`"")
    Test-InstalledStage 'baseline' '0.0.1'
    Invoke-Installer 'patch' @('/p', $Patch, 'REINSTALL=ALL', 'REINSTALLMODE=omus')
    Test-InstalledStage 'target' '0.0.2'
    Invoke-Installer 'repeat-patch' @('/p', $Patch, 'REINSTALL=ALL', 'REINSTALLMODE=omus')
    Test-InstalledStage 'target' '0.0.2'
    Invoke-Installer 'normal-repair' @('/fomus', $ProductCode)
    Test-InstalledStage 'target' '0.0.2'
    Invoke-Installer 'forced-repair' @('/fa', $ProductCode)
    Test-InstalledStage 'target' '0.0.2'
    Invoke-Installer 'remove-patch' @('/uninstall', $Patch, '/package', $ProductCode)
    Test-InstalledStage 'baseline' '0.0.1'
    Invoke-Installer 'reapply-patch' @('/p', $Patch, 'REINSTALL=ALL', 'REINSTALLMODE=omus')
    Test-InstalledStage 'target' '0.0.2'
} finally {
    if (Test-Path $Registration) {
        Invoke-Installer 'uninstall' @('/x', $ProductCode)
    }
}
if ((Test-Path $Registration) -or (Test-Path $InstallDirectory)) {
    throw 'Uninstall left prototype registration or files'
}
if ((Get-FileHash $Fixture).Hash -ne $FixtureHash) { throw 'User configuration changed' }
'PATCH_PROTOTYPE_LIFECYCLE_OK' | Set-Content "$Output/result.txt"
Write-Host '[Patch prototype] PASS. Synthetic proof only; release updater remains disabled.'

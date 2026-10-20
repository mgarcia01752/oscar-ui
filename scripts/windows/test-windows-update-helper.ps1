# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
Test Windows updater validation with mocked GitHub and registry reads.
.DESCRIPTION
Extracts only validation functions; never runs the updater entrypoint, elevation,
network downloads, application exit, or Windows Installer.
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Tokens = $null; $Errors = $null
$Ast = [Management.Automation.Language.Parser]::ParseFile("$PSScriptRoot/windows-update.ps1", [ref]$Tokens, [ref]$Errors)
if ($Errors.Count) {
    throw 'Updater syntax failed'
}
foreach ($Name in @('Get-CompatibleUpdate', 'Test-PackageHash')) {
    $Definition = $Ast.Find({ param($Node)
        $Node -is [Management.Automation.Language.FunctionDefinitionAst] -and $Node.Name -eq $Name
    }, $true)
    Invoke-Expression $Definition.Extent.Text
}
$FixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('oscar-update-test-' + [guid]::NewGuid())
$OriginalProgramFiles = $env:ProgramFiles
$env:ProgramFiles = Join-Path $FixtureRoot 'Program Files'
$InstallRoot = Join-Path $env:ProgramFiles 'OSCAR-UI'
$Project = 'https://github.com/mgarcia01752/oscar-ui'
$Api = 'https://api.github.com/repos/mgarcia01752/oscar-ui'
$Version = '1.0.1'
$Prompt = $Ast.Find({ param($Node)
    $Node -is [Management.Automation.Language.ExpandableStringExpressionAst] -and
    $Node.Value.StartsWith('Install OSCAR-UI ')
}, $true)
if (!$Prompt -or (Invoke-Expression $Prompt.Extent.Text) -ne
    "Install OSCAR-UI ${Version}? Windows will request administrator approval.") {
    throw 'Update confirmation prompt failed'
}
$RegisteredVersion = '1.0.0'
$FakeHash = 'a' * 64

# Return the synthetic registry version; do not access installed products.
function Get-ItemProperty([string]$Path) { return @{ DisplayVersion = $RegisteredVersion } }
# Return fixture release metadata instead of reaching GitHub.
function Invoke-RestMethod([string]$Uri, [int]$TimeoutSec) { return $Release }
# Supply fixture metadata; package hashing is exercised separately below.
function Save-VerifiedAsset([string]$Url, [long]$Size, [string]$Digest, [string]$Destination) {
    $Manifest | ConvertTo-Json -Depth 5 | Set-Content $Destination
}
# Require an invalid operation to fail without suppressing unexpected successes.
function Assert-Rejected([scriptblock]$Operation) {
    $Rejected = $false
    try { & $Operation | Out-Null } catch { $Rejected = $true }
    if (!$Rejected) {
        throw 'Expected updater validation to reject input'
    }
}
try {
    New-Item -ItemType Directory "$InstallRoot/app" -Force | Out-Null
    'runtime fixture' | Set-Content "$InstallRoot/runtime.txt"
    $DependencyHash = (Get-FileHash "$InstallRoot/runtime.txt").Hash
    @{ layout = 1; productCode = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'; fingerprint = $FakeHash
       dependencies = @{ 'runtime.txt' = $DependencyHash } } | ConvertTo-Json -Depth 3 |
        Set-Content "$InstallRoot/app/windows-baseline.json"
    $Manifest = @{ layout = 1; version = $Version; fromVersion = '1.0.0'
        productCode = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'; fingerprint = $FakeHash
        patch = @{url = "$Project/releases/download/v$Version/OSCAR-UI-$Version-windows-x64.msp"
                  size = 100; digest = "sha256:$FakeHash"} }
    $Release = @{ draft = $false; prerelease = $false; tag_name = "v$Version"
        assets = @(@{name = 'windows-update.json'; size = 500; digest = "sha256:$FakeHash"
                     browser_download_url = "$Project/releases/download/v$Version/windows-update.json"},
                   @{name = "OSCAR-UI-$Version-windows-x64.msp"; size = 100; digest = "sha256:$FakeHash"
                     browser_download_url = $Manifest.patch.url}) }
    if ((Get-CompatibleUpdate).version -ne $Version) {
        throw 'Compatible patch was not selected'
    }
    $RegisteredVersion = '1.0.2'
    Assert-Rejected { Get-CompatibleUpdate }
    $RegisteredVersion = '0.9.9'
    Assert-Rejected { Get-CompatibleUpdate }
    $RegisteredVersion = '1.0.0'
    $Manifest.fingerprint = 'b' * 64
    Assert-Rejected { Get-CompatibleUpdate }
    $Manifest.fingerprint = $FakeHash
    $Release.assets[1].digest = 'sha256:' + ('b' * 64)
    Assert-Rejected { Get-CompatibleUpdate }
    $Release.assets[1].digest = "sha256:$FakeHash"
    'tampered runtime' | Set-Content "$InstallRoot/runtime.txt"
    Assert-Rejected { Get-CompatibleUpdate }
    $Size = (Get-Item "$InstallRoot/runtime.txt").Length
    $Hash = 'sha256:' + (Get-FileHash "$InstallRoot/runtime.txt").Hash
    Test-PackageHash "$InstallRoot/runtime.txt" $Size $Hash
    Assert-Rejected { Test-PackageHash "$InstallRoot/runtime.txt" ($Size + 1) $Hash }
    Assert-Rejected { Test-PackageHash "$InstallRoot/runtime.txt" $Size "sha256:$FakeHash" }
    Write-Host 'WINDOWS_UPDATE_VALIDATION_OK'
} finally {
    $env:ProgramFiles = $OriginalProgramFiles
    Remove-Item $FixtureRoot -Recurse -Force
}

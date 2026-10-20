# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
Build the production MSI, compact patch inputs, and a compatible MSP when possible.
.DESCRIPTION
Consumes the prepared application image. Uses only the latest published stable
release as a patch source. Release publishing remains the coordinator's job.
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Image = "$Root/dist/windows/app-image/OSCAR-UI"
$Output = "$Root/target/windows-production"
$Release = "$Root/dist/windows/release"
[xml]$Pom = Get-Content "$Root/pom.xml"
$Version = [string]$Pom.project.version
$Project = 'https://github.com/mgarcia01752/oscar-ui'
$Builder = "$Root/scripts/build/windows-installer.py"

# Run build tools with checked exit codes and visible progress.
function Invoke-Build([string]$Tool, [string[]]$Arguments) {
    Write-Host "[Windows MSI] $Tool $($Arguments -join ' ')"
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Tool exited $LASTEXITCODE"
    }
}

# Make the generated launcher replaceable in the copied previous-version image.
# jpackage marks it read-only. Change only that attribute, never its bytes.
function Initialize-PatchLauncher([string]$ImagePath) {
    $Launcher = Get-Item -LiteralPath (Join-Path $ImagePath 'OSCAR-UI.exe')
    $Launcher.IsReadOnly = $false
    Write-Host '[Windows MSI] Prepared launcher attributes for patch image copying.'
}

# Return a build-local Torch executable with the WiX #5778 compatibility switch.
# Preserve installed WiX and other runtime settings; never modify the registry.
function Initialize-PatchTorch([string]$WixDirectory, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination) {
        throw 'Local Torch directory already exists'
    }
    Copy-Item -LiteralPath $WixDirectory -Destination $Destination -Recurse
    $ConfigPath = Join-Path $Destination 'torch.exe.config'
    $Config = [xml](Get-Content -LiteralPath $ConfigPath -Raw)
    $Runtime = $Config.SelectSingleNode('/configuration/runtime')
    if (!$Runtime) {
        $Runtime = $Config.configuration.AppendChild($Config.CreateElement('runtime'))
    }
    $Overrides = $Runtime.SelectSingleNode('AppContextSwitchOverrides')
    if (!$Overrides) {
        $Overrides = $Runtime.AppendChild($Config.CreateElement('AppContextSwitchOverrides'))
    }
    $Switch = 'Switch.System.DisableTempFileCollectionDirectoryFeature'
    $Values = @($Overrides.GetAttribute('value').Split(';') |
        Where-Object { $_ -and !$_.StartsWith("$Switch=") })
    $Overrides.SetAttribute('value', (($Values + "$Switch=true") -join ';'))
    $Config.Save($ConfigPath)
    Write-Host '[Windows MSI] Prepared build-local Torch temporary-file compatibility.'
    return Join-Path $Destination 'torch.exe'
}

# Download an exact GitHub release asset and verify its API-reported digest.
function Get-VerifiedAsset($Asset, [string]$Destination, [string]$Tag) {
    if (!$Asset.digest -or $Asset.digest -notmatch '^sha256:[a-fA-F0-9]{64}$' -or
        !$Asset.browser_download_url.StartsWith("$Project/releases/download/$Tag/")) {
        throw 'Release asset has invalid URL or missing SHA-256'
    }
    Invoke-WebRequest $Asset.browser_download_url -OutFile $Destination
    if ((Get-Item $Destination).Length -ne $Asset.size -or
        (Get-FileHash $Destination -Algorithm SHA256).Hash -ne $Asset.digest.Substring(7)) {
        throw 'Release asset size or digest mismatch'
    }
}

# Authenticate only fixed-repository API metadata requests, never asset URLs.
# Local builds may use public API access when GH_TOKEN is not supplied.
function Get-ReleaseMetadata([string]$ReleasePath) {
    if ($ReleasePath -notmatch '^(latest|tags/v[0-9]+\.[0-9]+\.[0-9]+)$') {
        throw 'Invalid release metadata path'
    }
    $Headers = @{ Accept = 'application/vnd.github+json' }
    if ($env:GH_TOKEN) {
        $Headers.Authorization = "Bearer $env:GH_TOKEN"
    }
    Invoke-RestMethod -Uri "https://api.github.com/repos/mgarcia01752/oscar-ui/releases/$ReleasePath" -Headers $Headers
}

$Previous = $null
$Latest = $null
try {
    $Latest = Get-ReleaseMetadata 'latest'
} catch {
    if (!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404) {
        throw
    }
}
if ($Latest -and $Latest.tag_name -eq "v$Version" -and $env:GITHUB_REF_TYPE -eq 'tag') {
    throw 'This version is already published; do not regenerate its installer identity.'
}
if ($Latest -and !$Latest.draft -and !$Latest.prerelease) {
    $Inputs = @($Latest.assets | Where-Object name -eq 'windows-patch-inputs.zip')
    if ($Inputs.Count -eq 1) {
        $Previous = "$Root/target/windows-previous"
        if (Test-Path $Previous) {
            throw 'Previous input directory already exists'
        }
        New-Item -ItemType Directory $Previous | Out-Null
        Get-VerifiedAsset $Inputs[0] "$Previous/inputs.zip" $Latest.tag_name
        # Extract a fixed allowlist only; ignore paths supplied by ZIP entries.
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $Archive = [IO.Compression.ZipFile]::OpenRead("$Previous/inputs.zip")
        try {
            foreach ($Name in @('inputs.json', 'product.wixpdb', 'OSCAR-UI.exe', 'app/oscar-ui.jar')) {
                $Entry = $Archive.GetEntry($Name)
                if (!$Entry -or $Entry.Length -gt 100MB) {
                    throw "Missing/oversized patch input: $Name"
                }
                $Destination = Join-Path $Previous $Name
                New-Item -ItemType Directory -Force (Split-Path $Destination) | Out-Null
                [IO.Compression.ZipFileExtensions]::ExtractToFile($Entry, $Destination)
            }
        } finally { $Archive.Dispose() }
    }
}
$Arguments = @($Builder, 'prepare', '--image', $Image, '--version', $Version, '--output', $Output)
if ($Previous) {
    $Arguments += @('--previous', $Previous)
}
Initialize-PatchLauncher $Image
Invoke-Build python $Arguments
Invoke-Build candle.exe @('-nologo', '-arch', 'x64', '-out', "$Output/product.wixobj", "$Output/product.wxs")
Invoke-Build light.exe @('-nologo', '-ext', 'WixUIExtension', '-b', "payload=$Image", '-out', "$Output/product.msi", "$Output/product.wixobj")
New-Item -ItemType Directory -Force $Release | Out-Null
Copy-Item "$Output/product.msi" "$Release/OSCAR-UI-$Version-windows-x64.msi"
Invoke-Build python @($Builder, 'archive', '--image', $Image, '--output', $Output, '--archive', "$Release/windows-patch-inputs.zip")
$Metadata = Get-Content "$Output/inputs.json" -Raw | ConvertFrom-Json
$Manifest = @{
    layout = 1; version = $Version; productCode = $Metadata.productCode
    fingerprint = $Metadata.fingerprint; fromVersion = $Metadata.fromVersion; patch = $null
}
if ($Metadata.fromVersion) {
    if ($Latest.tag_name -ne "v$($Metadata.fromVersion)") {
        throw 'Previous release identity mismatch'
    }
    $WixDirectory = Split-Path (Get-Command torch.exe -CommandType Application).Source
    $Torch = Initialize-PatchTorch $WixDirectory "$Output/torch-tools"
    Invoke-Build $Torch @('-p', '-xi', "$Output/previous.wixpdb", "$Output/product.wixpdb", '-out', "$Output/patch.wixmst")
    Invoke-Build candle.exe @('-nologo', '-out', "$Output/patch.wixobj", "$Output/patch.wxs")
    Invoke-Build light.exe @('-nologo', '-out', "$Output/patch.wixmsp", "$Output/patch.wixobj")
    $PatchName = "OSCAR-UI-$Version-windows-x64.msp"
    Invoke-Build pyro.exe @("$Output/patch.wixmsp", '-out', "$Release/$PatchName", '-t', 'Previous', "$Output/patch.wixmst",
        '-bt', "payload=$Output/previous-image", '-bu', "payload=$Image")
    $Manifest.patch = @{ url = "$Project/releases/download/v$Version/$PatchName"
        size = (Get-Item "$Release/$PatchName").Length
        digest = 'sha256:' + (Get-FileHash "$Release/$PatchName" -Algorithm SHA256).Hash.ToLowerInvariant() }
    $OldMsi = @($Latest.assets | Where-Object name -eq "OSCAR-UI-$($Metadata.fromVersion)-windows-x64.msi")
    if ($OldMsi.Count -ne 1) {
        throw 'Previous MSI unavailable for mandatory patch lifecycle test'
    }
    Get-VerifiedAsset $OldMsi[0] "$Output/previous.msi" $Latest.tag_name
    # Also exercise the installed patch chain, not just the previous full MSI.
    $Chain = @()
    $Cursor = $Metadata.fromVersion
    while ($Cursor -ne $Metadata.baselineVersion) {
        if ($Chain.Count -ge 20) {
            throw 'Patch chain exceeds supported test limit; establish a new baseline'
        }
        $PriorRelease = Get-ReleaseMetadata "tags/v$Cursor"
        $PriorManifestAsset = @($PriorRelease.assets | Where-Object name -eq 'windows-update.json')
        if ($PriorManifestAsset.Count -ne 1) {
            throw 'Missing patch-chain metadata'
        }
        Get-VerifiedAsset $PriorManifestAsset[0] "$Output/chain-$Cursor.json" "v$Cursor"
        $PriorManifest = Get-Content "$Output/chain-$Cursor.json" -Raw | ConvertFrom-Json
        if (!$PriorManifest.patch -or $PriorManifest.productCode -ne $Metadata.productCode -or
            [version]$PriorManifest.fromVersion -ge [version]$Cursor) { throw 'Invalid patch chain' }
        $PriorPatchAsset = @($PriorRelease.assets | Where-Object name -eq "OSCAR-UI-$Cursor-windows-x64.msp")
        if ($PriorPatchAsset.Count -ne 1) {
            throw 'Missing patch-chain artifact'
        }
        $PatchPath = "$Output/chain-$Cursor.msp"
        Get-VerifiedAsset $PriorPatchAsset[0] $PatchPath "v$Cursor"
        $Chain = @($PatchPath) + $Chain
        $Cursor = $PriorManifest.fromVersion
    }
    if ($Chain.Count -gt 0) {
        $BaselineRelease = Get-ReleaseMetadata "tags/v$Cursor"
        $BaselineAsset = @($BaselineRelease.assets | Where-Object name -eq "OSCAR-UI-$Cursor-windows-x64.msi")
        if ($BaselineAsset.Count -ne 1) {
            throw 'Missing original baseline MSI'
        }
        Get-VerifiedAsset $BaselineAsset[0] "$Output/chain-baseline.msi" "v$Cursor"
    }
    ConvertTo-Json -InputObject @($Chain) | Set-Content "$Output/chain.json"
}
$Manifest | ConvertTo-Json -Depth 4 | Set-Content "$Release/windows-update.json" -Encoding utf8
if ($Metadata.fromVersion -and $env:GITHUB_ACTIONS -eq 'true') {
    & "$Root/scripts/windows/test-production-patch.ps1"
    if ($LASTEXITCODE -ne 0) {
        throw 'Production patch lifecycle validation failed'
    }
}
Write-Host '[Windows MSI] Installer and update metadata ready; no release published.'

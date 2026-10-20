# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
Apply a compatible published OSCAR-UI MSP after the application exits.
.DESCRIPTION
Trusts HTTPS release metadata and SHA-256 digests from the OSCAR-UI GitHub
repository. This is repository trust, not Authenticode publisher signing.
The normal-user process downloads and restarts; the elevated child revalidates
metadata, copies into protected staging, checks the hash, and invokes msiexec.
#>
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [int]$WaitPid = 0,
    [switch]$Elevated,
    [string]$DownloadedPackage = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$InstallRoot = Split-Path -Parent $PSScriptRoot
$Project = 'https://github.com/mgarcia01752/oscar-ui'
$Api = 'https://api.github.com/repos/mgarcia01752/oscar-ui'
$DownloadDirectory = $null
$Staging = $null
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Net.Http
$script:CancelDownload = $false

# Verify a bounded release asset after download. The destination is caller-owned.
function Save-VerifiedAsset([string]$Url, [long]$Size, [string]$Digest, [string]$Destination) {
    if (!$Url.StartsWith("$Project/releases/download/v$Version/") -or
        $Size -le 0 -or $Size -gt 100MB -or $Digest -notmatch '^sha256:[a-fA-F0-9]{64}$') {
        throw 'Invalid update download identity'
    }
    $Handler = [Net.Http.HttpClientHandler]::new()
    $Handler.AllowAutoRedirect = $false
    $Client = [Net.Http.HttpClient]::new($Handler)
    $Cancellation = [Threading.CancellationTokenSource]::new()
    $Cancellation.CancelAfter(300000)
    $Response = $null; $InputStream = $null; $OutputStream = $null
    try {
        $Address = [uri]$Url
        for ($Redirect = 0; $Redirect -le 5; $Redirect++) {
            if ($Address.Scheme -ne 'https' -or $Address.UserInfo -or
                $Address.Host -notin @('github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com')) {
                throw 'Untrusted update download redirect'
            }
            $Task = $Client.GetAsync($Address, [Net.Http.HttpCompletionOption]::ResponseHeadersRead, $Cancellation.Token)
            Wait-DownloadTask $Task $Cancellation
            $Response = $Task.GetAwaiter().GetResult()
            if ([int]$Response.StatusCode -eq 200) {
                break
            }
            if ([int]$Response.StatusCode -notin @(301,302,303,307,308) -or !$Response.Headers.Location -or $Redirect -eq 5) {
                throw 'Update download failed'
            }
            $Address = [uri]::new($Address, $Response.Headers.Location)
            $Response.Dispose(); $Response = $null
        }
        $InputStream = $Response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $OutputStream = [IO.File]::Open($Destination, [IO.FileMode]::CreateNew)
        $Buffer = New-Object byte[] 65536
        $Received = 0L
        while ($true) {
            $Task = $InputStream.ReadAsync($Buffer, 0, $Buffer.Length, $Cancellation.Token)
            Wait-DownloadTask $Task $Cancellation
            $Count = $Task.GetAwaiter().GetResult()
            if ($Count -eq 0) {
                break
            }
            $Received += $Count
            if ($Received -gt $Size) {
                throw 'Download exceeds expected size'
            }
            $OutputStream.Write($Buffer, 0, $Count)
        }
    } finally {
        $Cancellation.Cancel()
        if ($OutputStream) {
            $OutputStream.Dispose()
        }
        if ($InputStream) {
            $InputStream.Dispose()
        }
        if ($Response) {
            $Response.Dispose()
        }
        $Client.Dispose(); $Cancellation.Dispose()
    }
    Test-PackageHash $Destination $Size $Digest
}

# Keep the download dialog responsive and propagate cancellation/timeouts.
function Wait-DownloadTask($Task, $Cancellation) {
    while (!$Task.IsCompleted) {
        [Windows.Forms.Application]::DoEvents()
        if ($script:CancelDownload) {
            $Cancellation.Cancel(); throw 'Download canceled; nothing was installed'
        }
        Start-Sleep -Milliseconds 20
    }
}

# Check exact bytes after copying to prevent installing an altered download.
function Test-PackageHash([string]$Path, [long]$Size, [string]$Digest) {
    if ((Get-Item -LiteralPath $Path).Length -ne $Size -or
        (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Digest.Substring(7)) {
        throw 'Update size or SHA-256 mismatch'
    }
}

# Read the protected installation and GitHub metadata independently in each process.
# Reject changed dependencies, unknown baselines, skipped releases and downgrades.
function Get-CompatibleUpdate {
    $ProgramFilesRoot = [IO.Path]::GetFullPath($env:ProgramFiles).TrimEnd('\') + '\'
    if (!$InstallRoot.StartsWith($ProgramFilesRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Automatic updating requires an installation under Program Files'
    }
    $Baseline = Get-Content -LiteralPath "$InstallRoot/app/windows-baseline.json" -Raw | ConvertFrom-Json
    $ProductCode = '{' + $Baseline.productCode.Trim('{}') + '}'
    $Registration = Get-ItemProperty "HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall/$ProductCode"
    if ([version]$Version -le [version]$Registration.DisplayVersion) {
        throw 'Update is not newer than installed version'
    }
    $Release = Invoke-RestMethod "$Api/releases/tags/v$Version" -TimeoutSec 30
    if ($Release.draft -or $Release.prerelease -or $Release.tag_name -ne "v$Version") {
        throw 'Release is not a stable published version'
    }
    $Assets = @($Release.assets | Where-Object name -eq 'windows-update.json')
    if ($Assets.Count -ne 1 -or $Assets[0].size -gt 1MB) {
        throw 'Update metadata is unavailable'
    }
    $MetadataFile = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString() + '.json')
    try {
        Save-VerifiedAsset $Assets[0].browser_download_url $Assets[0].size $Assets[0].digest $MetadataFile
        $Update = Get-Content -LiteralPath $MetadataFile -Raw | ConvertFrom-Json
    } finally { Remove-Item -LiteralPath $MetadataFile -Force -ErrorAction SilentlyContinue }
    if ($Update.layout -ne 1 -or $Baseline.layout -ne 1 -or $Update.version -ne $Version -or
        $Update.productCode -ne $Baseline.productCode -or $Update.fingerprint -ne $Baseline.fingerprint -or
        $Update.fromVersion -ne $Registration.DisplayVersion -or !$Update.patch) {
        throw 'This release requires a full MSI installation; see the Windows installation guide'
    }
    # Defense in depth: metadata cannot authorize an arbitrary local path.
    foreach ($Dependency in $Baseline.dependencies.PSObject.Properties) {
        $Path = [IO.Path]::GetFullPath((Join-Path $InstallRoot $Dependency.Name))
        if (!$Path.StartsWith($InstallRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -or
            (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Dependency.Value) {
            throw 'Installed runtime differs from the supported baseline; reinstall using the full MSI'
        }
    }
    $PatchAssets = @($Release.assets | Where-Object browser_download_url -eq $Update.patch.url)
    if ($PatchAssets.Count -ne 1 -or $PatchAssets[0].size -ne $Update.patch.size -or
        $PatchAssets[0].digest -ne $Update.patch.digest -or !$Update.patch.url.EndsWith('.msp')) {
        throw 'Patch metadata does not match the published artifact'
    }
    return $Update
}

try {
    if ($Elevated) {
        $Identity = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
        if (!$Identity.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            throw 'Administrator approval is required'
        }
        $Update = Get-CompatibleUpdate
        $Staging = Join-Path (Join-Path $env:SystemRoot 'Temp') ('OSCAR-UI-update-' + [guid]::NewGuid())
        New-Item -ItemType Directory $Staging | Out-Null
        # Explicit ACL: administrators/system write, normal users can read the log.
        $Acl = [Security.AccessControl.DirectorySecurity]::new()
        $Acl.SetAccessRuleProtection($true, $false)
        foreach ($Entry in @(@('S-1-5-32-544', 'FullControl'), @('S-1-5-18', 'FullControl'), @('S-1-5-32-545', 'ReadAndExecute'))) {
            $Rule = [Security.AccessControl.FileSystemAccessRule]::new(
                [Security.Principal.SecurityIdentifier]::new($Entry[0]), $Entry[1], 'ContainerInherit,ObjectInherit', 'None', 'Allow')
            $Acl.AddAccessRule($Rule)
        }
        Set-Acl -LiteralPath $Staging -AclObject $Acl
        $Patch = Join-Path $Staging 'update.msp'
        # Hold the source handle without write/delete sharing across hash and copy.
        # Never copy an arbitrary privileged file into user-readable staging first.
        $Source = [IO.File]::Open($DownloadedPackage, 'Open', 'Read', 'Read')
        try {
            $Hasher = [Security.Cryptography.SHA256]::Create()
            try { $Digest = ([BitConverter]::ToString($Hasher.ComputeHash($Source))).Replace('-', '') }
            finally { $Hasher.Dispose() }
            if ($Source.Length -ne $Update.patch.size -or $Digest -ne $Update.patch.digest.Substring(7)) {
                throw 'Downloaded patch changed before installation'
            }
            $Source.Position = 0
            $Destination = [IO.File]::Open($Patch, 'CreateNew', 'Write', 'None')
            try { $Source.CopyTo($Destination) } finally { $Destination.Dispose() }
        } finally { $Source.Dispose() }
        Test-PackageHash $Patch $Update.patch.size $Update.patch.digest
        $Log = Join-Path $Staging 'install.log'
        $Process = Start-Process msiexec.exe -ArgumentList @('/p', "`"$Patch`"", '/passive', '/norestart',
            'REINSTALL=ALL', 'REINSTALLMODE=omus', '/L*v', "`"$Log`"") -PassThru -Wait
        $Process.Refresh()
        $ExitCode = $Process.ExitCode
        if ($ExitCode -notin @(0, 3010)) {
            throw "Windows Installer exited $ExitCode. Log: $Log"
        }
        $ProductCode = '{' + $Update.productCode.Trim('{}') + '}'
        if ((Get-ItemProperty "HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall/$ProductCode").DisplayVersion -ne $Version) {
            throw "Installer did not register version $Version. Log: $Log"
        }
        exit $ExitCode
    }
    $Update = Get-CompatibleUpdate
    $DownloadDirectory = Join-Path ([IO.Path]::GetTempPath()) ('OSCAR-UI-update-' + [guid]::NewGuid())
    New-Item -ItemType Directory $DownloadDirectory | Out-Null
    $Patch = Join-Path $DownloadDirectory 'update.msp'
    $Progress = [Windows.Forms.Form]::new()
    $Progress.Text = 'Updating OSCAR-UI'
    $Progress.Width = 430; $Progress.Height = 150
    $Progress.ControlBox = $false
    $Label = [Windows.Forms.Label]::new()
    $Label.Text = "Downloading and verifying OSCAR-UI $Version..."
    $Label.AutoSize = $true; $Label.Left = 20; $Label.Top = 20
    $Cancel = [Windows.Forms.Button]::new()
    $Cancel.Text = 'Cancel'; $Cancel.Left = 20; $Cancel.Top = 55
    $Cancel.Add_Click({ $script:CancelDownload = $true })
    $Progress.Controls.Add($Cancel)
    $Progress.Controls.Add($Label); $Progress.Show(); [Windows.Forms.Application]::DoEvents()
    try { Save-VerifiedAsset $Update.patch.url $Update.patch.size $Update.patch.digest $Patch }
    finally { $Progress.Close(); $Progress.Dispose() }
    if ($WaitPid -gt 0) {
        $Application = Get-Process -Id $WaitPid -ErrorAction SilentlyContinue
        if ($Application -and !$Application.WaitForExit(120000)) {
            throw 'OSCAR-UI is still running; nothing was installed'
        }
    }
    $Choice = [Windows.Forms.MessageBox]::Show("Install OSCAR-UI $Version? Windows will request administrator approval.",
        'Update OSCAR-UI', 'OKCancel', 'Information')
    if ($Choice -ne 'OK') {
        throw 'Update canceled; nothing was installed'
    }
    $Shell = "$env:SystemRoot/System32/WindowsPowerShell/v1.0/powershell.exe"
    $Process = Start-Process $Shell -Verb RunAs -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        "`"$PSCommandPath`"", '-Version', $Version, '-Elevated', '-DownloadedPackage', "`"$Patch`"") -PassThru -Wait
    $Process.Refresh()
    if ($Process.ExitCode -eq 3010) {
        [void][Windows.Forms.MessageBox]::Show('Update installed. Restart Windows before opening OSCAR-UI.', 'OSCAR-UI')
    } elseif ($Process.ExitCode -eq 0) {
        Start-Process "$InstallRoot/OSCAR-UI.exe"
    } else { throw 'Update did not finish. Your installation may need repair; see the installer message.' }
} catch {
    [void][Windows.Forms.MessageBox]::Show($_.Exception.Message, 'OSCAR-UI update failed', 'OK', 'Warning')
    if (!$Elevated -and (Test-Path "$InstallRoot/OSCAR-UI.exe")) {
        if ([Windows.Forms.MessageBox]::Show('Reopen OSCAR-UI?', 'OSCAR-UI', 'YesNo') -eq 'Yes') {
            Start-Process "$InstallRoot/OSCAR-UI.exe"
        }
    }
    exit 1
} finally {
    if ($DownloadDirectory) {
        Remove-Item -LiteralPath $DownloadDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
    if ($Staging) {
        Remove-Item -LiteralPath (Join-Path $Staging 'update.msp') -Force -ErrorAction SilentlyContinue
    }
}

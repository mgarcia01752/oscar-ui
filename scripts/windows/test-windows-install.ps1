# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
# Verify the release MSI lifecycle on a disposable Windows CI runner.
# Installs machine-wide and removes the product in a finally block.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($env:GITHUB_ACTIONS -ne 'true') {
    throw 'Run this machine-changing test only on a disposable GitHub Actions runner.'
}
$Installers = @(Get-ChildItem "$Root/dist/windows/release/*.msi")
if ($Installers.Count -ne 1) {
    throw 'Expected exactly one release MSI'
}
$Msi = $Installers[0].FullName
$InstallDirectory = Join-Path $env:ProgramFiles 'OSCAR-UI'
$MenuDirectory = Join-Path ([Environment]::GetFolderPath('CommonPrograms')) 'OSCAR-UI'
$DesktopShortcut = Join-Path ([Environment]::GetFolderPath('CommonDesktopDirectory')) 'OSCAR-UI.lnk'
$RegistryRoot = 'HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall'
if ((Test-Path $InstallDirectory) -or (Test-Path $MenuDirectory) -or (Test-Path $DesktopShortcut)) {
    throw 'OSCAR-UI already exists; refusing to modify an existing installation'
}
$LogDirectory = "$Root/target/installer-logs"
New-Item -ItemType Directory -Force $LogDirectory | Out-Null
$UserDirectory = Join-Path $env:RUNNER_TEMP ('OSCAR-UI user files ' + [guid]::NewGuid())
New-Item -ItemType Directory $UserDirectory | Out-Null
$Fixture = Join-Path $UserDirectory 'configuration.txt'
Copy-Item "$Root/src/test/resources/simple-docsis.txt" $Fixture
$FixtureHash = (Get-FileHash $Fixture).Hash

# Run Windows Installer synchronously, retaining diagnostic logs on failure.
function Invoke-MsiOperation([string]$Operation, [string]$LogName) {
    $Arguments = @($Operation, "`"$Msi`"", '/qn', '/norestart', '/L*v', "`"$LogDirectory/$LogName.log`"")
    $Process = Start-Process msiexec.exe -ArgumentList $Arguments -PassThru
    if (!$Process.WaitForExit(300000)) {
        $Process.Kill()
        throw "Windows Installer timed out: $Operation"
    }
    $Process.Refresh()
    if ($Process.ExitCode -notin @(0, 3010)) {
        throw "Windows Installer failed: $Operation, exit $($Process.ExitCode)"
    }
}

$OriginalPath = $env:PATH
$OriginalJava = $env:JAVA_HOME
$OriginalQt = $env:QT_PLUGIN_PATH
$OriginalPlatform = $env:QT_QPA_PLATFORM
try {
    Invoke-MsiOperation '/i' 'install'
    & python "$Root/scripts/build/verify-java-distribution.py" `
        --runtime "$InstallDirectory/runtime" --sources "$InstallDirectory/app/sources" --jdk-home $env:JAVA_HOME
    if ($LASTEXITCODE -ne 0) { throw 'Installed Java distribution verification failed' }
    foreach ($Required in @("$InstallDirectory/OSCAR-UI.exe", "$InstallDirectory/runtime/release",
                            "$MenuDirectory/OSCAR-UI.lnk", $DesktopShortcut)) {
        if (!(Test-Path $Required)) {
            throw "Missing installed file: $Required"
        }
    }
    $Registrations = @(Get-ItemProperty "$RegistryRoot/*" |
        Where-Object { $_.PSObject.Properties['DisplayName'] -and $_.DisplayName -eq 'OSCAR-UI' })
    if ($Registrations.Count -ne 1 -or !$Registrations[0].UninstallString) {
        throw 'Expected one registered OSCAR-UI uninstaller'
    }
    $env:PATH = "$env:SystemRoot/System32;$env:SystemRoot"
    $env:JAVA_HOME = ''
    $env:QT_PLUGIN_PATH = ''
    $env:QT_QPA_PLATFORM = 'windows'
    $Process = Start-Process "$InstallDirectory/OSCAR-UI.exe" `
        -ArgumentList @('--smoke-test', "`"$Fixture`"") -WorkingDirectory $UserDirectory -PassThru
    if (!$Process.WaitForExit(40000)) {
        $Process.Kill()
        throw 'Installed application timed out'
    }
    $Process.Refresh()
    if ($Process.ExitCode -ne 0) {
        throw "Installed application failed: $($Process.ExitCode)"
    }
} finally {
    $env:PATH = $OriginalPath
    $env:JAVA_HOME = $OriginalJava
    $env:QT_PLUGIN_PATH = $OriginalQt
    $env:QT_QPA_PLATFORM = $OriginalPlatform
    Invoke-MsiOperation '/x' 'uninstall'
}
foreach ($Removed in @("$InstallDirectory/OSCAR-UI.exe", "$InstallDirectory/runtime", "$InstallDirectory/app",
                       "$MenuDirectory/OSCAR-UI.lnk", $DesktopShortcut)) {
    if (Test-Path $Removed) {
        throw "Uninstall left packaged content: $Removed"
    }
}
$Remaining = @(Get-ItemProperty "$RegistryRoot/*" |
    Where-Object { $_.PSObject.Properties['DisplayName'] -and $_.DisplayName -eq 'OSCAR-UI' })
if ($Remaining.Count -ne 0) {
    throw 'Uninstall left product registration'
}
if ((Get-FileHash $Fixture).Hash -ne $FixtureHash) {
    throw 'Uninstall changed the user configuration'
}
Write-Output 'MSI_INSTALL_LAUNCH_UNINSTALL_OK'

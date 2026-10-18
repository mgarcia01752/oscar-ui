# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
# Build an MSI from the runtime-complete, smoke-tested application image.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root
[xml]$Pom = Get-Content "$Root/pom.xml"
$Version = $Pom.project.version
if ($env:GITHUB_REF_TYPE -eq 'tag' -and $env:GITHUB_REF_NAME -ne "v$Version") {
    throw "Release tag must match pom.xml version v$Version"
}
& "$Root/scripts/windows/desktop.ps1" build
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
& python "$Root/scripts/build/collect-release-sources.py"
if ($LASTEXITCODE -ne 0) { throw 'Source collection failed' }
& "$Root/scripts/windows/desktop.ps1" package -Type app-image
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
$Image = "$Root/dist/windows/app-image/OSCAR-UI"
& python "$Root/scripts/build/verify-java-distribution.py" `
    --runtime "$Image/runtime" --sources "$Image/app/sources" --jdk-home $env:JAVA_HOME
if ($LASTEXITCODE -ne 0) { throw 'Java distribution verification failed' }
# Keep one copy beside the launcher for Windows DLL dependency lookup.
& python "$Root/scripts/build/windows-dll-layout.py" --image $Image
if ($LASTEXITCODE -ne 0) { throw 'Windows DLL layout preparation failed' }
$VsWhere = "${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
$Vs = & $VsWhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or !$Vs) { throw 'Visual Studio C++ redistributable files not found' }
$Crt = Get-ChildItem "$Vs/VC/Redist/MSVC/*/x64/Microsoft.VC*.CRT" -Directory |
    Sort-Object FullName -Descending | Select-Object -First 1
if (!$Crt) { throw 'x64 redistributable CRT directory not found' }
Copy-Item "$($Crt.FullName)/*.dll" $Image
foreach ($Name in @('msvcp140.dll', 'vcruntime140.dll', 'vcruntime140_1.dll')) {
    if (!(Test-Path "$Image/$Name")) { throw "Missing runtime: $Name" }
}
@'
OSCAR-UI for Windows x64

Launch OSCAR-UI from the Start menu.
Uninstall using Windows Settings > Apps > Installed apps.
Java, Qt, Python, and a repository checkout are not required.
Licenses and notices are in app/; Java notices are in runtime/legal/.
'@ | Set-Content "$Image/START-HERE.txt"
# Test a relocated copy with no developer tools on PATH, using the native launcher.
$TempRoot = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { [IO.Path]::GetTempPath() }
$Probe = Join-Path $TempRoot ('Oscar release test ' + [guid]::NewGuid().ToString())
if (Test-Path $Probe) { throw "Test destination already exists: $Probe" }
Copy-Item $Image $Probe -Recurse
$OriginalPath = $env:PATH
$OriginalJava = $env:JAVA_HOME
$OriginalQt = $env:QT_PLUGIN_PATH
$OriginalPlatform = $env:QT_QPA_PLATFORM
try {
    $env:PATH = "$env:SystemRoot/System32;$env:SystemRoot"
    $env:JAVA_HOME = ''
    $env:QT_PLUGIN_PATH = ''
    $env:QT_QPA_PLATFORM = 'windows'
    $Fixture = "$Root/src/test/resources/simple-docsis.txt"
    $Process = Start-Process -FilePath "$Probe/OSCAR-UI.exe" -ArgumentList @('--smoke-test', "`"$Fixture`"") -WorkingDirectory $Probe -PassThru
    if (!$Process.WaitForExit(40000)) {
        $Process.Kill()
        throw 'Packaged launcher smoke test timed out'
    }
    $Process.Refresh()
    if ($Process.ExitCode -ne 0) { throw "Packaged launcher failed: $($Process.ExitCode)" }
} finally {
    $env:PATH = $OriginalPath
    $env:JAVA_HOME = $OriginalJava
    $env:QT_PLUGIN_PATH = $OriginalQt
    $env:QT_QPA_PLATFORM = $OriginalPlatform
    Remove-Item $Probe -Recurse -Force
}
$Output = "$Root/dist/windows/release"
New-Item -ItemType Directory -Force $Output | Out-Null
# Package the prepared image so Qt and Visual C++ DLLs stay in the installer.
$InstallerOutput = "$Root/dist/windows/msi"
if (Test-Path $InstallerOutput) { throw "Output already exists: $InstallerOutput" }
# Show package contents before the unchanged license in the MSI license dialog.
$InstallerInformation = "$Root/target/windows-installer-information.txt"
$PackageInformation = Get-Content "$Root/packaging/windows/install-information.txt" -Raw
$ApplicationLicense = Get-Content "$Root/LICENSE" -Raw
[IO.File]::WriteAllText($InstallerInformation, "$PackageInformation`r`n$ApplicationLicense")
$JPackage = if ($env:JAVA_HOME) { "$env:JAVA_HOME/bin/jpackage.exe" } else { 'jpackage.exe' }
# Customize the current JDK template, retaining its installation behavior.
$InstallerResources = "$Root/target/windows-installer-resources"
New-Item -ItemType Directory -Force $InstallerResources | Out-Null
$JdkHome = Split-Path -Parent (Split-Path -Parent (Get-Command $JPackage).Source)
$TemplateEntry = 'classes/jdk/jpackage/internal/resources/main.wxs'
Push-Location $InstallerResources
try {
    & "$JdkHome/bin/jmod.exe" extract --dir extracted "$JdkHome/jmods/jdk.jpackage.jmod"
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to extract the JDK installer template'
    }
    $Template = Get-Content "extracted/$TemplateEntry" -Raw
    if ([regex]::Matches($Template, '</Product>').Count -ne 1) {
        throw 'Unexpected JDK installer template: expected one Product closing tag'
    }
    $Branding = @'
    <WixVariable Id="WixUIDialogBmp" Value="$(var.JpConfigDir)/dialog.bmp" />
    <WixVariable Id="WixUIBannerBmp" Value="$(var.JpConfigDir)/banner.bmp" />
'@
    # Absolute resource paths avoid relying on jpackage copying extra files.
    $Branding = $Branding.Replace('$(var.JpConfigDir)', [System.Security.SecurityElement]::Escape($InstallerResources))
    [IO.File]::WriteAllText("$InstallerResources/main.wxs", $Template.Replace('</Product>', "$Branding`n</Product>"))
    Copy-Item "$Root/packaging/windows/dialog.bmp", "$Root/packaging/windows/banner.bmp" $InstallerResources
} finally {
    Pop-Location
}
& $JPackage --type msi --name OSCAR-UI --app-version $Version `
    --resource-dir $InstallerResources `
    --vendor 'OSCAR-UI contributors' --app-image $Image --dest $InstallerOutput `
    --install-dir OSCAR-UI --license-file $InstallerInformation `
    --win-menu --win-menu-group OSCAR-UI --win-shortcut --win-shortcut-prompt `
    --win-dir-chooser --win-upgrade-uuid '80186748-531f-4a39-a6da-f5c81a1f0730'
if ($LASTEXITCODE -ne 0) { throw 'MSI packaging failed' }
$Installers = @(Get-ChildItem "$InstallerOutput/*.msi")
if ($Installers.Count -ne 1) { throw 'Expected exactly one MSI' }
Copy-Item $Installers[0].FullName "$Output/OSCAR-UI-$Version-windows-x64.msi"

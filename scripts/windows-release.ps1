# Build a portable release from an already bootstrapped Windows checkout.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
[xml]$Pom = Get-Content "$Root/pom.xml"
$Version = $Pom.project.version
if ($env:GITHUB_REF_TYPE -eq 'tag' -and $env:GITHUB_REF_NAME -ne "v$Version") {
    throw "Release tag must match pom.xml version v$Version"
}
& "$Root/scripts/desktop.ps1" build
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
& python "$Root/scripts/collect-release-sources.py"
if ($LASTEXITCODE -ne 0) { throw 'Source collection failed' }
& "$Root/scripts/desktop.ps1" package -Type app-image
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
$Image = "$Root/dist/windows/app-image/OSCAR-UI"
# Put transitive native dependencies beside the launcher for Windows DLL lookup.
Copy-Item "$Image/app/qt/lib/*.dll" $Image
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

Extract the entire ZIP, then double-click OSCAR-UI.exe.
Keep the app and runtime folders beside the executable.
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
Compress-Archive -Path $Image -DestinationPath "$Output/OSCAR-UI-$Version-windows-x64.zip"

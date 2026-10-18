$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'Windows x86_64 required' }
& python -m venv "$Root/.tools/venv"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$Root/.tools/venv/Scripts/python.exe" -m pip install aqtinstall==3.3.0
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$Root/.tools/venv/Scripts/python.exe" -m aqt install-qt windows desktop 6.8.3 win64_msvc2022_64 --archives qtbase -O "$Root/.tools/Qt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

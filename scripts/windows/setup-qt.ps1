# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'Windows x86_64 required' }
[xml]$Pom = Get-Content -Raw "$Root/pom.xml"
$QtVersion = $Pom.project.properties.'qtjambi.version'
& python -m venv "$Root/.tools/venv"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$Root/.tools/venv/Scripts/python.exe" -m pip install -r "$Root/scripts/build/qt-tools-requirements.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$Root/.tools/venv/Scripts/python.exe" -m aqt install-qt windows desktop $QtVersion win64_msvc2022_64 --archives qtbase -O "$Root/.tools/Qt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

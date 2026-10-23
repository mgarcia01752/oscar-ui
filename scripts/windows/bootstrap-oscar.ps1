# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Version = '3.7.0'
$Commit = '85661ace2df754a9b396b0e6b4375a5582016049'
$Sha256 = '898ba5332ac4a28f2699d45077ca06ee0aa7226e28efbef2208b1b1d3a0e5ae6'
$Release = "$Root/.tools/oscar-$Version"
New-Item -ItemType Directory -Force -Path $Release | Out-Null
Invoke-WebRequest -Uri "https://github.com/Comcast/Oscar/releases/download/v$Version/oscar-thin.jar" -OutFile "$Release/oscar-thin.jar.download"
if ((Get-FileHash "$Release/oscar-thin.jar.download" -Algorithm SHA256).Hash -ne $Sha256) {
    throw 'Oscar release checksum mismatch'
}
Move-Item -Force "$Release/oscar-thin.jar.download" "$Release/oscar-thin.jar"
foreach ($Name in @('LICENSE', 'NOTICE')) {
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/Comcast/Oscar/$Commit/$Name" -OutFile "$Release/$Name"
}
& "$Root/mvnw.cmd" -B "-Dmaven.repo.local=$Root/.m2" -f "$Root/pom.xml" install:install-file "-Dfile=$Release/oscar-thin.jar"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Version = '3.7.5'
$Commit = '87102343d22cb50ea3586bef254b5eefdd07009e'
$Sha256 = '7f02f429d55d8152735ce5d1e0b88325f2d2aae193011d8b31d40fdca835b4a7'
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

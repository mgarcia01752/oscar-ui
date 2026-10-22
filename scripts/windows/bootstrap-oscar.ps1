# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Version = '3.6.0'
$Commit = '66dd66c36f9199ab708092210554ee3f67761325'
$Sha256 = '0948d361124dbdfc3d4a69133e812017f51123e9f0302ac53b0057f35bb0f635'
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
& "$Root/mvnw.cmd" -B "-Dmaven.repo.local=$Root/.m2" org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file "-Dfile=$Release/oscar-thin.jar"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

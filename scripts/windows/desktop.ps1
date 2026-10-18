# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
param(
    [ValidateSet('build','run','smoke','package')][string]$Command = 'build',
    [ValidateSet('app-image','msi','exe')][string]$Type = 'app-image'
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
& python "$Root/scripts/build/desktop.py" $Command --type $Type
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

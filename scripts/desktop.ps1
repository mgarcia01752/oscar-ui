param(
    [ValidateSet('build','run','smoke','package')][string]$Command = 'build',
    [ValidateSet('app-image','msi','exe')][string]$Type = 'app-image'
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
& python "$Root/scripts/desktop.py" $Command --type $Type
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

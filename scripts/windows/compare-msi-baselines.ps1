# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
Inspect two MSI databases without installing or modifying either package.
.DESCRIPTION
Write identity and layout evidence for the Windows update prototype. Exit 2
when a conservative minor-update prerequisite fails. A pass is not proof of
MSP compatibility: payload, signing, patching and repair tests remain required.
.PARAMETER Baseline
Existing baseline MSI path.
.PARAMETER Target
Candidate newer MSI path.
#>
param(
    [Parameter(Mandatory = $true)][string]$Baseline,
    [Parameter(Mandatory = $true)][string]$Target
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Read an MSI table through Windows Installer automation in read-only mode.
# Return ordered rows as tab-separated strings for deterministic comparison.
function Read-MsiRows($Database, [string]$Query, [int]$ColumnCount) {
    $View = $Database.OpenView($Query)
    try {
        $View.Execute()
        while ($null -ne ($Record = $View.Fetch())) {
            try {
                $Values = for ($Column = 1; $Column -le $ColumnCount; $Column++) {
                    $Record.StringData($Column)
                }
                $Values -join "`t"
            } finally {
                [void][Runtime.InteropServices.Marshal]::ReleaseComObject($Record)
            }
        }
    } finally {
        $View.Close()
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($View)
    }
}

# Snapshot identity and installer-owned paths; never open the database for writes.
function Read-MsiSnapshot($Installer, [string]$PackagePath) {
    $ResolvedPath = (Resolve-Path -LiteralPath $PackagePath).Path
    $Database = $Installer.OpenDatabase($ResolvedPath, 0)
    try {
        $Properties = @{}
        foreach ($Row in (Read-MsiRows $Database 'SELECT `Property`, `Value` FROM `Property`' 2)) {
            $Name, $Value = $Row -split "`t", 2
            $Properties[$Name] = $Value
        }
        $Summary = $Database.SummaryInformation(0)
        try {
            $Template = $Summary.Property(7)
            $PackageCode = $Summary.Property(9)
        } finally {
            [void][Runtime.InteropServices.Marshal]::ReleaseComObject($Summary)
        }
        @{
            Path = $ResolvedPath
            ProductCode = $Properties['ProductCode']
            UpgradeCode = $Properties['UpgradeCode']
            ProductVersion = $Properties['ProductVersion']
            PackageCode = $PackageCode
            Template = $Template
            Components = @(Read-MsiRows $Database 'SELECT `Component`, `ComponentId`, `Directory_`, `Attributes`, `Condition`, `KeyPath` FROM `Component`' 6 | Sort-Object)
            Files = @(Read-MsiRows $Database 'SELECT `File`, `Component_`, `FileName` FROM `File`' 3 | Sort-Object)
            Directories = @(Read-MsiRows $Database 'SELECT `Directory`, `Directory_Parent`, `DefaultDir` FROM `Directory`' 3 | Sort-Object)
            Features = @(Read-MsiRows $Database 'SELECT `Feature_`, `Component_` FROM `FeatureComponents`' 2 | Sort-Object)
        }
    } finally {
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($Database)
    }
}

$Installer = New-Object -ComObject WindowsInstaller.Installer
try {
    Write-Host '[MSI audit] Reading baseline and candidate; no installation...'
    $Before = Read-MsiSnapshot $Installer $Baseline
    $After = Read-MsiSnapshot $Installer $Target
    $Blockers = @()
    foreach ($Identity in @('ProductCode', 'UpgradeCode', 'Template')) {
        if (!$Before[$Identity] -or $Before[$Identity] -ne $After[$Identity]) {
            $Blockers += "$Identity differs or is missing"
        }
    }
    if (!$Before.PackageCode -or !$After.PackageCode -or $Before.PackageCode -eq $After.PackageCode) {
        $Blockers += 'PackageCode must change'
    }
    if ([version]$After.ProductVersion -le [version]$Before.ProductVersion) {
        $Blockers += 'Target version must be newer'
    }
    foreach ($Table in @('Components', 'Files', 'Directories', 'Features')) {
        if (($Before[$Table] -join "`n") -cne ($After[$Table] -join "`n")) {
            $Blockers += "$Table layout differs; requires authoring review"
        }
    }
    @{
        Baseline = $Before
        Target = $After
        Blockers = $Blockers
        Scope = 'Identity/layout prerequisites only; not approval to publish an MSP'
    } | ConvertTo-Json -Depth 5
    if ($Blockers.Count -gt 0) { exit 2 }
} finally {
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($Installer)
}

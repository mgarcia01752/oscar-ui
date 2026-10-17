# Windows release packaging

The public download is a portable Windows x64 ZIP. Users extract it and run
`Oscar Desktop.exe`; Java, Qt, and app-local Visual C++ runtime DLLs are included.
No separate dependency installation is required. Support targets Windows 10/11
x64. The current bundle is unsigned.

## Build and publish

1. Set the application version in `pom.xml` (independent of Oscar's version).
   The packaging script reads this value directly.
2. Commit and push the release changes. For a trial build, run **Actions >
   Windows release > Run workflow** on the desired branch. Download its
   `windows-release` artifact after success.
3. Create and push the matching `v<version>` tag. The Windows runner downloads
   pinned Oscar 3.1.1 and Qt 6.8.3, uses Temurin 21.0.10+7, runs service tests,
   packages the app, and tests a relocated copy through its native executable.
4. The workflow attaches the application ZIP, corresponding dependency sources,
   and `SHA256SUMS.txt` to a **draft GitHub Release**. Review the assets, perform
   the clean-PC checks below, then publish the draft so users can download it.

The tag must match the Maven version. Existing release names are not overwritten.
Manual workflow runs upload Actions artifacts only; end users should use the
published Release assets, which do not require downloading the repository.

## Local maintainer build

Use Windows x64 with JDK 21, Python, and Visual Studio 2022 C++ build tools
(including its x64 CRT redistributable directory). From PowerShell at repo root:

```powershell
./scripts/bootstrap-oscar.ps1
./scripts/setup-qt.ps1
./scripts/windows-release.ps1
```

Output: `dist/windows/release/Oscar-Desktop-<version>-windows-x64.zip`.
Move an existing `dist/windows/app-image` aside before rebuilding. WiX is not
needed for this ZIP. The release helper adds the app-local CRT and tests the
launcher; plain `desktop.ps1 package` is a lower-level packaging command.
Local builds must collect matching dependency sources before publication; the
workflow automates that for its pinned JDK/Qt versions.

## Validation and release contents

The workflow removes developer tools from PATH and clears Java/Qt environment
settings for the relocated launcher smoke test. This checks the bundled Java
runtime, Qt Windows platform, menus, opening a sample, and clipboard behavior.
The build runner still has system runtimes installed, so this is not a substitute
for testing on a clean Windows PC without Java, Qt, or Visual Studio installed.

Before publishing, check on that clean PC:

- Extract into a path containing spaces and launch by double-click.
- Open text and binary configurations; add, edit, duplicate, and delete TLVs.
- Save text and binary output, close, and reopen the files.
- Check dark/light mode and cancel/overwrite dialogs.

Keep `runtime/legal`, application license files, original Oscar notices, and
Qt/Qt Jambi notices in the ZIP. Release assets include matching Qt base, Qt Jambi,
and Temurin JDK source archives, with hashes for every download. These unmodified
upstream sources include their build instructions and attribution. When updating
JDK/Qt versions, update the workflow source URLs too. App-local Microsoft runtime
DLLs remain subject to Visual Studio redistribution terms and must be updated
through a new application release when their runtime needs updating.

Windows execution and clean-PC verification remain pending until run on Windows;
Linux checks cannot establish Windows packaging success.

# Install OSCAR-UI on Windows

[README](../../README.md) · [Ubuntu installation](ubuntu.md)

Download the Windows x64 `.msi` from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Java and Qt are included; no repository checkout is needed. Choose the MSI
under **Assets**, not a Source code archive.

## Install and launch

1. Save the MSI in **Downloads**.
2. Double-click it and follow the installer prompts. Administrator permission
   is required. The installer is currently unsigned. If SmartScreen appears,
   confirm you downloaded the intended release from the project, then choose
   **More info → Run anyway**.
3. Open the Start menu, search for **OSCAR-UI**, and launch it.

## If the downloaded MSI does not open

On some Windows systems, double-clicking the MSI in Downloads may not start
installation, even after **More info → Run anyway**. Try **right-click → Install**.
If nothing happens, use the PowerShell command below to start Windows Installer
explicitly and record a diagnostic log. It does not bypass Windows security
checks; complete any approval and installer prompts that appear.

### PowerShell

Open **PowerShell** and paste this entire block. It selects the most recently
downloaded OSCAR-UI MSI in your Downloads folder and prints the selected filename
before starting installation:

```powershell
$msi = Get-ChildItem "$env:USERPROFILE\Downloads\OSCAR-UI-*.msi" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($msi) {
    $log = "$env:TEMP\oscar-ui-install.log"
    Write-Host "Installing: $($msi.FullName)"
    $result = Start-Process msiexec.exe -ArgumentList "/i `"$($msi.FullName)`" /L*V `"$log`"" -Wait -PassThru
    Write-Host "Installer exit code: $($result.ExitCode)"
    Write-Host "Log: $log"
} else {
    Write-Host "No OSCAR-UI MSI found in Downloads. Download the Windows installer first."
}
```

After installation completes, launch **OSCAR-UI from the Start menu**. You do
not need to repeat this command each time you use the application. If installation
fails, include the exit code and error message when reporting it. Open the log
from PowerShell with:

```powershell
notepad "$env:TEMP\oscar-ui-install.log"
```

Use these commands in PowerShell, not Command Prompt. PowerShell uses
`$env:USERPROFILE`; Command Prompt uses `%USERPROFILE%`.

## Install from Command Prompt

If double-clicking does not start installation, open **Command Prompt** (`cmd`)
and paste these lines. They select the most recently modified Windows x64
OSCAR-UI MSI in Downloads and write a diagnostic log. No version number is needed:

```bat
set "OSCAR_MSI="
for /f "delims=" %F in ('dir /b /a-d /o-d /t:w "%USERPROFILE%\Downloads\OSCAR-UI-*-windows-x64.msi" 2^>nul') do @if not defined OSCAR_MSI set "OSCAR_MSI=%F"
if defined OSCAR_MSI (echo Installing: %OSCAR_MSI%) else (echo No OSCAR-UI MSI found in Downloads.)
if defined OSCAR_MSI msiexec.exe /i "%USERPROFILE%\Downloads\%OSCAR_MSI%" /L*V "%TEMP%\oscar-ui-install.log"
```

These commands are for an interactive Command Prompt. In a `.bat` file, use
`%%F` instead of `%F`. Selection uses file modification time, not the highest
version number; check the printed filename. If no matching file exists,
installation does not start. Download the Windows x64 MSI first.

If installation fails, open the log:

```bat
notepad "%TEMP%\oscar-ui-install.log"
```

Include the error message and the lines surrounding `Return value 3`, if present,
when reporting the problem. Review logs for personal information before sharing.

## Installation size

The MSI includes a private Java runtime, Qt, the application libraries, and
compressed dependency source archives. Java does not need a separate install
or uninstall. Source archives are included for source availability and account
for a substantial part of the package; they are not loaded to run the UI.

Windows packaging now keeps one copy of the Qt DLLs. Testing this layout against
the v0.3.0 files removed **20.93 MiB (21.9 MB)**, reducing that extracted payload
from **318.4 MB to 296.4 MB**. New release sizes can differ. Existing installations
receive the change only after installing a release built with the new layout.

The release asset lists the compressed download size. Windows **Installed apps**
shows an estimate that can differ from the files in the installation directory.
For comparison, PowerShell's `1MB` divisor measures MiB (1,048,576 bytes), while
MB here means 1,000,000 bytes. See the
[packaging measurements](../development/build/packaging.md#windows-package-size-measurements)
for the component breakdown.

## Update

Use **About → Check for updates**. When **Update on Windows** is enabled, click
it, save or discard edits, and confirm closing the app. The helper downloads a
smaller MSP patch, asks to install, and requests administrator approval. It
restarts OSCAR-UI after success or reports if Windows needs a reboot.

If About requests a **Full MSI installation**, the installed baseline is not
compatible with the available patch. This can happen after a baseline correction,
a skipped release, or a bundled dependency change:

1. Download the newer MSI from [GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
2. Close OSCAR-UI and uninstall the old version using Installed apps.
3. Install the downloaded MSI and launch from the Start menu.

Keep configurations outside the installation directory. Do not replace the JAR
manually: Windows Installer must track the update for repair and uninstall.
On patch failure, reopen the installed app or follow the full-MSI steps above.
If the updater reports that variable `$Version?` has not been set, its confirmation
prompt failed before installation. Reopen OSCAR-UI; recovery requires a full MSI
containing the corrected updater. Repeating the same update cannot fix the
installed helper.
Installer logs are under `%SystemRoot%\Temp\OSCAR-UI-update-*`.
The update checks release hashes over HTTPS; it does not bypass Windows security
prompts or provide Authenticode publisher signing.

## Uninstall

Open **Settings → Apps → Installed apps → OSCAR-UI → Uninstall**.

Keep configuration files in your own folders, outside the installation directory.
Uninstall removes the application and bundled runtime; those files remain.

Continue with the [user guide](../ui/README.md).

You can also start removal from **About → Uninstall OSCAR-UI…** in an installed
package. Save your changes when prompted. Windows opens Installed apps; select OSCAR-UI and choose Uninstall.
See the [About guide](../ui/about.md#uninstall-oscar-ui).

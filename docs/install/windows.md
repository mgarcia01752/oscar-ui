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
and paste this command. It installs version **0.2.2** and writes a diagnostic log:

```bat
msiexec.exe /i "%USERPROFILE%\Downloads\OSCAR-UI-0.2.2-windows-x64.msi" /L*V "%TEMP%\oscar-ui-install.log"
```

For another version, use its actual downloaded filename. To list MSI files:

```bat
dir "%USERPROFILE%\Downloads\*.msi"
```

Do not enter a placeholder such as `EXACT-FILENAME.msi`. If Windows says
“This installation package could not be opened”, first check that the filename
and location match the downloaded file.

If installation fails, open the log:

```bat
notepad "%TEMP%\oscar-ui-install.log"
```

Include the error message and the lines surrounding `Return value 3`, if present,
when reporting the problem. Review logs for personal information before sharing.

## Update

Close OSCAR-UI and download the newer Windows MSI from
[GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases).
Run the new installer and follow its prompts, then launch OSCAR-UI from the
Start menu. To remove the installed version manually, use the Uninstall steps
below.

## Uninstall

Open **Settings → Apps → Installed apps → OSCAR-UI → Uninstall**.

Keep configuration files in your own folders, outside the installation directory.
Uninstall removes the application and bundled runtime; those files remain.

Continue with the [user guide](../ui/README.md).

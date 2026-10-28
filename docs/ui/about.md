# Help, About and updates

[User guide](README.md)

**Help** is the last menu. Choose **Help → User Guide** to open the
[online user guide](https://github.com/mgarcia01752/oscar-ui/tree/main/docs/ui)
in your default browser. This requires an internet connection.

Choose **Help → About** to see installed component versions in the
**Package | Version | Link** table. Project links open OSCAR-UI and OSCAR on GitHub. The license link opens OSCAR-UI's license in its repository.
Beside the version and release-update panels, **TLV Registry | Specification** lists each supported
file type and its source edition from the bundled OSCAR JAR. Multiple source
specifications appear on separate lines within the same cell. The compact panel matches the version panel styling without a table header bar
or unused rows; hover over an edition to see its full source text.
These editions identify registry sources, not complete specification compliance.

**Report an issue with OSCAR-UI** opens the project's GitHub issue form in your
browser with OSCAR-UI, OSCAR, bundled Qt/Qt Jambi, Java, OS version, and
architecture already filled in. Describe the reproduction steps, expected result,
and actual result; optionally add screenshots. Review the draft and submit it
on GitHub (sign-in may be required). The app does not submit reports or attach
configuration files, secrets, usernames, paths, or logs automatically.

Choose **Help → About → Check for updates** for a manual online check against the latest
published stable OSCAR-UI release. Draft releases are not offered. The check does
not install updates automatically. Use the releases/downloads link to obtain the
installer for your platform and follow the [installation instructions](../../README.md#download).

**How to update or uninstall OSCAR-UI** opens the guide for your platform
(Windows or Ubuntu), including upgrade and removal instructions.

An unavailable network or failed update check does not mean the installed
version is current; retry when GitHub is reachable.

## Dark-mode screenshots

**Help → About: package links and release updates**

![About, package links, and release updates in OSCAR-UI dark mode](../images/user-guide/about-dark.png)

![About specification grid in light mode](../images/user-guide/about-light.png)

Shown from a development build; installed packages also show the uninstall button.

## Install an Ubuntu update

After **Check for updates** finds a newer verified Ubuntu release, click
**Update on Ubuntu**. Review and save or discard unsaved work in every tab, wait for the download and
verification, then choose **Close and install**. The app closes; Ubuntu requests
administrator approval. A separate progress window reports the result and offers
to reopen OSCAR-UI. Canceling before installation does not change the package.

This is available for installed Ubuntu x64 DEB packages. Development runs retain the release download link. See the
[Ubuntu update instructions](../install/ubuntu.md#update) for fallback and failures.

## Uninstall OSCAR-UI

Choose **Help → About**. In a supported installed package, the
**Uninstall OSCAR-UI…** button is below **Release updates**, above **Close**.
Confirm, then save or discard unsaved changes in every tab; Cancel keeps the application open.
On Ubuntu the UI closes, administrator approval appears, and a separate progress
window reports removal. On Windows the UI closes and Installed apps opens;
select OSCAR-UI and choose Uninstall there.

Configuration files outside the installation directory remain. Ubuntu removal
does not purge or autoremove dependencies. If authorization is canceled, the
package remains installed and you can launch it again. A failed removal shows
the log path; use the platform installation guide to recover from APT errors.
The button is hidden when running from a development checkout with `desktop.sh`.
An older installed release will not gain the button until you install a package
that includes this feature. The uninstall-guide link remains available.

### Ubuntu terminal command

Close OSCAR-UI, then run:

```bash
sudo apt remove oscar-ui
```

Configuration files outside the installation directory remain. This also works
when your installed version does not have the uninstall button.

### Windows manual removal

Open **Settings → Apps → Installed apps → OSCAR-UI → Uninstall**.
See the [Windows installation guide](../install/windows.md) for more details.

## Upgrade indicator

When a newer stable release is available, a green **Update** chip appears at
the upper-right of the main window's menu bar. Click it to open the update
dialog. Compatible installed packages offer their platform update action;
other installations retain the download link. Clicking the chip installs nothing.

OSCAR-UI makes one background GitHub release check at startup. The chip stays
hidden when up to date, running a newer development build, or offline.
**Help → About → Check for updates** retries the check and refreshes the indicator.

## Install a Windows update

A compatible release enables **Update on Windows**. Save or discard changes in every tab,
confirm closing the app, then follow the download and administrator prompts.
The app restarts after success; a reboot requirement is reported separately.
Cancel or failure offers to reopen it.

This requires the new MSI baseline and the immediately previous compatible
release. If About requests a full MSI, install that baseline before trying later
patches. Changed dependencies or skipped versions also use the full installer.
See [Windows update instructions](../install/windows.md#update).

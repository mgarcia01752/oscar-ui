# oscar-ui

**Work in progress.**

A cross-platform desktop interface for Comcast Oscar, built with Qt Jambi for Linux and Windows.

## Development setup

On Debian/Ubuntu Linux x86_64:

```sh
./install.sh --development --check
./install.sh --development
```

If system packages are missing, the installer prints an `apt-get` command.
Run that command yourself, then rerun setup. Java 21 must be selected through
`JAVA_HOME` or `PATH`; setup does not change your shell configuration or use
elevated privileges. Other Linux distributions are not currently supported by
this installer.

Development setup downloads the verified Oscar release, installs Qt 6.8.3
through a project-local Python virtual environment, and builds and tests the
UI using the Maven wrapper. Existing Qt installations can be selected with
`QT_HOME`. Downloads require internet access. The `--check` option checks only
system prerequisites, without downloading or building anything.

After a successful setup, launch with `./scripts/desktop.sh run`. A graphical
desktop is required for normal use. Native installer packaging tools such as
`rpm` and `fakeroot` are not part of development setup.

## Oscar dependency

Until Oscar is published to Maven Central, use the prebuilt thin JAR from
[Oscar v3.1.0](https://github.com/Comcast/Oscar/releases/tag/v3.1.0).
Bootstrap verifies its pinned SHA-256 and installs the JAR with its embedded
Maven POM into this project's `.m2` cache. Dependencies still come from Maven
Central; no Oscar source checkout or source build is required.

Use Java 21. On Linux, bootstrap also requires `curl` and `sha256sum`:

```sh
scripts/bootstrap-oscar.sh
./mvnw -B -Dmaven.repo.local="$PWD/.m2" test
```

On Windows, run from PowerShell:

```powershell
./scripts/bootstrap-oscar.ps1
./mvnw.cmd -B "-Dmaven.repo.local=$PWD/.m2" test
```

The desktop build command (`scripts/desktop.sh build` or
`scripts/desktop.ps1 build`) uses the same cache. Bootstrap also downloads the
release's original license and notice for inclusion in desktop packages.

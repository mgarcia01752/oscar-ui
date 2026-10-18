# OSCAR-UI

[![CI status](https://img.shields.io/github/actions/workflow/status/mgarcia01752/oscar-ui/java-ci.yml?branch=main&label=CI)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![Java CI](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml/badge.svg)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![Ubuntu 22.04 CI target](https://img.shields.io/badge/Ubuntu-22.04-E95420?logo=ubuntu&logoColor=white)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![Ubuntu 24.04 CI target](https://img.shields.io/badge/Ubuntu-24.04-E95420?logo=ubuntu&logoColor=white)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![macOS 15 CI target](https://img.shields.io/badge/macOS-15-000000?logo=apple&logoColor=white)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-007396)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A desktop editor for Comcast Oscar configuration files on Ubuntu and Windows.
**Under development:** complete configuration validation is still unfinished.

## Download

Open [GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases) and
choose the package for your system under **Assets**. Java and Qt are included;
you do not need to download the repository.

## Ubuntu 22.04 / 24.04 (x64)

1. Download `oscar-ui_<version>_amd64.deb`.
2. Open a terminal in the download folder and install the package:

   ```bash
   sudo apt install ./oscar-ui_*.deb
   ```

   Keep only the version you want to install in that folder.
3. Launch **OSCAR-UI** from the application menu.

## Windows (x64)

1. Download `OSCAR-UI-<version>-windows-x64.zip`.
2. Extract the entire ZIP.
3. Open the extracted folder and double-click **OSCAR-UI.exe**.

Keep the executable, DLLs, `app`, and `runtime` folders together. The application
is currently unsigned, so Windows may display a SmartScreen warning.

GitHub's **Source code** ZIP/TAR downloads are for developers, not runnable
application packages.

## Development

See the [development guide](docs/development.md) for setup, build and test
commands, current functionality, and limitations. Maintainers can find release
instructions in [Release packaging](docs/packaging.md).

Licensed under [Apache License 2.0](LICENSE). See
[Third-party notices](THIRD-PARTY-NOTICES.md) for bundled dependencies.

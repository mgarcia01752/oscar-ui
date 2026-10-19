# OSCAR-UI

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/oscar-ui-dark.png">
    <source media="(prefers-color-scheme: light)" srcset="docs/images/oscar-ui-light.png">
    <img alt="OSCAR-UI" src="docs/images/oscar-ui-light.png" width="480">
  </picture>
</p>

[![CI status](https://img.shields.io/github/actions/workflow/status/mgarcia01752/oscar-ui/java-ci.yml?branch=main&label=CI)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![Windows MSI and Ubuntu DEB release status](https://img.shields.io/github/actions/workflow/status/mgarcia01752/oscar-ui/release.yml?label=Windows%20MSI%20%2B%20Ubuntu%20DEB)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/release.yml)
[![Java CI](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml/badge.svg)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![Ubuntu LTS CI targets: 22.04, 24.04, 26.04](https://img.shields.io/badge/Ubuntu_LTS-22.04%20%7C%2024.04%20%7C%2026.04-E95420?logo=ubuntu&logoColor=white)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![macOS 15 CI target](https://img.shields.io/badge/macOS-15-000000?logo=apple&logoColor=white)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-007396)](https://github.com/mgarcia01752/oscar-ui/actions/workflows/java-ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A desktop editor for Comcast Oscar configuration files on Ubuntu and Windows.
**Under development:** complete configuration validation is still unfinished.

## Download

Open [GitHub Releases](https://github.com/mgarcia01752/oscar-ui/releases) and
choose the package for your system under **Assets**. Java and Qt are included;
you do not need to download the repository.

## Installation

| System | Package | Instructions |
| --- | --- | --- |
| Ubuntu 22.04 / 24.04 (x64) | `.deb` | [Ubuntu installation](docs/install/ubuntu.md) |
| Windows (x64) | `.msi` | [Windows installation](docs/install/windows.md) |

The **Windows MSI + Ubuntu DEB** badge shows the combined release workflow status;
both platform builds and installer checks must pass. See the
[release runs](https://github.com/mgarcia01752/oscar-ui/actions/workflows/release.yml)
for each platform's results.

Each guide covers installation, launch, troubleshooting, and uninstall.
GitHub's **Source code** archives are for developers, not runnable applications.

## User guide

See the [OSCAR-UI user guide](docs/ui/README.md) for file operations, TLV editing,
inspection, settings, Tools → Check Shared Secret, and update checks.

## Configuration file security concerns

OSCAR-UI masks recognized secrets on screen; it does not encrypt or protect
saved configuration files. Read [Configuration file security concerns](docs/configuration-file-security.md)
for the limits of masking and your responsibility for sensitive files.

## Development

See the [development guide](docs/development/README.md) for setup, build and test
commands, current functionality, and limitations. Maintainers can find release
instructions in [Release packaging](docs/development/packaging.md).
To create a local DEB or MSI, see [Build local installers](docs/development/build-images.md).

Licensed under [Apache License 2.0](LICENSE). See
[Third-party notices](THIRD-PARTY-NOTICES.md) for bundled dependencies.

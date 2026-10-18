#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Run local validation before a push; never commit, tag, or push."""
import argparse
import importlib.util
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    """
    Run host-supported checks and return nonzero at the first failure.
    """
    argparse.ArgumentParser(description=__doc__).parse_args()
    try:
        _checks()
    except (OSError, RuntimeError, ValueError, subprocess.CalledProcessError) as error:
        print(f'[pre-push] FAILED: {error}', file=sys.stderr, flush=True)
        return 1
    print('[pre-push] PASSED: all required local checks. Installer jobs still run in CI.', flush=True)
    return 0


def _environment():
    environment = os.environ.copy()
    # Hooks inherit repository-local Git variables; fixture tests use other repos.
    for name in ('GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR',
                 'GIT_PREFIX', 'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES'):
        environment.pop(name, None)
    return environment


def _run(label, command, environment=None):
    print(f'[pre-push] {label}', flush=True)
    subprocess.run([part.as_posix() if isinstance(part, Path) else str(part) for part in command], cwd=ROOT,
                   env=environment or _environment(), check=True, stdin=subprocess.DEVNULL)


def _tool(name, local_path=None):
    executable = shutil.which(name)
    if not executable and local_path and (ROOT / local_path).is_file():
        executable = str(ROOT / local_path)
    if not executable:
        raise RuntimeError(f'{name} is required. See docs/development/pre-push-checks.md; no push was performed.')
    return executable


def _files():
    result = subprocess.run(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                            cwd=ROOT, env=_environment(), capture_output=True, check=True)
    return sorted({ROOT / name.decode('utf-8') for name in result.stdout.split(b'\0')
                   if name and (ROOT / name.decode('utf-8')).is_file()})


def _documentation(files):
    for path in files:
        if path.suffix not in ('.py', '.md', '.sh', '.ps1', '.yml', '.xml'):
            continue
        source = path.read_text(encoding='utf-8')
        if path.suffix == '.py':
            compile(source, str(path), 'exec')
        if path.suffix != '.md':
            continue
        for target in re.findall(r'\]\(([^)]+)\)', source):
            if '://' in target or target.startswith('mailto:'):
                continue
            location, _, anchor = target.partition('#')
            destination = path.parent / location if location else path
            if not destination.exists():
                raise RuntimeError(f'Broken local link in {path.relative_to(ROOT)}: {target}')
            if anchor and destination.suffix == '.md':
                headings = re.findall(r'^#+\s+(.+?)\s*#*$', destination.read_text(encoding='utf-8'), re.M)
                slugs = [re.sub(r'[^\w\- ]', '', heading.lower()).replace(' ', '-') for heading in headings]
                if anchor not in slugs:
                    raise RuntimeError(f'Broken anchor in {path.relative_to(ROOT)}: {target}')
    catalog = (ROOT / 'cm-config/README.md').read_text(encoding='utf-8')
    for path in files:
        if path.is_relative_to(ROOT / 'cm-config/examples'):
            if path.relative_to(ROOT / 'cm-config').as_posix() not in catalog:
                raise RuntimeError(f'Example missing from cm-config/README.md: {path.name}')


def _pins():
    specification = importlib.util.spec_from_file_location('oscar_updater', ROOT / 'tools/development/update-oscar.py')
    updater = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(updater)
    shell = (ROOT / 'scripts/unix/bootstrap-oscar.sh').read_text(encoding='utf-8')
    values = {}
    for name in ('VERSION', 'COMMIT', 'SHA256'):
        match = re.search(rf'^{name}=(\S+)$', shell, re.M)
        if not match:
            raise RuntimeError(f'Missing OSCAR {name} pin.')
        values[name] = match[1]
    _, original, expected = updater._references(ROOT, values['VERSION'], values['COMMIT'], values['SHA256'])
    if original != expected:
        raise RuntimeError('OSCAR pins or current-version references disagree; run the dependency updater.')


def _checks():
    windows = sys.platform == 'win32'
    bash = _tool('bash')
    powershell = _tool('pwsh', '.tools/release-validation/pwsh/pwsh.exe' if windows else
                       '.tools/release-validation/pwsh/pwsh')
    actionlint = _tool('actionlint', '.tools/release-validation/actionlint/actionlint.exe' if windows else
                       '.tools/release-validation/actionlint/actionlint')
    files = _files()
    _run('Source license headers', [sys.executable, ROOT / 'tools/development/check-license-headers.py'])
    _run('Whitespace checks', ['git', 'diff', '--check', 'HEAD'])
    print('[pre-push] UTF-8, Python syntax, documentation links, examples, and OSCAR pins', flush=True)
    _documentation(files)
    _pins()
    for suite in ('tools/git', 'tools/development', 'scripts/tests'):
        options = ['-X', 'warn_default_encoding', '-W', 'error::EncodingWarning'] if suite == 'tools/development' else []
        _run(f'Python tests: {suite}', [sys.executable, *options, '-m', 'unittest', 'discover', '-s', suite, '-p', 'test_*.py'])
    for path in files:
        if path.suffix == '.sh' or path == ROOT / 'tools/git/hooks/pre-push':
            _run(f'Shell syntax: {path.relative_to(ROOT)}', [bash, '-n', path])
    _run('Mocked developer-installer checks', [bash, ROOT / 'scripts/tests/test-install.sh'])
    _run('PowerShell syntax (parse only)', [powershell, '-NoProfile', '-NonInteractive', '-Command',
         "$ErrorActionPreference='Stop'; $failed=$false; "
         "Get-ChildItem scripts -Recurse -Filter *.ps1 | ForEach-Object { "
         "$tokens=$null; $errors=$null; "
         "[void][System.Management.Automation.Language.Parser]::ParseFile($_.FullName,[ref]$tokens,[ref]$errors); "
         "if ($errors.Count) { $errors | ForEach-Object { Write-Host $_ }; $failed=$true } }; "
         "if ($failed) { exit 1 }"])
    _run('GitHub workflow lint', [actionlint, '-shellcheck=', '-pyflakes='])
    bootstrap = [powershell, '-NoProfile', '-File', ROOT / 'scripts/windows/bootstrap-oscar.ps1'] if windows else [bash, ROOT / 'scripts/unix/bootstrap-oscar.sh']
    _run('Verify and install pinned OSCAR dependency', bootstrap)
    desktop = [sys.executable, ROOT / 'scripts/build/desktop.py']
    wrapper = ROOT / ('mvnw.cmd' if windows else 'mvnw')
    maven = [wrapper, '-B', f'-Dmaven.repo.local={ROOT / ".m2"}']
    if sys.platform in ('linux', 'win32'):
        _run('Desktop build and Java tests', [*desktop, 'build'])
        _run('Native Qt smoke check', [*desktop, 'smoke'])
        environment = _environment()
        environment['QT_QPA_PLATFORM'] = 'offscreen'
        environment['QT_PLUGIN_PATH'] = str(ROOT / 'target/app/qt/plugins')
        library = str(ROOT / 'target/app/qt/lib')
        variable = 'PATH' if windows else 'LD_LIBRARY_PATH'
        environment[variable] = library + os.pathsep + environment.get(variable, '')
        _run('Native Qt dialog tests', [*maven, '-Doscar.ui.tests=true', 'test'], environment)
    elif sys.platform == 'darwin':
        _run('Java build and service tests', [*maven, 'clean', 'verify'])
        print('[pre-push] macOS native Qt packaging is unsupported; Java/tool checks only.', flush=True)
    else:
        raise RuntimeError(f'Unsupported host: {sys.platform}')
    print('[pre-push] Windows/Ubuntu installer install/uninstall and other OS matrix checks remain CI-only.', flush=True)


if __name__ == '__main__':
    raise SystemExit(main())

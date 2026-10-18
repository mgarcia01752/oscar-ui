#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Enable the tracked pre-push hook for this checkout without replacing other hooks."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
HOOKS = 'tools/git/hooks'


def main():
    """
    Install repository-local hook configuration, refusing conflicting hooks.
    """
    try:
        _install()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        return 1
    print('Pre-push checks enabled for this checkout (branches and tags).', flush=True)
    return 0


def _git(*arguments, check=True):
    return subprocess.run(['git', '-C', str(ROOT), *arguments], check=check,
                          capture_output=True, text=True, encoding='utf-8')


def _install():
    if Path(_git('rev-parse', '--show-toplevel').stdout.strip()).resolve() != ROOT:
        raise RuntimeError('Hook installer must belong to the checkout root.')
    configured = _git('config', '--get', 'core.hooksPath', check=False).stdout.strip()
    if configured and configured != HOOKS:
        raise RuntimeError(f'Existing core.hooksPath is {configured}; integrate the pre-push hook there before changing it.')
    hook = ROOT / HOOKS / 'pre-push'
    if not hook.is_file():
        raise RuntimeError('Tracked pre-push hook is missing.')
    if not configured:
        existing = Path(_git('rev-parse', '--git-path', 'hooks/pre-push').stdout.strip())
        if not existing.is_absolute():
            existing = ROOT / existing
        if existing.exists():
            raise RuntimeError('An existing pre-push hook needs integration; it was not overwritten.')
        # Switching hooksPath would hide other active hooks too.
        directory = existing.parent
        if directory.exists() and any(p.is_file() and not p.name.endswith('.sample') for p in directory.iterdir()):
            raise RuntimeError('Existing Git hooks need integration; no hook configuration was changed.')
    hook.chmod(hook.stat().st_mode | 0o111)
    _git('config', '--local', 'core.hooksPath', HOOKS)


if __name__ == '__main__':
    raise SystemExit(main())

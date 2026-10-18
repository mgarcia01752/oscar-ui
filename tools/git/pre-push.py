#!/usr/bin/env python3
"""Verify the pushed commit matches the clean checkout before running checks."""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    """
    Validate Git's proposed ref updates and block uploads on local test failure.
    """
    try:
        refs = sys.stdin.read().splitlines()
        _verify_refs(refs)
        environment = os.environ.copy()
        for name in ('GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR',
                     'GIT_PREFIX', 'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES'):
            environment.pop(name, None)
        subprocess.run([sys.executable, str(ROOT / 'tools/development/pre-push-checks.py')],
                       cwd=ROOT, env=environment, stdin=subprocess.DEVNULL, check=True)
        # Reject a checkout modified while checks were running.
        _verify_refs(refs)
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f'[pre-push] Push blocked: {error}', file=sys.stderr, flush=True)
        return 1
    return 0


def _git(*arguments):
    result = subprocess.run(['git', '-C', str(ROOT), *arguments], capture_output=True,
                            text=True, encoding='utf-8', check=True)
    return result.stdout.strip()


def _verify_refs(lines):
    if _git('status', '--porcelain'):
        raise RuntimeError('Commit or stash changes before pushing so checks cover the pushed revision.')
    head = _git('rev-parse', 'HEAD')
    for line in lines:
        fields = line.split()
        if len(fields) != 4:
            raise RuntimeError('Invalid pre-push ref input.')
        local_ref, local_oid, _, _ = fields
        if set(local_oid) == {'0'}:
            continue
        if _git('rev-parse', f'{local_oid}^{{commit}}') != head:
            raise RuntimeError(f'{local_ref} is not the checked-out commit; check out that revision and push separately.')


if __name__ == '__main__':
    raise SystemExit(main())

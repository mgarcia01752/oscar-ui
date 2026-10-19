#!/usr/bin/env python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Show the latest release workflow and its jobs using authenticated GitHub CLI."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[2]


def gh_json(*arguments):
    """Read GitHub JSON using the checkout's authenticated gh installation.

    Raise on command or JSON errors instead of reporting a false CI result.
    """
    result = subprocess.run(['gh', *arguments], cwd=ROOT, check=True,
                            capture_output=True, text=True)
    return json.loads(result.stdout)


def report(tag=None):
    """Print the newest release run, optionally restricted to an exact tag.

    Include all jobs from the latest attempt and failed step names. Return
    zero for success or pending runs, one for an unsuccessful completed run,
    and two when no matching run exists. This never changes GitHub state.
    """
    repository = gh_json('repo', 'view', '--json', 'nameWithOwner')['nameWithOwner']
    query = {'per_page': 1}
    if tag:
        query['branch'] = tag
    endpoint = f'repos/{repository}/actions'
    runs = gh_json('api', f'{endpoint}/workflows/release.yml/runs?{urlencode(query)}')['workflow_runs']
    if not runs:
        print(f'No release workflow run found{f" for {tag}" if tag else ""}.')
        return 2
    run = runs[0]
    print(f'Release: {run["head_branch"]} | Run: {run["id"]}')
    print(f'Status: {run["status"]} | Result: {run["conclusion"] or "pending"}')
    print(run['html_url'])
    page_number = 1
    while True:
        page = gh_json('api', f'{endpoint}/runs/{run["id"]}/jobs?filter=latest&per_page=100&page={page_number}')
        for job in page['jobs']:
            print(f'  {job["conclusion"] or job["status"]}: {job["name"]}')
            if job['conclusion'] == 'failure':
                for step in job.get('steps', []):
                    if step.get('conclusion') == 'failure':
                        print(f'    Failed step: {step["name"]}')
                print(f'    Log: gh run view --repo {repository} --job {job["id"]} --log-failed')
        if len(page['jobs']) < 100:
            break
        page_number += 1
    if run['status'] == 'completed' and run['conclusion'] != 'success':
        return 1
    return 0


def main():
    """Parse an optional tag and report status without pushing or cancelling.

    API/authentication/tool failures return two with an actionable error.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', help='Check this tag instead of the latest release run')
    arguments = parser.parse_args()
    try:
        return report(arguments.tag)
    except FileNotFoundError:
        print('ERROR: Install GitHub CLI (gh) and run gh auth login.', file=sys.stderr)
    except subprocess.CalledProcessError as error:
        print(f'ERROR: GitHub query failed: {error.stderr.strip()}', file=sys.stderr)
    except (ValueError, KeyError, TypeError) as error:
        print(f'ERROR: Unexpected GitHub response: {error}', file=sys.stderr)
    return 2


if __name__ == '__main__':
    sys.exit(main())

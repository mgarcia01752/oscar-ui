# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Check release reporting with API fixtures; never modify GitHub."""
import contextlib
import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('release_status', Path(__file__).with_name('release-status.py'))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReleaseStatusTest(unittest.TestCase):
    def test_results_and_paginated_jobs(self):
        for status, conclusion, expected in [('queued', None, 0), ('completed', 'success', 0),
                                               ('completed', 'failure', 1)]:
            with self.subTest(status=status, conclusion=conclusion):
                run = dict(id=123, head_branch='v1.2.3', status=status,
                           conclusion=conclusion, html_url='https://example.test/run')
                pages = [{'jobs': [dict(id=1, name='Build', status='completed', conclusion='success')] * 100},
                         {'jobs': [dict(id=2, name='Install', status='completed', conclusion='failure',
                                        steps=[dict(name='APT install', conclusion='failure')])]}]
                output = io.StringIO()
                with patch.object(MODULE, 'gh_json', side_effect=[{'nameWithOwner': 'owner/repo'},
                                  {'workflow_runs': [run]}, *pages]) as query, contextlib.redirect_stdout(output):
                    self.assertEqual(MODULE.report('v1.2.3'), expected)
                self.assertIn('branch=v1.2.3', query.call_args_list[1].args[1])
                self.assertIn('page=2', query.call_args_list[3].args[1])
                self.assertIn('Failed step: APT install', output.getvalue())
                self.assertIn('--job 2 --log-failed', output.getvalue())

    def test_no_run(self):
        with patch.object(MODULE, 'gh_json', side_effect=[{'nameWithOwner': 'owner/repo'},
                          {'workflow_runs': []}]), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(MODULE.report(), 2)

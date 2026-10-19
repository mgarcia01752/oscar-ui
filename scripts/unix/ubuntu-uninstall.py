#!/usr/bin/python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Wait for the desktop app to exit, then request authorized APT removal."""
import argparse
import os
from pathlib import Path
import subprocess
import time


def uninstall(parent_pid, log_path):
    """Remove only OSCAR-UI after its process exits and polkit authorizes APT.

    Run as the normal desktop user. Keep configuration files and dependencies;
    do not purge or autoremove. Report cancellation/failure with the log path.
    Return the package manager's exit status; raise if the UI does not exit.
    """
    if parent_pid <= 1 or os.geteuid() == 0:
        raise ValueError('Start uninstall from the normal OSCAR-UI desktop session.')
    for _ in range(240):
        try:
            os.kill(parent_pid, 0)
        except ProcessLookupError:
            break
        time.sleep(0.5)
    else:
        raise ValueError('OSCAR-UI did not close; uninstall was not started.')
    removal = subprocess.Popen(['/usr/bin/pkexec', '/usr/bin/apt-get', '-y',
                                '-o', 'DPkg::Lock::Timeout=60', 'remove', 'oscar-ui'],
                               stdin=subprocess.DEVNULL)
    progress = None
    try:
        progress = subprocess.Popen(['/usr/bin/zenity', '--progress', '--pulsate',
                                     '--no-cancel', '--auto-close', '--title=Uninstall OSCAR-UI',
                                     '--text=Waiting for approval or removing OSCAR-UI.'],
                                    stdin=subprocess.PIPE)
    except OSError:
        pass  # Still wait for APT and report its outcome if progress cannot open.
    result = removal.wait()
    if progress is not None:
        try:
            progress.communicate(input=b'100\n', timeout=5)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            progress.terminate()
    message = ('OSCAR-UI was removed. Your configuration files were kept.' if result == 0
               else f'Uninstall canceled or failed. See {log_path}. '
                    'If APT reported a partial removal, repair it before retrying.')
    subprocess.run(['/usr/bin/zenity', '--info' if result == 0 else '--error',
                    '--title=Uninstall OSCAR-UI', '--text=' + message], check=False)
    return result


def main():
    """Parse the UI handoff and show operational errors without elevating Python.

    The log path identifies the output file opened by the UI before handoff.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('parent_pid', type=int)
    parser.add_argument('log_path', type=Path)
    args = parser.parse_args()
    try:
        return uninstall(args.parent_pid, args.log_path)
    except (OSError, ValueError) as error:
        print(f'Uninstall failed: {error}', flush=True)
        subprocess.run(['/usr/bin/zenity', '--error', '--title=Uninstall OSCAR-UI',
                        '--text=Uninstall could not complete. See ' + str(args.log_path)], check=False)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())

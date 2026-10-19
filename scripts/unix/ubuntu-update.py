#!/usr/bin/python3
# Copyright 2026 OSCAR-UI contributors
# SPDX-License-Identifier: Apache-2.0
"""Install an explicitly approved OSCAR-UI DEB; never run the UI as root."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import time

HELPER = '/opt/oscar-ui/lib/app/ubuntu-update.py'
LAUNCHER = '/opt/oscar-ui/bin/OSCAR-UI'
MAX_PACKAGE_BYTES = 2_000_000_000


def validate_package(package, version, digest):
    """Verify the private DEB against the selected version and SHA-256 digest.

    Return normally only for an installed oscar-ui amd64 package upgrade.
    Raise ValueError or CalledProcessError without installing on any mismatch.
    """
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version) or not re.fullmatch(r'[0-9a-fA-F]{64}', digest):
        raise ValueError('Invalid update identity.')
    calculated = hashlib.sha256()
    with package.open('rb') as source:
        for chunk in iter(lambda: source.read(65536), b''):
            calculated.update(chunk)
    if calculated.hexdigest() != digest.lower():
        raise ValueError('Installer checksum mismatch.')
    def field(name):
        return subprocess.check_output(['/usr/bin/dpkg-deb', '-f', str(package), name], text=True).strip()
    if field('Package') != 'oscar-ui' or field('Architecture') != 'amd64':
        raise ValueError('Expected the OSCAR-UI amd64 package.')
    package_version = field('Version')
    if package_version not in (version, version + '-1'):
        raise ValueError('Installer version does not match the selected release.')
    installed = subprocess.check_output(
        ['/usr/bin/dpkg-query', '-W', '-f=${Status}\n${Version}', 'oscar-ui'], text=True).splitlines()
    if len(installed) != 2 or installed[0] != 'install ok installed':
        raise ValueError('OSCAR-UI is not fully installed. Repair it through APT first.')
    if subprocess.run(['/usr/bin/dpkg', '--compare-versions', package_version, 'gt', installed[1]]).returncode:
        raise ValueError('The installed package is already this version or newer.')


def install(package, version, digest):
    """Install an authorized package after copying it into private root-owned storage.

    Accept a caller-owned regular file, stable version, and expected SHA-256.
    APT runs only after verification; failures propagate and temporary files are removed.
    Package maintainer scripts execute with normal APT administrator privileges.
    """
    if os.geteuid() != 0:
        raise ValueError('Administrator authorization is required.')
    with tempfile.TemporaryDirectory(prefix='oscar-ui-install-', dir='/var/tmp') as directory:
        private_package = Path(directory) / 'update.deb'
        descriptor = os.open(package, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(descriptor, 'rb') as source:
            metadata = os.fstat(source.fileno())
            caller_uid = int(os.environ.get('PKEXEC_UID', os.environ.get('SUDO_UID', '0')))
            if metadata.st_uid != caller_uid:
                raise ValueError('The downloaded installer must belong to the requesting user.')
            if not stat.S_ISREG(metadata.st_mode) or not 0 < metadata.st_size <= MAX_PACKAGE_BYTES:
                raise ValueError('Invalid installer file.')
            with private_package.open('wb') as destination:
                remaining = MAX_PACKAGE_BYTES + 1
                while remaining:
                    chunk = source.read(min(65536, remaining))
                    if not chunk:
                        break
                    destination.write(chunk)
                    remaining -= len(chunk)
                if remaining == 0:
                    raise ValueError('Installer is too large.')
        validate_package(private_package, version, digest)
        private_package.chmod(0o644)
        os.chmod(directory, 0o755)
        environment = {'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'HOME': '/root',
                       'DEBIAN_FRONTEND': 'noninteractive', 'LANG': 'C.UTF-8'}
        subprocess.run(['/usr/bin/apt-get', '-y', '--no-remove', '-o', 'DPkg::Lock::Timeout=60',
                        'install', str(private_package)], check=True, env=environment)


def run_after_exit(parent_pid, package, version, digest):
    """Wait for the UI PID to exit, authenticate with polkit, and report results.

    The package, version, and digest identify the previously downloaded update.
    Successful installation offers a normal-user restart; failures retain the log.
    """
    if os.geteuid() == 0:
        raise ValueError('Start the update from your normal desktop session.')
    for _ in range(240):
        try:
            os.kill(parent_pid, 0)
        except ProcessLookupError:
            break
        time.sleep(0.5)
    else:
        raise ValueError('OSCAR-UI did not close; installation was not started.')
    installer = subprocess.Popen(['/usr/bin/pkexec', '/usr/bin/python3', '-I', HELPER,
                                  '--install', str(package), version, digest])
    progress = subprocess.Popen(['/usr/bin/zenity', '--progress', '--pulsate', '--no-cancel',
                                 '--auto-close', '--title=OSCAR-UI update',
                                 '--text=Waiting for approval or installing. Do not shut down.'],
                                stdin=subprocess.PIPE)
    try:
        result = installer.wait()
    finally:
        try:
            progress.communicate(input=b'100\n', timeout=5)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            progress.terminate()
    if result == 0:
        package.unlink(missing_ok=True)
        answer = subprocess.run(['/usr/bin/zenity', '--question', '--title=OSCAR-UI update',
                                 '--text=Update installed. Open OSCAR-UI now?'])
        if answer.returncode == 0:
            environment = os.environ.copy()
            for key in ('LD_LIBRARY_PATH', 'QT_PLUGIN_PATH', 'JAVA_HOME'):
                environment.pop(key, None)
            subprocess.Popen([LAUNCHER], env=environment, start_new_session=True)
    else:
        subprocess.run(['/usr/bin/zenity', '--error', '--title=OSCAR-UI update',
                        '--text=Update canceled or failed. No automatic rollback was performed. '
                        'See ' + str(package.with_name('install.log')) +
                        '. If APT reported a partial installation, repair it before retrying.'])


def main():
    """Dispatch the requested handoff or authenticated installation mode.

    Return zero after completion, or one after reporting an operational failure.
    The normal-user mode displays failures; privileged mode writes to the caller's log.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--wait', type=int, metavar='PID')
    mode.add_argument('--install', action='store_true')
    parser.add_argument('package', type=Path)
    parser.add_argument('version')
    parser.add_argument('digest')
    args = parser.parse_args()
    try:
        if args.install:
            install(args.package, args.version, args.digest)
        else:
            if args.wait <= 1:
                raise ValueError('Invalid parent process.')
            run_after_exit(args.wait, args.package, args.version, args.digest)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f'Update failed: {error}', flush=True)
        if args.wait and os.geteuid() != 0:
            subprocess.run(['/usr/bin/zenity', '--error', '--title=OSCAR-UI update',
                            '--text=Update could not complete. See ' + str(args.package.with_name('install.log'))])
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

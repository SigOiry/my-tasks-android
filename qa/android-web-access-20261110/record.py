#!/usr/bin/env python3
"""Bounded full-display recording on the configured synthetic QA emulator.
Never modifies production data or reads private app storage.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent/'liquid-glass'))
import device
import run_native

def record(name, seconds, steps=None):
    if not re.fullmatch(r'[a-zA-Z0-9_-]+', name):
        raise ValueError('Safe recording name required')
    if not 1 <= seconds <= 180:
        raise ValueError('Bounded duration required')
    device.EVIDENCE = HERE/'evidence'
    device.EVIDENCE.mkdir(parents=True, exist_ok=True)
    device.shell('am', 'start', '-n', 'com.simon.mytasks.qa/com.simon.mytasks.MainActivity')
    time.sleep(1)
    resumed = device.adb('shell', 'dumpsys activity activities')
    if not any('topResumedActivity=' in line and 'com.simon.mytasks.qa/' in line for line in resumed.splitlines()):
        raise RuntimeError('Refusing recording outside synthetic QA foreground')
    target = '/sdcard/'+name+'.mp4'
    # Remote child owns only this bounded screenrecord process.
    command = [device.ADB, 'shell', 'screenrecord', '--size', '1080x1080',
               '--bit-rate', '6000000', '--time-limit', str(seconds), target]
    proc = subprocess.Popen(['ssh', *device.OPTS, device.HOST, shlex.join(command)],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    started = time.monotonic()
    failure = None
    try:
        time.sleep(.7)
        if steps is not None:
            run_native.run('scenario', name+'-scenario', json.loads(Path(steps).read_text()))
    except BaseException as exc:
        failure = exc
    # Let the explicit encoder time limit stop it; no blanket process termination.
    output, error = proc.communicate(timeout=max(1, seconds-(time.monotonic()-started))+20)
    if proc.returncode:
        raise RuntimeError('Bounded screenrecord failed: '+error[-1000:])
    remote = device.REMOTE+'/'+name+'.mp4'
    device.adb('pull', target, remote)
    destination = device.EVIDENCE/(name+'.mp4')
    device.pull(remote, destination)
    probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries',
        'format=duration:stream=width,height,r_frame_rate', '-of', 'json', str(destination)],
        capture_output=True, text=True, check=True)
    manifest = {'file':str(destination), 'sha256':hashlib.sha256(destination.read_bytes()).hexdigest(),
        'probe':json.loads(probe.stdout), 'capture':'actual Android full display; fixed encoder canvas; not measured UI FPS'}
    destination.with_suffix('.json').write_text(json.dumps(manifest, indent=2))
    print(json.dumps(manifest, indent=2))
    if failure is not None:
        raise failure
    return destination

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('name')
    parser.add_argument('--seconds', type=int, default=30)
    parser.add_argument('--steps', type=Path)
    args = parser.parse_args()
    record(args.name, args.seconds, args.steps)

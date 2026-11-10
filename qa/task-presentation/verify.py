#!/usr/bin/env python3
"""Compare client output with independently pinned expectations (never each other)."""

import json
import re
from datetime import date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def expected(vectors):
    palette = {}
    for case in vectors['palette']:
        color = vectors['neutral'] if case['slot'] < 0 else vectors['colors'][case['slot']]
        palette[case['name']] = dict(slot=case['slot'], **color)
    return {
        'contexts': {name: value['today'] for name, value in vectors['contexts'].items()},
        'palette': palette,
        'scores': {case['name']: case['expected'] for case in vectors['scores']},
        'orders': {case['name']: case['expected'] for case in vectors['orders']},
    }


def valid_day(value):
    if not isinstance(value, str) or not re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', value):
        return None
    try:
        return date.fromisoformat(value)
    except ValueError:
        return None


def valid_instant(value):
    if not isinstance(value, str) or not re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]+)?(?:Z|[+-][0-9]{2}:[0-9]{2})', value):
        return None
    try:
        return datetime.fromisoformat(value.replace('Z', '+00:00'))
    except ValueError:
        return None


def oracle(vectors):
    """Independent Python specification; never reads client code or expected scores."""
    result = {'contexts': {}, 'palette': {}, 'scores': {}, 'orders': {}}
    for name, context in vectors['contexts'].items():
        result['contexts'][name] = datetime.fromisoformat(context['now'].replace('Z', '+00:00')).astimezone(ZoneInfo(context['timezone'])).date().isoformat()
    for case in vectors['palette']:
        task = case['task']
        identity = next((task[k] for k in ('project_id', 'project') if isinstance(task.get(k), str) and task[k] != ''), None)
        slot = -1
        if identity is not None:
            hash_value = 2166136261
            for byte in identity.encode('utf-8'):
                hash_value = ((hash_value ^ byte) * 16777619) % (2 ** 32)
            if 'hash_u32' in case and hash_value != case['hash_u32']:
                raise ValueError('Corrupt pinned hash: ' + case['name'])
            slot = hash_value % 6
        color = vectors['neutral'] if slot < 0 else vectors['colors'][slot]
        result['palette'][case['name']] = dict(slot=slot, **color)

    def score(task, context_name):
        context = vectors['contexts'][context_name]
        today = date.fromisoformat(result['contexts'][context_name])
        due = valid_day(task.get('due_date'))
        delta = None if due is None else (due - today).days
        deadline = 0 if delta is None else 50 if delta < 0 else 40 if delta == 0 else 25 if delta == 1 else 10 if delta <= 7 else 0
        created = valid_instant(task.get('created_at'))
        age = 0 if created is None else min(4, max(0, (today - created.astimezone(ZoneInfo(context['timezone'])).date()).days // 7))
        notification = task.get('notification')
        unread = isinstance(notification, dict) and notification.get('unread') is True
        return deadline + {'high': 30, 'medium': 15}.get(task.get('priority'), 0) + 6 * (task.get('status') == 'in_progress') + 6 * unread + age

    for case in vectors['scores']:
        result['scores'][case['name']] = score(case['task'], case['context'])
    for case in vectors['orders']:
        today = date.fromisoformat(result['contexts'][case['context']])
        tasks = []
        for task in case['tasks']:
            terminal = task.get('status') in ('completed', 'cancelled')
            if bool(task.get('deleted')) != (case['view'] == 'trash'):
                continue
            if case['view'] in ('active', 'today', 'week') and terminal:
                continue
            due = valid_day(task.get('due_date'))
            if case['view'] == 'today' and (due is None or due > today):
                continue
            tasks.append(task)
        def key(task):
            bucket = 2 if task.get('status') in ('completed', 'cancelled') else 1 if task.get('status') == 'waiting' else 0
            created = valid_instant(task.get('created_at'))
            return (bucket, -score(task, case['context']), valid_day(task.get('due_date')) or date.max,
                    {'high': 0, 'medium': 1, 'low': 2}.get(task.get('priority'), 3),
                    created.timestamp() if created else float('inf'), task['id'].encode('utf-16-be'))
        result['orders'][case['name']] = [task['id'] for task in sorted(tasks, key=key)]
    return result


import argparse
import os
import shutil
import subprocess
import tempfile


def checked(command, **kwargs):
    completed = subprocess.run([str(arg) for arg in command], text=True, capture_output=True, timeout=90, **kwargs)
    if completed.returncode:
        raise RuntimeError(f'Command failed ({completed.returncode}): {command[0]}\n{completed.stderr or completed.stdout}')
    return completed.stdout


def run_web(fixture, module):
    return json.loads(checked(['node', HERE / 'web_adapter.mjs', Path(fixture).resolve(), Path(module).resolve()]))


def run_android(fixture, json_jar, java_home=None):
    json_jar = Path(json_jar).resolve()
    if not json_jar.is_file():
        raise RuntimeError('A real JVM org.json jar is required; Android android.jar contains non-executable stubs')
    javac = Path(java_home) / 'bin/javac' if java_home else shutil.which('javac')
    java = Path(java_home) / 'bin/java' if java_home else shutil.which('java')
    if not javac or not java:
        raise RuntimeError('Set JAVA_HOME to a JDK or put java and javac on PATH')
    build = HERE / '.build'
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='jvm-', dir=build) as output:
        source = ROOT / 'android/src/com/simon/mytasks'
        checked([javac, '-encoding', 'UTF-8', '-cp', json_jar, '-d', output, source / 'Core.java', source / 'PresentationPolicy.java', HERE / 'GoldenAdapter.java'])
        return json.loads(checked([java, '-cp', str(json_jar) + os.pathsep + output, 'com.simon.mytasks.GoldenAdapter', Path(fixture).resolve()]))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', choices=['oracle', 'web', 'android', 'both'], default='both')
    parser.add_argument('--fixture', type=Path, default=HERE / 'golden.json')
    parser.add_argument('--web-module', type=Path, default=ROOT / 'server/web/policy.mjs')
    parser.add_argument('--json-jar', type=Path, default=os.environ.get('JSON_JAR'))
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    args = parser.parse_args(argv)
    vectors = json.loads(args.fixture.read_text(encoding='utf-8'))
    want = expected(vectors)
    failures = differences(want, oracle(vectors))
    if failures:
        print('FIXTURE/ORACLE FAILURE\n' + '\n'.join(failures))
        return 2
    print('Independent oracle: PASS (' + ', '.join(f'{len(cases)} {name}' for name, cases in want.items()) + ')')
    clients = ['web', 'android'] if args.client == 'both' else [] if args.client == 'oracle' else [args.client]
    failed = False
    for client in clients:
        try:
            if client == 'android' and not args.json_jar:
                raise RuntimeError('Android verification not run: pass --json-jar or JSON_JAR (real JVM org.json)')
            actual = run_web(args.fixture, args.web_module) if client == 'web' else run_android(args.fixture, args.json_jar, args.java_home)
            mismatches = differences(want, actual)
            print(f'{client}: ' + ('FAIL' if mismatches else 'PASS'))
            for message in mismatches:
                print('  ' + message)
            failed |= bool(mismatches)
        except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as error:
            failed = True
            print(f'{client}: ERROR: {error}')
    return 1 if failed else 0


def differences(expected, actual, path='$'):
    if type(expected) is not type(actual):
        return [f'{path}: expected {expected!r}, got {actual!r}']
    if isinstance(expected, dict):
        failures = []
        for key in sorted(expected.keys() | actual.keys()):
            if key not in actual:
                failures.append(f'{path}.{key}: missing')
            elif key not in expected:
                failures.append(f'{path}.{key}: unexpected')
            else:
                failures.extend(differences(expected[key], actual[key], f'{path}.{key}'))
        return failures
    return [] if expected == actual else [f'{path}: expected {expected!r}, got {actual!r}']


if __name__ == '__main__':
    raise SystemExit(main())

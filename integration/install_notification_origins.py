"""Guarded, opt-in installer. Importing this module never changes live files."""
import argparse
import hashlib
import os
from pathlib import Path
import tempfile

BASE_SHA256 = 'eeed8e6d039b03fc36b7a12d7a154ff12b684e7c683c2b270cbb5ec0f8634853'
MARKER = '# notification-origins extension v1'
PATCHES = (
    ('from taskdb import Store, ROME, normalize_title\n',
     'from taskdb import Store, ROME, normalize_title\n'
     + MARKER + '\nfrom notification_origins import validate_origins, attach_origins\n'),
    ("            atomic_json(run/'reviewed.json', review)\n",
     "            origins = validate_origins(review, manifest, candidates, by_id)\n"
     "            atomic_json(run/'reviewed.json', review)\n"),
    ("                        recorded = {'run_id':run_id,",
     "                            attach_origins(db, task_id, snapshot, prior.get(task_id), blocked,\n"
     "                                           origins.get(candidate['action_key']), run_id, digest(manifest))\n"
     "                        recorded = {'run_id':run_id,"),
    ("                        'notification_summaries': {},\n",
     "                        'notification_summaries': {},\n"
     "                        'notification_origins_version': 1, 'notification_origins': {},\n"),
    ("            os.chmod(run/'review.md',0o600)\n",
     "            with (run/'review.md').open('a') as instructions:\n"
     "                instructions.write('\\n\\nExact email provenance v1: notification_origins_version must be 1. "
     "Optionally populate notification_origins={action_key:[{item_id,reference,role}]} for new or meaningfully changed candidates. "
     "Use role trigger only for the exact email causing this update; use context for supporting mail. "
     "Copy exact Gmail message source_reference (never thread IDs) or complete Nantes UID reference from that same manifest item. "
     "The candidate must carry the reference and a matching-hash disposition must link the item to its action_key. "
     "Do not infer origins from the newest message, a thread ID, prose, or historical dispositions. "
     "Omit when uncertain: historical source chooser remains mandatory; omission never infers an origin.\\n')\n"
     "            os.chmod(run/'review.md',0o600)\n"),
)


def patched_source(source):
    """Fail closed unless the entire original or installed source is recognized."""
    installed = MARKER in source
    original = source
    if installed:
        for before, after in reversed(PATCHES):
            if original.count(after) != 1:
                raise ValueError('unrecognized installed notification origins anchors')
            original = original.replace(after, before, 1)
    if hashlib.sha256(original.encode()).hexdigest() != BASE_SHA256:
        raise ValueError('unsupported sync_workflow.py digest; review new anchors first')
    result = original
    for before, after in PATCHES:
        if result.count(before) != 1:
            raise ValueError('sync_workflow.py anchor must occur exactly once')
        result = result.replace(before, after, 1)
    compile(result, 'sync_workflow.py', 'exec')
    return result


def _replace(path, content, mode):
    fd, temp = tempfile.mkstemp(dir=os.environ['TMPDIR'], prefix='notification-origins-')
    try:
        os.fchmod(fd, mode)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def install(workflow_path, *, apply=False):
    """Dry run by default. Parent must check review lease before apply=True.

    Copy helper first, then atomically replace recognized workflow. No DB access,
    provider access, taskdb changes, or lease overrides. Caller owns deployment.
    """
    path = Path(workflow_path)
    if path.is_symlink() or path.name != 'sync_workflow.py':
        raise ValueError('expected a regular sync_workflow.py target, not symlink')
    source = path.read_bytes().decode('utf-8')
    patched = patched_source(source)
    helper = Path(__file__).with_name('notification_origins.py').read_bytes()
    compile(helper, 'notification_origins.py', 'exec')
    destination = path.with_name('notification_origins.py')
    if destination.is_symlink():
        raise ValueError('helper target must not be a symlink')
    if destination.exists() and destination.read_bytes() != helper:
        raise ValueError('existing helper differs; explicit migration review required')
    changed = source != patched or not destination.exists()
    if apply and changed:
        if not destination.exists():
            _replace(destination, helper, 0o600)
        if source != patched:
            _replace(path, patched.encode(), path.stat().st_mode & 0o777)
    return {'changed': changed, 'applied': bool(apply and changed),
            'workflow_sha256': hashlib.sha256(patched.encode()).hexdigest(), 'version': 1}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('workflow', type=Path)
    parser.add_argument('--apply', action='store_true', help='only after parent review and lease check')
    args = parser.parse_args()
    print(install(args.workflow, apply=args.apply))


if __name__ == '__main__':
    main()

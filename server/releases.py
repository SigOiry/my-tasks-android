"""Fixed public GitHub release lookup. Never forwards application credentials."""
import json
import time
from threading import Lock
from urllib.request import Request, urlopen
from urllib.parse import urlsplit
from fastapi import HTTPException

API = 'https://api.github.com/repos/SigOiry/my-tasks-android/releases/latest'
LATEST = 'https://github.com/SigOiry/my-tasks-android/releases/latest'
_lock = Lock()
_cached = None

def clear_cache():
    global _cached
    with _lock:
        _cached = None

def release_status():
    global _cached
    with _lock:
        if _cached and time.monotonic() - _cached[0] < 300:
            return _cached[1]
        try:
            request = Request(API,headers={'Accept':'application/vnd.github+json','User-Agent':'MyTasks-web-release-check','X-GitHub-Api-Version':'2022-11-28'})
            with urlopen(request,timeout=10) as response:
                raw = response.read(262145)
            if len(raw) > 262144:
                raise ValueError('Release response too large')
            data = json.loads(raw)
            tag = data['tag_name']
            if not isinstance(tag,str) or not 1 <= len(tag) <= 200 or any(ord(c) < 32 for c in tag):
                raise ValueError('Invalid release tag')
            url = data.get('html_url',LATEST)
            parsed = urlsplit(url)
            if parsed.scheme != 'https' or parsed.netloc != 'github.com' or not parsed.path.startswith('/SigOiry/my-tasks-android/releases/') or parsed.username:
                raise ValueError('Unexpected release destination')
            result = {'tag':tag,'published_at':str(data.get('published_at') or '')[:80],'release_url':url}
            _cached = (time.monotonic(),result)
            return result
        except Exception:
            raise HTTPException(503,'GitHub release status unavailable. Open the latest release link to check directly.')

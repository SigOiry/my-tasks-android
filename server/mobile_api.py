"""Isolated mobile surface. The canonical dashboard module is never modified."""
import hmac
import importlib.util
import os
import sqlite3
import stat
from pathlib import Path
from fastapi import FastAPI, Depends, HTTPException, Request
from hermes_chat import ChatRequest, answer

PLUGIN = Path('/opt/data/plugins/personal-tasks/dashboard/plugin_api.py')
spec = importlib.util.spec_from_file_location('_mobile_dashboard', PLUGIN)
dashboard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dashboard)


def create_app(*, db_path=None, token_file=None, metadata_path=None, apk_dir=None,
               session_path=None, web_origin=None, cookie_secure=True):
    db_path = Path(db_path or os.environ.get('MOBILE_TASK_DB', '/opt/data/personal-assistant/tasks.json'))
    token_file = Path(token_file or os.environ.get('MOBILE_TOKEN_FILE', '/run/secrets/mobile_token'))
    mode = token_file.stat().st_mode
    if not stat.S_ISREG(mode) or mode & 0o077:
        raise ValueError('Mobile token must be a private regular file (0600 or 0400)')
    token = token_file.read_text().strip()
    if len(token) < 32 or not token.isascii() or any(c.isspace() for c in token):
        raise ValueError('Mobile token must contain at least 32 ASCII non-whitespace characters')
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)

    from web_auth import Sessions, COOKIE, SESSION_SECONDS
    from fastapi.responses import JSONResponse
    from pydantic import BaseModel, ConfigDict, Field
    from urllib.parse import urlsplit
    origin = web_origin or os.environ.get('MOBILE_WEB_ORIGIN', 'https://task.oirysimon.com')
    parsed_origin = urlsplit(origin)
    if parsed_origin.scheme not in {'http','https'} or not parsed_origin.netloc or parsed_origin.path or parsed_origin.query or parsed_origin.fragment or parsed_origin.username:
        raise ValueError('Web origin must be an exact HTTP(S) origin without a path')
    if not cookie_secure and parsed_origin.hostname not in {'localhost','127.0.0.1','testserver','::1'}:
        raise ValueError('Insecure cookie override is only for loopback QA')
    metadata_path = Path(metadata_path or os.environ.get('MOBILE_METADATA_DB', '/var/lib/my-tasks-mobile/events.sqlite'))
    sessions = Sessions(session_path or metadata_path.with_name('web-sessions.sqlite'), token)

    def require_origin(request):
        if request.headers.get('origin') != origin:
            raise HTTPException(403, 'Same-origin request required')

    def cookie_session(request):
        return sessions.read(request.cookies.get(COOKIE))

    def csrf_check(request, session):
        require_origin(request)
        supplied = request.headers.get('x-csrf-token', '')
        if not session or not supplied.isascii() or not hmac.compare_digest(supplied, session['csrf']):
            raise HTTPException(403, 'Valid CSRF header required')

    def authorize(request: Request):
        supplied = request.headers.get('authorization')
        if supplied is not None:
            if not hmac.compare_digest(supplied.encode(), ('Bearer ' + token).encode()):
                raise HTTPException(401, 'Mobile bearer token required', headers={'WWW-Authenticate':'Bearer'})
            return
        session = cookie_session(request)
        if not session:
            raise HTTPException(401, 'Authentication required', headers={'WWW-Authenticate':'Bearer'})
        if request.method not in {'GET','HEAD','OPTIONS'}:
            csrf_check(request, session)

    @app.middleware('http')
    async def boundary(request, call_next):
        try:
            # Reject ambiguous credentials before choosing bearer or cookie auth.
            # ASGI retains repeated fields; raw names are compared case-insensitively.
            protected = {b'authorization', b'origin', b'x-csrf-token'}
            seen = set()
            for name, _ in request.scope['headers']:
                name = name.lower()
                if name in protected:
                    if name in seen:
                        raise HTTPException(400, 'Repeated security header')
                    seen.add(name)
            if request.url.path.startswith('/api/'):
                authorize(request)
            if request.url.path.startswith(('/api/', '/auth/')):
                body = bytearray()
                async for chunk in request.stream():
                    body.extend(chunk)
                    if len(body) > 65536:
                        raise HTTPException(413, 'Request body too large')
                request._body = bytes(body)
            response = await call_next(request)
        except HTTPException as error:
            response = JSONResponse({'detail':error.detail},status_code=error.status_code,headers=error.headers)
        except (sqlite3.Error, OSError):
            response = JSONResponse({'detail':'Service metadata unavailable; no unsafe change attempted'},status_code=503)
        response.headers['Cache-Control'] = 'no-store'
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['Content-Security-Policy'] = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"
        response.headers['Referrer-Policy'] = 'no-referrer'
        response.headers['X-Frame-Options'] = 'DENY'
        response.headers['Permissions-Policy'] = 'camera=(), geolocation=(), microphone=(self)'
        return response

    from fastapi.exceptions import RequestValidationError

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, error):
        # Pydantic's default input echo can expose credentials/private chat text.
        return JSONResponse({'detail':'Invalid request fields'},status_code=422)

    class Login(BaseModel):
        model_config = ConfigDict(extra='forbid')
        token: str = Field(min_length=1,max_length=4096)

    @app.post('/auth/login')
    def login(payload: Login, request: Request):
        require_origin(request)
        sessions.attempt(request.client.host if request.client else 'unknown')
        if not sessions.matches(payload.token):
            raise HTTPException(401, 'Invalid access token')
        opaque, csrf = sessions.issue(request.cookies.get(COOKIE))
        response = JSONResponse({'authenticated':True,'csrf':csrf})
        response.set_cookie(COOKIE,opaque,max_age=SESSION_SECONDS,httponly=True,secure=cookie_secure,samesite='strict',path='/')
        return response

    @app.get('/auth/session')
    def session_status(request: Request):
        return cookie_session(request) or {'authenticated':False}

    @app.post('/auth/logout')
    def logout(request: Request):
        csrf_check(request,cookie_session(request))
        sessions.revoke(request.cookies.get(COOKIE))
        response = JSONResponse({'authenticated':False})
        response.delete_cookie(COOKIE,path='/',secure=cookie_secure,httponly=True,samesite='strict')
        return response

    from fastapi.responses import FileResponse
    import json
    bundle = Path(__file__).parent/'web_dist'

    @app.api_route('/', methods=['GET','HEAD'])
    def web_shell():
        return FileResponse(bundle/'index.html',media_type='text/html')

    @app.get('/app-info')
    def app_info():
        return {'version':json.loads((bundle/'manifest.json').read_text())['version'],
                'android_release_url':'https://github.com/SigOiry/my-tasks-android/releases/latest',
                'default_server':'https://task.oirysimon.com'}

    @app.api_route('/assets/{name}',methods=['GET','HEAD'])
    def asset(name: str):
        if name not in {'app.js','app.css','policy.mjs','icon.svg'} or (bundle/name).is_symlink():
            raise HTTPException(404,'Asset not found')
        media = {'js':'text/javascript','mjs':'text/javascript','css':'text/css','svg':'image/svg+xml'}
        return FileResponse(bundle/name,media_type=media[name.rsplit('.',1)[1]])

    @app.get('/health')
    def health():
        return {'status':'ok'}

    @app.get('/download/my-tasks.apk')
    def download_apk():
        from fastapi.responses import FileResponse
        directory = Path(apk_dir or os.environ.get('MOBILE_APK_DIR', str(Path(__file__).parent/'public')))
        artifact = directory/'my-tasks.apk'
        if artifact.is_symlink() or not artifact.is_file():
            raise HTTPException(404,'APK not published')
        return FileResponse(artifact,media_type='application/vnd.android.package-archive',filename='my-tasks.apk')

    # Copy only endpoint callables/paths, not their host auth dependency graph.
    # Own dependency protects every copied route; host router remains untouched.
    from fastapi import APIRouter
    router = APIRouter(prefix='/api', dependencies=[Depends(authorize)], route_class=dashboard.SafeRoute)
    allowed = {('/tasks','GET'),('/tasks','POST'),('/tasks/{task_id}','GET'),
               ('/tasks/{task_id}','PATCH'),('/tasks/{task_id}','DELETE'),
               ('/tasks/{task_id}/restore','POST'),('/tasks/{task_id}/updates/read','POST')}
    for route in dashboard.create_router(db_path).routes:
        methods = {method for method in route.methods if (route.path,method) in allowed}
        if methods:
            router.add_api_route(route.path, route.endpoint, methods=methods, status_code=route.status_code)
    from event_feed import EventFeed
    from fastapi import Query
    feed = EventFeed(metadata_path or os.environ.get('MOBILE_METADATA_DB', '/var/lib/my-tasks-mobile/events.sqlite'), dashboard, db_path)

    @router.get('/release-status')
    def latest_release():
        from releases import release_status
        return release_status()

    @router.get('/events')
    def events(after: int = Query(0,ge=0,le=9223372036854775806), limit: int = Query(100,ge=1,le=500)):
        import sqlite3
        try:
            return feed.poll(after,limit)
        except sqlite3.Error:
            raise HTTPException(503,'Event metadata unavailable')

    @router.post('/chat')
    def chat(payload: ChatRequest):
        db = dashboard.taskdb.Store(db_path).read()
        task = dashboard.enrich(dashboard.find_task(db,payload.task_id),db)
        return answer(task,payload)

    app.include_router(router)
    return app

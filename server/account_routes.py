"""Password HTTP contract and authoritative bearer-vs-cookie selection."""
import hmac
from fastapi import HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel,ConfigDict,Field
from account_auth import SESSION_SECONDS
from web_auth import COOKIE

class Login(BaseModel):
    model_config = ConfigDict(extra='forbid',strict=True)
    username: str = Field(min_length=1,max_length=128)
    password: str = Field(min_length=1,max_length=128)

class Password(BaseModel):
    model_config = ConfigDict(extra='forbid',strict=True)
    current_password: str = Field(min_length=1,max_length=128)
    new_password: str = Field(min_length=15,max_length=128)

class Activation(BaseModel):
    model_config = ConfigDict(extra='forbid',strict=True)
    activation_code: str = Field(pattern=r'^[0-9a-fA-F]{64}$')

class AccountAuth:
    def __init__(self,store,legacy,token,origin,secure):
        self.store,self.legacy,self.token,self.origin,self.secure = store,legacy,token,origin,secure

    def identity(self,request,native=False):
        snapshot = self.store.snapshot()
        supplied = request.headers.get('authorization')
        if supplied is not None:
            if not supplied.startswith('Bearer '):
                raise HTTPException(401,'Authentication required')
            opaque = supplied[7:]
            result = self.store.read(opaque,'native')
            if result: return result
            if not snapshot['active'] and hmac.compare_digest(supplied.encode(),('Bearer '+self.token).encode()):
                return dict(authenticated=True,username=snapshot['username'],password_change_required=True,_epoch=snapshot['epoch'])
            raise HTTPException(401,'Authentication required')
        if native: return None
        opaque = request.cookies.get(COOKIE)
        result = self.store.read(opaque,'browser')
        if result: return result
        if not snapshot['active']:
            result = self.legacy.read(opaque)
            if result:
                return dict(result,username=snapshot['username'],password_change_required=True,_epoch=snapshot['epoch'])
        return None

    def origin_check(self,request,native=False):
        supplied = request.headers.get('origin')
        if (not native or supplied is not None) and supplied != self.origin:
            raise HTTPException(403,'Same-origin request required')

    def csrf(self,request,identity):
        self.origin_check(request)
        supplied = request.headers.get('x-csrf-token','')
        if not identity or not identity.get('csrf') or not supplied.isascii() or not hmac.compare_digest(supplied,identity['csrf']):
            raise HTTPException(403,'Valid CSRF header required')

    def authorize(self,request):
        identity = self.identity(request)
        if not identity: raise HTTPException(401,'Authentication required')
        if identity['password_change_required']:
            raise HTTPException(428,'password_change_required')
        if request.headers.get('authorization') is None and request.method not in {'GET','HEAD','OPTIONS'}:
            self.csrf(request,identity)

    def result(self,issued,native=False):
        opaque,meta = issued
        response = JSONResponse(meta)
        if not native:
            response.set_cookie(COOKIE,opaque,max_age=SESSION_SECONDS,httponly=True,secure=self.secure,samesite='strict',path='/')
        return response

    def attempt(self,request,native=False,json_body=True):
        self.origin_check(request,native)
        if json_body and request.headers.get('content-type','').split(';')[0].lower() != 'application/json':
            raise HTTPException(415,'JSON request required')
        self.store.attempt(request.client.host if request.client else 'unknown')

    def install(self,app):
        # Remove the old token-login handlers only for initialized account mode.
        app.router.routes[:] = [r for r in app.router.routes if not getattr(r,'path','').startswith('/auth/')]

        @app.get('/auth/session')
        def session(request: Request):
            try:
                identity = self.identity(request)
            except HTTPException as error:
                if error.status_code != 401: raise
                identity = None
            return {k:v for k,v in identity.items() if not k.startswith('_')} if identity else {'authenticated':False}

        def do_login(payload,request,native):
            self.attempt(request,native)
            proof = self.identity(request,native)
            snapshot = self.store.snapshot()
            # Ownership is checked before password work, making public defaults useless.
            if not snapshot['active'] and not proof:
                raise HTTPException(428,'activation_required')
            if len(payload.password.encode('utf-8'))>1024:
                raise HTTPException(422,'Invalid request fields')
            valid = self.store.verify(snapshot,payload.password)
            if not hmac.compare_digest(payload.username.encode(),snapshot['username'].encode()) or not valid:
                raise HTTPException(401,'Invalid username or password')
            issued = self.store.issue(snapshot,'native' if native else 'browser',not snapshot['active'],request.cookies.get(COOKIE) if not native else None)
            if not native and not snapshot['active'] and request.cookies.get(COOKIE):
                self.legacy.revoke(request.cookies[COOKIE])
            return self.result(issued,native)

        @app.post('/auth/login')
        def login(payload: Login,request: Request):
            return do_login(payload,request,False)

        @app.post('/auth/native/login')
        def native_login(payload: Login,request: Request):
            return do_login(payload,request,True)

        @app.post('/auth/activate')
        def activate(payload: Activation,request: Request):
            self.attempt(request)
            # A supplied invalid Authorization must never silently use cookies.
            if request.headers.get('authorization') is not None: self.identity(request)
            return self.result(self.store.activate(payload.activation_code,request.cookies.get(COOKIE)))

        def do_password(payload,request,native):
            self.attempt(request,native)
            identity = self.identity(request,native)
            if not identity: raise HTTPException(401,'Authentication required')
            if not native: self.csrf(request,identity)
            if len(payload.current_password.encode('utf-8'))>1024 or len(payload.new_password.encode('utf-8'))>1024:
                raise HTTPException(422,'Invalid request fields')
            return self.result(self.store.change(self.store.snapshot(),payload.current_password,payload.new_password,'native' if native else 'browser',identity),native)

        @app.post('/auth/password')
        def password(payload: Password,request: Request):
            return do_password(payload,request,False)

        @app.post('/auth/native/password')
        def native_password(payload: Password,request: Request):
            return do_password(payload,request,True)

        @app.post('/auth/logout')
        def logout(request: Request):
            identity = self.identity(request)
            self.csrf(request,identity)
            opaque = request.cookies.get(COOKIE)
            self.store.revoke(opaque,'browser')
            self.legacy.revoke(opaque)
            response = JSONResponse({'authenticated':False})
            response.delete_cookie(COOKIE,path='/',secure=self.secure,httponly=True,samesite='strict')
            return response

        @app.post('/auth/native/logout')
        def native_logout(request: Request):
            self.origin_check(request,True)
            identity = self.identity(request,True)
            if not identity: raise HTTPException(401,'Authentication required')
            self.store.revoke(request.headers['authorization'][7:],'native')
            return {'authenticated':False}

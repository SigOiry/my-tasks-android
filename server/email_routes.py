"""Strict API schemas; install ONLY on the host's authenticated /api router."""
from typing import Annotated, Literal
from fastapi import Query
from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictInt, field_validator

Token=Annotated[str,Field(min_length=1,max_length=256,pattern=r'^[A-Za-z0-9_.:@+\-]+$')]
Revision=Annotated[StrictInt,Field(ge=1,le=9223372036854775806)]
class StrictModel(BaseModel):
    model_config=ConfigDict(extra='forbid',strict=True)
class NewDraft(StrictModel):
    request_id: Token
    task_id: Token
    source_id: Annotated[str,Field(pattern=r'^src_[a-f0-9]{32}$')]
    update_token: Token | None = None
    reply_all: StrictBool = False
class SaveDraft(StrictModel):
    expected_revision: Revision
    body: Annotated[str,Field(max_length=60000)]

class RevisionRequest(StrictModel):
    expected_revision: Revision
class Empty(StrictModel):
    pass
class SendDraft(RevisionRequest):
    request_id: Token
    confirmation_id: Token
    confirmed: Annotated[StrictBool,Field()]

    @field_validator('confirmed')
    @classmethod
    def must_confirm(cls,value):
        if value is not True:raise ValueError('Explicit confirmation required')
        return value
class Assist(RevisionRequest):
    request_id: Token
    intent: Literal['draft','improve','discuss','edit']
    instruction: Annotated[str,Field(min_length=1,max_length=8000)]
class RefreshContext(RevisionRequest):
    reply_all: StrictBool | None = None


def install_routes(app,workspace):
    from fastapi import FastAPI, APIRouter
    if isinstance(app,FastAPI):
        # Root application middleware supplies existing auth/Origin/CSRF/no-store.
        router=APIRouter(prefix='/api')
        install_routes(router,workspace)
        app.include_router(router)
        return
    @app.get('/tasks/{task_id}/email-sources')
    def sources(task_id: str,update_token: Annotated[str | None,Query(min_length=1,max_length=256)]=None):
        return workspace.list_sources(task_id,update_token)
    @app.get('/tasks/{task_id}/email-sources/{source_id}')
    def source(task_id: str,source_id: str):
        return workspace.source_detail(task_id,source_id)
    @app.post('/email/drafts')
    def create(p: NewDraft):return workspace.create(p)
    @app.post('/email/drafts/{id}/refresh-context')
    def refresh(id: str,p: RefreshContext):return workspace.refresh(id,p)
    @app.delete('/email/drafts/{id}')
    def discard(id: str,p: RevisionRequest):return workspace.discard(id,p)
    @app.post('/email/drafts/{id}/preview')
    def preview(id: str,p: RevisionRequest):return workspace.preview(id,p)
    @app.post('/email/drafts/{id}/send')
    def send(id: str,p: SendDraft):return workspace.send(id,p)
    @app.post('/email/drafts/{id}/reconcile')
    def reconcile(id: str,p: Empty):return workspace.reconcile(id)
    @app.post('/email/drafts/{id}/assist',status_code=202)
    def assist(id: str,p: Assist):return workspace.assist(id,p)
    @app.get('/email/drafts/{id}/assist/{request_id}')
    def job(id: str,request_id: str):return workspace.job(id,request_id)
    @app.post('/email/drafts/{id}/proposals/{proposal_id}/apply')
    def apply(id: str,proposal_id: str,p: RevisionRequest):return workspace.apply(id,proposal_id,p)
    @app.post('/email/drafts/{id}/proposals/{proposal_id}/dismiss')
    def dismiss(id: str,proposal_id: str,p: Empty):return workspace.dismiss(id,proposal_id)
    @app.get('/email/drafts/{id}')
    def get(id: str):return workspace.get(id)
    @app.patch('/email/drafts/{id}')
    def patch(id: str,p: SaveDraft):return workspace.patch(id,p)

#!/usr/bin/env python3
"""JVM native email client -> actual email API/store, synthetic provider only."""
from pathlib import Path
import os,sys,socket,threading,time,tempfile,subprocess,importlib.util
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1];SERVER=ROOT.parent/'server';sys.path.insert(0,str(SERVER))
from fastapi import FastAPI,APIRouter,Depends,HTTPException,Header
import uvicorn
from email_workspace import EmailWorkspace
from email_routes import install_routes
spec=importlib.util.spec_from_file_location('synthetic_email_provider',SERVER/'tests/test_email_workspace.py');fixture=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture)
def main():
 javac=str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else __import__('shutil').which('javac')
 if not javac:raise SystemExit('JAVA_HOME or javac is required')
 java=Path(javac).parent
 with tempfile.TemporaryDirectory(prefix='native-email-api-') as temp:
  root=Path(temp);provider=fixture.FixtureProvider();calls=[]
  def llm(**kwargs):
   assert kwargs['tools']==[];calls.append(kwargs)
   return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Synthetic proposed reply',tool_calls=[]))])
  task=dict(id='task1',title='Synthetic native reply fixture',description='Private synthetic context',source_references=['gmail:owner@example.test:message:abc'],notification={'token':'token1'})
  workspace=EmailWorkspace(root/'email.sqlite',lambda tid:task if tid=='task1' else None,provider=provider,call_llm=llm);workspace.start()
  def auth(authorization:str=Header(default='')):
   if authorization!='Bearer native-synthetic-email-contract-token':raise HTTPException(401,'Synthetic auth required')
  app=FastAPI();router=APIRouter(prefix='/api',dependencies=[Depends(auth)]);install_routes(router,workspace);app.include_router(router)
  sock=socket.socket();sock.bind(('127.0.0.1',0));url='http://127.0.0.1:'+str(sock.getsockname()[1]);server=uvicorn.Server(uvicorn.Config(app,log_level='error',access_log=False));thread=threading.Thread(target=server.run,kwargs={'sockets':[sock]},daemon=True)
  try:
   print('Starting private loopback email fixture',flush=True)
   thread.start();deadline=time.monotonic()+10
   while not server.started:
    if time.monotonic()>deadline:raise RuntimeError('loopback fixture did not start')
    time.sleep(.02)
   print('Compiling actual Java client contract fixture',flush=True)
   classes=root/'classes';classes.mkdir();jar=ROOT/'build/test-lib/json.jar';sources=[ROOT/'src/com/simon/mytasks'/(n+'.java') for n in ['ConversationState','ConversationClient','EmailReplyClient']]+[ROOT/'tests'/(n+'.java') for n in ['ConversationApiIntegrationTest','EmailReplyApiIntegrationTest']]
   subprocess.run([str(java/'javac'),'--release','17','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True,timeout=30)
   print('Running actual Java HTTP contract (90-second deadline)',flush=True)
   subprocess.run([str(java/'java'),'-cp',str(classes)+os.pathsep+str(jar),'com.simon.mytasks.EmailReplyApiIntegrationTest',url,'native-synthetic-email-contract-token'],check=True,timeout=90)
   assert len(provider.sent)==1, 'duplicate send reached synthetic provider'
   assert len(calls)==1,'AI work duplicated'
   assert task['notification']['token']=='token1','opening/reply mutated update identity'
   print('Verified exactly one synthetic send, one tool-free AI call; canonical source task unchanged.')
  finally:
   server.should_exit=True;thread.join(10);sock.close();workspace.close()
if __name__=='__main__':main()

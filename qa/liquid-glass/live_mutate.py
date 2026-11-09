"""Explicit isolated fixture writes through real TLS/native auth and canonical API."""
import argparse,json,ssl,time,urllib.request
from pathlib import Path

def mutate(root,action):
 context=ssl.create_default_context(cafile=str(root/'qa-cert.pem'))
 opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=context))
 def request(path,payload=None,token=None,method=None):
  headers={'Content-Type':'application/json'}
  if token:headers['Authorization']='Bearer '+token
  with opener.open(urllib.request.Request('https://127.0.0.1:9147'+path,data=None if payload is None else json.dumps(payload).encode(),headers=headers,method=method),timeout=15) as r:return json.load(r)
 token=request('/auth/native/login',{'username':'qa-owner','password':(root/'qa-password').read_text()})['session_token']
 try:
  title='Book the team planning session' if action=='newer-update' else 'Collect workshop reading notes'
  matches=[t for t in request('/api/tasks',token=token)['tasks'] if t['title']==title or (action=='conflict' and t['title'].startswith(title))]
  if len(matches)!=1:raise AssertionError('One unambiguous synthetic fixture target required')
  task=matches[0]
  if not task['description'].startswith('SYNTHETIC QA'):raise AssertionError('Refusing non-synthetic fixture write')
  body={'revision':task['revision']}
  if action=='newer-update':
   marker=json.loads((root/'liquid-glass-synthetic.json').read_text());assert marker.get('synthetic') is True
   # Canonical external-updater path, not the manual-user PATCH route (which marks its own edit read).
   import importlib
   dashboard=getattr(importlib.import_module('runtime'),'dashboard')
   dashboard.taskdb.Store(root/'tasks.json').edit(task['id'],description='SYNTHETIC QA — A newer update arrived while review was open. Fixture sequence '+str(time.time_ns()))
   updated=request('/api/tasks/'+task['id'],token=token)
   assert updated.get('notification',{}).get('unread') is True
  elif action=='conflict':
   body['notes']='SYNTHETIC QA remote writer changed this note.'
   updated=request('/api/tasks/'+task['id'],body,token,method='PATCH')
  else:raise ValueError('Unsupported fixture action')
  assert updated['revision']!=task['revision']
  return {'action':action,'task_id':task['id'],'changed':True,'unread':updated.get('notification',{}).get('unread',False)}
 finally:request('/auth/native/logout',{},token)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('action',choices=['newer-update','conflict']);a=p.parse_args();print(json.dumps(mutate(a.root,a.action)))

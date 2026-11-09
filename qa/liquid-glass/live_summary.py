"""Linux-loopback synthetic API state, using real TLS/auth, no credentials in output."""
import argparse,json,ssl,urllib.request
from pathlib import Path

def report(root):
 context=ssl.create_default_context(cafile=str(root/'qa-cert.pem'))
 opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=context))
 origin='https://127.0.0.1:9147'
 def request(path,payload=None,token=None):
  headers={'Content-Type':'application/json'}
  if token:headers['Authorization']='Bearer '+token
  with opener.open(urllib.request.Request(origin+path,data=None if payload is None else json.dumps(payload).encode(),headers=headers),timeout=15) as response:return json.load(response)
 token=request('/auth/native/login',{'username':'qa-owner','password':(root/'qa-password').read_text()})['session_token']
 try:
  tasks=request('/api/tasks',token=token)['tasks'];threads=request('/api/conversations?limit=100',token=token)['conversations']
  if not all('SYNTHETIC QA' in t.get('description','') for t in tasks):raise AssertionError('Only explicitly synthetic fixture tasks may be inspected')
  summaries=[]
  for thread in threads:
   data=request('/api/conversations/'+thread['id']+'?limit=100',token=token)
   if data['has_more']:raise AssertionError('Report must fetch all messages before asserting a total')
   req=thread.get('latest_request') or {}
   summaries.append({'id':thread['id'],'title':thread['title'],'messages':len(data['messages']),'latest_status':req.get('status'),'task_bound':thread.get('task_id') is not None,'archived':thread['archived'],'contents':[m['content'] for m in data['messages']]})
  return {'tasks':[{'id':t['id'],'title':t['title'],'unread':t.get('notification',{}).get('unread',False),'revision':t['revision'],'description':t.get('description'),'project':t.get('project'),'priority':t.get('priority'),'status':t.get('status'),'due_date':t.get('due_date'),'notes':t.get('notes')} for t in tasks],'conversations':summaries}
 finally:request('/auth/native/logout',{},token)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);a=p.parse_args();print(json.dumps(report(a.root),ensure_ascii=False))

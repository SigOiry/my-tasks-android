#!/usr/bin/env python3
"""Build/run true QA-only instrumentation. Credentials never enter logs."""
import argparse,hashlib,json,subprocess,sys,time,threading,re,shlex
from pathlib import Path
import device
HERE=Path(__file__).resolve().parent
RUNTIME=Path('/opt/data/cache/scratch/liquid-glass-runtime-20261109')
RUNNER='com.simon.mytasks.uiqa/com.simon.mytasks.uiqa.Acceptance'

def deploy_runner():
 digest=hashlib.sha256((HERE/'native/Acceptance.java').read_bytes()+(HERE/'build-native-tests.sh').read_bytes()).hexdigest()[:16]
 out=Path('/opt/data/cache/scratch')/('native-ui-tests-'+digest)
 apk=out/'ui-acceptance.apk'
 if not apk.exists():subprocess.run(['bash',str(HERE/'build-native-tests.sh'),str(RUNTIME),str(out)],check=True)
 target='/home/simon/hermes-apks/ui-acceptance.apk'
 subprocess.run(['scp',*device.OPTS,str(apk),device.HOST+':'+target],check=True,capture_output=True)
 local=hashlib.sha256(apk.read_bytes()).hexdigest();remote=device.remote('sha256sum '+target).split()[0]
 if local!=remote:raise RuntimeError('instrumentation transfer hash mismatch')
 result=device.adb('install','-r',target)
 if 'Success' not in result:raise RuntimeError('instrumentation install not confirmed')
 print('Verified instrumentation transfer/install',local)

def collect(name):
 base='/sdcard/Android/data/com.simon.mytasks.qa/files/evidence/'
 for ext in ['json','png']:
  target=device.REMOTE+'/'+name+'.'+ext;device.adb('pull',base+name+'.'+ext,target);device.pull(target,device.EVIDENCE/(name+'.'+ext))

def display_observer(stop):
 base='/sdcard/Android/data/com.simon.mytasks.qa/files/evidence/'
 previous=None
 while not stop.is_set():
  try:
   name=device.shell('cat',base+'capture-request').strip()
   if name!=previous and re.fullmatch(r'[a-zA-Z0-9_-]+',name):
    if name=='host-offline':device.adb('reverse','--remove','tcp:9147')
    elif name=='host-online':device.adb('reverse','tcp:9147','tcp:9147')
    elif name=='host-fold':device.fold(0)
    elif name=='host-unfold':device.fold(2)
    elif name in ('host-rotate-landscape','host-rotate-portrait'):
     device.shell('settings','put','system','accelerometer_rotation','0');device.shell('settings','put','system','user_rotation','1' if name.endswith('landscape') else '0')
    elif name in ('host-font-200','host-font-100'):device.shell('settings','put','system','font_scale','2.0' if name.endswith('200') else '1.0')
    elif name in ('host-dark','host-light'):device.shell('cmd','uimode','night','yes' if name.endswith('dark') else 'no')
    elif name in ('host-newer-update','host-conflict'):
     action=name.removeprefix('host-');result=device.remote('/home/simon/hermes-apks/qa-runtime/venv/bin/python /home/simon/hermes-apks/qa-runtime/qa/liquid-glass/live_mutate.py --root /home/simon/hermes-apks/qa-runtime/scratch/runtime '+action);(device.EVIDENCE/(name+'-result.json')).write_text(result)
    elif name.startswith('host-'):raise RuntimeError('Unknown QA host action')
    else:
     device.shell('screencap','-p','/sdcard/mytasks-live-native.png')
     target=device.REMOTE+'/'+name+'-display.png';device.adb('pull','/sdcard/mytasks-live-native.png',target);device.pull(target,device.EVIDENCE/(name+'-display.png'))
    device.shell('sh','-c',shlex.join(['printf','%s',name])+' > '+shlex.quote(base+'capture-ack'));previous=name
  except Exception:
   pass  # No stale file or placeholder is returned; instrumentation times out if capture fails.
  stop.wait(.12)

def run(mode,name,steps=None,label=None,text=None,deploy=False):
 if deploy:deploy_runner()
 command=['am','instrument','-w','-e','mode',mode,'-e','name',name]
 if mode=='login':command+=['-e','fixture_password',(RUNTIME/'qa-password').read_text().strip()]
 if steps is not None:command+=['-e','steps',json.dumps(steps,ensure_ascii=False,separators=(',',':'))]
 if label is not None:command+=['-e','label',label]
 if text is not None:command+=['-e','text',text]
 stop=threading.Event();observer=None
 if steps is not None:
  base='/sdcard/Android/data/com.simon.mytasks.qa/files/evidence/';device.shell('rm','-f',base+'capture-request',base+'capture-ack');command+=['-e','capture_handshake','true'];observer=threading.Thread(target=display_observer,args=(stop,),daemon=True);observer.start()
 try:output=device.shell(*command,RUNNER)
 except Exception as e:
  detail=(' '+str(getattr(e,'stderr',''))[-2000:]) if mode!='login' and getattr(e,'stderr',None) else ''
  raise RuntimeError('Native QA execution failed; credential-bearing command deliberately omitted'+detail) from None
 finally:
  stop.set()
  if observer:observer.join(timeout=5)
 print(output)
 (device.EVIDENCE/(name+'-result.txt')).write_text(output)
 if 'result=PASS '+mode not in output:raise RuntimeError('native acceptance failed; see safe result output')
 collect(name)
 if steps:
  for step in steps:
   if step.get('op')=='capture':collect(step['name'])
 return output

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('mode',choices=['login','tasks','snapshot','scenario','tap','draft']);p.add_argument('--name',required=True);p.add_argument('--steps');p.add_argument('--label');p.add_argument('--text');p.add_argument('--deploy-runner',action='store_true');args=p.parse_args()
 try:run(args.mode,args.name,json.loads(Path(args.steps).read_text()) if args.steps else None,args.label,args.text,args.deploy_runner)
 except Exception as e:print(str(e),file=sys.stderr);sys.exit(1)

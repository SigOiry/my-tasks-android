#!/usr/bin/env python3
"""Strict-SSH Pixel Fold QA helper. Never reads application-private storage."""
import argparse,json,re,shlex,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
HOST='simon@192.168.0.133'
ADB='/home/simon/Android/Sdk/platform-tools/adb'
OPTS=['-i','/opt/data/ssh/android-testing','-o','UserKnownHostsFile=/opt/data/ssh/android-known_hosts','-o','StrictHostKeyChecking=yes','-o','BatchMode=yes']
ROOT=Path(__file__).resolve().parent
EVIDENCE=ROOT/'evidence'
REMOTE='/home/simon/hermes-apks/evidence'
def remote(command):
 return subprocess.run(['ssh',*OPTS,HOST,command],check=True,capture_output=True,text=True).stdout
def adb(*args):
 return remote(shlex.join([ADB,*map(str,args)]))
def shell(*args):
 return adb('shell',shlex.join(list(map(str,args))))
def pull(source,dest):
 subprocess.run(['scp',*OPTS,HOST+':'+source,str(dest)],check=True,capture_output=True)
def dump():
 try:
  shell('uiautomator','dump','/sdcard/mytasks-qa-window.xml')
 except subprocess.CalledProcessError:
  raise RuntimeError('Mobile MCP owns UiAutomation. Use mobile_list_elements_on_screen or native instrumentation; no stale dump was read.') from None
 raw=adb('exec-out','cat','/sdcard/mytasks-qa-window.xml')
 return ET.fromstring(raw)
def nodes():
 return [dict(n.attrib) for n in dump().iter('node')]
def tap(label,exact=False):
 candidates=[n for n in nodes() if ((n.get('text')==label or n.get('content-desc')==label) if exact else (label in n.get('text','') or label in n.get('content-desc',''))) and n.get('enabled')=='true']
 if not candidates: raise RuntimeError('No current node matching '+label)
 node=next((n for n in candidates if n.get('clickable')=='true'),candidates[0]);coords=list(map(int,re.findall(r'\d+',node['bounds'])))
 shell('input','tap',(coords[0]+coords[2])//2,(coords[1]+coords[3])//2)
 return node

def capture(name):
 if not re.fullmatch(r'[a-zA-Z0-9_-]+',name):raise ValueError('safe artifact name required')
 EVIDENCE.mkdir(parents=True,exist_ok=True)
 remote('mkdir -p '+REMOTE)
 shell('screencap','-p','/sdcard/mytasks-qa-capture.png')
 adb('pull','/sdcard/mytasks-qa-capture.png',REMOTE+'/'+name+'.png')
 pull(REMOTE+'/'+name+'.png',EVIDENCE/(name+'.png'))
 tree=nodes();(EVIDENCE/(name+'.json')).write_text(json.dumps(tree,indent=2))
 return str(EVIDENCE/(name+'.png'))

def fold(state):
 shell('cmd','device_state','state',state);time.sleep(1.5);shell('input','keyevent','KEYCODE_WAKEUP');shell('wm','dismiss-keyguard')

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('action',choices=['dump','tap','capture','fold','unfold']);p.add_argument('value',nargs='?');a=p.parse_args()
 if a.action=='dump':print(json.dumps(nodes(),indent=2))
 elif a.action=='tap':print(tap(a.value))
 elif a.action=='capture':print(capture(a.value))
 else:fold(0 if a.action=='fold' else 2)

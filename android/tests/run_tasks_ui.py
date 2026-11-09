#!/usr/bin/env python3
"""Owned module checks. SDK compilation is not an Android runtime test.

Run: python3 android/tests/run_tasks_ui.py
Optional: JAVA_HOME, ANDROID_HOME, TMPDIR. Never compiles concurrent Activity code.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ANDROID=Path(__file__).resolve().parents[1]
SRC=ANDROID/'src/com/simon/mytasks'
JAVA=Path(os.environ.get('JAVA_HOME','/opt/data/cache/android-build/jdk-17.0.2'))/'bin'
SDK=Path(os.environ.get('ANDROID_HOME','/opt/data/cache/android-build/sdk'))
SCRATCH=Path(os.environ.get('TMPDIR','/opt/data/cache/scratch'))
SCRATCH.mkdir(parents=True,exist_ok=True)

def run(command):
 print('+ '+' '.join(map(str,command)),flush=True)
 subprocess.run(list(map(str,command)),check=True)

with tempfile.TemporaryDirectory(prefix='tasks-ui-check-',dir=SCRATCH) as work:
 work=Path(work);jvm=work/'jvm';jvm.mkdir()
 run([JAVA/'javac','--release','8','-d',jvm,SRC/'UpdateEmphasis.java',ANDROID/'tests/UpdateEmphasisTest.java'])
 run([JAVA/'java','-cp',jvm,'com.simon.mytasks.UpdateEmphasisTest'])
 run([JAVA/'javac','--release','8','-d',jvm,SRC/'TaskPresentation.java',ANDROID/'tests/TaskPresentationTest.java'])
 run([JAVA/'java','-cp',jvm,'com.simon.mytasks.TaskPresentationTest'])
 run(['python3',ANDROID/'tests/test_tasks_ui_contract.py'])
 # Signature-only seam: not packaged, not used to claim behavior or rendering.
 seams=work/'seams/com/simon/mytasks';seams.mkdir(parents=True)
 (seams/'MainActivity.java').write_text('''package com.simon.mytasks;
 import android.app.Activity;import android.app.AlertDialog;import org.json.*;
 class MainActivity extends Activity {
  WorkspaceUi ui;String filter="Active",projectFilter="",query="",sortOrder="Project",selected="";
  boolean showSearch,online,busy,foreground=true;JSONArray tasks=new JSONArray();
  void navigate(String id){} void projectMenu(){} void sortMenu(){} void settings(){}
  void showTaskActions(JSONObject t){} void quickChange(JSONObject t,String field,Object value){}
  String dueLabel(JSONObject t){return "";} String human(String s){return s;}
  void captureDraft(){} void render(){} void saveWorkspacePreferences(){} void refresh(){}
  android.widget.LinearLayout vertical(){return null;} android.widget.LinearLayout horizontal(){return null;}
  android.widget.TextView heading(String text,int size){return null;}
  void add(android.widget.LinearLayout parent,android.view.View child){}
  AlertDialog.Builder dialogBuilder(){return null;}
 }''')
 (seams/'R.java').write_text('''package com.simon.mytasks;
 final class R {static final class font {static final int inter_variable=1;}
 static final class drawable {static final int ic_more=2,ic_search=3,ic_flag=4;}}''')
 classes=work/'sdk';classes.mkdir()
 sources=[SRC/(n+'.java') for n in ['WorkspaceUi','GlassLayout','GlassDrawable','GlassPixels','CompletionMark','TasksUi','TaskRowView','TaskPresentation','UpdateEmphasis','PresentationPolicy','CompactPolicy','Core']]
 run([JAVA/'javac','--release','8','-cp',SDK/'platforms/android-35/android.jar','-d',classes,*sources,seams/'MainActivity.java',seams/'R.java',ANDROID/'tests/TasksAnchorTest.java'])
 print('Tasks UI: SDK 35 / Java 8 isolated module compilation passed')
 run([JAVA/'java','-cp',str(classes)+os.pathsep+str(SDK/'platforms/android-35/android.jar'),'com.simon.mytasks.TasksAnchorTest'])

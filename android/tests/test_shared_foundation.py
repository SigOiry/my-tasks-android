"""Native color contract; actual constructor extraction, not device rendering."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from test_native_contrast import contrast
from test_tasks_anchor_methods import extract

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'android/src/com/simon/mytasks/WorkspaceUi.java'
ROLES = {'canvas': 'canvas', 'surface': 'surface', 'ink': 'ink',
         'secondary': 'muted', 'border': 'border', 'primary': 'primary',
         'soft': 'soft', 'danger': 'danger', 'separator': 'separator',
         'action': 'action', 'onAction': 'onAction', 'nav': 'nav', 'onNav': 'onNav', 'navMuted': 'navMuted', 'navSelected': 'navSelected'}
XML_ROLES = {'colorAccent': 'primary', 'colorControlActivated': 'primary',
             'colorControlNormal': 'muted', 'colorButtonNormal': 'surface',
             'colorBackground': 'canvas', 'windowBackground': 'canvas',
             'textColorPrimary': 'ink', 'textColorSecondary': 'muted', 'textColorHint': 'muted'}


class SharedFoundationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tokens = json.loads((ROOT / 'design/tokens.json').read_text())['colors']
        source = SOURCE.read_text()
        constructor = extract(source, r'^ WorkspaceUi\(')
        fields = '\n'.join(re.findall(r'^ final int .+;', source, re.M))
        # Only framework seams: the production constructor/color expression runs unchanged.
        harness = '''class Context {Resources getResources(){return new Resources();}}
class Resources {Typeface getFont(int id){return new Typeface();}}
class Typeface {static final int NORMAL=0;static Typeface create(String s,int n){return new Typeface();}}
class R {static class font {static int inter_variable=1;}}
class WorkspaceUi {
final Context context; final Typeface font;
FIELDS
CONSTRUCTOR
static int color(String s){return (int)(0xff000000L|Long.parseLong(s.substring(1),16));}
public static void main(String[] args) throws Exception {
for(boolean dark:new boolean[]{false,true}){WorkspaceUi ui=new WorkspaceUi(new Context(),dark);
for(java.lang.reflect.Field f:WorkspaceUi.class.getDeclaredFields())if(f.getType()==int.class)
System.out.println((dark?"dark":"light")+" "+f.getName()+" "+String.format("#%06X",f.getInt(ui)&0xffffff));}
}}
'''.replace('FIELDS', fields).replace('CONSTRUCTOR', constructor)
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            raise RuntimeError('Set JAVA_HOME or put javac on PATH')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'WorkspaceUi.java'
            path.write_text(harness)
            policy = ROOT / 'android/src/com/simon/mytasks/PresentationPolicy.java'
            # The constructor calls only the actual unread-color method.
            unread = extract(policy.read_text(), r'^ public static int unreadColor\(')
            (Path(directory) / 'PresentationPolicy.java').write_text('class PresentationPolicy {' + unread + '}')
            compile_result = subprocess.run([javac, '--release', '8', '-d', directory, str(path), str(Path(directory) / 'PresentationPolicy.java')], capture_output=True, text=True)
            if compile_result.returncode:
                raise RuntimeError(compile_result.stderr)
            result = subprocess.run([str(Path(javac).resolve().parent / 'java'), '-cp', directory, 'WorkspaceUi'], check=True, capture_output=True, text=True)
        cls.actual = {'light': {}, 'dark': {}}
        for row in result.stdout.splitlines():
            mode, role, value = row.split()
            cls.actual[mode][role] = value

    def test_actual_native_palette_matches_shared_roles(self):
        for mode, actual in self.actual.items():
            for native, shared in ROLES.items():
                with self.subTest(mode=mode, role=native):
                    self.assertEqual(actual.get(native), self.tokens[mode][shared].upper())

    def test_opaque_navigation_roles_meet_text_contrast(self):
        for mode, colors in self.actual.items():
            for foreground, background in [('onNav','nav'),('navMuted','nav'),('onNav','navSelected')]:
                with self.subTest(mode=mode, foreground=foreground, background=background):
                    self.assertGreaterEqual(contrast(colors[foreground], colors[background]), 4.5)

    def test_xml_platform_theme_matches_shared_roles(self):
        themes = {s.attrib['name']: {i.attrib['name']: i.text or '' for i in s}
                  for s in ET.parse(ROOT / 'android/res/values/styles.xml').getroot()}
        for mode in self.tokens:
            for native, shared in XML_ROLES.items():
                with self.subTest(mode=mode, role=native):
                    self.assertEqual(themes['MyTasks.' + mode.title()]['android:' + native].upper(), self.tokens[mode][shared].upper())

    def test_filled_actions_use_action_pair_not_text_accent(self):
        self.assertIn('tone(b,action,0,onAction)', extract(SOURCE.read_text(), r'^ void primary\('))
        conversation = (ROOT / 'android/src/com/simon/mytasks/ConversationsUi.java').read_text()
        self.assertIn('a.primary("New conversation",', conversation)
        self.assertIn('touch(a.ui.action,0,24)', conversation)
        self.assertEqual(conversation.count('ColorStateList.valueOf(a.ui.onAction)'), 1)
        for mode, colors in self.actual.items():
            with self.subTest(mode=mode):
                self.assertGreaterEqual(contrast(colors.get('onAction', '#FFFFFF'), colors.get('action', '#FFFFFF')), 4.5)

    def test_actual_task_tints_keep_reading_and_category_contrast(self):
        jar = ROOT / 'android/build/test-lib/json.jar'
        self.assertTrue(jar.is_file(), 'Run android/test.sh to provision pinned host JSON dependency')
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            raise RuntimeError('Set JAVA_HOME or put javac on PATH')
        source = '''package com.simon.mytasks;
import org.json.JSONObject;
class FoundationTints {
public static void main(String[] args) throws Exception {
for(boolean dark:new boolean[]{false,true}){
int surface=(int)Long.parseLong(args[dark?1:0].substring(1),16)|0xff000000;
java.util.Set<Integer> seen=new java.util.HashSet<>();
for(int i=0;i<100&&seen.size()<6;i++){
JSONObject task=new JSONObject().put("project","synthetic-project-"+i);
int slot=PresentationPolicy.projectSlot(task);if(!seen.add(slot))continue;
for(String mode:new String[]{"Project","Importance","Neutral"})for(String priority:new String[]{"high","medium","low"}){
task.put("priority",priority);int tint=PresentationPolicy.rowTint(task,mode,dark,surface);
int cue=PresentationPolicy.taskCueColor(PresentationPolicy.taskColor(task,mode,dark,PresentationPolicy.rowSecondary(dark)),dark);
System.out.printf("%s %s %s %s #%06X #%06X #%06X #%06X%n",dark?"dark":"light",slot,mode,priority,tint&0xffffff,PresentationPolicy.rowSecondary(dark)&0xffffff,PresentationPolicy.rowUnreadColor(dark)&0xffffff,cue&0xffffff);
}}
if(seen.size()!=6)throw new AssertionError("All six project hues required");
}}}
'''
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'FoundationTints.java'
            path.write_text(source)
            sources = [str(ROOT / ('android/src/com/simon/mytasks/' + name + '.java')) for name in ('PresentationPolicy', 'Core')]
            result = subprocess.run([javac, '--release', '8', '-cp', str(jar), '-d', directory, str(path), *sources], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([str(Path(javac).resolve().parent / 'java'), '-cp', directory + os.pathsep + str(jar), 'com.simon.mytasks.FoundationTints', self.actual['light']['surface'], self.actual['dark']['surface']], capture_output=True, text=True, check=True)
        rows = result.stdout.splitlines()
        self.assertEqual(len(rows), 108)
        for row in rows:
            mode, slot, color_mode, priority, tint, secondary, unread, cue = row.split()
            with self.subTest(theme=mode, hue=slot, color_mode=color_mode, priority=priority):
                for foreground in (self.actual[mode]['ink'], secondary, unread):
                    self.assertGreaterEqual(contrast(foreground, tint), 4.5)
                self.assertGreaterEqual(contrast(cue, tint), 3)

    def test_reading_selected_disabled_and_focus_contrast(self):
        for mode, colors in self.actual.items():
            for foreground in ('ink', 'secondary', 'primary', 'danger'):
                for background in ('canvas', 'surface', 'soft'):
                    with self.subTest(mode=mode, fg=foreground, bg=background):
                        self.assertGreaterEqual(contrast(colors[foreground], colors[background]), 4.5)
            self.assertGreaterEqual(contrast(colors['focus'], colors['surface']), 3)
            self.assertGreaterEqual(contrast(colors['onPrimary'], colors['primary']), 3)


if __name__ == '__main__':
    unittest.main()

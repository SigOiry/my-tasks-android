"""Actual extracted motion methods; deterministic geometry/animator seams, no device proof."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest
from test_tasks_anchor_methods import extract

ANDROID = Path(__file__).resolve().parents[1]

class NativeMotionMethodsTest(unittest.TestCase):
    def run_harness(self, methods, body, task_methods='', activity_methods=''):
        template = (ANDROID / 'tests/NativeMotionMethodsHarness.java').read_text()
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            self.fail('Set JAVA_HOME or put javac on PATH')
        if 'deferRefresh()' in task_methods:
            task_methods += '\n' + extract((ANDROID / 'src/com/simon/mytasks/TasksUi.java').read_text(), r'^ private boolean deferRefresh\(')
        template = template.replace('// TASK_METHODS', task_methods).replace('// ACTIVITY_METHODS', activity_methods)
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / 'NativeMotionMethodsHarness.java'
            file.write_text(template.replace('// METHODS', methods).replace('// ASSERTIONS', body))
            subprocess.run([javac, '--release', '8', '-d', tmp, str(file)], check=True)
            result = subprocess.run([str(Path(javac).resolve().parent / 'java'), '-cp', tmp, 'NativeMotionMethodsHarness'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_capsule_moves_from_previous_render_position(self):
        source = (ANDROID / 'src/com/simon/mytasks/WorkspaceUi.java').read_text()
        methods = extract(source, r'^ void placeScopeCapsule\(')
        self.run_harness(methods, '''
 WorkspaceUi ui=new WorkspaceUi(); ViewGroup scopes=new ViewGroup();
 scopes.width=306; scopes.children=new View[]{new View(3,3,100,48),new View(103,3,100,48),new View(203,3,100,48)};
 View capsule=new View();ui.placeScopeCapsule(capsule,scopes,2,3f/306);
 check(capsule.x==3,"rebuilt capsule starts at old slot, not new slot");
 check(capsule.anim.targetX==203&&capsule.anim.duration==210,"capsule crosses to All over 210ms");
 check(capsule.params.width==100&&capsule.params.height==48,"measured target geometry");
 ui.placeScopeCapsule(capsule,scopes,0,153f/306);
 check(capsule.x==153&&capsule.anim.targetX==3,"interrupted motion resumes current position backwards");
 ui.enabled=false; capsule.anim.started=false; ui.placeScopeCapsule(capsule,scopes,1,0);
 check(capsule.x==103&&!capsule.anim.started,"reduced motion snaps to selected slot");
 ui.enabled=true;capsule.anim.started=false;ui.placeScopeCapsule(capsule,scopes,1,Float.NaN);
 check(capsule.x==103&&!capsule.anim.started,"first build static");
 ''')

    def test_navigation_reverses_horizontal_direction_without_insertion_drift(self):
        source = (ANDROID / 'src/com/simon/mytasks/WorkspaceUi.java').read_text()
        methods = '\n'.join(extract(source, pattern) for pattern in [r'^ void navigatePage\(', r'^ void enter\('])
        self.run_harness(methods, '''
 WorkspaceUi ui=new WorkspaceUi();View page=new View();ui.navigatePage(page,false);
 check(page.x==24&&page.y==0&&page.anim.targetX==0&&page.anim.duration==280,"forward page enters horizontally over 280ms");
 ui.navigatePage(page,true);check(page.x==-24&&page.y==0,"return reverses direction");
 ui.enabled=false;page.anim.started=false;ui.navigatePage(page,false);
 check(page.x==0&&page.y==0&&page.alpha==1&&!page.anim.started,"reduced motion static");
 ui.enabled=true;ui.enter(page,200);check(page.y==6&&page.anim.targetY==0&&page.anim.duration==200,"message insertion retains Y behavior");
 ''')

    def test_actual_task_navigation_uses_signed_motion_after_confirmation(self):
        source = (ANDROID / 'src/com/simon/mytasks/WorkspaceUi.java').read_text()
        methods = '\n'.join(extract(source, p) for p in [r'^ void enter\(', r'^ void navigatePage\('])
        activity = extract((ANDROID / 'src/com/simon/mytasks/MainActivity.java').read_text(), r'^ void navigate\(')
        self.run_harness(methods, '''
 Activity a=new Activity();a.unsaved=true;a.navigate("task1");
 check(!a.rendered&&!a.detailPane.anim.started,"no navigation before discard confirmation");
 a.confirmed.run();check(a.detailPane.x==24&&a.detailPane.y==0,"actual details navigation horizontal forward");
 a.unsaved=false;a.navigate("");check(a.listPane.x==-24&&a.listPane.y==0,"actual return navigation horizontal reverse");
 ''', activity_methods=activity)

    def test_rendered_chat_and_task_returns_use_same_page_motion(self):
        source = (ANDROID / 'src/com/simon/mytasks/WorkspaceUi.java').read_text()
        methods = extract(source, r'^ void navigatePage\(')
        activity = extract((ANDROID / 'src/com/simon/mytasks/MainActivity.java').read_text(), r'^ void renderPage\(')
        self.run_harness(methods, '''
 Activity a=new Activity();a.destination="Discussions";a.renderPage(false);
 check(a.rendered&&a.root.x==24,"discussion forward renders and transitions");
 a.renderPage(true);check(a.root.x==-24,"discussion return reverses");
 a.destination="Tasks";a.selected="task1";a.renderPage(true);check(a.detailPane.x==-24,"chat to task details reverses");
 a.selected="";a.renderPage(true);check(a.listPane.x==-24,"task list return reverses");
 ''', activity_methods=activity)

    def test_idle_reorder_animates_retained_tags_and_respects_safety_gates(self):
        source = (ANDROID / 'src/com/simon/mytasks/TasksUi.java').read_text()
        methods = '\n'.join(extract(source, p) for p in [r'^ private java.util.Map<String,Float> snapshotRowPositions\(', r'^ private void applyRowPositions\('])
        self.run_harness('', '''
 TasksUi tasks=new TasksUi();tasks.list=new ListView();TaskRowView one=new TaskRowView("one",100),two=new TaskRowView("two",180);
 tasks.list.children=new View[]{one,two};java.util.Map<String,Float> before=tasks.snapshotRowPositions();
 one.top=180;two.top=100;tasks.applyRowPositions(before);
 check(one.y==-80&&two.y==80,"retained identities start at old visual positions");
 check(one.anim.targetY==0&&one.anim.duration==200,"position transition is 200ms to final layout");
 tasks.touching=true;check(tasks.snapshotRowPositions().isEmpty(),"touch gates snapshot");
 one.anim.started=false;tasks.applyRowPositions(before);check(!one.anim.started,"interaction beginning after snapshot suppresses motion");
 tasks.touching=false;tasks.scrolling=true;check(tasks.snapshotRowPositions().isEmpty(),"scroll gates snapshot");tasks.scrolling=false;
 tasks.a.editing=true;check(tasks.snapshotRowPositions().isEmpty(),"editing gates snapshot");tasks.a.editing=false;
 tasks.a.dialogClosers.add(new Object());check(tasks.snapshotRowPositions().isEmpty(),"menu gates snapshot");tasks.a.dialogClosers.clear();
 tasks.paused=true;check(tasks.snapshotRowPositions().isEmpty(),"paused gates snapshot");tasks.paused=false;
 tasks.a.ui.enabled=false;check(tasks.snapshotRowPositions().isEmpty(),"reduced motion skips reorder");
 tasks.a.ui.enabled=true;one.top=900;one.anim.started=false;tasks.applyRowPositions(before);check(!one.anim.started,"large offscreen displacement is not animated");
 ''', task_methods=methods)

    def test_interaction_and_recycle_reset_row_translation(self):
        source = (ANDROID / 'src/com/simon/mytasks/TasksUi.java').read_text()
        methods = '\n'.join(extract(source, p) for p in [r'^ private void stopRowMotion\(', r'^ private void resetRowMotion\('])
        self.run_harness('', '''
 TasksUi tasks=new TasksUi();tasks.list=new ListView();TaskRowView row=new TaskRowView("one",100);row.y=70;tasks.list.children=new View[]{row};
 tasks.stopRowMotion();check(row.y==0&&row.anim.cancelled,"interaction immediately cancels visual offsets");
 row.y=-30;row.anim.cancelled=false;tasks.resetRowMotion(row);check(row.y==0&&row.anim.cancelled,"recycling never carries old identity translation");
 ''', task_methods=methods)

    def test_pause_finishes_page_offsets(self):
        source = (ANDROID / 'src/com/simon/mytasks/WorkspaceUi.java').read_text()
        self.run_harness(extract(source, r'^ void finishPageMotion\('), '''
 WorkspaceUi ui=new WorkspaceUi();View page=new View();page.x=12;page.y=4;page.alpha=.8f;ui.finishPageMotion(page);
 check(page.x==0&&page.y==0&&page.alpha==1&&page.anim.cancelled,"pause never leaves partially translated content");ui.finishPageMotion(null);
 ''')

    def test_selection_carries_live_position_across_render(self):
        source = (ANDROID / 'src/com/simon/mytasks/TasksUi.java').read_text()
        method = extract(source, r'^ void selectScope\(')
        self.run_harness('', '''
 TasksUi tasks=new TasksUi();tasks.scopeTrack=new ViewGroup();tasks.scopeTrack.width=300;
 tasks.scopeCapsule=new View();tasks.scopeCapsule.x=145;tasks.selectScope("All");
 check(Math.abs(tasks.scopeOrigin-145f/300)<.0001,"capture live in-flight capsule before rebuilding");
 check(tasks.a.filter.equals("All")&&tasks.a.rendered&&tasks.reset,"selection updates and renders");
 ''', task_methods=method)

if __name__ == '__main__':
    unittest.main()

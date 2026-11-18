"""M5 source wiring contracts complement the actual-method Java harness, not native QA."""
from pathlib import Path
import unittest
from run_m5_native import method
SRC=Path(__file__).resolve().parents[1]/'src/com/simon/mytasks'
class M5Wiring(unittest.TestCase):
    def test_navigation_and_creation_are_in_flow(self):
        nav=method('MainActivity.java','buildPrimaryTabs')
        self.assertIn('root.addView(navigationSurface,new LinearLayout.LayoutParams(-1,-2))',nav)
        self.assertNotIn('rootFrame.addView(navigationSurface',nav)
        self.assertNotIn('glassSurface',nav)
        tasks=method('TasksUi.java','build')
        self.assertIn('parent.addView(create,new LinearLayout.LayoutParams(-1,-2))',tasks)
        self.assertIn('()->a.navigate("NEW")',tasks)
        self.assertNotIn('addFooterView',tasks)
        discussions=method('ConversationsUi.java','buildList')
        self.assertIn('pane.addView(create,new LinearLayout.LayoutParams(-1,-2))',discussions)
        self.assertIn('()->c.create(null,',discussions)
        self.assertNotIn('Gravity.BOTTOM|Gravity.END',discussions)
    def test_task_capture_restore_lifecycle_is_presentation_only(self):
        render=method('MainActivity.java','render')
        self.assertLess(render.index('captureTaskPresentation()'),render.index('title=description=notes=due=project=null'))
        self.assertIn('saveTaskPresentation(out)',method('MainActivity.java','onSaveInstanceState'))
        self.assertIn('loadTaskPresentation(saved)',method('MainActivity.java','onCreate'))
        self.assertIn('captureTaskPresentation()',method('MainActivity.java','captureDraft'))
        binding=method('MainActivity.java','buildTaskEditor')
        self.assertLess(binding.index('notes=field('),binding.index('restoreTaskPresentation()'))
        restore=method('MainActivity.java','restoreTaskPresentation')
        self.assertNotIn('setText(',restore)
        for fence in ('title!=target','detailScroll!=outer','!selected.equals(targetTask)','!alive(owner)','ticket!=taskPresentationGeneration'):
            self.assertIn(fence,restore)
    def test_discussion_thread_and_list_bindings_are_owned(self):
        self.assertIn('restoreComposerPresentation()',method('ConversationsUi.java','buildThread'))
        self.assertIn('restoreListPresentation()',method('ConversationsUi.java','updateRows'))
        for name in ('captureComposerPresentation','restoreComposerPresentation','captureListPresentation','restoreListPresentation'):
            source=method('ConversationsUi.java',name)
            self.assertIn('c.listener!=',source)
            self.assertIn('!c.valid()',source)
        self.assertNotIn('setText(',method('ConversationsUi.java','restoreComposerPresentation'))
        self.assertIn('saved.optInt("end")',method('ConversationsUi.java','restoreComposerPresentation'))
        # Search/filter anchor is bounded to one current view, not one growing entry per keystroke.
        self.assertIn('String key="list_presentation"',method('ConversationsUi.java','listViewport'))
    def test_actual_bounds_dispatch_and_insets_have_current_view_fences(self):
        read=method('MainActivity.java','readShellPolicy')
        for token in ('host.getWidth()','host.getHeight()','getCurrentWindowMetrics()','getLocationInWindow(origin)','getConfiguration().fontScale','composerOcclusions','shellInsetLeft'):
            self.assertIn(token,read)
        render=method('MainActivity.java','render')
        self.assertIn('if(v!=root)return insets;',render)
        self.assertIn('if(v==rootFrame&&',render)
        self.assertIn('if(railNavigation)buildRail(shell)',render)
        self.assertIn('if(wide||selected.isEmpty())buildList()',render)
        self.assertIn('else if(root!=null)root.requestApplyInsets()',method('MainActivity.java','onResume'))
    def test_email_keeps_its_in_place_geometry_and_back_is_not_bridged(self):
        config=method('MainActivity.java','onConfigurationChanged')
        self.assertIn('if(emailUi!=null&&!emailUi.needsStyleRebuild(config))',config)
        self.assertLess(config.index('emailUi.fitPane();root.requestApplyInsets();return;'),config.index('captureDraft();'))
        geometry=method('MainActivity.java','updateShellGeometry')
        self.assertIn('host!=rootFrame||emailUi!=null',geometry)
        self.assertIn('rootFrame!=target',geometry)
        self.assertIn('!alive(owner)||emailUi!=null',geometry)
        source=(SRC/'MainActivity.java').read_text()
        self.assertNotIn('OnBackInvoked',source)
if __name__=='__main__':unittest.main()

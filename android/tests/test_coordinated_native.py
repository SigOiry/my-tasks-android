"""Native integration contracts, supplementary to executable JVM policy tests.
No runtime/render claims. Prevent a correct policy remaining unwired in the UI.
"""
from pathlib import Path
import re, unittest
SRC=Path(__file__).resolve().parents[1]/'src/com/simon/mytasks'
class CoordinatedNativeTest(unittest.TestCase):
 def text(self,n):return (SRC/(n+'.java')).read_text()
 def test_saved_preferences_and_process_state(self):
  s=self.text('MainActivity')
  for key in ['ui_sort_reverse','ui_color_mode']:
   self.assertIn('getBoolean("'+key+'"' if key.endswith('reverse') else 'getString("'+key+'"',s)
   self.assertIn('putBoolean("'+key+'"' if key.endswith('reverse') else 'putString("'+key+'"',s)
  self.assertIn('PresentationPolicy.savedSort(store.prefs.getString("ui_sort",null))',s)
  for key in ['sortReverse','colorMode']:
   self.assertIn('saved.get',s);self.assertIn('"'+key+'"',s)
  self.assertIn('PresentationPolicy.sortModes()',s)
  self.assertIn('Why this order',s);self.assertIn('PresentationPolicy.whySmart()',s)
 def test_policy_used_before_all_entries_and_interaction_deferral(self):
  s=self.text('TasksUi')
  self.assertIn('a.sortReverse,today,java.time.ZoneId.systemDefault()',s)
  self.assertLess(s.index('Collections.sort(shown'),s.index('for(JSONObject task:shown)'))
  self.assertIn('deferRefresh',s);self.assertIn('SCROLL_STATE_IDLE',s);self.assertIn('ACTION_CANCEL',s)
  self.assertNotIn('else{draft=null;render();}',self.text('MainActivity'),'quiet detail updates must not rebuild/reset an interacting master list')
  self.assertIn('a.colorMenu()',s)
  self.assertIn('"Waiting"',s);self.assertIn('"Inactive"',s)
 def test_native_motion_uses_real_request_state_and_lifecycle(self):
  s=self.text('ConversationsUi');ui=self.text('WorkspaceUi');main=self.text('MainActivity')
  self.assertIn('new PendingDots(a,pending.optString("request_id"))',s)
  self.assertIn('pending.optString("status").equals("running")',s)
  self.assertIn('NativeMotionPolicy.duration("insertion")',s)
  self.assertIn('NativeMotionPolicy.duration("tab")',main)
  self.assertIn('void enter(',ui)
  dots=self.text('PendingDots')
  for gate in ['NativeMotionPolicy.pendingWave','a.foreground','getGlobalVisibleRect','ui.motionEnabled()','onDetachedFromWindow','onWindowVisibilityChanged','removeCallbacks']:
   self.assertIn(gate,dots)
 def test_category_surface_and_external_halo_do_not_recolor_reading_text(self):
  # Latest owner request supersedes the tiny accent/fixed blue halo contract.
  s=self.text('TaskRowView')
  self.assertIn('PresentationPolicy.taskColor(value,a.colorMode',s)
  self.assertIn('PresentationPolicy.rowTint(value,a.colorMode',s)
  self.assertNotIn('canvas.drawCircle',s)
  self.assertIn('halo.setColor(category)',s)
  self.assertIn('new ForegroundColorSpan(PresentationPolicy.rowUnreadColor',s)
  self.assertNotRegex(s,r'title.setTextColor\(.*category')
if __name__=='__main__':unittest.main()

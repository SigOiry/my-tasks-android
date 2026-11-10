"""Source contracts only, not Android runtime or rendering tests."""
from pathlib import Path
import re
import unittest
ROOT=Path(__file__).resolve().parents[1]/'src/com/simon/mytasks'

class TasksUiContractTest(unittest.TestCase):
 def source(self,name):
  p=ROOT/(name+'.java')
  self.assertTrue(p.exists(),name+' module exists')
  return p.read_text()
 def test_visual_tokens_are_semantic_and_compatible(self):
  s=self.source('WorkspaceUi')
  for token in ['SPACE_UNIT','PAGE_GUTTER','ROW_MIN','ACTION_MIN','TYPE_TITLE','TYPE_CONTEXT','RADIUS_CONTROL','ELEVATION_FLOATING','MOTION_FEEDBACK','MOTION_NAVIGATION']:
   self.assertRegex(s,r'\b'+token+r'\b')
  for api in ['glass','button','selected','input','motionEnabled','text','touch']:
   self.assertRegex(s,r'\b'+api+r'\(')
  self.assertIn('R.font.inter_variable',s)
  self.assertTrue((ROOT.parents[3]/'res/raw/inter_license.txt').exists())
 def test_opaque_palette_text_contrast(self):
  s=self.source('WorkspaceUi')
  palette={name:(dark,light) for name,dark,light in re.findall(r'(\w+)=color\(dark\?"(#[0-9a-fA-F]{6})":"(#[0-9a-fA-F]{6})"\)',s)}
  def luminance(color):
   rgb=[int(color[i:i+2],16)/255 for i in (1,3,5)]
   linear=[c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4 for c in rgb]
   return sum(c*w for c,w in zip(linear,[0.2126,0.7152,0.0722]))
  for mode in [0,1]:
   for foreground,background in [('ink','surface'),('secondary','surface'),('primary','surface'),('primary','soft'),('onPrimary','primary')]:
    x,y=sorted([luminance(palette[foreground][mode]),luminance(palette[background][mode])])
    self.assertGreaterEqual((y+0.05)/(x+0.05),4.5,(mode,foreground,background))
 def test_tasks_virtualizes_and_retains_id_anchor(self):
  s=self.source('TasksUi')
  for api in ['build','refresh','capture','pause','clear']:
   self.assertRegex(s,r'void '+api+r'\(')
  for required in ['ListView','BaseAdapter','setSelectionFromTop','anchorId','anchorTop','PresentationPolicy.matches','PresentationPolicy.compare','resetAnchor']:
   self.assertIn(required,s)
  self.assertNotRegex(s,r'a\.(listScroll|listItems)')
  for callback in ['navigate','projectMenu','sortMenu','settings']:
   self.assertIn('a.'+callback+'(',s)
 def test_process_state_and_floating_nav_clearance(self):
  s=self.source('TasksUi')
  for api in ['save','restore','acceptOwnChange']:
   self.assertRegex(s,r'void '+api+r'\(')
  self.assertIn('Bundle',s)
  self.assertIn('emphasis.observe(',s)
  self.assertIn('NAVIGATION_CLEARANCE=104',s)
  self.assertIn('setClipToPadding(false)',s)
  self.assertIn('ui.dp(NAVIGATION_CLEARANCE)',s)
 def test_row_has_distinct_controls_and_static_update_marker(self):
  s=self.source('TaskRowView')
  for required in ['CheckBox','showTaskActions','quickChange','New update','setMaxLines(2)','LONG_PRESS','onDetachedFromWindow','UpdateEmphasis.CYCLE_MS','UpdateEmphasis.CYCLES','setRepeatCount','stopEmphasis']:
   self.assertIn(required,s)
  self.assertNotRegex(s,r'(?<![\w.])(?:this\.|title\.|context\.|row\.)?setAlpha\(', 'halo must not fade row text')
  self.assertIn('ownsEmphasis',s,'a non-animating recycled row must not release another row slot')
  self.assertNotIn('RenderEffect',s,'do not blur text children')

 def test_scope_selection_is_visible_checked_and_saved(self):
  s=self.source('TasksUi')
  self.assertIn('TaskPresentation.visibleScopes(a.filter)',s)
  self.assertIn('setSingleChoiceItems(labels,TaskPresentation.selectedScope(a.filter)',s)
  self.assertIn('a.filter=labels[index]',s)
  self.assertIn('a.saveWorkspacePreferences()',s)
  self.assertIn('a.filter=scope',s)
 def test_unread_presentation_is_used_and_resets_recycled_context(self):
  s=self.source('TaskRowView')
  for required in ['TaskPresentation.context(detail,unread)','TaskPresentation.wrapContext(unread)',
                   'context.setSingleLine(!wrap)','context.setMaxLines(wrap?Integer.MAX_VALUE:1)',
                   'context.setEllipsize(wrap?null:TextUtils.TruncateAt.END)']:
   self.assertIn(required,s)

 def test_priority_policy_drives_actual_icons_and_colors(self):
  s=self.source('TaskRowView')
  for required in ['int flag=TaskPresentation.flag(priority)',
                   'if(flag==TaskPresentation.FLAG_FILLED)overflow.setImageDrawable(filledFlag())',
                   'flag==TaskPresentation.FLAG_OUTLINE?R.drawable.ic_flag:R.drawable.ic_more',
                   '"Neutral".equals(a.colorMode)?ui.secondary:cue']:
   self.assertIn(required,s)
 def test_compact_rows_and_accessible_scope_targets(self):
  row=self.source('TaskRowView');tasks=self.source('TasksUi')
  self.assertIn('setMinimumHeight(ui.dp(72))',row)
  self.assertGreaterEqual(row.count('new LayoutParams(ui.dp(WorkspaceUi.ACTION_MIN),ui.dp(WorkspaceUi.ACTION_MIN))'),2)
  self.assertIn('tab.setMinHeight(ui.dp(48))',tasks)
 def test_clear_scope_persists_like_menu_and_tabs(self):
  s=self.source('TasksUi')
  self.assertIn('a.filter="Active";resetAnchor();a.saveWorkspacePreferences();a.render();',s)

if __name__=='__main__':unittest.main(verbosity=2)

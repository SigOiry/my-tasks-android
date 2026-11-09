"""Window bounds must be set before first native frame, not only OnShow."""
from pathlib import Path
import re,unittest
SRC=Path(__file__).resolve().parents[1]/'src/com/simon/mytasks'
class WindowPreflight(unittest.TestCase):
 def test_update_sheet_preflights_before_window_attach(self):
  source=(SRC/'UpdateSheet.java').read_text();match=re.search(r'void show\(\)\{([^\n]+)\}',source)
  if match is None:self.fail('production show method required')
  show=match.group(1)
  self.assertIn('NativeSheets.fit(',show)
  self.assertLess(show.index('NativeSheets.fit('),show.index('dialog.show()'))
 def test_menu_preflights_final_anchor_before_window_attach(self):
  source=(SRC/'NativeSheets.java').read_text()
  self.assertRegex(source,r'positionMenu\(a,dialog,material,anchor\);dialog.show\(\);return dialog;')
if __name__=='__main__':unittest.main()

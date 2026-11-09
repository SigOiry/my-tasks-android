"""Validate the layout resource topology used by the Activity, not Java text.

The compact resource must not reserve any fixed-height chrome outside its
single vertical scroll. This guards the short-window starvation regression.
Android measurement, touch and IME behavior still require a device.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[1] / 'res'
A = '{http://schemas.android.com/apk/res/android}'


class ListLayoutTest(unittest.TestCase):
    def layout(self, qualifier):
        path = RES / qualifier / 'task_list.xml'
        self.assertTrue(path.exists(), 'Missing scroll-safe task list resource')
        root = ET.parse(path).getroot()
        nodes = {e.get(A + 'id', '').split('/')[-1]: e for e in root.iter()}
        return root, nodes

    def test_compact_chrome_controls_and_rows_share_one_scroll(self):
        root, nodes = self.layout('layout')
        self.assertEqual(root.tag, 'ScrollView')
        self.assertEqual(root.get(A + 'id'), '@+id/list_scroll')
        self.assertEqual(root.get(A + 'layout_height'), 'match_parent')
        self.assertEqual(len(root), 1)
        content = root[0]
        self.assertEqual(content.get(A + 'orientation'), 'vertical')
        self.assertEqual(content.get(A + 'layout_height'), 'wrap_content')
        self.assertEqual([e.get(A + 'id') for e in content],
                         ['@+id/list_header', '@+id/list_controls', '@+id/list_rows'])
        self.assertEqual(sum(e.tag == 'ScrollView' for e in root.iter()), 1)
        for key in ('list_header', 'list_controls', 'list_rows'):
            self.assertEqual(nodes[key].get(A + 'layout_height'), 'wrap_content')
            self.assertIsNone(nodes[key].get(A + 'layout_weight'))

    def test_expanded_controls_stay_fixed_above_independent_rows_scroll(self):
        root, nodes = self.layout('layout-w720dp')
        self.assertEqual(root.tag, 'LinearLayout')
        self.assertEqual(root.get(A + 'orientation'), 'vertical')
        self.assertEqual(list(root), [nodes['list_controls'], nodes['list_scroll']])
        self.assertEqual(nodes['list_controls'].get(A + 'layout_height'), 'wrap_content')
        self.assertEqual(nodes['list_scroll'].get(A + 'layout_weight'), '1')
        self.assertEqual(list(nodes['list_scroll']), [nodes['list_rows']])
        self.assertEqual(sum(e.tag == 'ScrollView' for e in root.iter()), 1)


if __name__ == '__main__':
    unittest.main()

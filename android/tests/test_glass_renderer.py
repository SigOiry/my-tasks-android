"""Executable tests of the production CPU kernel, no Android mocks/dependencies."""
import pathlib
import subprocess
import tempfile
import unittest
import os

ROOT = pathlib.Path(__file__).resolve().parents[1]
JDK = pathlib.Path(os.environ.get('JAVA_HOME', '/opt/data/cache/android-build/jdk-17.0.2')) / 'bin'

class GlassKernelTests(unittest.TestCase):
    def test_real_blur_spreads_backdrop_and_bounds_capture(self):
        source = ROOT / 'src/com/simon/mytasks/GlassPixels.java'
        self.assertTrue(source.exists(), 'Production bounded backdrop kernel must exist')
        harness = '''package com.simon.mytasks;
public class GlassPixelsTest {
 public static void main(String[] args) {
  int[] p = {0xff000000,0xff000000,0xffffffff,0xff000000,0xff000000};
  int[] scratch = new int[5];
  GlassPixels.blur(p,scratch,5,1,1);
  if ((p[1]&255) != 85 || (p[2]&255) != 85 || (p[3]&255) != 85)
   throw new AssertionError("real neighboring pixels must diffuse");
  if (p[0] != 0xff000000 || p[4] != 0xff000000)
   throw new AssertionError("blur must remain local");
  int[] constant = new int[9]; java.util.Arrays.fill(constant,0xff123456);
  GlassPixels.blur(constant,new int[9],3,3,3);
  for(int c:constant) if(c!=0xff123456) throw new AssertionError("constant edge preservation");
  if(GlassPixels.sampleScale(10000,10000) < 40)
   throw new AssertionError("large surfaces need bounded allocations");
  if(GlassPixels.sampleScale(390,64) != 6)
   throw new AssertionError("normal capsules use sixfold downsampling");
  System.out.println("GlassPixels: blur diffusion, local edges, constant preservation, bounded allocation PASS");
 }
}'''
        with tempfile.TemporaryDirectory() as d:
            harness_path = pathlib.Path(d)/'GlassPixelsTest.java'
            harness_path.write_text(harness)
            subprocess.run([str(JDK/'javac'), '--release', '8', '-d', d, str(source), str(harness_path)], check=True)
            subprocess.run([str(JDK/'java'), '-cp', d, 'com.simon.mytasks.GlassPixelsTest'], check=True)

class GlassIntegrationContractTests(unittest.TestCase):
    def test_surface_is_frame_layout_with_excluded_capture_and_sharp_children(self):
        path = ROOT / 'src/com/simon/mytasks/GlassLayout.java'
        self.assertTrue(path.exists(), 'Native glass container must exist')
        s = path.read_text()
        self.assertIn('extends FrameLayout', s)
        self.assertIn('if (captureDepth > 0) return;', s)
        self.assertIn('root.draw(captureCanvas)', s)
        self.assertIn('finally { captureDepth--; }', s)
        self.assertIn('super.draw(canvas)', s)
        self.assertNotIn('setRenderEffect', s)
        self.assertIn('getGlobalVisibleRect', s)
        self.assertIn('isHighContrastTextEnabled', s)
        self.assertIn('accessibility_reduce_transparency', s)
        self.assertIn('getWindowVisibility()', s)
        self.assertIn('!hasWindowFocus()', s)
        self.assertIn('removeOnPreDrawListener', s)
        self.assertIn('removeOnScrollChangedListener', s)

    def test_shared_palette_factory_and_capsule_selection(self):
        s = (ROOT / 'src/com/simon/mytasks/WorkspaceUi.java').read_text()
        self.assertIn('GlassLayout glassSurface()', s)
        self.assertIn('#F5F5F7', s)
        self.assertIn('#0D0F14', s)
        self.assertIn('TYPE_HEADING=30', s)
        self.assertNotIn('selected?primary:0', s)
        self.assertIn('Drawable glass()', s)
        self.assertIn('#0068E5', s)
        self.assertIn('b.setImageTintList(ColorStateList.valueOf(ink))', s)
        self.assertIn('Typeface.create(font,500,false)', s)
        self.assertIn('(soft&0x00ffffff)|0x99000000', s)

if __name__ == '__main__':
    unittest.main()

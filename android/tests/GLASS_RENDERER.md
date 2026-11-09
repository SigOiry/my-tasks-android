# Glass renderer integration and verification

## Integration contract

`WorkspaceUi.glassSurface()` returns **GlassLayout extends FrameLayout**, not LinearLayout. Existing WorkspaceUi helper signatures remain intact. Example inside package `com.simon.mytasks`:

```java
GlassLayout capsule = ui.glassSurface();
capsule.setCornerRadiusDp(26);
LinearLayout row = new LinearLayout(context);
row.setOrientation(LinearLayout.HORIZONTAL);
row.addView(tasksButton);
row.addView(discussionsButton);
capsule.addView(row, new FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
rootOverlay.addView(capsule, bottomAlignedFrameParams);
```

- Overlay capsules on content which extends beneath them; reserve bottom list padding for reachability, not an opaque backing strip.
- Do not replace the capsule background with `ui.glass()`; that retained Drawable helper is an explicitly opaque compatibility fallback, not a backdrop renderer.
- Do not wrap ordinary list content in GlassLayout. Every glass subtree, including its children, is omitted from the root snapshot to avoid feedback and label blur.
- Use stable ordinary surfaces behind long text. Start with a few capsules; each surface traverses the decor tree into a bounded software canvas.
- `refreshBackdrop()` explicitly requests a new sample after unusual non-native data invalidations. Native root pre-draw samples and scroll/trailing callbacks normally handle refresh.
- Utility `ui.icon(...)` now defaults to `ink` (charcoal/light ink), not blue. Semantic send/selected icons must explicitly override their `ImageTintList`.
- Buttons use weight 500 (medium), selected capsules use a 60% soft fill without a stroke, and the light primary is exactly `#0068E5`. Light soft fill is `#EDF3FF` so primary/soft passes the existing 4.5:1 opaque-palette contrast regression (4.588:1). Keep native 48dp hit targets; scope/container padding remains the integrating screen's responsibility.
- `setCornerRadiusDp(float)` updates both sampled material geometry and native child clipping.

The renderer samples only its bounds plus an 18dp halo. It uses sixfold downsampling, increasing the factor to keep each bitmap at <=65,536 pixels; reuses bitmap/pixel buffers; applies two separable box blurs; throttles sample work to 48ms; and has a 50ms trailing scroll refresh. It does not continuously animate when idle. The default optical tint is 34% light / 40% dark plus a fine highlight, not near-opaque fake glass.

Accessibility: the app's appearance `reduce_transparency` preference selects opaque material; secure-system reduce-transparency keys are best-effort OEM compatibility rather than a portable Android public contract. The current public AccessibilityManager `isHighContrastTextEnabled` method is invoked reflectively when present to keep compilation against SDK35, with older secure high-text-contrast fallback. Settings/preferences observers refresh policy. There is no decorative material animation; existing button feedback consults app/system reduced motion and now cancels/resets scale if motion is disabled mid-gesture.

## Tests and evidence

Run host suite:

```sh
python3 -m unittest discover -s android/tests -v
```

`test_glass_renderer.py` executes the production Java CPU kernel and separately labels its source integration contract tests. They do not claim to execute Android lifecycle methods.

`GlassNativeRendererTest.java` is a standalone, actual Android Bitmap/Canvas probe, compiled alongside GlassDrawable/GlassPixels, dexed using SDK35 d8 and run with `app_process`. It tests real pixel diffusion, translucent retained backdrop contrast, sharp overlaid foreground and opaque accessibility fallback. It does not launch the application or touch account data. Verified output on emulator-5554:

```
NATIVE GLASS PASS: blurred backdrop, translucent sampled contrast, sharp foreground, opaque fallback
```

Probe artifacts were built only in `/opt/data/cache/scratch/glass-compile-61u6ka40`, transferred to `/home/simon/hermes-apks/glass-native-test.jar` on Linux and `/data/local/tmp/glass-native-test.jar` on the emulator. The Linux adb executable is `/home/simon/Android/Sdk/platform-tools/adb`; it is not on the SSH noninteractive PATH.

## Remaining native acceptance checks

The standalone probe does NOT prove attached FrameLayout capture exclusion, scroll performance, contrast over actual task content, lifecycle transitions or the final UI. The parent must build/deploy the integrated Tasks screen, capture compact/unfolded light/dark native screenshots and a scrolling recording, and test background/offscreen and accessibility toggles. Do not claim those are verified from the graphics probe.

Limitations: software snapshots do not sample SurfaceView/video, hardware-only bitmaps/effects, cross-window surfaces or platform popup windows. Unsupported software drawing / allocation errors select a stable opaque fallback for that container. Root traversal remains UI-thread CPU work despite bounded rasterization; physical-device frame time is unmeasured. Static non-scroll changes within the 48ms throttle window may need explicit `refreshBackdrop()` if no subsequent draw occurs.

## Official API references checked

- https://developer.android.com/reference/android/view/View (`draw(Canvas)`, root/window geometry)
- https://developer.android.com/reference/android/view/ViewTreeObserver (pre-draw / scroll listeners and removal)
- https://developer.android.com/reference/android/view/ViewTreeObserver.OnPreDrawListener (return true to proceed)
- https://developer.android.com/reference/android/graphics/RenderEffect (blur affects RenderNode contents, not an automatic backdrop)
- https://developer.android.com/reference/android/view/accessibility/AccessibilityManager (current public `isHighContrastTextEnabled`)

No Compose, third-party rendering dependency, build-script change, auth/signing change or git commit is required.

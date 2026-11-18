"""Current Activity builders/callbacks with host Views and synthetic transport.
Not Android rendering, IME, accessibility-service or native acceptance evidence.
"""
from pathlib import Path
import hashlib
import os
import re
import shutil
import subprocess
import tempfile
import unittest
from test_tasks_anchor_methods import extract

ANDROID = Path(__file__).resolve().parents[1]
SRC = ANDROID / 'src/com/simon/mytasks'
METHODS = ['field', 'valueField', 'choice', 'buildDetails', 'buildTaskReading',
           'readingFact', 'readingDescription', 'readingSection', 'sourceContext',
           'buildTaskEditor', 'pickDate', 'revision', 'save', 'mutation', 'captureDraft',
           'dirty', 'task', 'copy', 'empty', 'human', 'dueLabel', 'text',
           'bindTaskEditorNotice', 'updateTaskEditorNotice', 'bindTaskEditorLayout', 'taskEditorMetricState', 'taskEditorChromeHeight', 'sizeTaskEditorMultiline']


class TaskReaderEditorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = (SRC / 'MainActivity.java').read_text()
        methods = '\n'.join(extract(source, r'^ (?:static )?\w+ ' + name + r'\(') for name in METHODS)
        methods += '\n' + extract(source, r'^ void refresh\(boolean ')
        methods += '\n' + '\n'.join(extract(source, r'^ void ' + name + r'\(') for name in ['quietRefresh', 'buildListRows'])
        render = extract(source, r'^ void render\(')
        pane_binding = re.search(r'detailPane=(?:vertical|taskDetailPane)\(\);', render)
        if not pane_binding:
            raise AssertionError('Missing real render task pane factory binding')
        if 'taskDetailPane()' in pane_binding.group():
            methods += '\n' + extract(source, r'^ TaskDetailPane taskDetailPane\(')
            methods += '\n' + extract(source, r'^ class TaskDetailPane extends LinearLayout')
        inset = re.search(r'if\(stateLabel!=null\)stateLabel.setVisibility\(utilities.*?;', render)
        if not inset:
            raise AssertionError('Missing production status inset visibility statement')
        methods += '\n void applyStatusInsets(boolean keyboard){boolean utilities=CompactPolicy.secondaryChrome(keyboard,800);' + inset.group() + '}\n'
        template = (ANDROID / 'tests/TaskReaderEditorHarness.java').read_text()
        template = template.replace('// TASK_PANE_BINDING', pane_binding.group())
        global_binding = next(line.strip() for line in source.splitlines() if line.strip().startswith('stateLabel=label(banner,'))
        template = template.replace('// GLOBAL_STATUS_BINDING', global_binding)
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            raise AssertionError('Set JAVA_HOME or put javac on PATH')
        cls.java = str(Path(javac).resolve().parent / 'java')
        cls.jar = ANDROID / 'build/test-lib/json.jar'
        if not cls.jar.exists():
            raise AssertionError('Run android/test.sh for the verified host JSON jar')
        cls.directory = tempfile.TemporaryDirectory(prefix='m6-task-reader-editor-')
        cls.addClassCleanup(cls.directory.cleanup)
        cls.work = Path(cls.directory.name)
        harness = cls.work / 'TaskReaderEditorHarness.java'
        harness.write_text(template.replace('// PRODUCTION_METHODS', methods))
        for name, data in [('MainActivity', source.encode()), ('extracted methods', methods.encode()),
                           ('harness', harness.read_bytes()), ('test', Path(__file__).read_bytes())]:
            print(name + ' sha256=' + hashlib.sha256(data).hexdigest(), flush=True)
        result = subprocess.run([javac, '--release', '8', '-cp', str(cls.jar), '-d', str(cls.work),
                                 *[str(SRC / (n + '.java')) for n in ['Core', 'PresentationPolicy', 'SafeMarkdown', 'TaskDescriptionText', 'UiReadGate', 'CompactPolicy']],
                                 str(harness)], text=True, capture_output=True)
        if result.returncode:
            raise AssertionError('Host harness compilation failed:\n' + result.stdout + result.stderr)

    def run_case(self, name, *args):
        result = subprocess.run([self.java, '-cp', str(self.work) + os.pathsep + str(self.jar),
                                 'com.simon.mytasks.TaskReaderEditorHarness', name, *args], text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        print(result.stdout.strip())

    def test_same_character_formatted_paste_recalibrates(self):
        self.run_case('metric-paste', 'false', 'description', 'same')

    def test_ordered_shrink_prevents_zero_frame_descendant_focus_clear(self):
        self.run_case('ordered-shrink')

    def test_ordered_first_short_shrink_grow_width_density_font_and_values(self):
        self.run_case('ordered-matrix')

    def test_ordered_measure_uses_current_resolved_specs_and_padding(self):
        self.run_case('ordered-resolved')

    def test_ordered_metric_changes_and_same_bounds_convergence(self):
        self.run_case('ordered-metrics')

    def test_ordered_measure_and_listener_reject_invalidated_bindings(self):
        self.run_case('ordered-invalidation')

    def test_ordered_relocation_retains_original_date_save_cancel(self):
        self.run_case('ordered-actions')

    def test_formatted_paste_four_counterexamples_and_eight_controls(self):
        for fresh in ['true', 'false']:
            for field in ['description', 'notes']:
                for mode in ['same', 'changed', 'plain']:
                    with self.subTest(fresh=fresh, field=field, mode=mode):
                        self.run_case('metric-paste', fresh, field, mode)

    def test_metric_changes_without_character_changes(self):
        for fresh in ['true', 'false']:
            for field in ['description', 'notes']:
                for enabled in ['true', 'false']:
                    for mode in ['add', 'remove', 'replace', 'mutate', 'range',
                                 'hidden-contraction', 'reflow', 'nonmetric', 'second-pass']:
                        with self.subTest(fresh=fresh, field=field, enabled=enabled, mode=mode):
                            self.run_case('metric-change', fresh, field, enabled, mode)

    def test_measurement_keeps_existing_ownership_fences(self):
        for mode in ['owner', 'root', 'pane', 'editor', 'scroll', 'title',
                     'description', 'notes', 'epoch', 'session', 'auth', 'store',
                     'task', 'reader', 'destination', 'detached', 'content-detached',
                     'window-detached', 'header-detached', 'gap-detached',
                     'actions-detached', 'field-detached', 'rebind']:
            with self.subTest(mode=mode):
                self.run_case('metric-owner', mode)

    def test_settled_first_short_growth_shrink_and_fractional_density(self):
        for fresh in ["true", "false"]:
            for enabled in ["true", "false"]:
                for density in ["1", "1.25", "2.625"]:
                    with self.subTest(fresh=fresh, enabled=enabled, density=density):
                        self.run_case("settled-transitions", fresh, enabled, density)

    def test_inactive_layout_controls_never_request_work(self):
        for mode in ["owner", "detached", "destination", "generation"]:
            with self.subTest(mode=mode):
                self.run_case("settled-inactive", mode)
        self.run_case("settled-reader")

    def test_suppressed_second_pass_measures_current_capacity(self):
        for changed in ["false", "true"]:
            with self.subTest(changed_spec=changed):
                self.run_case("settled-stale", changed)

    def test_settled_adaptation_stops_requesting_layout(self):
        for height in ['281', '841', '1000']:
            for enabled in ['true', 'false']:
                for fresh in ['true', 'false']:
                    with self.subTest(height=height, enabled=enabled, fresh=fresh):
                        self.run_case('settled-stable', height, enabled, fresh)

    def test_adaptive_viewport_keeps_complete_title_reachable(self):
        self.run_case('adaptive-viewport')

    def test_multiline_widgets_fit_retained_viewports_without_losing_text(self):
        self.run_case('adaptive-multiline')

    def test_adaptive_layout_rejects_stale_or_detached_bindings(self):
        for mode in ['root', 'pane', 'editor', 'scroll', 'title', 'description', 'notes',
                     'owner', 'epoch', 'session', 'auth', 'store', 'task', 'reader',
                     'destination', 'detached', 'content-detached', 'window-detached',
                     'header-detached', 'gap-detached', 'actions-detached', 'field-detached', 'rebind']:
            with self.subTest(mode=mode):
                self.run_case('adaptive-owner-' + mode)

    def test_null_owner_geometry_is_presentation_only(self):
        self.run_case('adaptive-null-owner')

    def test_multiline_uses_actual_measured_lines_including_font_fallback(self):
        self.run_case('adaptive-measured-lines')

    def test_adaptive_new_existing_round_trips_preserve_actual_controls(self):
        self.run_case('adaptive-controls')

    def test_adaptive_geometry_handles_fractional_density(self):
        self.run_case('adaptive-density')

    def test_facts_before_long_description(self):
        self.run_case('order')

    def test_retained_changed_notice_with_hidden_global_status(self):
        self.run_case('retained-changed')

    def test_retained_failure_notice_with_hidden_global_status(self):
        self.run_case('retained-failure')

    def test_retained_offline_guidance_does_not_claim_fields_disabled(self):
        self.run_case('retained-failure-guidance')

    def test_retained_recovery_and_warning_clearing(self):
        for mode in ['recovery', 'clear']:
            with self.subTest(mode=mode):
                self.run_case('retained-' + mode)

    def test_rendered_warning_and_invalidated_read_controls(self):
        for mode in ['render-control', 'stale-control']:
            with self.subTest(mode=mode):
                self.run_case('retained-' + mode)

    def test_notice_binding_rejects_replaced_ownership(self):
        for mode in ['root', 'editor', 'task', 'owner', 'epoch', 'session', 'auth', 'store',
                     'detached', 'notice-detached', 'reader', 'destination', 'rebind']:
            with self.subTest(mode=mode):
                self.run_case('notice-owner-' + mode)

    def test_queued_success_and_failure_respect_current_notice_ownership(self):
        for failure in ['false', 'true']:
            for mode in ['ticket', 'owner', 'auth', 'root', 'editor', 'task', 'session', 'detached', 'rebind']:
                with self.subTest(mode=mode, failure=failure):
                    self.run_case('notice-queued-' + mode, failure)

    def test_same_editor_online_error_recovery_clear_round_trip(self):
        self.run_case('notice-roundtrip')

    def test_live_notice_uses_exact_incumbent_predicate_without_side_effects(self):
        self.run_case('notice-predicate')

    def test_solid_groups_and_fields_use_standard_corners(self):
        self.run_case('groups')

    def test_wrapping_local_notice_and_verbatim_existing_banner(self):
        self.run_case('notice')

    def test_opaque_in_flow_action_wrappers(self):
        self.run_case('actions')

    def test_actual_save_failure_and_confirmed_reload(self):
        self.run_case('save-reload')

    def test_source_browser_consent_trash_missing_cache_and_cancel(self):
        self.run_case('routes')

    def test_preserved_fields_source_routes_and_callbacks(self):
        self.run_case('preservation')


if __name__ == '__main__':
    unittest.main(verbosity=2)

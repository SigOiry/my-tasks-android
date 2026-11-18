package com.simon.mytasks;
import java.util.*;import org.json.*;
/** Test-only Android View shells. Measures parentage/state, NOT Android layout/IME. */
public class M5NativeHarness {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){if("STAGE_VALUE".equals("repair-outer-fallback")){repairOuterFallback();return;}if("STAGE_VALUE".equals("repair-outer-enablement")){repairOuterEnablement();return;}if("STAGE_VALUE".equals("repair-outer-boundaries")){repairOuterOwnership();repairOuterNoEditor();return;}if("STAGE_VALUE".equals("all")){repairOuterFallback();repairOuterEnablement();repairOuterOwnership();repairOuterNoEditor();}if("STAGE_VALUE".equals("repair-r1")){repairR1();return;}if("STAGE_VALUE".equals("repair-r2")){repairR2();return;}if("STAGE_VALUE".equals("repair-matrix")){repairMatrix();return;}if("STAGE_VALUE".equals("all")){repairR1();repairR2();repairMatrix();}if(!"STAGE_VALUE".equals("nav"))task();if(Arrays.asList("chat","shell","all").contains("STAGE_VALUE"))chat();if(Arrays.asList("shell","all").contains("STAGE_VALUE"))shell();nav();System.out.println("PASS M5 in-flow navigation + opaque scrollable rail (host Views)");}
 static void repairOuterFallback(){
  int cases=0,failures=0;
  for(int savedY:new int[]{37,100,619})for(boolean changed:new boolean[]{false,true})for(int path=0;path<5;path++){
   MainActivity a=new MainActivity();a.selected="a";a.detailY=savedY;a.bindTask("first binding text");if(changed)a.detailScroll.y=271;
   if(path==4){Bundle saved=new Bundle();a.onSaveInstanceState(saved);a=new MainActivity();a.recreate(saved);}else if(path==3){a.captureDraft();a.captureDraft();a.bindTask(a.draft.optString("title"));}else for(int n=0;n<path;n++)a.onConfigurationChanged(new Configuration());
   Queue.run();int expected=changed?271:savedY;boolean ok=a.detailScroll.y==expected;cases++;if(!ok)failures++;
   System.out.println((ok?"PASS":"FAIL")+" outer-fallback saved="+savedY+" changed="+changed+" path="+path+" expected="+expected+" actual="+a.detailScroll.y);
  }
  System.out.println("OUTER_FALLBACK_TOTAL "+cases+" FAILURES "+failures);check(failures==0,"outer fallback lifecycle failures="+failures);
 }
 static void repairOuterEnablement(){
  int cases=0,failures=0;
  for(String mode:new String[]{"enabled","busy","offline","offline-busy"})for(boolean retained:new boolean[]{false,true})for(boolean changed:new boolean[]{false,true})for(int path=0;path<5;path++){
   MainActivity a=new MainActivity();a.selected="a";
   if(retained){a.bindTask("task text retained");a.title.setSelection(1,4);a.title.focused=true;a.detailScroll.y=83;a.captureDraft();}else a.detailY=83;
   a.online=!mode.startsWith("offline");a.busy=mode.endsWith("busy");a.bindTask("task text retained");boolean enabled=a.online&&!a.busy;check(a.title.isEnabled()==enabled,"actual field enablement seam "+mode);
   if(changed)a.detailScroll.y=427;
   if(path==4){Bundle saved=new Bundle();a.onSaveInstanceState(saved);MainActivity recreated=new MainActivity();recreated.online=a.online;recreated.busy=a.busy;recreated.recreate(saved);a=recreated;}else if(path==3){a.captureDraft();a.captureDraft();a.bindTask(a.draft.optString("title"));}else for(int n=0;n<path;n++)a.onConfigurationChanged(new Configuration());
   Queue.run();int expected=changed?427:83;boolean ok=a.detailScroll.y==expected;cases++;if(!ok)failures++;
   check(a.title.isEnabled()==enabled,"lifecycle must not enable fields "+mode);if(!enabled)check(!a.title.focused,"disabled fields must not be focused");check(a.title.text.equals("task text retained"),"outer capture cannot change task text");
   System.out.println((ok?"PASS":"FAIL")+" outer-enablement mode="+mode+" retained="+retained+" changed="+changed+" path="+path+" expected="+expected+" actual="+a.detailScroll.y);
  }
  System.out.println("OUTER_ENABLEMENT_TOTAL "+cases+" FAILURES "+failures);check(failures==0,"outer enablement lifecycle failures="+failures);
 }
 static void repairOuterOwnership(){
  int cases=0;
  for(String mode:new String[]{"enabled","busy","offline"})for(boolean retained:new boolean[]{false,true})for(String boundary:new String[]{"view","task","account","auth","route","generation"}){
   MainActivity a=new MainActivity();a.selected="a";if(retained){a.bindTask("task A text");a.detailScroll.y=83;a.captureDraft();}else a.detailY=83;
   a.online=!mode.equals("offline");a.busy=mode.equals("busy");a.bindTask("task A text");ScrollView old=a.detailScroll;
   if(boundary.equals("view")){a.captureDraft();a.bindTask("task A text");a.detailScroll.y=427;}
   if(boundary.equals("task"))a.selected="b";if(boundary.equals("account"))a.store.epoch="epoch-b";if(boundary.equals("auth"))a.authState=AuthPolicy.State.LOGIN;if(boundary.equals("route"))a.destination="Discussions";if(boundary.equals("generation"))a.taskPresentationGeneration++;
   Queue.run();check(old.y==0,"revoked outer callback "+mode+" retained="+retained+" boundary="+boundary);if(boundary.equals("view"))check(a.detailScroll.y==427,"stale callback cannot touch replacement outer");cases++;
  }
  System.out.println("PASS OUTER_OWNERSHIP_TOTAL "+cases);
 }
 static void repairOuterNoEditor(){
  int cases=0,failures=0;
  for(boolean changed:new boolean[]{false,true}){
   MainActivity a=new MainActivity();a.selected="a";a.detailY=83;a.bindTask("first binding");a.title=a.description=a.notes=a.project=a.due=null; // View seam: outer remains usable without fields.
   if(changed)a.detailScroll.y=427;Bundle saved=new Bundle();a.onSaveInstanceState(saved);int expected=changed?427:83;
   boolean ok=saved.getInt("detailY")==expected;cases++;if(!ok)failures++;System.out.println((ok?"PASS":"FAIL")+" outer-without-fields Bundle changed="+changed+" expected="+expected+" actual="+saved.getInt("detailY"));Queue.run();
  }
  System.out.println("OUTER_NO_EDITOR_TOTAL "+cases+" FAILURES "+failures);check(failures==0,"outer-without-fields Bundle failures="+failures);
 }
 static void repairMatrix(){
  int cases=0;
  for(String change:new String[]{"untouched","selection","focus","internal-scroll","outer-scroll","text"})for(int path=0;path<4;path++){
   MainActivity a=new MainActivity();a.selected="a";a.bindTask("original task text");a.notes.text="notes to retain";a.title.setSelection(1,4);a.title.focused=true;a.title.scrollTo(3,17);a.notes.setSelection(2,6);a.notes.scrollTo(5,18);a.detailScroll.y=100;a.captureDraft();a.bindTask("original task text");
   if(change.equals("selection"))a.title.setSelection(7,9);if(change.equals("focus"))a.notes.focused=true;if(change.equals("internal-scroll"))a.title.scrollTo(13,44);if(change.equals("outer-scroll"))a.detailScroll.y=333;if(change.equals("text")){a.title.text="new local task text";a.title.setSelection(7,9);}
   if(path==3){Bundle b=new Bundle();a.onSaveInstanceState(b);check(!b.getString("task_presentation").contains("\"text\""),"presentation Bundle must not persist binding text");a=new MainActivity();a.recreate(b);}else for(int n=0;n<path;n++)a.onConfigurationChanged(new Configuration());
   Queue.run();String why="task "+change+" lifecycle="+path;
   if(change.equals("untouched"))check(a.title.start==1&&a.title.end==4&&a.title.focused&&a.title.getScrollX()==3&&a.title.y==17&&a.detailScroll.y==100,why+" untouched defaults cannot replace retained state");
   if(change.equals("selection")||change.equals("text"))check(a.title.start==7&&a.title.end==9,why+" newer range");
   if(change.equals("text"))check(a.title.text.equals("new local task text"),why+" text captured only by draft lifecycle");
   if(change.equals("focus"))check(a.notes.focused&&!a.title.focused,why+" newer focused field wins");
   if(change.equals("internal-scroll"))check(a.title.getScrollX()==13&&a.title.y==44,why+" newer internal scroll");
   if(change.equals("outer-scroll"))check(a.detailScroll.y==333,why+" newer outer scroll");
   if(!change.equals("focus"))check(a.notes.start==2&&a.notes.end==6&&a.notes.getScrollX()==5&&a.notes.y==18,why+" untouched sibling must retain original presentation");cases++;
  }
  for(String change:new String[]{"untouched","selection","focus","internal-scroll","text"})for(int path=0;path<4;path++){
   MainActivity a=new MainActivity();Controller c=a.conversations;ConversationsUi u=new ConversationsUi(a,c);u.bind("A","original composer text");u.input.setSelection(1,4);u.input.scrollTo(3,17);u.capture();u=new ConversationsUi(a,c);u.bind("A","original composer text");
   if(change.equals("selection"))u.input.setSelection(7,9);if(change.equals("focus"))u.input.focused=true;if(change.equals("internal-scroll"))u.input.scrollTo(13,44);if(change.equals("text")){u.input.text="new local composer text";u.input.setSelection(7,9);}
   if(path==3){u.capture();Controller restored=new Controller();restored.state.data=new JSONObject(c.state.json().toString());c=restored;u=new ConversationsUi(a,c);u.bind("A",c.state.data.optString("draft:A"));}else for(int n=0;n<path;n++){u.capture();u=new ConversationsUi(a,c);u.bind("A",c.state.data.optString("draft:A"));}
   Queue.run();String why="composer "+change+" lifecycle="+path;
   if(change.equals("untouched"))check(u.input.start==1&&u.input.end==4&&u.input.getScrollX()==3&&u.input.y==17,why+" untouched defaults cannot replace retained state");
   if(change.equals("selection")||change.equals("text"))check(u.input.start==7&&u.input.end==9,why+" newer range");
   if(change.equals("text"))check(u.input.text.equals("new local composer text"),why+" exact new draft");
   if(change.equals("focus"))check(u.input.focused&&u.input.end==0,why+" newly focused input");
   if(change.equals("internal-scroll"))check(u.input.getScrollX()==13&&u.input.y==44,why+" newer internal scroll");check(!u.viewport().optJSONObject("composer").has("text"),why+" no stale text in presentation");cases++;
  }
  // The actual fallback binding also queues an outer restore without a saved editor.
  MainActivity fallback=new MainActivity();fallback.selected="a";fallback.detailY=100;fallback.bindTask("first binding");fallback.detailScroll.y=333;Queue.run();check(fallback.detailScroll.y==333,"first binding fallback must not overwrite newer outer scroll");cases++;
  for(String boundary:new String[]{"view","task","account","route","generation"}){
   MainActivity a=new MainActivity();a.selected="a";a.bindTask("old task text");a.title.setSelection(1,4);a.title.focused=true;a.captureDraft();a.bindTask("queued task text");EditText old=a.title;
   if(boundary.equals("view")){a.captureDraft();a.bindTask("fresh task text");a.title.setSelection(7,9);}
   if(boundary.equals("task"))a.selected="b";if(boundary.equals("account"))a.store.epoch="epoch-b";if(boundary.equals("route"))a.destination="Discussions";
   if(boundary.equals("generation")){a.title.setSelection(7,9);a.captureDraft();a.restoreTaskPresentation();}
   Queue.run();check(!old.focused,"stale task "+boundary+" cannot take focus");if(boundary.equals("view")||boundary.equals("generation"))check(a.title.start==7&&a.title.end==9,"stale task "+boundary+" cannot overwrite current range");cases++;
  }
  for(String boundary:new String[]{"view","thread","account","listener","generation"}){
   MainActivity a=new MainActivity();Controller c=a.conversations;ConversationsUi u=new ConversationsUi(a,c);u.bind("A","composer A text");u.input.setSelection(1,4);u.input.focused=true;u.capture();u=new ConversationsUi(a,c);u.bind("A","composer A text");EditText old=u.input;
   if(boundary.equals("view")){u.bind("A","fresh composer text");u.input.setSelection(7,9);}
   if(boundary.equals("thread"))u.thread="B";if(boundary.equals("account"))c.valid=false;if(boundary.equals("listener"))c.listener=new Object();
   if(boundary.equals("generation")){u.input.setSelection(7,9);u.capture();u.restoreComposerPresentation();}
   Queue.run();check(!old.focused,"stale composer "+boundary+" cannot take focus");if(boundary.equals("view")||boundary.equals("generation"))check(u.input.start==7&&u.input.end==9,"stale composer "+boundary+" cannot overwrite current range");cases++;
  }
  MainActivity ab=new MainActivity();Controller c=ab.conversations;ConversationsUi u=new ConversationsUi(ab,c);u.bind("A","composer A text");u.input.setSelection(1,4);u.capture();u=new ConversationsUi(ab,c);u.bind("A","composer A text");u.input.setSelection(7,9);u.capture();u=new ConversationsUi(ab,c);u.bind("B","composer B text");u.input.setSelection(2,5);u.capture();u=new ConversationsUi(ab,c);u.bind("A","composer A text");Queue.run();check(u.input.start==7&&u.input.end==9,"A/B/A retains newer A range despite undrained callbacks");u.capture();u=new ConversationsUi(ab,c);u.bind("B","composer B text");Queue.run();check(u.input.start==2&&u.input.end==5,"A/B/A/B retains independent B range");cases++;
  MainActivity taskSwitch=new MainActivity();taskSwitch.selected="A";taskSwitch.bindTask("task A text");taskSwitch.title.setSelection(1,4);taskSwitch.captureDraft();taskSwitch.bindTask("task A text");EditText oldA=taskSwitch.title;taskSwitch.selected="B";taskSwitch.bindTask("task B text");taskSwitch.title.setSelection(2,5);taskSwitch.captureDraft();taskSwitch.selected="A";taskSwitch.bindTask("authoritative A text");Queue.run();check(!oldA.focused&&taskSwitch.title.end==0&&taskSwitch.title.text.equals("authoritative A text"),"task A/B/A must revoke old single-editor ownership, not import B or stale A");cases++;
  MainActivity blur=new MainActivity();blur.selected="a";blur.bindTask("task focus text");blur.title.focused=true;blur.title.setSelection(1,4);blur.captureDraft();blur.bindTask("task focus text");blur.title.focused=true;blur.restoreTaskPresentation();blur.title.focused=false;Queue.run();check(!blur.title.focused&&blur.title.end==0,"newer task focus loss must not refocus or reselect");cases++;
  ConversationsUi chatBlur=new ConversationsUi(blur,blur.conversations);chatBlur.bind("A","composer focus text");chatBlur.input.focused=true;chatBlur.input.setSelection(1,4);chatBlur.capture();chatBlur.bind("A","composer focus text");chatBlur.input.focused=true;chatBlur.restoreComposerPresentation();chatBlur.input.focused=false;Queue.run();check(!chatBlur.input.focused&&chatBlur.input.end==0,"newer composer focus loss must not refocus or reselect");cases++;
  System.out.println("PASS M5 repair lifecycle matrix: "+cases+" cases (host queues, not Android runtime)");
 }
 static void repairR2(){
  int failures=0;
  for(String change:new String[]{"selection","focus","internal-scroll","outer-scroll","text-and-outer"}){
   MainActivity a=new MainActivity();a.selected="a";a.bindTask("original task text");a.title.setSelection(1,4);a.title.focused=true;a.title.y=17;a.detailScroll.y=100;a.captureDraft();a.bindTask("original task text");
   if(change.equals("selection"))a.title.setSelection(7,9);
   if(change.equals("focus"))a.notes.focused=true;
   if(change.equals("internal-scroll"))a.title.scrollTo(13,44);
   if(change.equals("outer-scroll"))a.detailScroll.y=333;
   if(change.equals("text-and-outer")){a.title.text="new local task text";a.title.setSelection(7,9);a.detailScroll.y=333;}
   Queue.run();boolean ok=change.equals("selection")?a.title.start==7&&a.title.end==9:change.equals("focus")?a.notes.focused&&!a.title.focused:change.equals("internal-scroll")?a.title.getScrollX()==13&&a.title.y==44:change.equals("outer-scroll")?a.detailScroll.y==333:a.title.start==7&&a.title.end==9&&a.detailScroll.y==333&&a.title.text.equals("new local task text");
   System.out.println((ok?"PASS":"FAIL")+" R2 task "+change+" range="+a.title.start+".."+a.title.end+" outer="+a.detailScroll.y);if(!ok)failures++;
  }
  for(String change:new String[]{"selection","focus","internal-scroll"}){
   MainActivity a=new MainActivity();ConversationsUi u=new ConversationsUi(a,a.conversations);u.bind("A","original composer text");u.input.setSelection(1,4);u.input.y=17;u.capture();
   ConversationsUi v=new ConversationsUi(a,a.conversations);v.bind("A","original composer text");
   if(change.equals("selection"))v.input.setSelection(7,9);if(change.equals("focus"))v.input.focused=true;if(change.equals("internal-scroll"))v.input.scrollTo(13,44);
   Queue.run();boolean ok=change.equals("selection")?v.input.start==7&&v.input.end==9:change.equals("focus")?v.input.focused&&v.input.end==0:v.input.getScrollX()==13&&v.input.y==44;
   System.out.println((ok?"PASS":"FAIL")+" R2 composer "+change);if(!ok)failures++;
  }
  check(failures==0,"R2 late presentation counterexamples: "+failures);
 }
 static void repairR1(){
  int failures=0;
  MainActivity a=new MainActivity();a.selected="a";a.bindTask("original draft text");a.title.setSelection(1,4);a.title.focused=true;a.captureDraft();
  a.bindTask("original draft text");a.title.text="new local text here";a.title.setSelection(7,9);a.title.focused=true;a.captureDraft();a.bindTask(a.draft.optString("title"));Queue.run();
  boolean task=a.title.start==7&&a.title.end==9&&a.title.text.equals("new local text here");System.out.println((task?"PASS":"FAIL")+" R1 task pending capture/rebuild range="+a.title.start+".."+a.title.end);if(!task)failures++;
  MainActivity b=new MainActivity();Controller c=b.conversations;ConversationsUi first=new ConversationsUi(b,c);first.bind("A","original composer text");first.input.setSelection(1,4);first.input.focused=true;first.capture();
  ConversationsUi second=new ConversationsUi(b,c);second.bind("A","original composer text");second.input.text="new local composer text";second.input.setSelection(7,9);second.input.focused=true;second.capture();ConversationsUi third=new ConversationsUi(b,c);third.bind("A",c.state.data.optString("draft:A"));Queue.run();
  boolean chat=third.input.start==7&&third.input.end==9&&third.input.text.equals("new local composer text");System.out.println((chat?"PASS":"FAIL")+" R1 composer pending capture/rebuild range="+third.input.start+".."+third.input.end);if(!chat)failures++;
  check(failures==0,"R1 late presentation counterexamples: "+failures);
 }
 static void task(){
  MainActivity a=new MainActivity();a.selected="a";a.bindTask("authoritative text");a.title.start=2;a.title.end=9;a.title.focused=true;a.title.y=17;a.detailScroll.y=140;a.captureDraft();
  a.bindTask("NEW authoritative text");Queue.run();check(a.title.start==2&&a.title.end==9,"task rerender must restore both selection endpoints");check(a.title.focused&&a.title.y==17,"task field focus and internal scroll survive");check(a.title.text.equals("NEW authoritative text"),"presentation never overwrites authoritative binding");check(a.detailScroll.y==140,"outer task scroll restored");
  a.captureDraft();a.bindTask("short");Queue.run();check(a.title.end==5,"range clamps to new authoritative length");
  a.captureDraft();a.bindTask("replaced");EditText stale=a.title;a.bindTask("current");Queue.run();check(!stale.focused,"posted restores cannot focus a replaced editor");
  a.captureDraft();Bundle saved=new Bundle();a.onSaveInstanceState(saved);MainActivity recreated=new MainActivity();recreated.recreate(saved);Queue.run();check(recreated.title.start==2&&recreated.title.end==5,"saved-instance recreation restores clamped task range");
  recreated.title.start=1;recreated.title.end=4;recreated.title.focused=true;recreated.title.y=19;recreated.onConfigurationChanged(new Configuration());Queue.run();check(recreated.title.start==1&&recreated.title.end==4&&recreated.title.y==19,"actual configuration method retains field presentation");
  MainActivity wrong=new MainActivity();wrong.account=new Store.Account("epoch-b");wrong.store.epoch="epoch-b";wrong.recreate(saved);Queue.run();check(!wrong.title.focused&&wrong.title.end==0,"recreation cannot import another account's presentation");
  a.captureDraft();a.selected="b";a.bindTask("B text");Queue.run();check(!a.title.focused&&a.title.end==0,"A selection cannot leak into B");
  MainActivity fields=new MainActivity();fields.selected="fields";fields.bindTask("title");fields.description.text="description";fields.notes.text="notes text";fields.project.text="project";fields.due.text="2026-11-16";EditText[] oldFields={fields.title,fields.description,fields.notes,fields.project,fields.due};for(EditText f:oldFields){f.start=1;f.end=4;f.y=12;}fields.notes.focused=true;fields.captureDraft();fields.onConfigurationChanged(new Configuration());Queue.run();check(fields.notes.focused&&fields.notes.start==1&&fields.notes.end==4&&fields.notes.y==12,"non-title focused field retained independently");
  fields.captureDraft();fields.bindTask("before input");fields.title.text="new typing";fields.title.start=7;fields.title.end=8;Queue.run();check(fields.title.start==7&&fields.title.end==8,"queued task restoration must not move a newly typed field's selection");
  fields.captureDraft();fields.bindTask("stale");fields.store.epoch="signed-out";Queue.run();check(!fields.title.focused,"invalidated account cancels posted task restoration");
  MainActivity discard=new MainActivity();discard.selected="a";discard.bindTask("draft");discard.title.focused=true;discard.title.start=1;discard.title.end=3;discard.captureDraft();discard.editing=false;discard.render();Queue.run();discard.editing=true;discard.bindTask("canonical");Queue.run();check(!discard.title.focused&&discard.title.end==0,"explicit cancel/save ending editing must not revive old field focus or scroll on a later Edit");
  System.out.println("PASS M5 actual task capture + binding: range/focus/scroll, clamping, authoritative text, replaced views, A/B");
 }
 static void chat(){
  MainActivity listActivity=new MainActivity();Controller lc=listActivity.conversations;ConversationsUi lu=new ConversationsUi(listActivity,lc);lu.list(100,300);Queue.run();lu.discussionScroll.y=320;lu.capture();ConversationsUi lr=new ConversationsUi(listActivity,lc);lr.list(150,420);Queue.run();check(lr.discussionScroll.y==440,"discussion ID anchor survives changed header/row heights");

  MainActivity a=new MainActivity();Controller c=a.conversations;ConversationsUi u=new ConversationsUi(a,c);u.bind("A","draft A text");u.input.focused=true;u.input.start=2;u.input.end=8;u.input.y=23;u.capture();
  ConversationsUi v=new ConversationsUi(a,c);v.bind("B","draft B");Queue.run();check(v.input.end==0&&!v.input.focused,"A range must not leak into B");v.capture();
  ConversationsUi back=new ConversationsUi(a,c);back.bind("A","draft A text");Queue.run();check(back.input.start==2&&back.input.end==8,"per-thread composer must restore both endpoints A -> B -> A");check(back.input.focused&&back.input.y==23,"thread composer focus/scroll restored");
  back.capture();ConversationsUi stale=new ConversationsUi(a,c);stale.bind("A","draft A text");ConversationsUi fresh=new ConversationsUi(a,c);fresh.bind("B","draft B");Queue.run();check(!stale.input.focused,"old presenter posted callback cannot restore");
  Controller recreatedController=new Controller();recreatedController.state.data=new JSONObject(c.state.json().toString());ConversationsUi recreated=new ConversationsUi(a,recreatedController);recreated.bind("A","draft A text");Queue.run();check(recreated.input.start==2&&recreated.input.end==8&&recreated.input.y==23,"composer presentation survives serialized account state recreation");
  ConversationsUi typing=new ConversationsUi(a,recreatedController);typing.bind("A","draft A text");typing.input.text="new local typing";typing.input.start=5;typing.input.end=7;Queue.run();check(typing.input.start==5&&typing.input.end==7,"late composer restoration cannot move freshly typed range");
  back=new ConversationsUi(a,c);back.bind("A","draft A text");c.valid=false;Queue.run();check(!back.input.focused,"logout closes pending restore");
  lc.listener=lr;lr.capture();ConversationsUi oldList=new ConversationsUi(listActivity,lc);oldList.list(150,450);ConversationsUi newList=new ConversationsUi(listActivity,lc);newList.list(150,480);Queue.run();check(oldList.discussionScroll.y==0&&newList.discussionScroll.y==500,"old discussion list restore is listener fenced");
  newList.capture();ConversationsUi loggedOutList=new ConversationsUi(listActivity,lc);loggedOutList.list(150,500);lc.valid=false;Queue.run();check(loggedOutList.discussionScroll.y==0,"logout cancels discussion-list pre-draw restore");
  System.out.println("PASS M5 per-thread actual capture/binding + listener/account fences (host Views)");
 }
 static void shell(){MainActivity a=new MainActivity();a.shellProbe(800,700,1,0,0,0,0,new ArrayList<>());check(a.railNavigation&&!a.wide,"rail feasibility is not two useful panes: 800dp must retain usable detail width");
  a.shellProbe(1000,700,1,0,0,0,0,new ArrayList<>());check(a.wide&&a.railNavigation,"room for useful master and detail plus rail");
  a.shellProbe(1000,700,2,0,0,0,0,new ArrayList<>());check(!a.wide&&!a.railNavigation,"font scale participates in readable pane feasibility");
  a.shellProbe(1000,700,1,160,0,160,0,new ArrayList<>());check(!a.wide,"cutout/system insets reduce pane feasibility");check(a.shellLayout.left==160&&a.shellLayout.right==840,"insets owned exactly once");
  a.shellProbe(1000,700,1,0,0,0,0,Arrays.asList(new int[]{490,0,510,700}));check(!a.wide&&!a.railNavigation&&a.shellLayout.right<=490,"nonzero vertical hinge chooses contiguous useful single pane");
  a.shellProbe(1000,700,1,0,0,0,0,Arrays.asList(new int[]{0,280,1000,310}));check(a.shellLayout.top==310&&a.shellLayout.bottom==700,"horizontal occlusion avoids all overlapping controls");
  a.shellProbe(1000,700,1,0,0,0,0,Arrays.asList(new int[]{500,0,500,700}));check(a.wide,"zero-area fold line does not invent a hinge");
  a.shellProbe(1000,190,1,0,0,0,0,new ArrayList<>());check(a.railNavigation&&!a.wide,"short rail is scrollable while dual panes deliberately fall back");
  a.shellProbe(1000,700,1,0,0,0,550,new ArrayList<>());check(!a.wide&&!a.railNavigation,"IME usable height participates independently of window height");
  a.shellProbe(1000,700,1,0,24,0,32,new ArrayList<>());a.updateShellGeometry(a.rootFrame);check(a.root.paddingTop==24&&a.root.paddingBottom==32,"actual shell root applies each inset once");
  a.rootFrame.width=800;a.updateShellGeometry(a.rootFrame);Queue.run();check(!a.wide&&a.renders==1,"actual measured resize queues one fenced rebuild");
  a.emailUi=new EmailUi();a.rootFrame.width=1200;int before=a.renders;a.updateShellGeometry(a.rootFrame);Queue.run();check(a.renders==before,"email attached-input path never enters shell rebuild");a.emailUi=null;
  a.rootFrame.width=1000;a.updateShellGeometry(a.rootFrame);a.rootFrame=new FrameLayout(a);Queue.run();check(a.renders==before,"replaced root cancels posted geometry rebuild");
  int railBoundary=32+184+16+360,dualBoundary=32+184+16+300+29+360;
  a.shellProbe(railBoundary-1,700,1,0,0,0,0,new ArrayList<>());check(!a.railNavigation,"just below measured rail feasibility");a.shellProbe(railBoundary,700,1,0,0,0,0,new ArrayList<>());check(a.railNavigation&&!a.wide,"rail threshold independent from dual panes");a.shellProbe(dualBoundary-1,700,1,0,0,0,0,new ArrayList<>());check(!a.wide,"just below useful dual-pane widths");a.shellProbe(dualBoundary,700,1,0,0,0,0,new ArrayList<>());check(a.wide,"exact useful dual-pane threshold");
  Build.VERSION.SDK_INT=26;check(a.readShellPolicy(null).dual,"no WindowMetrics API falls back to actual decor bounds");Build.VERSION.SDK_INT=35;
  Random random=new Random(5);for(int n=0;n<1000;n++){int width=100+random.nextInt(1400),height=100+random.nextInt(900);List<int[]> features=new ArrayList<>();for(int i=0;i<3;i++){int x=random.nextInt(width),y=random.nextInt(height);features.add(new int[]{x,y,x+random.nextInt(60),y+random.nextInt(60)});}ShellLayoutPolicy.Plan plan=ShellLayoutPolicy.measure(width,height,1.5f,1.3f,12,24,16,32,features);check(plan.left>=12&&plan.top>=24&&plan.right<=width-16&&plan.bottom<=height-32,"safe rect stays inside inset-adjusted bounds");if(plan.right>plan.left&&plan.bottom>plan.top)for(int[] f:features)if(f[2]>f[0]&&f[3]>f[1])check(plan.right<=f[0]||plan.left>=f[2]||plan.bottom<=f[1]||plan.top>=f[3],"returned usable region never overlaps a reported area");}
  System.out.println("PASS M5 measured shell dispatch + real policy: rail/panes/font/insets/IME/vertical-horizontal occlusion/zero-area fallback");
 }
 static void nav(){MainActivity a=new MainActivity();a.buildPrimaryTabs();check(a.navigationSurface.parent==a.root,"compact navigation must be an in-flow root child, not rootFrame overlay");check(a.navigationSurface.color==a.ui.nav,"navigation must have opaque nav role");check(a.navigationClearance()==0,"in-flow navigation must not add duplicate list reserve");check(a.bottomTabs.children.size()==2,"destinations are Tasks and Discussions; creation is route-local");Button discussions=(Button)a.bottomTabs.children.get(1);discussions.action.run();check(a.destination.equals("Discussions"),"actual destination action navigates");LinearLayout shell=new LinearLayout(a);a.buildRail(shell);View rail=shell.children.get(0);check(rail instanceof ScrollView,"short-height rail must scroll to Settings");check(rail.color==a.ui.nav,"rail opaque nav role");LinearLayout items=(LinearLayout)((ScrollView)rail).children.get(0);Button first=(Button)items.children.get(0);check(first.size>=13,"readable rail label, no 10sp");check(items.children.stream().anyMatch(v->v instanceof Button&&((Button)v).text.equals("Settings")),"Settings labelled and reachable");}
}
class MainActivity extends LifecycleBase {
 boolean wide;String destination="Tasks",selected="";Controller conversations=new Controller();ConversationUi conversationUi;AuthPolicy.State authState=AuthPolicy.State.FULL;
 WorkspaceUi ui=new WorkspaceUi();LinearLayout root=new LinearLayout(this),bottomTabs;FrameLayout rootFrame=new FrameLayout(this),navigationSurface;float navigationOrigin=Float.NaN;String restoreComposerThread="";int restoreComposerPosition;int accent=1,ink=2;TasksUi tasksUi=new TasksUi();
 int dp(float v){return Math.round(v);}LinearLayout horizontal(){return new LinearLayout(this);}LinearLayout vertical(){return new LinearLayout(this);}Button button(String s,Runnable r){return ui.button(s,r);}void render(){
 // RENDER_PRESENTATION
 // SHELL_DECISION
 renders++;bindTask(draft.optString("title"));
 }void recreate(Bundle saved){
 // RECREATE_PRESENTATION
 bindTask(draft.optString("title"));
 }void settings(){}void space(LinearLayout p,int h){p.addView(new View(this));}android.graphics.drawable.Drawable getDrawable(int r){return new android.graphics.drawable.Drawable();}boolean useRail(){return wide;}
 // NAV_METHODS
 boolean restoring,showSearch,sortReverse;String filter="Active",query="",projectFilter="",sortOrder="Smart",colorMode="Project",detailsMode="Details",voiceEpoch="",voiceThread="";EmailUi emailUi;Email email;UpdateSheet summaryUi;void applyAppearance(){}
 Store.Account account=new Store.Account("epoch-a");Store store=new Store();boolean editing=true,online=true,busy;JSONObject draft=new JSONObject(),taskPresentation=new JSONObject(),taskPresentationInitial=new JSONObject();String taskEditorEpoch="",taskEditorTask="";int taskPresentationGeneration;boolean taskPresentationPending;
 EditText title,description,notes,project,due;Spinner priority=new Spinner(),status=new Spinner();String[] priorities={"high"},statuses={"todo"};ScrollView detailScroll,listScroll;int detailY,listY;
 void saveWorkspacePreferences(){}boolean alive(Store.Account o){return authState==AuthPolicy.State.FULL&&store.current(o);}void bindTask(String text){title=new EditText();title.text=text;description=new EditText();description.text=draft.optString("description");notes=new EditText();notes.text=draft.optString("notes");project=new EditText();project.text=draft.optString("project");due=new EditText();due.text=draft.optString("due_date");detailScroll=new ScrollView(this);
 // FIELD_ENABLEMENT
 // TASK_BINDING
 // TASK_OUTER_BINDING
 }
 // TASK_METHODS
 // OUTER_FIELDS
 int measuredW,measuredH,shellInsetLeft,shellInsetTop,shellInsetRight,shellInsetBottom,renders;float fontScale=1;final List<android.graphics.Rect> composerOcclusions=new ArrayList<>();boolean railNavigation,shellRebuildQueued;int shellMasterWidth,shellRailWidth;Queue main=new Queue();
 void shellProbe(int w,int h,float f,int l,int t,int r,int b,List<int[]> features){measuredW=w;measuredH=h;rootFrame.width=w;rootFrame.height=h;fontScale=f;shellInsetLeft=l;shellInsetTop=t;shellInsetRight=r;shellInsetBottom=b;composerOcclusions.clear();for(int[] x:features)composerOcclusions.add(new android.graphics.Rect(x[0],x[1],x[2],x[3]));
 // SHELL_DECISION
 }
 ShellLayoutPolicy.Plan shellLayout;
 Resources getResources(){return new Resources(fontScale,measuredW,measuredH);}WindowManager getWindowManager(){return new WindowManager(measuredW,measuredH);}Window getWindow(){return new Window(rootFrame);}
 // SHELL_METHODS
}
class Resources {float scale;int width,height;Resources(float s,int w,int h){scale=s;width=w;height=h;}Configuration getConfiguration(){Configuration c=new Configuration();c.fontScale=scale;return c;}Metrics getDisplayMetrics(){Metrics m=new Metrics();m.widthPixels=width;m.heightPixels=height;return m;}}
class Metrics {float density=1;int widthPixels,heightPixels;}
class WindowManager {int w,h;WindowManager(int w,int h){this.w=w;this.h=h;}WindowMetrics getCurrentWindowMetrics(){return new WindowMetrics(w,h);}}
class WindowMetrics {int w,h;WindowMetrics(int w,int h){this.w=w;this.h=h;}android.graphics.Rect getBounds(){return new android.graphics.Rect(0,0,w,h);}}
class Window {View decor;Window(View v){decor=v;}View getDecorView(){return decor;}}
class Build {static class VERSION {static int SDK_INT=35;}}
class Configuration {int screenWidthDp=1000;float fontScale=1;}
class TasksUi {void navigationClearance(int n){}void capture(){}void save(Bundle b){}}
class Controller {State state=new State();Object listener;boolean valid=true;String visible="";void save(){}boolean valid(){return valid;}}
class State {JSONObject data=new JSONObject();String selected(){return "";}JSONObject json(){return data;}void draft(String id,String text){data.put("draft:"+id,text);}}
class ConversationState {static void put(JSONObject o,String k,Object v){o.put(k,v);}}
class ConversationsUi {
 final MainActivity a;final Controller c;EditText input;String thread="",listOwner="";ScrollView discussionScroll;LinearLayout rows;LinkedHashMap<String,View> discussionRows=new LinkedHashMap<>();boolean composerRestoring,listRestoring;int composerGeneration,listGeneration;
 JSONObject composerInitial;
 ConversationsUi(MainActivity a,Controller c){this.a=a;this.c=c;}void bind(String id,String text){thread=id;c.listener=this;c.visible=id;input=new EditText();input.text=text;
 // CHAT_BINDING
 }void list(int rowTop,int itemTop){c.listener=this;listOwner="all";discussionScroll=new ScrollView(a);rows=new LinearLayout(a);rows.top=rowTop;View row=new View();row.top=itemTop-rowTop;row.height=80;discussionRows.put("thread-anchor",row);
 // LIST_BINDING
 }void captureViewport(){}
 // CHAT_METHODS
}
class ConversationUi {EditText input;String thread="";void capture(){}}
class AuthPolicy {enum State{FULL,LOGIN,CHECKING}}
class NativeMotionPolicy {static int duration(String s){return 0;}}
class WorkspaceUi {int soft=9,primary=1,secondary=2,nav=0xff172b4a,onNav=0xffedf3fc,navMuted=0xffbacbe2,navSelected=0xff294d79;FrameLayout glassSurface(){return new FrameLayout(null);}Object shape(int a,int b,int c){return a;}void selected(Button b,boolean s){}void tone(Button b,int f,int s,int t){b.color=f;}void enter(View v,int n){}void placeScopeCapsule(View a,View b,int s,float o){}Button button(String s,Runnable r){Button b=new Button();b.text=s;b.action=r;return b;}ImageButton icon(int id,String s,Runnable r){return new ImageButton();}
 // NAV_ROLE_METHODS
void leadingIcon(Button b,int r){} }
class View {static int VISIBLE=0,GONE=8,IMPORTANT_FOR_ACCESSIBILITY_NO=2;View parent;Object params;int color,width=400,height=64,top,y;List<View> children=new ArrayList<>();View(Object c){}View(){this(null);}int paddingLeft,paddingTop,paddingRight,paddingBottom;void getLocationInWindow(int[] o){o[0]=0;o[1]=0;}void setPadding(int a,int b,int c,int d){paddingLeft=a;paddingTop=b;paddingRight=c;paddingBottom=d;}void setBackground(Object b){}void setBackgroundColor(int c){color=c;}void setElevation(int n){}void setImportantForAccessibility(int i){}int getWidth(){return width;}int getHeight(){return height;}float getTranslationX(){return 0;}int getTop(){return top;}void setLayoutParams(Object p){params=p;}Object getLayoutParams(){return params;}void addOnLayoutChangeListener(Layout l){}void requestApplyInsets(){}void post(Runnable r){Queue.q.add(r);}android.view.ViewTreeObserver observer=new android.view.ViewTreeObserver();android.view.ViewTreeObserver getViewTreeObserver(){return observer;}void invalidate(){}void setVisibility(int v){}void addView(View v){v.parent=this;children.add(v);}void addView(View v,Object p){addView(v);v.params=p;}interface Layout{void change(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}}
class LinearLayout extends View {LinearLayout(Object c){super(c);}void setGravity(int n){}static class LayoutParams{int width,height;LayoutParams(int w,int h){width=w;height=h;}LayoutParams(int w,int h,int weight){this(w,h);}void setMargins(int l,int t,int r,int b){}void setMarginStart(int v){}}}
class FrameLayout extends View {FrameLayout(Object c){super(c);}static class LayoutParams extends LinearLayout.LayoutParams{int bottomMargin,leftMargin,rightMargin;LayoutParams(int w,int h){super(w,h);}LayoutParams(int w,int h,int g){super(w,h);}}}
class ScrollView extends View {ScrollView(Object c){super(c);}void setFillViewport(boolean b){}void setClipToPadding(boolean b){}int getScrollY(){return y;}void scrollTo(int x,int v){y=v;}}
class Button extends View {String text="";int size=14;Runnable action;String getText(){return text;}void setSelected(boolean b){}void setContentDescription(String s){}void setTextSize(int s){size=s;}void setMinHeight(int h){}int getCurrentTextColor(){return 1;}void setCompoundDrawables(Object a,Object b,Object c,Object d){}void setCompoundDrawablePadding(int n){} }
class ImageButton extends Button {}
class EditText extends View {boolean enabled=true,focused;int start,end,scrollX;String text="";int getSelectionStart(){return start;}int getSelectionEnd(){return end;}String getText(){return text;}int getScrollX(){return scrollX;}int getScrollY(){return y;}void scrollTo(int x,int s){scrollX=x;y=s;}void setEnabled(boolean value){enabled=value;}boolean isEnabled(){return enabled;}int length(){return text.length();}void requestFocus(){focused=true;}void setSelection(int s){start=end=s;}void setSelection(int s,int e){start=s;end=e;}boolean hasFocus(){return focused;}}
class Store {String epoch="epoch-a";static class Account {String epoch;Account(String e){epoch=e;}}boolean current(Account a){return a!=null&&epoch.equals(a.epoch);}}
class Spinner {int getSelectedItemPosition(){return 0;}}
class Bundle {Map<String,Object> values=new HashMap<>();void putString(String k,String v){values.put(k,v);}String getString(String k,String fallback){return (String)values.getOrDefault(k,fallback);}String getString(String k){return getString(k,"");}void putBoolean(String k,boolean v){values.put(k,v);}boolean getBoolean(String k){return (Boolean)values.getOrDefault(k,false);}boolean getBoolean(String k,boolean fallback){return (Boolean)values.getOrDefault(k,fallback);}void putInt(String k,int v){values.put(k,v);}int getInt(String k){return (Integer)values.getOrDefault(k,0);}boolean containsKey(String k){return values.containsKey(k);}}
class LifecycleBase {public void onConfigurationChanged(Configuration c){}protected void onSaveInstanceState(Bundle b){}}
class EmailUi {boolean needsStyleRebuild(Configuration c){return false;}void capture(){}void fitPane(){}}
class Email {void persist(){}}
class UpdateSheet {Dialog dialog=new Dialog();boolean waiting,failed;Store.Account owner;Object shown;UpdateSheet(Object a,Object b){}void show(){}}
class Dialog {void dismiss(){}}
class Drawable {Drawable mutate(){return this;}void setTint(int c){}void setBounds(int a,int b,int c,int d){}}
class Gravity {static int BOTTOM=1,CENTER_HORIZONTAL=2;}
class Color {static int TRANSPARENT=0;}
class R {static class drawable {static int ic_tasks=1,ic_chat=2,ic_settings=3;}}
class Queue {static ArrayDeque<Runnable> q=new ArrayDeque<>();void post(Runnable r){q.add(r);}static void run(){while(!q.isEmpty())q.remove().run();}}
class android {static class view {static class ViewTreeObserver {interface OnPreDrawListener{boolean onPreDraw();}boolean isAlive(){return true;}void removeOnPreDrawListener(OnPreDrawListener l){}void addOnPreDrawListener(OnPreDrawListener l){Queue.q.add(()->l.onPreDraw());}}}static class graphics {static class Rect {int left,top,right,bottom;Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}int width(){return right-left;}int height(){return bottom-top;}}static class drawable {static class Drawable extends com.simon.mytasks.Drawable{Drawable mutate(){return this;}}}}}

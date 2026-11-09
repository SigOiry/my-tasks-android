package com.simon.mytasks;
import android.app.Dialog;import android.view.*;import android.widget.*;import org.json.*;
/** A review owns immutable displayed prose/token even while canonical tasks refresh behind it. */
final class UpdateSheet {
 final MainActivity a;final UpdateReviewPolicy.Snapshot shown;final Dialog dialog;final Store.Account owner;final long generation;
 final LinearLayout panel;final TextView state;final Button acknowledge,latest;boolean waiting,failed;
 UpdateSheet(MainActivity activity,JSONObject task){this(activity,UpdateReviewPolicy.capture(task));}
 UpdateSheet(MainActivity activity,UpdateReviewPolicy.Snapshot snapshot){
  a=activity;shown=snapshot;owner=a.account;generation=a.surfaces.generation();dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setCanceledOnTouchOutside(true);
  FrameLayout material=a.ui.glassSurface();material.setPadding(a.dp(3),a.dp(3),a.dp(3),a.dp(3));panel=a.vertical();panel.setPadding(a.dp(16),a.dp(8),a.dp(16),a.dp(12));panel.setBackground(a.ui.shape(a.card,0,25));material.addView(panel,new FrameLayout.LayoutParams(-1,-2));
  LinearLayout header=a.horizontal();TextView label=a.heading("✧  Hermes",16);label.setTextColor(a.accent);label.setContentDescription("Hermes update review");header.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button close=a.button("Close",dialog::dismiss);a.ui.quiet(close);close.setContentDescription("Close update review and keep unread");header.addView(close);a.add(panel,header);
  LinearLayout prose=a.vertical();prose.setPadding(a.dp(4),a.dp(4),a.dp(4),a.dp(12));TextView title=a.heading(shown.title(),24);title.setTextIsSelectable(true);a.add(prose,title);
  boolean supplied=false;for(String[] section:new String[][]{{"context","Context"},{"update","What’s new"},{"next_steps","Next step"}}){String value=shown.section(section[0]);if(value.trim().isEmpty())continue;supplied=true;a.space(prose,16);a.add(prose,a.heading(section[1],14));TextView text=a.label("",16,a.ink);text.setText(MarkdownText.render(value));text.setTextIsSelectable(true);text.setAutoLinkMask(0);a.add(prose,text);}
  if(!supplied){if(!shown.headline().isEmpty())a.add(prose,a.label(shown.headline(),16,a.ink));a.add(prose,a.label("No written summary was supplied for this snapshot. Open details to review the saved task.",14,a.muted));}
  state=a.label("",13,a.muted);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);latest=a.button("Review latest update",()->{if(current()&&!waiting)a.refreshSummary(shown.taskId());});a.ui.secondary(latest);
  acknowledge=a.primary("Mark reviewed",()->{if(current()&&!waiting)a.markRead(shown.taskId(),shown.token());});
  LinearLayout actions=a.vertical();a.add(actions,state);a.add(actions,latest);a.space(actions,4);a.add(actions,acknowledge);a.space(actions,8);
  Button details=a.button("Open task",()->{if(!current())return;dialog.dismiss();a.openReviewedTask(shown.taskId(),false);});Button discuss=a.button("Ask Hermes",()->{if(!current())return;dialog.dismiss();a.openReviewedTask(shown.taskId(),true);});a.pair(actions,details,discuss);
  TextView explanation=a.label("Open task and Ask Hermes keep this update unread.",12,a.muted);a.add(actions,explanation);
  ScrollView body=new ScrollView(a);body.setFillViewport(false);boolean compact=a.getResources().getConfiguration().screenHeightDp<500||a.getResources().getConfiguration().fontScale>=1.5f;
  if(compact){a.add(prose,actions);body.addView(prose);panel.addView(body,new LinearLayout.LayoutParams(-1,-2,1));}else{body.addView(prose);panel.addView(body,new LinearLayout.LayoutParams(-1,-2,1));a.add(panel,actions);}
  dialog.setContentView(material);a.trackDialog(dialog);dialog.setOnDismissListener(d->{material.animate().cancel();a.forgetDialog(dialog);if(a.summaryDialog==dialog){a.summaryDialog=null;a.summaryUi=null;}});dialog.setOnShowListener(d->{updateState();NativeSheets.fit(a,dialog,material,false);});
 }
 boolean current(){return a.alive(owner)&&a.surfaces.current(generation)&&a.summaryUi==this;}
 void show(){a.summaryUi=this;a.summaryDialog=dialog;updateState();NativeSheets.fit(a,dialog,(View)panel.getParent(),false);dialog.show();}
 void updateState(){
  if(!current())return;boolean newer=UpdateReviewPolicy.newer(shown,a.task(shown.taskId()));boolean fresh=!a.online||a.ackNeedsRefresh||failed;
  acknowledge.setEnabled(!waiting&&!a.busy&&!fresh&&!newer&&!shown.token().isEmpty());acknowledge.setText(waiting?"Acknowledging…":"Mark reviewed");latest.setVisibility(newer||fresh?View.VISIBLE:View.GONE);latest.setEnabled(!waiting&&!a.busy);latest.setText(a.online?"Review latest update":"Refresh before acknowledging");
  if(waiting)state.setText("Only the displayed snapshot is being acknowledged.");else if(newer)state.setText("A newer update has arrived. This review has not changed; review the latest snapshot before acknowledging.");else if(failed)state.setText("Acknowledgement was not confirmed. Your displayed summary is retained; refresh before retrying.");else if(!a.online)state.setText("Cached summary · connect and refresh before acknowledging.");else state.setText("Review this update, then explicitly acknowledge it.");
 }
 void waiting(boolean value){waiting=value;updateState();}
 void failure(){waiting=false;failed=true;updateState();}
}

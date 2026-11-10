package com.simon.mytasks;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import org.json.JSONObject;

/** Retained Tasks presenter. ListView owns recycling; Activity owns canonical state. */
final class TasksUi {
 static final int NAVIGATION_CLEARANCE=104;
 private final MainActivity a;
 private final UpdateEmphasis emphasis=new UpdateEmphasis();
 private final ArrayList<Entry> entries=new ArrayList<>();
 private ListView list;
 private Rows adapter;
 private TextView count;
 private Button create;
 private Runnable restore;
 private View scopeCapsule;private LinearLayout scopeTrack;private float scopeOrigin=Float.NaN;
 private final Anchor anchor=new Anchor();
 private String viewFilter,projectFilter,query,sortOrder;
 private boolean paused,anchorReset,touching,scrolling,pendingRefresh,reverse;
 private String colorMode;
 private final Runnable deferredRefresh=()->{if(!paused&&pendingRefresh)refresh();};

 TasksUi(MainActivity activity){a=activity;emphasis.onSlotAvailable(()->{if(list!=null)list.post(()->{if(effectsAtRest()&&a.foreground&&list!=null)resume();});});}
 void build(LinearLayout parent){
  if(list!=null){capture();pause();list.setAdapter(null);}
  paused=false;touching=scrolling=false;
  WorkspaceUi ui=a.ui;
  FrameLayout dock=new FrameLayout(a);parent.addView(dock,new LinearLayout.LayoutParams(-1,0,1));
  list=new ListView(a);list.setBackgroundColor(ui.canvas);list.setDivider(null);list.setDividerHeight(0);
  list.setClipToPadding(false);list.setPadding(0,0,0,ui.dp(NAVIGATION_CLEARANCE));
  list.setCacheColorHint(android.graphics.Color.TRANSPARENT);
  list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
  dock.addView(list,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout controls=a.vertical();controls.setPadding(ui.dp(4),0,ui.dp(4),0);
  LinearLayout titlebar=a.horizontal();TextView page=a.heading(a.projectFilter.isEmpty()?"Tasks":a.projectFilter,30);page.setMaxLines(2);titlebar.addView(page,new LinearLayout.LayoutParams(0,-2,1));
  titlebar.addView(ui.icon(R.drawable.ic_search,"Search tasks",()->{a.captureDraft();a.showSearch=!a.showSearch;a.render();}),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
  titlebar.addView(ui.icon(R.drawable.ic_more,"Task views, project, sort and settings",this::moreMenu),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));a.add(controls,titlebar);
  FrameLayout segment=ui.glassSurface();LinearLayout scopes=a.horizontal();scopeTrack=scopes;scopes.setPadding(ui.dp(3),ui.dp(3),ui.dp(3),ui.dp(3));
  View capsule=new View(a);scopeCapsule=capsule;capsule.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);capsule.setBackground(ui.shape((ui.soft&0x00ffffff)|0x99000000,0,24));segment.addView(capsule,new FrameLayout.LayoutParams(0,0));
  int selectedScope=0;String[] visibleScopes=TaskPresentation.visibleScopes(a.filter);
  for(int i=0;i<visibleScopes.length;i++){String scope=visibleScopes[i];boolean selected=a.filter.equals(scope);if(selected)selectedScope=i;Button tab=ui.button(scope,()->selectScope(scope));ui.selected(tab,selected);ui.tone(tab,android.graphics.Color.TRANSPARENT,0,selected?ui.primary:ui.secondary);tab.setMinHeight(ui.dp(48));tab.setPadding(ui.dp(6),ui.dp(4),ui.dp(6),ui.dp(4));scopes.addView(tab,new LinearLayout.LayoutParams(0,-2,1));}
  final int slot=selectedScope;final float origin=scopeOrigin;scopeOrigin=Float.NaN;final boolean[] placed={false};
  scopes.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(!placed[0]||r-l!=or-ol||b-t!=ob-ot){ui.placeScopeCapsule(capsule,scopes,slot,placed[0]?Float.NaN:origin);placed[0]=true;}});
  segment.addView(scopes,new FrameLayout.LayoutParams(-1,-2));a.add(controls,segment);
  if(!"Project".equals(a.sortOrder)){TextView order=ui.text("Sorted by "+a.sortOrder.toLowerCase(java.util.Locale.getDefault()),12,ui.secondary,false);order.setPadding(ui.dp(8),ui.dp(4),ui.dp(8),ui.dp(4));a.add(controls,order);}
  page.setContentDescription((a.projectFilter.isEmpty()?"Tasks":a.projectFilter)+", "+a.filter+", sorted by "+a.sortOrder);
  if(a.showSearch||!a.query.isEmpty()){
   EditText search=new EditText(a);ui.input(search);search.setSingleLine(true);search.setHint("Search tasks or projects");search.setContentDescription("Search tasks or projects");search.setSaveEnabled(false);search.setText(a.query);
   controls.addView(search,new LinearLayout.LayoutParams(-1,-2));
   search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int length,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int length){a.query=s.toString();resetAnchor();refresh();}});
  }
  count=ui.text("",12,ui.secondary,false);count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  count.setVisibility(View.GONE);
  list.addHeaderView(controls,null,false);
  // Real footer leaves the final task/overflow reachable above the floating Add.
  View footer=new View(a);footer.setMinimumHeight(ui.dp(88));list.addFooterView(footer,null,false);
  adapter=new Rows();list.setAdapter(adapter);
  list.setRecyclerListener(v->{if(v instanceof TaskRowView)((TaskRowView)v).stopEmphasis();});
  list.setOnTouchListener((v,event)->{int action=event.getActionMasked();if(action==android.view.MotionEvent.ACTION_DOWN){touching=true;stopRowMotion();pauseRowEmphasis();}else if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL){touching=false;if(pendingRefresh)list.postDelayed(deferredRefresh,200);list.postDelayed(this::resume,200);}return false;});
  list.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){scrolling=state!=AbsListView.OnScrollListener.SCROLL_STATE_IDLE;if(scrolling){stopRowMotion();pauseRowEmphasis();}else{if(pendingRefresh)list.postDelayed(deferredRefresh,200);list.postDelayed(TasksUi.this::resume,200);}}public void onScroll(AbsListView v,int first,int visible,int total){if(effectsAtRest())for(int i=0;i<v.getChildCount();i++)if(v.getChildAt(i) instanceof TaskRowView)((TaskRowView)v.getChildAt(i)).resume();}});
  create=ui.button("+",()->a.navigate("NEW"));create.setEnabled(a.online&&!a.busy);ui.primary(create);create.setTextSize(28);create.setContentDescription("Create a new task");create.setTooltipText("New task");create.setPadding(0,0,0,0);create.setElevation(ui.dp(WorkspaceUi.ELEVATION_FLOATING));
  FrameLayout.LayoutParams add=new FrameLayout.LayoutParams(ui.dp(56),ui.dp(56),Gravity.BOTTOM|Gravity.END);add.setMargins(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));dock.addView(create,add);
  refresh();
 }
 void selectScope(String scope){
  scopeOrigin=scopeCapsule!=null&&scopeTrack!=null&&scopeTrack.getWidth()>0?scopeCapsule.getTranslationX()/scopeTrack.getWidth():Float.NaN;
  a.captureDraft();a.filter=scope;resetAnchor();a.saveWorkspacePreferences();a.render();a.ui.enter(a.listPane,NativeMotionPolicy.duration("tab"));
 }
 void moreMenu(){
  String[] labels=TaskPresentation.menuScopes();android.app.AlertDialog[] holder=new android.app.AlertDialog[1];LinearLayout refinements=a.vertical();refinements.setPadding(a.ui.dp(16),a.ui.dp(8),a.ui.dp(16),0);
  a.add(refinements,a.ui.button("Project · "+(a.projectFilter.isEmpty()?"All projects":a.projectFilter),()->{holder[0].dismiss();a.projectMenu();}));a.add(refinements,a.ui.button("Sort · "+a.sortOrder,()->{holder[0].dismiss();a.sortMenu();}));
  a.add(refinements,a.ui.button("Color by · "+a.colorMode,()->{holder[0].dismiss();a.colorMenu();}));
  holder[0]=a.dialogBuilder().setTitle("Task views & filters").setSingleChoiceItems(labels,TaskPresentation.selectedScope(a.filter),(d,index)->{
   a.captureDraft();a.filter=labels[index];resetAnchor();a.saveWorkspacePreferences();d.dismiss();a.render();
  }).setView(refinements).setNeutralButton("Refresh",(d,w)->a.refresh()).setNegativeButton("Settings",(d,w)->a.settings()).create();holder[0].show();
 }
 void refresh(){
  if(list==null)return;
  boolean changed=!sameFilters();
  if(!changed&&deferRefresh()){pendingRefresh=true;list.removeCallbacks(deferredRefresh);if(!paused)list.postDelayed(deferredRefresh,200);return;}
  final java.util.Map<String,Float> oldPositions=changed?java.util.Collections.emptyMap():snapshotRowPositions();
  pendingRefresh=false;list.removeCallbacks(deferredRefresh);
  if(changed&&(!a.filter.equals(viewFilter)||!a.projectFilter.equals(projectFilter)||!a.query.equals(query)))resetAnchor();else capture();
  viewFilter=a.filter;projectFilter=a.projectFilter;query=a.query;sortOrder=a.sortOrder;reverse=a.sortReverse;colorMode=a.colorMode;
  ArrayList<JSONObject> shown=new ArrayList<>();LocalDate today=LocalDate.now();int unread=0;
  for(int i=0;i<a.tasks.length();i++){
   JSONObject task=a.tasks.optJSONObject(i);
   if(task!=null&&PresentationPolicy.matches(task,a.filter,a.projectFilter,a.query,today)){
    shown.add(task);JSONObject n=task.optJSONObject("notification");if(n!=null&&n.optBoolean("unread"))unread++;
   }
  }
  Collections.sort(shown,(left,right)->PresentationPolicy.compare(left,right,a.sortOrder,a.sortReverse,today,java.time.ZoneId.systemDefault()));
  entries.clear();String last=null;int lastLane=-1;
  for(JSONObject task:shown){
   int lane=PresentationPolicy.smartLane(task);
   if(lane!=lastLane){
    String label=lane%3==0?"Actionable":lane%3==1?"Waiting":"Inactive";
    if(lane>=3)label=lane==3?"Deleted":"Deleted · "+label;
    entries.add(new Entry(label,null));lastLane=lane;last=null;
   }
   String group="Smart".equals(a.sortOrder)?null:"Priority".equals(a.sortOrder)||"Importance".equals(a.sortOrder)?a.human(task.optString("priority"))+" importance":"Due date".equals(a.sortOrder)?a.dueLabel(task):"Project".equals(a.sortOrder)?PresentationPolicy.project(task):a.sortOrder;
   if(group!=null&&!group.equals(last)){entries.add(new Entry(group,null));last=group;}
   entries.add(new Entry(null,task));
  }
  if(shown.isEmpty())entries.add(new Entry(null,null));
  count.setText(shown.size()+" tasks"+(unread>0?" · "+unread+" unread":"")+(a.projectFilter.isEmpty()?"":" · "+a.projectFilter)+(a.online?"":" · Read only"));
  create.setEnabled(a.online&&!a.busy);
  adapter.notifyDataSetChanged();restoreAnchor();
  if(!oldPositions.isEmpty()){
   final ListView target=list;
   target.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
    if(target.getViewTreeObserver().isAlive())target.getViewTreeObserver().removeOnPreDrawListener(this);
    if(list==target&&!anchorReset)applyRowPositions(oldPositions);return true;
   }});
  }
 }
 /** Bounded visible-only FLIP: layout/selection stays canonical, only pixels move. */
 private java.util.Map<String,Float> snapshotRowPositions(){
  java.util.Map<String,Float> positions=new java.util.HashMap<>();
  if(list==null||deferRefresh()||!a.ui.motionEnabled())return positions;
  for(int i=0;i<list.getChildCount()&&positions.size()<12;i++){View row=list.getChildAt(i);if(row instanceof TaskRowView&&row.getTag() instanceof String)positions.put((String)row.getTag(),row.getTop()+row.getTranslationY());}
  return positions;
 }
 private void applyRowPositions(java.util.Map<String,Float> positions){
  if(list==null||deferRefresh()||!a.ui.motionEnabled())return;
  for(int i=0;i<list.getChildCount();i++){
   View row=list.getChildAt(i);if(!(row instanceof TaskRowView))continue;Float old=positions.get(row.getTag());if(old==null)continue;
   float offset=old-row.getTop();row.animate().cancel();row.setTranslationY(0);
   if(Math.abs(offset)<1||Math.abs(offset)>a.ui.dp(160))continue;
   row.setTranslationY(offset);row.animate().translationY(0).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
  }
 }
 private void resetRowMotion(View row){row.animate().cancel();row.setTranslationY(0);}
 private void stopRowMotion(){if(list!=null)for(int i=0;i<list.getChildCount();i++)if(list.getChildAt(i) instanceof TaskRowView)resetRowMotion(list.getChildAt(i));}
 /** Measured floating navigation can grow when system text is enlarged. */
 void navigationClearance(int pixels){
  if(list==null)return;int clearance=Math.max(a.ui.dp(88),pixels);
  if(list.getPaddingBottom()!=clearance)list.setPadding(0,0,0,clearance);
  // The Add action shares the floating navigation baseline, not a second action tier.
 }
 private boolean deferRefresh(){return paused||touching||scrolling||a.editing||a.busy||!a.dialogClosers.isEmpty();}
 boolean effectsAtRest(){return !paused&&!touching&&!scrolling&&!a.busy&&!a.editing&&a.dialogClosers.isEmpty();}
 private void pauseRowEmphasis(){if(list!=null)for(int i=0;i<list.getChildCount();i++)if(list.getChildAt(i) instanceof TaskRowView)((TaskRowView)list.getChildAt(i)).pause();}
 void resume(){if(!a.foreground)return;paused=false;if(!effectsAtRest())return;if(pendingRefresh&&list!=null)list.post(deferredRefresh);if(list!=null&&list.isShown())for(int i=0;i<list.getChildCount();i++)if(list.getChildAt(i) instanceof TaskRowView)((TaskRowView)list.getChildAt(i)).resume();}
 private boolean sameFilters(){return a.filter.equals(viewFilter)&&a.projectFilter.equals(projectFilter)&&a.query.equals(query)&&a.sortOrder.equals(sortOrder)&&a.sortReverse==reverse&&a.colorMode.equals(colorMode);}
 /** Filter changes deliberately reset reading position; polling never does. */
 void resetAnchor(){anchor.reset();anchorReset=true;if(list!=null&&restore!=null){list.removeCallbacks(restore);restore=null;}}
 void capture(){
  if(list==null||list.getChildCount()==0||!sameFilters()||restore!=null||anchorReset)return;
  anchor.remember("",list.getChildAt(0).getTop()-list.getPaddingTop(),list.getFirstVisiblePosition());
  int headers=list.getHeaderViewsCount();
  // A visible toolbar owns the viewport, even when tasks are visible below it.
  // Task-relative restoration across changing header heights drifts the toolbar.
  if(list.getFirstVisiblePosition()<headers)return;
  for(int i=0;i<list.getChildCount();i++){
   int index=list.getFirstVisiblePosition()+i-headers;
   if(index>=0&&index<entries.size()&&entries.get(index).task!=null){anchor.remember(entries.get(index).task.optString("id",""),list.getChildAt(i).getTop()-list.getPaddingTop(),index+headers);break;}
  }
 }
 private void restoreAnchor(){
  if(restore!=null)list.removeCallbacks(restore);
  final ListView target=list;
  restore=()->{if(list!=target)return;restore=null;
   ArrayList<String> ids=new ArrayList<>();for(Entry e:entries)ids.add(e.task==null?"":e.task.optString("id",""));
   target.setSelectionFromTop(anchor.position(ids,target.getHeaderViewsCount(),target.getCount()),anchor.anchorTop);anchorReset=false;
  };
  target.post(restore);
 }
 void save(Bundle out){
  capture();out.putString("tasks_ui.anchor_id",anchor.anchorId);out.putInt("tasks_ui.anchor_top",anchor.anchorTop);out.putInt("tasks_ui.anchor_position",anchor.anchorPosition);
  out.putString("tasks_ui.filter",a.filter);out.putString("tasks_ui.project",a.projectFilter);out.putString("tasks_ui.query",a.query);out.putString("tasks_ui.sort",a.sortOrder);
 }
 void restore(Bundle state){
  if(state==null||!state.containsKey("tasks_ui.anchor_position"))return;
  anchor.remember(state.getString("tasks_ui.anchor_id",""),state.getInt("tasks_ui.anchor_top"),state.getInt("tasks_ui.anchor_position"));anchorReset=true;
  viewFilter=state.getString("tasks_ui.filter",a.filter);projectFilter=state.getString("tasks_ui.project",a.projectFilter);query=state.getString("tasks_ui.query",a.query);sortOrder=state.getString("tasks_ui.sort",a.sortOrder);
 }
 /** Suppress only the token returned by the mutation, not a later GET's update. */
 void acceptOwnChange(String id,String token){
  emphasis.observe(id,token);
  for(int i=0;i<a.tasks.length();i++){
   JSONObject t=a.tasks.optJSONObject(i);if(t==null||!id.equals(t.optString("id")))continue;
   JSONObject n=t.optJSONObject("notification");if(n==null||!token.equals(n.optString("token","")))return;
   if(list!=null)for(int child=0;child<list.getChildCount();child++){View row=list.getChildAt(child);if(row instanceof TaskRowView&&id.equals(row.getTag()))((TaskRowView)row).stopEmphasis();}
   return;
  }
 }
 void pause(){
  paused=true;stopRowMotion();touching=scrolling=false;if(list!=null)list.removeCallbacks(deferredRefresh);
  if(list!=null){if(restore!=null){list.removeCallbacks(restore);restore=null;}for(int i=0;i<list.getChildCount();i++)if(list.getChildAt(i) instanceof TaskRowView)((TaskRowView)list.getChildAt(i)).pause();}
  emphasis.pause();
 }
 void clear(){
  pause();emphasis.clear();entries.clear();resetAnchor();
  if(list!=null)list.setAdapter(null);list=null;adapter=null;count=null;create=null;
  viewFilter=null;projectFilter=null;query=null;sortOrder=null;
 }
 private final class Rows extends BaseAdapter {
  @Override public int getCount(){return entries.size();}
  @Override public Object getItem(int position){return entries.get(position);}
  @Override public long getItemId(int position){return position;}
  @Override public boolean hasStableIds(){return false;}
  @Override public int getViewTypeCount(){return 3;}
  @Override public int getItemViewType(int p){Entry e=entries.get(p);return e.task!=null?0:e.group!=null?1:2;}
  @Override public boolean areAllItemsEnabled(){return false;}
  @Override public boolean isEnabled(int p){return entries.get(p).task!=null;}
  @Override public View getView(int position,View recycled,ViewGroup parent){
   Entry e=entries.get(position);WorkspaceUi ui=a.ui;
   if(e.task!=null){TaskRowView row=recycled instanceof TaskRowView?(TaskRowView)recycled:new TaskRowView(a,emphasis);resetRowMotion(row);row.bind(e.task,"Project".equals(a.sortOrder));row.groupCorners(position==0||entries.get(position-1).task==null,position==entries.size()-1||entries.get(position+1).task==null);if(!effectsAtRest())row.pause();return row;}
   if(e.group!=null){TextView heading=recycled instanceof TextView?(TextView)recycled:ui.text("",13,ui.secondary,true);heading.setText(e.group.toUpperCase(java.util.Locale.getDefault()));heading.setPadding(ui.dp(4),ui.dp(16),ui.dp(4),ui.dp(8));if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);return heading;}
   LinearLayout empty=new LinearLayout(a);empty.setOrientation(LinearLayout.VERTICAL);empty.setPadding(ui.dp(20),ui.dp(24),ui.dp(20),ui.dp(24));
   empty.addView(ui.text("No tasks in this view",20,ui.ink,true));empty.addView(ui.text("Try another view, clear filters, or add a task when connected.",15,ui.secondary,false));
   if(!a.query.isEmpty()||!a.projectFilter.isEmpty()||!"Active".equals(a.filter))empty.addView(ui.button("Clear filters",()->{a.captureDraft();a.query="";a.projectFilter="";a.filter="Active";resetAnchor();a.saveWorkspacePreferences();a.render();}));
   if(!a.online)empty.addView(ui.button("Connection settings",a::settings));return empty;
  }
 }
 static final class Anchor {
  String anchorId="";int anchorTop,anchorPosition;
  void remember(String id,int top,int position){anchorId=id;anchorTop=top;anchorPosition=position;}
  void reset(){remember("",0,0);}
  int position(java.util.List<String> ids,int headers,int count){
   int position=anchorPosition;
   if(!anchorId.isEmpty()){int index=ids.indexOf(anchorId);if(index>=0)position=index+headers;}
   return Math.max(0,Math.min(position,count-1));
  }
 }
 private static final class Entry {final String group;final JSONObject task;Entry(String group,JSONObject task){this.group=group;this.task=task;}}
}

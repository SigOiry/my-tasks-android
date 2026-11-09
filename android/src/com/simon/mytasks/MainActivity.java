package com.simon.mytasks;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.speech.RecognizerIntent;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Native, dependency-free activity. Server is authoritative; cached tasks never accept edits. */
public final class MainActivity extends Activity {
 Store store;Store.Account account;
 JSONObject snapshot=new JSONObject();JSONArray tasks=new JSONArray();
 JSONObject draft;String selected="",filter="Active",query="",chatTask="",chatDraft="",chatModeValue="question";
 JSONArray history=new JSONArray();
 boolean online=false,busy=false,wide=false,restoring=false;
 String banner="Connect your server in Settings",chatStatus="",rejectedChatBody="";
 int bg,card,ink,muted,line,accent;int listY,detailY;
 LinearLayout root,listPane,detailPane,listItems,editor,chatMessages;TextView stateLabel;
 ScrollView listScroll,detailScroll;EditText title,description,notes,due,project,chatInput;Spinner priority,status,chatMode;
 String[] priorities={"high","medium","low"},statuses={"inbox","todo","in_progress","waiting","completed","cancelled"};
 final Handler main=new Handler(Looper.getMainLooper());
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 String text(JSONObject o,String key){return o==null||o.isNull(key)?"":o.optString(key,"");}
 @Override public void onCreate(Bundle saved){
  boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
  setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(saved);
  bg=Color.parseColor(dark?"#101715":"#F3F5F1");card=Color.parseColor(dark?"#1A2420":"#FFFFFF");ink=Color.parseColor(dark?"#ECF2EB":"#182C24");muted=Color.parseColor(dark?"#A6B7AD":"#53685D");line=Color.parseColor(dark?"#314139":"#D7E1D8");accent=Color.parseColor(dark?"#A5D9BB":"#23674B");
  getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
  if(!dark)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  store=new Store(this);
  if(saved!=null){selected=saved.getString("selected","");filter=saved.getString("filter","Active");query=saved.getString("query","");chatTask=saved.getString("chatTask","");chatDraft=saved.getString("chatDraft","");chatStatus=saved.getString("chatStatus","");listY=saved.getInt("listY");detailY=saved.getInt("detailY");try{if(saved.containsKey("draft"))draft=new JSONObject(saved.getString("draft"));history=new JSONArray(saved.getString("history","[]"));}catch(Exception ignored){}}
  try{account=store.account();}catch(Exception e){banner="Credentials unavailable. Re-enter Settings.";}
  if(saved!=null){chatModeValue=saved.getString("chatMode","question");String epoch=saved.getString("epoch","");if(account==null||!account.epoch.equals(epoch)){selected="";draft=null;history=new JSONArray();chatDraft="";chatTask="";}if(chatStatus.contains("thinking"))chatStatus="Previous request was interrupted. The original provider request may still be running. Your message was not resent automatically; wait before deciding to send again.";}
  JSONObject cached=store.cached();if(cached!=null){snapshot=cached;tasks=cached.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();banner="Offline cache · read only — refresh to edit";}
  if(draft!=null){JSONObject base=task(selected);if(base!=null){JSONObject merged=copy(base);Iterator<String> keys=draft.keys();while(keys.hasNext()){String k=keys.next();try{merged.put(k,draft.get(k));}catch(Exception ignored){}}draft=merged;}}
  if(saved==null)acceptIntent(getIntent());render();PollService.schedule(this);if(account!=null)refresh();
 }
 @Override protected void onResume(){super.onResume();UpdateManager.get(this).attach(this);}
 @Override protected void onPause(){UpdateManager.get(this).detach(this);super.onPause();}
 void acceptIntent(Intent i){String id=i.getStringExtra("task_id");String epoch=i.getStringExtra("account_epoch");if(id!=null&&account!=null&&account.epoch.equals(epoch)){selected=id;draft=null;}}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);if(busy){notice("Task update","Finish the current request, then select the updated task from the list.");return;}captureDraft();Runnable open=()->{acceptIntent(i);setIntent(i);render();refresh();};if(dirty())confirm("Open updated task?","Unsaved task fields will be discarded.",open);else open.run();}
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);captureDraft();boolean dark=(config.uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;getTheme().applyStyle(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar,true);bg=Color.parseColor(dark?"#101715":"#F3F5F1");card=Color.parseColor(dark?"#1A2420":"#FFFFFF");ink=Color.parseColor(dark?"#ECF2EB":"#182C24");muted=Color.parseColor(dark?"#A6B7AD":"#53685D");line=Color.parseColor(dark?"#314139":"#D7E1D8");accent=Color.parseColor(dark?"#A5D9BB":"#23674B");getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);render();}
 @Override protected void onSaveInstanceState(Bundle out){captureDraft();out.putString("epoch",account==null?"":account.epoch);out.putString("chatMode",chatModeValue);out.putString("selected",selected);out.putString("filter",filter);out.putString("query",query);out.putString("chatTask",chatTask);out.putString("chatDraft",chatDraft);out.putString("chatStatus",chatStatus);out.putString("history",history.toString());if(draft!=null)out.putString("draft",stateDraft().toString());out.putInt("listY",listScroll==null?listY:listScroll.getScrollY());out.putInt("detailY",detailScroll==null?detailY:detailScroll.getScrollY());super.onSaveInstanceState(out);}
 JSONObject stateDraft(){JSONObject state=new JSONObject();for(String k:new String[]{"title","description","notes","project","due_date","status","priority","revision","id","deleted"})try{if(draft.has(k))state.put(k,draft.get(k));}catch(Exception ignored){}return state;}
 boolean alive(Store.Account a){return !isFinishing()&&!isDestroyed()&&store.current(a);}
 LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 GradientDrawable surface(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
 TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
 TextView heading(String s,int size){TextView t=label(s,size,ink);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
 Button button(String name,Runnable action){Button b=new Button(this);b.setText(name);b.setTextColor(accent);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b;}
 void add(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 void space(LinearLayout parent,int h){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
 void render(){
  wide=getResources().getConfiguration().screenWidthDp>=720;
  title=description=notes=due=project=chatInput=null;priority=status=chatMode=null;
  root=vertical();root.setBackgroundColor(bg);root.setPadding(dp(16),dp(8),dp(16),0);
  root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom,left,right;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime()|WindowInsets.Type.displayCutout());top=i.top;bottom=i.bottom;left=i.left;right=i.right;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();}v.setPadding(dp(16)+left,dp(8)+top,dp(8)+right,bottom);return insets;});
  setContentView(root);root.requestApplyInsets();
  LinearLayout bar=horizontal();TextView logo=heading("My Tasks",26);bar.addView(logo,new LinearLayout.LayoutParams(0,-2,1));bar.addView(button("Refresh",()->{captureDraft();refresh();}));bar.addView(button("Settings",this::settings));add(root,bar);
  stateLabel=label(banner,12,muted);stateLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(root,stateLabel);
  LinearLayout body=horizontal();body.setGravity(Gravity.TOP);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
  listPane=vertical();detailPane=vertical();
  if(wide){body.addView(listPane,new LinearLayout.LayoutParams(0,-1,0.42f));View divider=new View(this);divider.setBackgroundColor(line);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(1),-1);dl.setMargins(dp(12),0,dp(12),0);body.addView(divider,dl);body.addView(detailPane,new LinearLayout.LayoutParams(0,-1,0.58f));}
  else body.addView(selected.isEmpty()?listPane:detailPane,new LinearLayout.LayoutParams(-1,-1));
  if(wide||selected.isEmpty())buildList();if(wide||!selected.isEmpty())buildDetails();
 }
 void buildList(){
  LinearLayout intro=horizontal();TextView h=heading("Your focus",21);intro.addView(h,new LinearLayout.LayoutParams(0,-2,1));Button create=button("+ New",()->navigate("NEW"));create.setEnabled(online&&!busy);intro.addView(create);add(listPane,intro);
  int active=0,unread=0;for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t==null||t.optBoolean("deleted"))continue;if(!terminal(t))active++;JSONObject n=t.optJSONObject("notification");if(n!=null&&n.optBoolean("unread"))unread++;}
  add(listPane,label(active+" open  ·  "+unread+" unread updates",13,muted));
  HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);LinearLayout chips=horizontal();for(String f:new String[]{"Active","Today","Week","All","Trash"}){Button b=button(f,()->{filter=f;buildListRows();});if(f.equals(filter))b.setTypeface(null,Typeface.BOLD);chips.addView(b);}filters.addView(chips);add(listPane,filters);
  EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(ink);search.setTextSize(15);search.setHint("Search tasks or projects");search.setHintTextColor(muted);search.setText(query);search.setContentDescription("Search tasks or projects");add(listPane,search);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();buildListRows();}public void afterTextChanged(Editable e){}});
  listScroll=new ScrollView(this);listScroll.setFillViewport(true);listItems=vertical();listScroll.addView(listItems);listPane.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));buildListRows();listScroll.post(()->listScroll.scrollTo(0,listY));
 }
 boolean terminal(JSONObject t){return "completed".equals(t.optString("status"))||"cancelled".equals(t.optString("status"));}
 Core.SortKey key(JSONObject t){String p=text(t,"project");return new Core.SortKey(t.optString("id"),p.isEmpty()?"Unsorted":p,t.optString("priority"),text(t,"due_date"));}
 void buildListRows(){if(listItems==null)return;listItems.removeAllViews();ArrayList<JSONObject> shown=new ArrayList<>();String today=snapshot.optString("today"),end=snapshot.optString("week_end");
  for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t==null)continue;boolean deleted=t.optBoolean("deleted");if(deleted!=filter.equals("Trash"))continue;if(!filter.equals("All")&&!filter.equals("Trash")&&terminal(t))continue;String date=text(t,"due_date");if(filter.equals("Today")&&(date.isEmpty()||date.compareTo(today)>0))continue;if(filter.equals("Week")&&(date.isEmpty()||date.compareTo(end)>0))continue;if(!(text(t,"title")+" "+text(t,"project")+" "+text(t,"description")).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))continue;shown.add(t);}
  Collections.sort(shown,(a,b)->Core.ORDER.compare(key(a),key(b)));String group="";
  for(JSONObject t:shown){Core.SortKey k=key(t);String g=k.project+" · "+k.priority;if(!g.equals(group)){space(listItems,10);add(listItems,label(g.toUpperCase(Locale.ROOT),11,muted));group=g;}
   LinearLayout row=vertical();row.setPadding(dp(14),dp(10),dp(14),dp(10));row.setBackground(surface(card,selected.equals(k.id)?accent:line));row.setMinimumHeight(dp(80));row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->navigate(k.id));
   LinearLayout first=horizontal();TextView taskTitle=heading(text(t,"title"),16);taskTitle.setMaxLines(3);first.addView(taskTitle,new LinearLayout.LayoutParams(0,-2,1));JSONObject n=t.optJSONObject("notification");if(n!=null&&n.optBoolean("unread")){TextView badge=label(" NEW ",10,accent);badge.setTypeface(null,Typeface.BOLD);first.addView(badge);}add(row,first);
   String meta=text(t,"status").replace('_',' ')+(k.due.isEmpty()?"  ·  No deadline":"  ·  "+k.due);add(row,label(meta,12,muted));add(listItems,row);space(listItems,7);
  }
  if(shown.isEmpty()){space(listItems,40);add(listItems,heading("Nothing here",22));add(listItems,label(account==null?"Add your HTTPS server and token in Settings.":"Choose another view, change your search, or create a task.",15,muted));}
  space(listItems,20);
 }
 JSONObject task(String id){for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&id.equals(t.optString("id")))return t;}return null;}
 JSONObject copy(JSONObject t){try{return new JSONObject(t.toString());}catch(Exception e){return new JSONObject();}}
 JSONObject empty(){JSONObject o=new JSONObject();try{o.put("title","").put("description","").put("notes","").put("project","").put("due_date","").put("priority","medium").put("status","todo");}catch(Exception ignored){}return o;}
 void navigate(String id){if(busy){notice("Please wait","A request is still in progress.");return;}captureDraft();Runnable go=()->{selected=id;draft=null;detailY=0;chatTask=id;history=new JSONArray();chatDraft="";chatStatus="";render();};if(dirty())confirm("Discard unsaved changes?","Your unsaved task fields will be lost.",go);else go.run();}
 boolean dirty(){if(draft==null)return false;JSONObject old=selected.equals("NEW")?empty():task(selected);if(old==null)return true;for(String f:new String[]{"title","description","notes","project","due_date","priority","status"})if(!text(draft,f).equals(text(old,f)))return true;return false;}
 void captureDraft(){if(draft!=null&&title!=null){try{draft.put("title",title.getText().toString()).put("description",description.getText().toString()).put("notes",notes.getText().toString()).put("project",project.getText().toString()).put("due_date",due.getText().toString()).put("priority",priorities[priority.getSelectedItemPosition()]).put("status",statuses[status.getSelectedItemPosition()]);}catch(Exception ignored){}}if(chatInput!=null)chatDraft=chatInput.getText().toString();if(chatMode!=null&&chatMode.getSelectedItem()!=null)chatModeValue=chatMode.getSelectedItem().toString();if(listScroll!=null)listY=listScroll.getScrollY();if(detailScroll!=null)detailY=detailScroll.getScrollY();}
 EditText field(LinearLayout parent,String name,String value,boolean multi){add(parent,label(name,12,muted));EditText e=new EditText(this);e.setText(value);e.setTextColor(ink);e.setHintTextColor(muted);e.setTextSize(16);e.setSingleLine(!multi);e.setMinLines(multi?3:1);e.setGravity(Gravity.TOP);e.setContentDescription(name);e.setSelectAllOnFocus(false);e.setPadding(dp(10),dp(10),dp(10),dp(10));e.setBackground(surface(card,line));e.setEnabled(online&&!busy);e.setSaveEnabled(false);add(parent,e);space(parent,8);return e;}
 Spinner choice(LinearLayout parent,String name,String[] options,String value){add(parent,label(name,12,muted));Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);s.setAdapter(a);for(int i=0;i<options.length;i++)if(options[i].equals(value))s.setSelection(i);s.setEnabled(online&&!busy);s.setContentDescription(name);add(parent,s);return s;}
 void buildDetails(){
  if(selected.isEmpty()){space(detailPane,90);add(detailPane,heading("Room to focus.",30));add(detailPane,label("Select a task to see its context, make a plan, or ask Hermes for help.",17,muted));return;}
  LinearLayout top=horizontal();if(!wide)top.addView(button("‹ Tasks",()->navigate("")));TextView h=heading(selected.equals("NEW")?"New task":"Task details",20);top.addView(h,new LinearLayout.LayoutParams(0,-2,1));Button reload=button("Reload latest",()->confirm("Reload latest task?","Discard unsaved fields and fetch the current server version.",()->{draft=null;refresh();}));reload.setEnabled(account!=null&&!busy);top.addView(reload);add(detailPane,top);
  detailScroll=new ScrollView(this);editor=vertical();editor.setPadding(0,0,dp(8),dp(28));detailScroll.addView(editor);detailPane.addView(detailScroll,new LinearLayout.LayoutParams(-1,0,1));
  JSONObject current=task(selected);if(draft==null)draft=selected.equals("NEW")?empty():current==null?null:copy(current);
  if(draft==null){add(editor,label("Task is not in the cache. Refresh to load it.",16,muted));return;}
  JSONObject n=draft.optJSONObject("notification");if(n!=null&&n.optBoolean("unread")){
   LinearLayout update=vertical();update.setPadding(dp(14),dp(12),dp(14),dp(12));update.setBackground(surface(card,line));add(update,heading(n.optString("headline","Task updated"),17));add(update,label(PollService.summary(n),14,ink));String token=n.optString("token");Button read=button("Mark this update read",()->markRead(token));read.setEnabled(online&&!busy);add(update,read);add(editor,update);space(editor,12);
  }
  if(draft.optBoolean("deleted")){add(editor,heading("In trash",22));add(editor,label(text(draft,"title"),18,ink));add(editor,label(text(draft,"description"),15,muted));Button restore=button("Restore task",()->mutation("POST","/api/tasks/"+Api.segment(selected)+"/restore",revision(),false));restore.setEnabled(online&&!busy);add(editor,restore);return;}
  title=field(editor,"Title",text(draft,"title"),false);description=field(editor,"Description",text(draft,"description"),true);
  priority=choice(editor,"Priority",priorities,text(draft,"priority"));status=choice(editor,"Status",statuses,text(draft,"status"));
  due=field(editor,"Due date · YYYY-MM-DD, blank for none",text(draft,"due_date"),false);due.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
  project=field(editor,"Project / category",text(draft,"project"),false);notes=field(editor,"Your notes",text(draft,"notes"),true);
  LinearLayout actions=horizontal();Button save=button(selected.equals("NEW")?"Create task":"Save changes",()->save(false));save.setEnabled(online&&!busy);actions.addView(save);if(!selected.equals("NEW")){Button complete=button("Complete",()->save(true));complete.setEnabled(online&&!busy);actions.addView(complete);}add(editor,actions);
  if(!selected.equals("NEW")){
   Button delete=button("Move to trash",()->{captureDraft();confirm("Move task to trash?","The task is soft-deleted and can be restored from Trash. Unsaved fields are not saved.",()->mutation("DELETE","/api/tasks/"+Api.segment(selected),revision(),false));});delete.setEnabled(online&&!busy);add(editor,delete);
   String source=text(draft,"source");if(!source.isEmpty()){add(editor,label("SOURCE · "+source,11,muted));JSONArray accounts=draft.optJSONArray("source_accounts");if(accounts!=null){StringBuilder names=new StringBuilder();for(int i=0;i<accounts.length();i++){if(i>0)names.append(" · ");names.append(accounts.optString(i));}add(editor,label(names.toString(),12,muted));}JSONArray evidence=draft.optJSONArray("evidence");if(evidence!=null&&evidence.length()>0){TextView ev=label(evidenceText(evidence),13,muted);ev.setTextIsSelectable(true);add(editor,ev);}}
   buildChat();
  }
  detailScroll.post(()->detailScroll.scrollTo(0,detailY));
 }
 String evidenceText(JSONArray a){StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(a.length(),12);i++){String s=a.optString(i);if(s.length()>1600)s=s.substring(0,1600)+"…";b.append(s).append("\n\n");}return b.toString();}
 JSONObject revision(){JSONObject o=new JSONObject();try{o.put("revision",text(draft,"revision"));}catch(Exception ignored){}return o;}
 void save(boolean complete){captureDraft();try{
  if(text(draft,"title").trim().isEmpty())throw new IllegalArgumentException("Title is required");String date=text(draft,"due_date").trim();if(!date.isEmpty()){LocalDate.parse(date);if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("Use YYYY-MM-DD");}
  JSONObject body=new JSONObject();for(String f:new String[]{"title","description","notes","priority","status"})body.put(f,text(draft,f));body.put("project",text(draft,"project").trim().isEmpty()?JSONObject.NULL:text(draft,"project").trim());body.put("due_date",date.isEmpty()?JSONObject.NULL:date);if(complete)body.put("status","completed");boolean create=selected.equals("NEW");if(!create)body.put("revision",text(draft,"revision"));mutation(create?"POST":"PATCH",create?"/api/tasks":"/api/tasks/"+Api.segment(selected),body,true);
 }catch(Exception e){notice("Check task fields",e instanceof java.time.format.DateTimeParseException?"Use a valid date in YYYY-MM-DD format.":e.getMessage());}}
 void mutation(String method,String path,JSONObject body,boolean clearDraft){
  if(!online||busy||account==null)return;captureDraft();busy=true;banner="Saving…";Store.Account a=account;render();
  Api.IO.execute(()->{try{JSONObject result=new Api(a).call(method,path,body);String id=result.getString("id");JSONObject latest=new Api(a).call("GET","/api/tasks",null);store.cache(a,latest);main.post(()->{if(!alive(a))return;busy=false;online=true;snapshot=latest;tasks=latest.optJSONArray("tasks");selected=id;draft=copy(result);banner="Saved · up to date";render();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;if(e instanceof Api.Failure&&((Api.Failure)e).code==409){banner="Conflict · draft preserved — reload latest before saving";render();notice("Task changed", "Your draft is preserved. Copy any changes you need, then choose Reload latest. Nothing was overwritten.");}else{online=false;banner=Api.friendly(e)+" A write may already have reached the server; refresh and check before retrying.";render();}});}});
 }
 void markRead(String token){
  if(account==null||!online||busy)return;captureDraft();Store.Account a=account;String id=selected;busy=true;banner="Marking displayed update read…";render();
  Api.IO.execute(()->{try{
   JSONObject t=new Api(a).call("POST","/api/tasks/"+Api.segment(id)+"/updates/read",new JSONObject().put("token",token));
   main.post(()->{if(!alive(a))return;busy=false;try{if(draft!=null&&id.equals(selected))draft.put("notification",t.getJSONObject("notification"));for(int i=0;i<tasks.length();i++){JSONObject item=tasks.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))tasks.put(i,t);}}catch(Exception ignored){}((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(id,0);banner="Displayed update marked read";render();});
  }catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;banner="Update not acknowledged · refresh for current summary";render();notice("Could not mark read",Api.friendly(e));});}});
 }
 void refresh(){
  if(account==null){settings();return;}if(busy)return;captureDraft();final boolean keepDraft=dirty();Store.Account a=account;busy=true;banner="Refreshing…";render();
  Api.IO.execute(()->{try{JSONObject latest=new Api(a).call("GET","/api/tasks",null);store.cache(a,latest);String notificationNote="";try{PollService.poll(this,store,a,()->isDestroyed());}catch(Exception e){notificationNote=" · update checks delayed";}final String suffix=notificationNote;main.post(()->{if(!alive(a))return;busy=false;online=true;snapshot=latest;tasks=latest.optJSONArray("tasks");if(tasks==null)tasks=new JSONArray();if(!keepDraft)draft=null;else if(draft!=null){JSONObject remote=task(selected);if(remote!=null)try{draft.put("notification",remote.optJSONObject("notification"));}catch(Exception ignored){}}banner="Connected · "+tasks.length()+" tasks"+suffix;render();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;online=false;banner=Api.friendly(e);render();});}});
 }
 void buildChat(){
  space(editor,24);add(editor,heading("Ask Hermes",22));add(editor,label("Task-linked help and email drafts. Nothing is sent as email. Review every response before using it.",13,muted));
  if(!chatTask.equals(selected)){chatTask=selected;history=new JSONArray();chatDraft="";}
  chatMessages=vertical();for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);add(chatMessages,label(("user".equals(m.optString("role"))?"You\n":"Hermes\n")+m.optString("content"),14,ink));space(chatMessages,6);}add(editor,chatMessages);if(!chatStatus.isEmpty())add(editor,label(chatStatus,13,muted));
  chatMode=choice(editor,"Mode",new String[]{"question","email_draft"},chatModeValue);chatInput=field(editor,"Message · review voice text before sending",chatDraft,true);
  LinearLayout row=horizontal();Button voice=button("Voice input",this::voice);voice.setEnabled(online&&!busy);row.addView(voice);Button send=button("Send to Hermes",this::sendChat);send.setEnabled(online&&!busy);row.addView(send);add(editor,row);
 }
 void sendChat(){
  captureDraft();if(!online||busy||account==null||chatDraft.trim().isEmpty())return;
  String message=chatDraft;Store.Account a=account;List<String[]> prior=new ArrayList<>();
  for(int i=0;i<history.length();i++){JSONObject t=history.optJSONObject(i);if(t!=null)prior.add(new String[]{t.optString("role"),t.optString("content")});}
  final String body;
  try{body=Core.chatBody(selected,message,chatModeValue,prior);}catch(IllegalArgumentException e){notice("Check chat message",e.getMessage());return;}
  if(body.equals(rejectedChatBody)){notice("Message still too large",Core.chatFailure(413));return;}
  busy=true;chatStatus="Hermes is thinking… (waiting up to 200 seconds; provider work may continue after a timeout)";render();
  Api.IO.execute(()->{try{JSONObject result=new Api(a).callChat(body);main.post(()->{if(!alive(a))return;busy=false;try{history.put(new JSONObject().put("role","user").put("content",message));history.put(new JSONObject().put("role","assistant").put("content",result.optString("reply")));history=copyHistory();}catch(Exception ignored){}chatDraft="";chatStatus="";render();});}catch(Exception e){main.post(()->{if(!alive(a))return;busy=false;if(e instanceof Api.Failure&&((Api.Failure)e).code==413)rejectedChatBody=body;chatStatus=Core.chatFailure(e instanceof Api.Failure?((Api.Failure)e).code:0);render();});}});
 }
 JSONArray copyHistory(){JSONArray a=new JSONArray();for(int i=Math.max(0,history.length()-12);i<history.length();i++){JSONObject turn=history.optJSONObject(i);if(turn==null)continue;try{String content=turn.optString("content");a.put(new JSONObject().put("role",turn.optString("role")).put("content",content));}catch(Exception ignored){}}return a;}
 void voice(){
  noticeConfirm("Platform speech recognition","Your device's speech recognition app may send audio to its provider. My Tasks does not record audio. The recognized text will be placed in the message field for you to review; it is never sent automatically.",()->{
   Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,"Dictate a message for Hermes");
   try{startActivityForResult(i,108);}catch(ActivityNotFoundException e){notice("No speech recognizer","Install or enable a platform speech recognition app, or type your message instead.");}
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==UpdateManager.PERMISSION_REQUEST||request==UpdateManager.INSTALL_REQUEST){UpdateManager.get(this).result(request);return;}if(request==108&&result==RESULT_OK&&data!=null){ArrayList<String> values=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(values!=null&&!values.isEmpty()){chatDraft=(chatInput==null?chatDraft:chatInput.getText().toString())+(chatDraft.isEmpty()?"":" ")+values.get(0);if(chatInput!=null){chatInput.setText(chatDraft);chatInput.setSelection(chatDraft.length());}notice("Review voice input","The transcript is in your message field. Edit it, then tap Send when ready.");}}}
 void settings(){
  captureDraft();LinearLayout form=vertical();form.setPadding(dp(22),dp(8),dp(22),dp(12));add(form,label("Your server must use HTTPS with a trusted certificate. Tokens are encrypted with Android Keystore. Changing settings clears the local cache and notification cursor.",14,muted));
  EditText origin=new EditText(this);origin.setHint("https://tasks.example");origin.setSingleLine(true);origin.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);origin.setText(account==null?"":account.server);origin.setContentDescription("HTTPS backend origin");add(form,origin);
  EditText token=new EditText(this);token.setHint(account==null?"Server token":"New token (required to change account)");token.setSingleLine(true);token.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setSaveEnabled(false);token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);token.setContentDescription("Server bearer token");add(form,token);
  add(form,label("Notifications: best-effort periodic checks, at least 15 minutes apart. Android may delay them much longer (Doze, battery saver, force-stop or offline). Refresh checks now. First connection quietly establishes a baseline; it does not alert for old events.",13,muted));
  add(form,button("Enable notifications",()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},71);else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));}));
  add(form,label("App updates · installed "+UpdateManager.get(this).installedName()+". Public GitHub checks are independent of your server/account. Automatic checks occur on the first foreground launch each local day; manual checks work anytime with internet access.",13,muted));
  add(form,button("Check for updates",()->UpdateManager.get(this).check(true)));
  ScrollView scroll=new ScrollView(this);scroll.addView(form);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connection & privacy").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save & reconnect",null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(busy){notice("Please wait","A request is in progress. Try settings again when it finishes.");return;}String url=origin.getText().toString(),secret=token.getText().toString();try{Core.server(url);if(secret.trim().isEmpty())throw new IllegalArgumentException("Enter the server token to save settings.");}catch(Exception e){token.setError(e.getMessage());return;}
   Runnable apply=()->{busy=true;Api.IO.execute(()->{try{store.configure(url,secret);Store.Account next=store.account();main.post(()->{if(isDestroyed())return;account=next;snapshot=new JSONObject();tasks=new JSONArray();selected="";draft=null;history=new JSONArray();chatDraft="";chatTask="";online=false;busy=false;banner="Connection saved";dialog.dismiss();render();PollService.schedule(this);refresh();});}catch(Exception e){main.post(()->{busy=false;notice("Settings not saved","Could not securely save credentials. Verify your HTTPS origin and token, then retry.");});}});};
   if(dirty())confirm("Change connection?","Unsaved fields will be discarded and cached tasks cleared.",apply);else apply.run();}));dialog.show();
 }
 void notice(String title,String message){if(isDestroyed())return;new AlertDialog.Builder(this).setTitle(title).setMessage(message==null?"Please retry.":message).setPositiveButton("OK",null).show();}
 void confirm(String title,String message,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->yes.run()).show();}
 void noticeConfirm(String title,String message,Runnable yes){confirm(title,message,yes);}
 @Override public void onBackPressed(){if(!selected.isEmpty()){navigate("");}else super.onBackPressed();}
}

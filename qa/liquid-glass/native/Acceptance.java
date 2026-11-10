package com.simon.mytasks.uiqa;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Real Android instrumentation; targets synthetic QA only, never production. */
public final class Acceptance extends Instrumentation {
 private Bundle args;private Activity activity;
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments==null?new Bundle():arguments;start();}
 private void onMain(Runnable task){final Throwable[] error={null};super.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0].getMessage(),error[0]);}
 private void settle()throws Exception{for(int i=0;i<100;i++){final boolean[] ready={false};onMain(()->{try{java.lang.reflect.Field state=activity.getClass().getDeclaredField("authState"),busy=activity.getClass().getDeclaredField("busy");state.setAccessible(true);busy.setAccessible(true);ready[0]=!"CHECKING".equals(String.valueOf(state.get(activity)))&&!busy.getBoolean(activity);}catch(Exception e){throw new RuntimeException(e);}});if(ready[0]){Thread.sleep(400);return;}Thread.sleep(100);}throw new AssertionError("workspace did not settle");}
 private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private List<View> all(){List<View> result=new ArrayList<>();try{java.lang.reflect.Field f=activity.getClass().getDeclaredField("dialogClosers");f.setAccessible(true);for(Object o:((Map<?,?>)f.get(activity)).keySet()){Dialog d=(Dialog)o;if(d.isShowing())walk(d.getWindow().getDecorView(),result);}}catch(ReflectiveOperationException unavailable){}walk(activity.getWindow().getDecorView(),result);return result;}
 private void walk(View v,List<View> list){if(v.getVisibility()!=View.VISIBLE)return;list.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)walk(((ViewGroup)v).getChildAt(i),list);}
 private String text(View v){return v instanceof TextView?((TextView)v).getText().toString():"";}
 private View find(String value){for(View v:all())if(value.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||value.equals(text(v)))return v;return null;}
 private EditText editable(String label){for(View v:all())if(v instanceof EditText&&label.equals(String.valueOf(v.getContentDescription())))return (EditText)v;throw new AssertionError("editable field exists: "+label);}
 private void click(View v){View current=v;while(current.getParent() instanceof View){View parent=(View)current.getParent();if(parent instanceof AdapterView){AdapterView<?> list=(AdapterView<?>)parent;int position=list.getPositionForView(current);if(position>=0&&list.getOnItemClickListener()!=null){list.performItemClick(current,position,list.getAdapter().getItemId(position));return;}}current=parent;}while(!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();require(v.isClickable(),"native clickable action exists");v.performClick();}
 private void back(){try{java.lang.reflect.Field f=activity.getClass().getDeclaredField("dialogClosers");f.setAccessible(true);for(Object o:((Map<?,?>)f.get(activity)).keySet()){Dialog d=(Dialog)o;if(d.isShowing()&&d.getWindow().getDecorView().hasWindowFocus()){d.onBackPressed();return;}}}catch(ReflectiveOperationException e){throw new RuntimeException(e);}activity.onBackPressed();}
 private void await(String label)throws Exception{for(int i=0;i<100;i++){final boolean[] present={false};onMain(()->present[0]=find(label)!=null);if(present[0])return;Thread.sleep(150);}throw new AssertionError("Timed out waiting for "+label);}
 private void login()throws Exception{
  await("Task server");
  onMain(()->{EditText server=null,user=null,password=null;for(View v:all())if(v instanceof EditText){String label=String.valueOf(v.getContentDescription());if(label.equals("Task server"))server=(EditText)v;if(label.equals("Username"))user=(EditText)v;if(label.equals("Password"))password=(EditText)v;}require(server!=null&&user!=null&&password!=null,"normal QA login fields exist");server.setText("https://127.0.0.1:9147");user.setText("qa-owner");password.setText(args.getString("fixture_password",""));for(View v:all())if(v instanceof Button&&text(v).equals("Sign in")){click(v);break;}});
  await("Active");settle();
 }
 private JSONObject snapshot()throws Exception{
  JSONObject result=new JSONObject();JSONArray nodes=new JSONArray();float density=activity.getResources().getDisplayMetrics().density;
  result.put("widthDp",activity.getResources().getConfiguration().screenWidthDp);result.put("heightDp",activity.getResources().getConfiguration().screenHeightDp);result.put("fontScale",activity.getResources().getConfiguration().fontScale);result.put("density",density);
  android.content.SharedPreferences appearance=getTargetContext().getSharedPreferences("appearance",Context.MODE_PRIVATE);result.put("theme",appearance.getString("theme","System")).put("reduceMotion",appearance.getBoolean("reduce_motion",false)).put("reduceTransparency",appearance.getBoolean("reduce_transparency",false));
  for(String key:new String[]{"selected","destination","detailsMode","wide","editing","online","filter","projectFilter","sortOrder"}){try{java.lang.reflect.Field f=activity.getClass().getDeclaredField(key);f.setAccessible(true);result.put(key,f.get(activity));}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
  try{java.lang.reflect.Field f=activity.getClass().getDeclaredField("conversationUi");f.setAccessible(true);Object conversation=f.get(activity);if(conversation!=null){for(String key:new String[]{"thread","boundTask"}){java.lang.reflect.Field value=conversation.getClass().getDeclaredField(key);value.setAccessible(true);result.put(key,value.get(conversation));}}}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
  for(View v:all()){
   Rect b=new Rect();if(!v.getGlobalVisibleRect(b))continue;JSONObject node=new JSONObject();node.put("class",v.getClass().getName()).put("text",text(v)).put("label",String.valueOf(v.getContentDescription())).put("bounds",new JSONArray(new int[]{b.left,b.top,b.right,b.bottom})).put("widthDp",v.getWidth()/density).put("heightDp",v.getHeight()/density).put("clickable",v.isClickable()).put("enabled",v.isEnabled()).put("selected",v.isSelected()).put("tag",String.valueOf(v.getTag())).put("scrollY",v.getScrollY());if(v instanceof TextView){TextView text=(TextView)v;boolean ellipsized=false;if(text.getLayout()!=null)for(int row=0;row<text.getLayout().getLineCount();row++)ellipsized|=text.getLayout().getEllipsisCount(row)>0;node.put("ellipsized",ellipsized).put("maxLines",text.getMaxLines());}if(v instanceof CompoundButton)node.put("checked",((CompoundButton)v).isChecked());if(v.getClass().getSimpleName().equals("GlassLayout")){java.lang.reflect.Field material=v.getClass().getDeclaredField("opaque");material.setAccessible(true);node.put("opaqueMaterial",material.getBoolean(v));}nodes.put(node);
  }result.put("nodes",nodes);return result;
 }
 private void tasks()throws Exception{
  await("Active");settle();onMain(()->{for(View v:all())if(v instanceof ListView){((ListView)v).setSelection(0);break;}});Thread.sleep(700);
  onMain(()->{for(View v:all())if(v instanceof ListView){ListView list=(ListView)v;if(list.getChildCount()>0&&activity.getResources().getConfiguration().fontScale<=1.01f){float header=list.getChildAt(0).getHeight()/activity.getResources().getDisplayMetrics().density;require(header<=128.5f,"Tasks chrome fits 128dp budget; actual="+header);}break;}});
  onMain(()->{View active=find("Active");require(active!=null,"scope visible");int count=0;for(View v:all())if(v instanceof CheckBox){Rect b=new Rect();if(v.getGlobalVisibleRect(b)&&b.height()>=v.getHeight())count++;float d=activity.getResources().getDisplayMetrics().density;require(v.getWidth()/d>=47.5f&&v.getHeight()/d>=47.5f,"completion control has 48dp native target");}int minimum=activity.getResources().getConfiguration().screenWidthDp>=720?4:6;require(count>=minimum,"useful fully visible synthetic task completion controls; actual="+count+", expected="+minimum);});
 }
 private void hostRequest(String name)throws Exception{
  require(name.matches("[a-zA-Z0-9_-]+"),"safe host request");File directory=new File(getTargetContext().getExternalFilesDir(null),"evidence");directory.mkdirs();File ack=new File(directory,"capture-ack"),request=new File(directory,"capture-request");ack.delete();try(FileOutputStream out=new FileOutputStream(request)){out.write(name.getBytes("UTF-8"));}
  boolean confirmed=false;for(int i=0;i<150;i++){if(ack.isFile()){try(FileInputStream in=new FileInputStream(ack)){byte[] value=new byte[(int)ack.length()];int read=in.read(value);if(read==value.length&&name.equals(new String(value,"UTF-8"))){confirmed=true;break;}}}Thread.sleep(100);}require(confirmed,"host must confirm "+name);
 }
 private void saveEvidence(String name)throws Exception{
  final JSONObject[] state={null};onMain(()->{try{state[0]=snapshot();}catch(Exception e){throw new RuntimeException(e);}});
  require(name.matches("[a-zA-Z0-9_-]+"),"safe evidence name");File directory=new File(getTargetContext().getExternalFilesDir(null),"evidence");require(directory.isDirectory()||directory.mkdirs(),"evidence directory writable");try(FileOutputStream out=new FileOutputStream(new File(directory,name+".json"))){out.write(state[0].toString(2).getBytes("UTF-8"));}
  Bitmap screenshot=Bitmap.createBitmap(activity.getWindow().getDecorView().getWidth(),activity.getWindow().getDecorView().getHeight(),Bitmap.Config.ARGB_8888);java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);int[] copyStatus={-1};PixelCopy.request(activity.getWindow(),screenshot,status->{copyStatus[0]=status;copied.countDown();},new Handler(Looper.getMainLooper()));require(copied.await(5,java.util.concurrent.TimeUnit.SECONDS)&&copyStatus[0]==PixelCopy.SUCCESS,"native Window PixelCopy available");try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}
  if("true".equals(args.getString("capture_handshake","false")))hostRequest(name);
 }
 private void scenario()throws Exception{
  JSONArray steps=new JSONArray(args.getString("steps","[]"));
  for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);String op=step.getString("op"),label=step.optString("label");
   if(op.equals("host")){hostRequest("host-"+step.getString("action"));settle();}
   else if(op.equals("await"))await(label);
   else if(op.equals("tap")){await(label);onMain(()->{View v=find(label);require(v!=null,"action exists: "+label);click(v);});}
   else if(op.equals("tapLast")){onMain(()->{View target=null;for(View v:all())if(label.equals(text(v))||label.equals(String.valueOf(v.getContentDescription())))target=v;require(target!=null,"last matching native action exists");click(target);});}
   else if(op.equals("tapIfPresent")){onMain(()->{View v=find(label);if(v!=null)click(v);});}
   else if(op.equals("aligned")){onMain(()->{View first=find(label),second=find(step.optString("other"));require(first!=null&&second!=null,"alignment controls exist");Rect x=new Rect(),y=new Rect();require(first.getGlobalVisibleRect(x)&&second.getGlobalVisibleRect(y),"aligned controls visible");float distance=Math.abs(x.exactCenterY()-y.exactCenterY())/activity.getResources().getDisplayMetrics().density;require(distance<=12,"floating controls share one baseline; difference dp="+distance);});}
   else if(op.equals("longPress")){await(label);onMain(()->{View v=find(label);while(!v.isLongClickable()&&v.getParent() instanceof View)v=(View)v.getParent();require(v.isLongClickable(),"long press target exists");v.performLongClick();});}
   else if(op.equals("accessibilityAction")){onMain(()->{View target=null;for(View v:all()){Rect bounds=new Rect();if(v instanceof TextView&&((TextView)v).isTextSelectable()&&text(v).startsWith(label)&&v.getGlobalVisibleRect(bounds))target=v;}require(target!=null,"visible selectable message exists");int action=step.optInt("id");if(step.has("actionLabel")){action=0;for(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction candidate:target.createAccessibilityNodeInfo().getActionList())if(step.optString("actionLabel").contentEquals(candidate.getLabel()==null?"":candidate.getLabel())){action=candidate.getId();break;}}require(action!=0&&target.performAccessibilityAction(action,new Bundle()),"message exposes the native review accessibility action");});}
   else if(op.equals("state")){onMain(()->{String field=step.optString("field");require(java.util.Arrays.asList("selected","destination","detailsMode","wide","editing","online","filter","projectFilter","sortOrder").contains(field),"allowed presentation state");try{java.lang.reflect.Field f=activity.getClass().getDeclaredField(field);f.setAccessible(true);require(step.optString("value").equals(String.valueOf(f.get(activity))),"actual presentation state "+field+" must equal "+step.optString("value"));}catch(ReflectiveOperationException e){throw new RuntimeException(e);}});}
   else if(op.equals("unclippedText")){await(label);onMain(()->{View v=find(label);require(v instanceof TextView,"text node exists");TextView text=(TextView)v;require(text.getLayout()!=null,"native text layout exists");for(int j=0;j<text.getLayout().getLineCount();j++)require(text.getLayout().getEllipsisCount(j)==0,"large text is not ellipsized: "+label);});}
   else if(op.equals("visibleInput")){onMain(()->{EditText v=editable(label);Rect bounds=new Rect();float density=activity.getResources().getDisplayMetrics().density;require(v.getGlobalVisibleRect(bounds)&&bounds.height()/density>=47.5f,"editable input has 48dp visible without scrolling: "+label);});}
   else if(op.equals("select")){onMain(()->{boolean changed=false;for(View v:all())if(v instanceof Spinner&&(label.equals(String.valueOf(v.getContentDescription()))||String.valueOf(v.getContentDescription()).startsWith(label+","))){Spinner spinner=(Spinner)v;for(int j=0;j<spinner.getAdapter().getCount();j++)if(step.optString("value").equals(String.valueOf(spinner.getAdapter().getItem(j)))){spinner.setSelection(j);changed=true;break;}}require(changed,"native selector value exists: "+label);});}
   else if(op.equals("text")){await(label);onMain(()->{editable(label).setText(step.optString("text"));});}
   else if(op.equals("assert")){await(label);}
   else if(op.equals("absent")){onMain(()->require(find(label)==null,"unexpected control: "+label));}
   else if(op.equals("back")){onMain(this::back);}
   else if(op.equals("keyboard")){await(label);onMain(()->{View v=editable(label);v.requestFocus();((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(v,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});}
   else if(op.equals("hideKeyboard")){onMain(()->{View focus=activity.getCurrentFocus();if(focus!=null)((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);});}
   else if(op.equals("capture")){saveEvidence(step.getString("name"));}
   else if(op.equals("scroll")){onMain(()->{for(View v:all())if(v instanceof ScrollView){((ScrollView)v).smoothScrollBy(0,step.optInt("dy",300));break;}else if(v instanceof ListView){((ListView)v).smoothScrollBy(step.optInt("dy",300),350);break;}});}
   else if(!op.equals("wait"))throw new AssertionError("unknown scenario action "+op);
   Thread.sleep(Math.min(10000,step.optInt("ms",350)));
  }
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
  require(getTargetContext().getPackageName().equals("com.simon.mytasks.qa"),"instrumentation must target isolated QA only");
  Intent intent=new Intent(Intent.ACTION_MAIN).setClassName("com.simon.mytasks.qa","com.simon.mytasks.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=startActivitySync(intent);waitForIdleSync();settle();
  String mode=args.getString("mode","snapshot");if(mode.equals("login"))login();else if(mode.equals("scenario"))scenario();else if(mode.equals("tasks"))tasks();else if(mode.equals("tap")){String label=args.getString("label","");await(label);onMain(()->{View v=find(label);require(v!=null,"action exists");click(v);});Thread.sleep(500);}else if(mode.equals("draft")){String label=args.getString("label","Message Hermès; Send submits this draft");await(label);onMain(()->{View v=find(label);require(v instanceof EditText,"composer exists");((EditText)v).setText(args.getString("text","Synthetic unsent multiline\ndraft survives folding"));v.requestFocus();((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(v,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});Thread.sleep(700);}
  final JSONObject[] state={null};onMain(()->{try{state[0]=snapshot();}catch(Exception e){throw new RuntimeException(e);}});
  String name=args.getString("name","native-"+mode);require(name.matches("[a-zA-Z0-9_-]+"),"safe evidence name");File directory=new File(getTargetContext().getExternalFilesDir(null),"evidence");require(directory.isDirectory()||directory.mkdirs(),"evidence directory writable");try(FileOutputStream out=new FileOutputStream(new File(directory,name+".json"))){out.write(state[0].toString(2).getBytes("UTF-8"));}
  Bitmap screenshot=Bitmap.createBitmap(activity.getWindow().getDecorView().getWidth(),activity.getWindow().getDecorView().getHeight(),Bitmap.Config.ARGB_8888);java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);int[] copyStatus={-1};PixelCopy.request(activity.getWindow(),screenshot,status->{copyStatus[0]=status;copied.countDown();},new Handler(Looper.getMainLooper()));require(copied.await(5,java.util.concurrent.TimeUnit.SECONDS)&&copyStatus[0]==PixelCopy.SUCCESS,"native Window PixelCopy available");try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}result.putString("evidence",directory.getAbsolutePath()+"/"+name);result.putString("result","PASS "+mode);finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("result","FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());finish(Activity.RESULT_CANCELED,result);}}
}

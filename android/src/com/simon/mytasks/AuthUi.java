package com.simon.mytasks;
import android.app.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import org.json.*;
/** Mandatory identity gate, not a dismissible token-paste dialog. */
final class AuthUi {
 static volatile boolean inFlight;final MainActivity a;AuthUi(MainActivity activity){a=activity;}
 EditText input(LinearLayout p,String name,String value,boolean password){a.add(p,a.label(name,13,a.muted));EditText e=new EditText(a);a.ui.input(e);e.setText(value);e.setSingleLine(true);e.setContentDescription(name);e.setInputType(InputType.TYPE_CLASS_TEXT|(password?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_NORMAL));e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);e.setEnabled(!a.authBusy);a.add(p,e);a.space(p,12);return e;}
 void build(LinearLayout root){
  a.authBusy=a.authBusy||inFlight;
  LinearLayout bar=a.horizontal();bar.addView(a.heading("My Tasks",22),new LinearLayout.LayoutParams(0,-2,1));bar.addView(a.ui.icon(R.drawable.ic_settings,"Settings and account-free updates",a::settings),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));a.add(root,bar);
  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);LinearLayout outer=a.vertical();outer.setGravity(Gravity.CENTER);outer.setPadding(a.dp(8),a.dp(16),a.dp(8),a.dp(24));scroll.addView(outer);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout form=a.vertical();form.setPadding(a.dp(20),a.dp(20),a.dp(20),a.dp(20));form.setBackground(a.ui.shape(a.card,0,24));form.setElevation(a.dp(1));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);if(a.getResources().getConfiguration().screenWidthDp>600)size.width=a.dp(480);outer.addView(form,size);
  boolean change=a.authState==AuthPolicy.State.CHANGE;
  a.add(form,a.heading(change?"Choose your private password":a.authState==AuthPolicy.State.CHECKING?"Checking your session":"Sign in",24));
  a.add(form,a.label(a.authMessage,14,a.muted));a.space(form,12);
  if(a.authState==AuthPolicy.State.CHECKING){ProgressBar progress=new ProgressBar(a);a.add(form,progress);return;}
  if(a.authState==AuthPolicy.State.ACTIVATION)a.add(form,a.label("This installation needs owner activation. Open your private activation link in the web workspace, complete the password change there, then sign in here. A public initial password alone cannot unlock the account.",15,a.ink));
  EditText server=input(form,"Task server",a.authServer.isEmpty()?a.store.server():a.authServer,false);server.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);server.setEnabled(!change&&!a.authBusy);
  EditText username=change?null:input(form,"Username","oiry-s",false);
  EditText current=input(form,change?"Current password":"Password","",true);
  EditText next=change?input(form,"New password · 15–128 characters","",true):null;
  EditText confirm=change?input(form,"Confirm new password","",true):null;
  Button submit=a.primary(change?"Change password and open workspace":"Sign in",()->{
   if(inFlight||a.busy)return;final String origin;try{origin=Core.server(server.getText().toString());AuthPolicy.passwordValue(current.getText().toString());if(change)AuthPolicy.password(current.getText().toString(),next.getText().toString(),confirm.getText().toString());else if(username.getText().length()==0||current.length()==0)throw new IllegalArgumentException("Enter username and password.");}catch(IllegalArgumentException e){a.notice("Check sign-in fields",e.getMessage());return;}
   final String first=current.getText().toString(),replacement=change?next.getText().toString():"",confirmation=change?confirm.getText().toString():"",name=change?"oiry-s":username.getText().toString();
   current.setText("");if(change){next.setText("");confirm.setText("");}
   Store.Account saved=a.account;final String proof=saved!=null&&saved.server.equals(origin)?saved.token:"";
   inFlight=true;a.authBusy=true;a.authServer=origin;a.authMessage=change?"Changing password…":"Signing in…";a.render();
   Api.IO.execute(()->{boolean storing=false;try{AuthClient client=Api.auth(origin);String validProof=proof;if(!change&&!proof.isEmpty()&&!client.session(proof).optBoolean("authenticated"))validProof="";JSONObject result=change?client.password(first,replacement,confirmation,proof):client.login(name,first,validProof);storing=true;a.store.session(origin,result.getString("session_token"));Store.Account issued=a.store.account();inFlight=false;a.main.post(()->{if(a.isDestroyed()){Api.AuthObserver observer=Api.authObserver;if(observer!=null)observer.blocked(issued,200);return;}a.authBusy=false;a.acceptSession(issued,result);});}catch(Exception e){inFlight=false;final boolean local=storing;a.main.post(()->{if(a.isDestroyed()){Api.AuthObserver observer=Api.authObserver;if(observer!=null)observer.blocked(null,0);return;}a.authBusy=false;if(local){a.authState=AuthPolicy.State.LOGIN;a.authMessage="Sign-in reached the server, but the issued session could not be saved locally. "+AuthPolicy.storageFailure(e)+" Sign in again; if changing password, use your new password.";a.render();return;}int code=e instanceof Api.Failure?((Api.Failure)e).code:0;if(code==428)a.authState=AuthPolicy.State.ACTIVATION;else if(change&&code==401)a.authState=AuthPolicy.State.LOGIN;else if(change&&(!(e instanceof Api.Failure)||code>=500||code==409)){a.authState=AuthPolicy.State.LOGIN;a.authMessage="Password change was not confirmed. Do not blindly repeat it. Sign in with your new password first; if rejected, try the old password.";a.render();return;}a.authMessage=Api.friendly(e);a.render();});}});
  });submit.setEnabled(!a.authBusy);a.add(form,submit);
  if(change)a.add(form,a.label("No tasks or conversations can be accessed until this change is confirmed. Spaces and Unicode are preserved; passwords are never stored on this device.",13,a.muted));
  if(a.account!=null){a.space(form,12);Button out=a.button("Sign out / clear saved credential",a::disconnectAccount);out.setEnabled(!a.authBusy);a.add(form,out);}
 }
}

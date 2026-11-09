package com.simon.mytasks;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
/** No credentials or passwords are retained by this pure policy. */
final class AuthPolicy {
 enum State { CHECKING, LOGIN, ACTIVATION, CHANGE, FULL }
 static State state(JSONObject session,long now){
  if(session.optBoolean("activation_required"))return State.ACTIVATION;
  if(!session.optBoolean("authenticated"))return State.LOGIN;
  if(session.has("expires_at")&&session.optLong("expires_at",0)<=now)return State.LOGIN;
  return session.optBoolean("password_change_required")?State.CHANGE:State.FULL;
 }
 static String sessionToken(String token){if(token==null||!token.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Invalid session response. Sign in again.");return token;}
 static String storageFailure(Exception error){String kind=error==null?"UnknownError":error.getClass().getSimpleName();if(!kind.matches("[A-Za-z0-9_$]{1,100}"))kind="StorageError";return "Could not save the issued session locally ("+kind+").";}
 static void passwordValue(String value){
  if(value==null)throw new IllegalArgumentException("Enter a password.");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))throw new IllegalArgumentException("Password contains invalid Unicode.");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Password contains invalid Unicode.");}
  int length=value.codePointCount(0,value.length());if(length<1||length>128||value.getBytes(StandardCharsets.UTF_8).length>1024)throw new IllegalArgumentException("Password must contain 1–128 Unicode characters.");
 }
 static void password(String current,String next,String confirm){
  passwordValue(current);passwordValue(next);passwordValue(confirm);
  int n=next.codePointCount(0,next.length());
  if(current.isEmpty())throw new IllegalArgumentException("Enter your current password.");
  if(n<15||n>128||next.getBytes(StandardCharsets.UTF_8).length>1024)throw new IllegalArgumentException("Use 15–128 characters for the new password. Spaces are preserved.");
  if(!next.equals(confirm))throw new IllegalArgumentException("The new passwords do not match.");
  if(current.equals(next))throw new IllegalArgumentException("Choose a different password.");
 }
}

package com.simon.mytasks;

import android.content.*;
import android.security.keystore.*;
import android.util.*;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.io.*;
import java.util.UUID;

/** App-private snapshots only. Tokens never appear in the task cache or logs. */
final class Store {
 static final String ALIAS="mytasks.token.v1";
 final Context context;
 final SharedPreferences prefs;
 Store(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("account",Context.MODE_PRIVATE);}
 static final class Account {
  final String server,token,epoch;
  Account(String s,String t,String e){server=s;token=t;epoch=e;}
 }
 private javax.crypto.SecretKey key() throws Exception {
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias(ALIAS)){
   KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");
   gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
  }
  return (javax.crypto.SecretKey)ks.getKey(ALIAS,null);
 }
 synchronized Account account() throws Exception {
  String origin=prefs.getString("server","");if(origin.isEmpty())return null;
  String encrypted=prefs.getString("secret","");String[] parts=encrypted.split(":");
  if(parts.length!=2)throw new IOException("Credentials unavailable. Re-enter settings.");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));cipher.updateAAD(origin.getBytes(StandardCharsets.UTF_8));
  return new Account(origin,new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8),prefs.getString("epoch",""));
 }
 void configure(String origin,String token) throws Exception {
  origin=Core.server(origin);token=Core.token(token);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(origin.getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
  synchronized(Store.class){
   if(!prefs.edit().clear().putString("server",origin).putString("epoch",UUID.randomUUID().toString()).putString("secret",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())throw new IOException("Could not save settings");
   new AtomicFile(new File(context.getFilesDir(),"tasks.json")).delete();
   ((android.app.NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
  }
 }
 boolean current(Account a){return a!=null&&a.epoch.equals(prefs.getString("epoch",""));}
 JSONObject cached(){
  synchronized(Store.class){try{
   AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"tasks.json"));
   JSONObject wrapper=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
   if(!wrapper.optString("epoch").equals(prefs.getString("epoch","")))return null;
   return wrapper.getJSONObject("data");
  }catch(Exception e){return null;}}
 }
 void cache(Account a,JSONObject data)throws Exception{
  synchronized(Store.class){if(!current(a))return;
   AtomicFile f=new AtomicFile(new File(context.getFilesDir(),"tasks.json"));FileOutputStream out=null;
   try{out=f.startWrite();out.write(new JSONObject().put("epoch",a.epoch).put("data",data).toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}
  }
 }
 long cursor(){return prefs.getLong("cursor",0);}
 boolean bootstrapped(){return prefs.getBoolean("bootstrapped",false);}
 void cursor(Account a,long value,boolean baseline)throws IOException{
  synchronized(Store.class){if(current(a)&&!prefs.edit().putLong("cursor",value).putBoolean("bootstrapped",baseline).commit())throw new IOException("Could not persist event cursor");}
 }
}

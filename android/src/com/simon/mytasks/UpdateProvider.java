package com.simon.mytasks;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.*;import android.provider.OpenableColumns;import java.io.*;
/** Non-exported, temporary read-only capability for exactly one verified accepted APK. */
public final class UpdateProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 private File file(Uri uri)throws FileNotFoundException{UpdateManager m=UpdateManager.existing();if(m==null)throw new FileNotFoundException("Update consent is not retained across process death");return m.sharedFile(uri);}
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read-only APK provider");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 @Override public String getType(Uri uri){try{file(uri);return "application/vnd.android.package-archive";}catch(FileNotFoundException e){return null;}}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
  try{File f=file(uri);String[] requested=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
   java.util.ArrayList<String> columns=new java.util.ArrayList<>();java.util.ArrayList<Object> values=new java.util.ArrayList<>();
   for(String col:requested){if(OpenableColumns.DISPLAY_NAME.equals(col)){columns.add(col);values.add("my-tasks.apk");}else if(OpenableColumns.SIZE.equals(col)){columns.add(col);values.add(f.length());}}
   MatrixCursor cursor=new MatrixCursor(columns.toArray(new String[0]),1);cursor.addRow(values);return cursor;
  }catch(FileNotFoundException e){return null;}
 }
 @Override public Uri insert(Uri uri,ContentValues values){throw new SecurityException("Read only");}
 @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new SecurityException("Read only");}
 @Override public int delete(Uri uri,String selection,String[] args){throw new SecurityException("Read only");}
}

package android.content;
import java.util.*;
/** Host-only synchronous private storage seam. */
public final class SharedPreferences {
 public boolean fail;final Map<String,String> values=new HashMap<>();
 public String getString(String key,String fallback){return values.getOrDefault(key,fallback);}
 public Editor edit(){return new Editor();}
 public final class Editor {final Map<String,String> next=new HashMap<>();public Editor putString(String key,String value){next.put(key,value);return this;}public boolean commit(){if(fail)return false;values.putAll(next);return true;}}
}

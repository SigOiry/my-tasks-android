package com.simon.mytasks;
import android.content.Context;
final class Store {
 final Context context=new Context();String epoch="account one";final Account account=new Account(epoch);
 static final class Account {final String epoch;Account(String e){epoch=e;}}
 boolean current(Account a){return a!=null&&a.epoch.equals(epoch);}
}

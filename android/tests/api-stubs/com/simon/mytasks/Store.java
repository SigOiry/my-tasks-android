package com.simon.mytasks;
/** Host-only account identity; not Android storage and never part of APK. */
final class Store {static final class Account {final String server,token,epoch;Account(String s,String t,String e){server=s;token=t;epoch=e;}}}

package com.hisaab.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;

public final class LedgerDb extends SQLiteOpenHelper {
 private final Context context;
 public LedgerDb(Context c){super(c,"hisaab.db",null,1);context=c;}
 @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
 @Override public void onCreate(SQLiteDatabase db){try(BufferedReader r=new BufferedReader(new InputStreamReader(context.getAssets().open("schema.sql")))){String s;while((s=r.readLine())!=null)if(!s.trim().isEmpty())db.execSQL(s);}catch(IOException e){throw new IllegalStateException(e);}}
 @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){throw new IllegalStateException("Unsupported database version");}
 static String id(){return UUID.randomUUID().toString();}
 static String now(){return Instant.now().toString();}
 static String required(JSONObject o,String k,int max)throws JSONException {String v=o.getString(k).trim();if(v.isEmpty()||v.length()>max)throw new IllegalArgumentException("Invalid "+k);return v;}
 static long money(JSONObject o)throws JSONException{Object raw=o.get("amount");String text=raw.toString();if(!text.matches("[0-9]+"))throw new IllegalArgumentException("Amount must be whole paise");long n=Long.parseLong(text);if(n<=0||n>100000000000L)throw new IllegalArgumentException("Amount out of range");return n;}
 static void validateEntry(JSONObject o)throws JSONException{
  money(o);LocalDate.parse(o.getString("date"));if(!Arrays.asList("JAMA","BAAKI").contains(o.getString("type")))throw new IllegalArgumentException("Invalid type");
  if(!Arrays.asList("ENTRY","OPENING","SETTLEMENT","ADJUSTMENT","REVERSAL").contains(o.getString("kind")))throw new IllegalArgumentException("Invalid entry kind");
  required(o,"id",100);required(o,"party",100);required(o,"actor",100);Instant.parse(o.getString("created"));Instant.parse(o.getString("updated"));
  for(String k:Arrays.asList("description","mode","reference","notes"))if(o.getString(k).length()>2000)throw new IllegalArgumentException("Text too long");
  if(o.getString("kind").equals("REVERSAL")==o.isNull("reverses"))throw new IllegalArgumentException("Invalid reversal link");
 }
 private JSONArray rows(String table)throws JSONException {JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM "+table,null)){while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));a.put(o);}}return a;}
 public synchronized JSONObject snapshot()throws JSONException{return new JSONObject().put("schema",1).put("parties",rows("party")).put("entries",rows("entry")).put("audit",rows("audit"));}
 private void insert(String table,JSONObject o,String... keys)throws JSONException{ContentValues v=new ContentValues();for(String k:keys){Object x=o.get(k);if(x==JSONObject.NULL)v.putNull(k);else if(x instanceof Number)v.put(k,((Number)x).longValue());else v.put(k,x.toString());}getWritableDatabase().insertOrThrow(table,null,v);}
 private void insertParty(JSONObject p)throws JSONException{required(p,"id",100);required(p,"name",100);if(p.getString("mobile").length()>30||p.getString("notes").length()>2000)throw new IllegalArgumentException("Text too long");Instant.parse(p.getString("created"));Instant.parse(p.getString("updated"));insert("party",p,"id","name","mobile","notes","active","pinned","created","updated");}
 private void insertEntry(JSONObject e)throws JSONException{validateEntry(e);insert("entry",e,"id","party","date","amount","type","kind","description","mode","reference","notes","actor","created","updated","reverses");}
 private void audit(String action,String entity,String detail)throws JSONException{insert("audit",new JSONObject().put("id",id()).put("action",action).put("entity",entity).put("detail",detail).put("actor","Owner").put("created",now()),"id","action","entity","detail","actor","created");}
 public synchronized String addParty(JSONObject p)throws JSONException{
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{long opening=p.getLong("opening");if(opening<0||opening>100000000000L)throw new IllegalArgumentException("Invalid opening amount");String key=id(),t=now();JSONObject row=new JSONObject().put("id",key).put("name",required(p,"name",100)).put("mobile",p.optString("mobile")).put("notes",p.optString("notes")).put("active",1).put("pinned",0).put("created",t).put("updated",t);insertParty(row);audit("PARTY_CREATED",key,row.toString());
   if(p.optLong("opening",0)>0){JSONObject e=new JSONObject().put("party",key).put("date",LocalDate.now().toString()).put("amount",p.getLong("opening")).put("type",p.getString("openingType")).put("kind","OPENING").put("description","Opening balance");createEntry(e);}d.setTransactionSuccessful();return key;
  }finally{d.endTransaction();}
 }
 private String createEntry(JSONObject e)throws JSONException{
  String key=id(),t=now();e.put("id",key).put("actor","Owner").put("created",t).put("updated",t);for(String k:Arrays.asList("description","mode","reference","notes"))if(!e.has(k))e.put(k,"");if(!e.has("reverses"))e.put("reverses",JSONObject.NULL);insertEntry(e);audit("ENTRY_CREATED",key,e.toString());return key;
 }
 public synchronized String addEntry(JSONObject e)throws JSONException{
  if(!Arrays.asList("ENTRY","SETTLEMENT","ADJUSTMENT").contains(e.getString("kind")))throw new IllegalArgumentException("Invalid operation");
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{try(Cursor c=d.rawQuery("SELECT active FROM party WHERE id=?",new String[]{e.getString("party")})){if(!c.moveToFirst()||c.getInt(0)!=1)throw new IllegalArgumentException("Party is inactive");}String key=createEntry(e);d.setTransactionSuccessful();return key;}finally{d.endTransaction();}
 }
 public synchronized void updateParty(JSONObject p)throws JSONException{SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{ContentValues v=new ContentValues();for(String k:Arrays.asList("active","pinned")){int n=p.getInt(k);if(n!=0&&n!=1)throw new IllegalArgumentException("Invalid status");v.put(k,n);}v.put("updated",now());if(d.update("party",v,"id=?",new String[]{p.getString("id")})!=1)throw new IllegalArgumentException("Party missing");audit("PARTY_STATUS",p.getString("id"),p.toString());d.setTransactionSuccessful();}finally{d.endTransaction();}}
 public synchronized void reverse(JSONObject r)throws JSONException{String reason=required(r,"reason",2000);SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{JSONObject old=null;JSONArray a=rows("entry");for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(r.getString("id")))old=a.getJSONObject(i);if(old==null||old.getString("kind").equals("REVERSAL"))throw new IllegalArgumentException("Invalid entry");JSONObject e=new JSONObject().put("party",old.getString("party")).put("date",LocalDate.now().toString()).put("amount",old.getLong("amount")).put("type",old.getString("type").equals("JAMA")?"BAAKI":"JAMA").put("kind","REVERSAL").put("description",reason).put("reverses",old.getString("id"));createEntry(e);d.setTransactionSuccessful();}finally{d.endTransaction();}}
 // Restore is deliberately only into an empty installation. Every imported row and FK is checked in one atomic transaction.
 public synchronized void restore(JSONObject root)throws JSONException{
  if(root.getInt("schema")!=1)throw new IllegalArgumentException("Unsupported backup version");
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   if(android.database.DatabaseUtils.longForQuery(d,"SELECT count(*) FROM party",null)!=0||android.database.DatabaseUtils.longForQuery(d,"SELECT count(*) FROM audit",null)!=0)throw new IllegalArgumentException("Restore requires a fresh installation. Existing accounts are protected.");
   JSONArray p=root.getJSONArray("parties"),e=root.getJSONArray("entries"),a=root.getJSONArray("audit");if(p.length()>100000||e.length()>1000000)throw new IllegalArgumentException("Backup too large");
   for(int i=0;i<p.length();i++)insertParty(p.getJSONObject(i));
   for(int pass=0;pass<2;pass++)for(int i=0;i<e.length();i++){JSONObject row=e.getJSONObject(i);if(row.isNull("reverses")==(pass==0))insertEntry(row);}
   for(int i=0;i<a.length();i++){JSONObject row=a.getJSONObject(i);required(row,"id",100);required(row,"actor",100);Instant.parse(row.getString("created"));insert("audit",row,"id","action","entity","detail","actor","created");}
   audit("RESTORE","database","Imported "+p.length()+" parties and "+e.length()+" entries");d.setTransactionSuccessful();
  }finally{d.endTransaction();}
 }
}

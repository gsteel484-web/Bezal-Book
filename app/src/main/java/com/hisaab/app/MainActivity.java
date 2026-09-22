package com.hisaab.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.view.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private WebView web; private LedgerDb db; private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private android.content.SharedPreferences prefs;
 @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("settings",0);db=new LedgerDb(this);web=new WebView(this);setContentView(web);
  web.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){Uri u=r.getUrl();String path=u.getPath();if("app.hisaab.local".equals(u.getHost())&&"https".equals(u.getScheme())&&path!=null&&path.matches("/[a-zA-Z0-9._-]+")){try{String type=path.endsWith(".css")?"text/css":path.endsWith(".js")?"application/javascript":"text/html";return new WebResourceResponse(type,"UTF-8",getAssets().open(path.substring(1)));}catch(IOException ignored){}}return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
  });web.addJavascriptInterface(new Bridge(),"Android");web.loadUrl("https://app.hisaab.local/index.html");
 }
 private void notifyUi(String msg){runOnUiThread(()->{if(!isFinishing())web.evaluateJavascript("window.nativeDone("+JSONObject.quote(msg)+")",null);});}
 private void async(Runnable action){worker.execute(()->{try{action.run();}catch(Exception ex){notifyUi("काम पूरा नहीं हुआ: "+ex.getMessage());}});}
 private String folder(){return prefs.getString("folder","");}
 public class Bridge {
  @JavascriptInterface public String call(String command,String json){try{JSONObject p=new JSONObject(json),out=new JSONObject().put("ok",true);switch(command){
   case "load":out.put("data",db.snapshot()).put("folder",!folder().isEmpty()).put("lastBackup",prefs.getString("lastBackup",""));break;
   case "party":out.put("id",db.addParty(p));break;
   case "entry":out.put("id",db.addEntry(p));autoReport(p.getString("party"));break;
   case "status":db.updateParty(p);break;
   case "reverse":db.reverse(p);autoReport(p.getString("party"));break;
   case "folder":runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),40));break;
   case "backup":async(()->backup());break;
   case "restore":runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("बैकअप वापस लाएँ?").setMessage("केवल खाली ऐप में बहाल होगा। मौजूदा खातों को बदला नहीं जाएगा।").setPositiveButton("फ़ाइल चुनें",(d,w)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),41)).setNegativeButton("रद्द",null).show());break;
   case "report":async(()->report(p,true));break;
   default:throw new IllegalArgumentException("Unknown action");
  }return out.toString();}catch(Exception ex){return "{\"ok\":false,\"error\":"+JSONObject.quote(ex.getMessage()==null?"Operation failed":ex.getMessage())+"}";}}
 }
 private void autoReport(String party){if(!folder().isEmpty())async(()->{try{report(new JSONObject().put("party",party).put("from","0001-01-01").put("to","9999-12-31"),false);}catch(JSONException e){throw new IllegalStateException(e);}});}
 private Uri saveToFolder(String name,String mime,byte[] bytes)throws Exception{
  if(folder().isEmpty())throw new IllegalStateException("पहले बैकअप फ़ोल्डर चुनें");Uri tree=Uri.parse(folder());Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));Uri created=DocumentsContract.createDocument(getContentResolver(),parent,mime,name);if(created==null)throw new IOException("Cannot create file");
  try(OutputStream out=getContentResolver().openOutputStream(created,"w")){if(out==null)throw new IOException("Cannot open file");out.write(bytes);}catch(Exception e){try{DocumentsContract.deleteDocument(getContentResolver(),created);}catch(Exception ignored){}throw e;}return created;
 }
 private void backup(){try{byte[] bytes=db.snapshot().toString().getBytes(StandardCharsets.UTF_8);saveToFolder("Hisaab-backup-"+System.currentTimeMillis()+".json","application/json",bytes);prefs.edit().putString("lastBackup",Instant.now().toString()).apply();notifyUi("पूरा बैकअप सुरक्षित हो गया");}catch(Exception e){throw new IllegalStateException(e.getMessage(),e);}}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(req==40){try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));prefs.edit().putString("folder",uri.toString()).apply();notifyUi("फ़ोल्डर चुना गया। नई एंट्री के बाद PDF अपने आप सहेजी जाएगी।");}catch(Exception e){notifyUi("फ़ोल्डर अनुमति नहीं मिली: "+e.getMessage());}}if(req==41)async(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("File unavailable");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>50*1024*1024)throw new IOException("Backup exceeds 50 MB");}db.restore(new JSONObject(out.toString("UTF-8")));notifyUi("खाते बहाल हो गए। बैकअप फ़ोल्डर दोबारा चुनें।");}catch(Exception e){throw new IllegalStateException(e.getMessage(),e);}});}
 static String money(long n){return String.format(Locale.ENGLISH,"%s₹%,d.%02d",n<0?"−":"",Math.abs(n)/100,Math.abs(n)%100);}
 private void report(JSONObject options,boolean interactive){try{
  JSONObject snapshot=db.snapshot();String party=options.optString("party",""),from=options.optString("from","0001-01-01"),to=options.optString("to","9999-12-31");LocalDate.parse(from);LocalDate.parse(to);if(from.compareTo(to)>0)throw new IllegalArgumentException("Invalid date range");
  String name="All parties";JSONArray parties=snapshot.getJSONArray("parties");Map<String,String> names=new HashMap<>();for(int i=0;i<parties.length();i++){JSONObject p=parties.getJSONObject(i);names.put(p.getString("id"),p.getString("name"));if(p.getString("id").equals(party))name=p.getString("name");}
  List<JSONObject> entries=new ArrayList<>();JSONArray a=snapshot.getJSONArray("entries");for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(party.isEmpty()||e.getString("party").equals(party))entries.add(e);}entries.sort(Comparator.comparing((JSONObject e)->e.optString("date")).thenComparing(e->e.optString("created")).thenComparing(e->e.optString("id")));
  List<String> lines=new ArrayList<>();lines.add("HISAAB  /  ACCOUNT STATEMENT");lines.add(name);lines.add(from+"  to  "+to);lines.add("Positive balance = receivable • Negative = payable");lines.add("Generated: "+Instant.now());long balance=0,jama=0,baaki=0;
  for(JSONObject e:entries)if(e.getString("date").compareTo(from)<0)balance=Math.addExact(balance,e.getString("type").equals("BAAKI")?e.getLong("amount"):-e.getLong("amount"));
  lines.add("Opening balance: "+money(balance));lines.add("Date | Detail | Jama | Baaki | Balance");
  for(JSONObject e:entries){String date=e.getString("date");if(date.compareTo(from)<0||date.compareTo(to)>0)continue;long amount=e.getLong("amount");boolean credit=e.getString("type").equals("JAMA");balance=Math.addExact(balance,credit?-amount:amount);if(credit)jama=Math.addExact(jama,amount);else baaki=Math.addExact(baaki,amount);
   lines.add(date+" | "+names.get(e.getString("party"))+" | "+e.getString("kind"));lines.add(e.getString("description"));lines.add("Jama "+money(credit?amount:0)+"    Baaki "+money(credit?0:amount)+"    Balance "+money(balance));lines.add("ID: "+e.getString("id")+" • By: "+e.getString("actor"));if(!e.isNull("reverses"))lines.add("Reverses: "+e.getString("reverses"));lines.add("");
  }lines.add("TOTAL JAMA: "+money(jama));lines.add("TOTAL BAAKI: "+money(baaki));lines.add("CLOSING BALANCE: "+money(balance));
  if(options.optBoolean("partyWise")){lines.clear();lines.add("HISAAB / PARTY SUMMARY");lines.add("Generated: "+Instant.now());lines.add("Positive balance = receivable; negative = payable");long totalJ=0,totalB=0;for(int i=0;i<parties.length();i++){JSONObject p=parties.getJSONObject(i);long j=0,b=0;for(JSONObject e:entries)if(e.getString("party").equals(p.getString("id"))){if(e.getString("type").equals("JAMA"))j=Math.addExact(j,e.getLong("amount"));else b=Math.addExact(b,e.getLong("amount"));}totalJ=Math.addExact(totalJ,j);totalB=Math.addExact(totalB,b);lines.add(p.getString("name")+" | "+(p.getInt("active")==1?"Active":"Inactive"));lines.add("Jama "+money(j)+" | Baaki "+money(b)+" | Balance "+money(b-j));lines.add("");}lines.add("TOTAL JAMA: "+money(totalJ));lines.add("TOTAL BAAKI: "+money(totalB));lines.add("NET BALANCE: "+money(totalB-totalJ));}
  PdfDocument pdf=new PdfDocument();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(11);paint.setColor(Color.rgb(26,44,55));List<String> wrapped=new ArrayList<>();for(String line:lines){if(line.isEmpty()){wrapped.add("");continue;}while(!line.isEmpty()){int cut=paint.breakText(line,true,515,null);if(cut==0)cut=1;wrapped.add(line.substring(0,cut));line=line.substring(cut);}}
  int pages=Math.max(1,(wrapped.size()+43)/44);for(int page=0;page<pages;page++){PdfDocument.Page p=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,page+1).create());Canvas c=p.getCanvas();paint.setColor(Color.rgb(8,127,112));c.drawRect(0,0,595,12,paint);paint.setColor(Color.rgb(26,44,55));for(int j=page*44;j<Math.min(wrapped.size(),(page+1)*44);j++)c.drawText(wrapped.get(j),40,48+(j%44)*17,paint);c.drawText("Hisaab • "+(page+1)+" / "+pages,40,815,paint);pdf.finishPage(p);}
  ByteArrayOutputStream output=new ByteArrayOutputStream();try{pdf.writeTo(output);}finally{pdf.close();}byte[] bytes=output.toByteArray();String filename="statement-"+UUID.randomUUID()+".pdf";File dir=new File(getCacheDir(),"reports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Report folder unavailable");File file=new File(dir,filename);try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
  String status="PDF तैयार है";if(!folder().isEmpty()){try{saveToFolder(filename,"application/pdf",bytes);status="PDF बैकअप फ़ोल्डर में सहेजी गई";}catch(Exception ex){status="PDF तैयार है, लेकिन फ़ोल्डर में नहीं सहेजी गई: "+ex.getMessage();}}else status+="। अपने आप सहेजने के लिए बैकअप फ़ोल्डर चुनें।";
  notifyUi(status);if(interactive){Uri uri=Uri.parse("content://com.hisaab.app.reports/"+filename);runOnUiThread(()->{Intent send=new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("Statement",uri));if(options.optBoolean("whatsapp"))send.setPackage("com.whatsapp");try{startActivity(options.optBoolean("whatsapp")?send:Intent.createChooser(send,"हिसाब शेयर / सहेजें"));}catch(ActivityNotFoundException ex){send.setPackage(null);startActivity(Intent.createChooser(send,"WhatsApp उपलब्ध नहीं; दूसरा ऐप चुनें"));}});}
 }catch(Exception ex){throw new IllegalStateException(ex.getMessage(),ex);}}
 @Override public void onBackPressed(){web.evaluateJavascript("window.goBack()",null);}
 @Override protected void onDestroy(){web.removeJavascriptInterface("Android");web.destroy();worker.shutdown();super.onDestroy();}
}

package com.maksolutions.trueaqua;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.Window;
import android.webkit.*;
import android.widget.Toast;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {
  private WebView web;
  private MediaRecorder recorder;
  private MediaPlayer player;
  private File voiceFile;
  private static final String ACTIVATION_HASH="79a60928316af11cd952457d9e857f334ebac22c09ed1d3d1298324013033db7";

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    Window w=getWindow(); w.setStatusBarColor(Color.WHITE); w.setNavigationBarColor(Color.WHITE);
    if(Build.VERSION.SDK_INT>=23)w.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    requestPerms();
    ReminderReceiver.createChannel(this);
    web=new WebView(this); setContentView(web);
    WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true);
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient());
    web.addJavascriptInterface(new Bridge(),"Android");
    web.loadUrl("file:///android_asset/index.html");
  }

  private void requestPerms(){
    if(Build.VERSION.SDK_INT<23)return;
    java.util.ArrayList<String> p=new java.util.ArrayList<>();
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.RECORD_AUDIO);
    if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.CAMERA);
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);
    if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),99);
  }

  private void toast(String m){runOnUiThread(()->Toast.makeText(this,m,Toast.LENGTH_SHORT).show());}
  private String safe(String s){return s==null?"":s;}
  private void stopPlayer(){try{if(player!=null){player.stop();player.release();player=null;}}catch(Exception ignored){}}
  private void stopRec(){
    try{if(recorder!=null){recorder.stop();recorder.release();recorder=null;}}catch(Exception e){recorder=null;}
  }

  @Override public void onBackPressed(){
    web.evaluateJavascript("window.nativeBack?window.nativeBack():false",v->{if(!"true".equals(v))super.onBackPressed();});
  }

  @Override protected void onDestroy(){stopRec();stopPlayer();if(web!=null)web.destroy();super.onDestroy();}

  public class Bridge {
    @JavascriptInterface public boolean isActivated(){
      return getSharedPreferences("tas_security",MODE_PRIVATE).getBoolean("activated",false);
    }
    @JavascriptInterface public boolean activate(String code){
      try{
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        byte[] d=md.digest(safe(code).trim().getBytes(StandardCharsets.UTF_8));
        StringBuilder h=new StringBuilder(); for(byte x:d)h.append(String.format(Locale.US,"%02x",x));
        boolean ok=ACTIVATION_HASH.equals(h.toString());
        if(ok)getSharedPreferences("tas_security",MODE_PRIVATE).edit().putBoolean("activated",true).apply();
        return ok;
      }catch(Exception e){return false;}
    }
    @JavascriptInterface public String version(){return "1.3";}
    @JavascriptInterface public void dial(String p){
      runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+safe(p))));}catch(Exception e){toast("Phone app unavailable");}});
    }
    @JavascriptInterface public void maps(String a){
      runOnUiThread(()->{try{String q=URLEncoder.encode(safe(a),"UTF-8");startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/search/?api=1&query="+q)));}catch(Exception e){toast("Maps unavailable");}});
    }
    @JavascriptInterface public void whatsapp(String p,String msg){
      runOnUiThread(()->{try{String d=safe(p).replaceAll("[^0-9]","");String m=URLEncoder.encode(safe(msg),"UTF-8");startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+d+"?text="+m)));}catch(Exception e){toast("WhatsApp unavailable");}});
    }
    @JavascriptInterface public boolean startVoice(){
      try{
        stopRec(); stopPlayer();
        voiceFile=new File(getFilesDir(),"customer_note.m4a");
        recorder=new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(64000);
        recorder.setAudioSamplingRate(44100);
        recorder.setOutputFile(voiceFile.getAbsolutePath());
        recorder.prepare(); recorder.start(); return true;
      }catch(Exception e){stopRec();return false;}
    }
    @JavascriptInterface public boolean stopVoice(){try{stopRec();return voiceFile!=null&&voiceFile.exists()&&voiceFile.length()>0;}catch(Exception e){return false;}}
    @JavascriptInterface public boolean playVoice(){
      try{
        stopPlayer(); if(voiceFile==null)voiceFile=new File(getFilesDir(),"customer_note.m4a");
        if(!voiceFile.exists())return false;
        player=new MediaPlayer();player.setDataSource(voiceFile.getAbsolutePath());player.prepare();player.start();return true;
      }catch(Exception e){stopPlayer();return false;}
    }
    @JavascriptInterface public void deleteVoice(){stopRec();stopPlayer();File f=new File(getFilesDir(),"customer_note.m4a");if(f.exists())f.delete();}
    @JavascriptInterface public void schedule(long when,String title,String text,int id){
      try{
        Intent i=new Intent(MainActivity.this,ReminderReceiver.class);i.putExtra("title",safe(title));i.putExtra("text",safe(text));
        PendingIntent pi=PendingIntent.getBroadcast(MainActivity.this,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
        if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms()){startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));return;}
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
      }catch(Exception e){toast("Reminder could not be scheduled");}
    }
  }
}

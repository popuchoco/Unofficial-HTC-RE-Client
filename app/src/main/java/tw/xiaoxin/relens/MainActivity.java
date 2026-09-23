package tw.xiaoxin.relens;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.concurrent.*;

public class MainActivity extends Activity implements ReConnectionManager.Listener {
    private final ExecutorService io=Executors.newCachedThreadPool(); private LinearLayout body,nav; private TextView status; private EditText host; private ReApi api; private int ink=Color.rgb(23,32,31),paper=Color.rgb(245,245,239),signal=Color.rgb(0,121,107);
    @Override public void onCreate(Bundle b){super.onCreate(b);requestPermissionsIfNeeded();showShell();showCapture();}
    private void showShell(){ LinearLayout root=column(); root.setPadding(dp(20),dp(16),dp(20),0); root.setBackgroundColor(paper);
        TextView brand=text("RE / LENS",13,true); brand.setLetterSpacing(.18f); root.addView(brand,lp(-1,dp(34)));
        LinearLayout connection=row(); status=text("未連線",14,true); connection.addView(status,new LinearLayout.LayoutParams(0,dp(48),1));
        host=new EditText(this);host.setSingleLine();host.setText("192.168.49.1");host.setHint("RE IP");host.setContentDescription("HTC RE IP 位址");connection.addView(host,new LinearLayout.LayoutParams(dp(150),dp(48)));root.addView(connection);
        body=column(); ScrollView scroll=new ScrollView(this);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();String[] labels={"拍攝","相簿","串流","裝置"};for(String s:labels){Button x=button(s,false);x.setOnClickListener(v->{if(s.equals("拍攝"))showCapture();else if(s.equals("相簿"))showAlbum();else if(s.equals("串流"))showStream();else showDevice();});nav.addView(x,new LinearLayout.LayoutParams(0,dp(64),1));}root.addView(nav);setContentView(root); }
    private void showCapture(){clear("拍照 / 攝影","藍牙負責發現與設定，拍攝命令走 RE 的區域網路 API。");
        LinearLayout tools=row();Button scan=button("尋找 RE",false);scan.setOnClickListener(v->new ReConnectionManager(this,this).scanBle());Button p2p=button("建立 Wi‑Fi Direct",false);p2p.setOnClickListener(v->new ReConnectionManager(this,this).createP2pGroup());tools.addView(scan,new LinearLayout.LayoutParams(0,dp(56),1));tools.addView(p2p,new LinearLayout.LayoutParams(0,dp(56),1));body.addView(tools);
        Space gap=new Space(this);body.addView(gap,lp(1,dp(40)));TextView mode=text("就緒",15,true);mode.setGravity(Gravity.CENTER);body.addView(mode,lp(-1,dp(40)));
        Button shutter=button("拍照",true);shutter.setTextSize(22);shutter.setOnClickListener(v->run("正在拍照…",()->api().capture(),o->mode.setText("照片已拍攝")));body.addView(shutter,lp(-1,dp(88)));
        LinearLayout rec=row();Button start=button("開始錄影",false);Button stop=button("停止錄影",false);start.setOnClickListener(v->run("正在開始錄影…",()->api().startRecording(),o->mode.setText("錄影中  ●")));stop.setOnClickListener(v->run("正在停止錄影…",()->api().stopRecording(),o->mode.setText("錄影已儲存")));rec.addView(start,new LinearLayout.LayoutParams(0,dp(64),1));rec.addView(stop,new LinearLayout.LayoutParams(0,dp(64),1));body.addView(rec); }
    private void showAlbum(){clear("RE 相簿","讀取相機媒體清單；下載支援 HTTP Range 續傳。");Button refresh=button("重新整理",true);refresh.setOnClickListener(v->loadMedia());body.addView(refresh,lp(-1,dp(56)));loadMedia();}
    private void loadMedia(){run("正在讀取相簿…",()->api().media(),a->{status.setText(a.length()+" 個項目");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("id",o.optString("name","item-"+i));LinearLayout line=row();TextView t=text(id+"\n"+o.optString("type",o.optString("media_type","媒體")),14,false);line.addView(t,new LinearLayout.LayoutParams(0,dp(72),1));Button dl=button("下載",false);dl.setOnClickListener(v->download(id,o));line.addView(dl,lp(dp(96),dp(56)));body.addView(line);}});}
    private void download(String id,JSONObject item){String kind=item.optString("type","").toLowerCase(java.util.Locale.ROOT).contains("video")?"small_video":"original";File dir=new File(getExternalFilesDir(null),"downloads");dir.mkdirs();File out=new File(dir,id.replaceAll("[^a-zA-Z0-9._-]","_")+(kind.contains("video")?".mp4":".jpg"));status.setText("下載中…");io.execute(()->{try{api().download(api().downloadUrl(id,kind),out,(d,t)->runOnUiThread(()->status.setText(t>0?(d*100/t)+"%":"已下載 "+d)));runOnUiThread(()->toast("已儲存："+out.getName()));}catch(Exception e){runOnUiThread(()->error(e));}});}
    private void showStream(){clear("串流","已確認端點：rtsp://<RE-IP>/live");TextView note=text("RTSP 解碼器列為下一階段。這一版保留連線契約與畫面位置，不以 WebView 或外部播放器冒充內建串流。",16,false);body.addView(note);Button copy=button("顯示串流位址",false);copy.setOnClickListener(v->toast("rtsp://"+host.getText().toString().trim()+"/live"));body.addView(copy,lp(-1,dp(56)));}
    private void showDevice(){clear("裝置","讀取 RE 回報的相機、儲存空間與序號資訊。");Button inspect=button("讀取裝置資訊",true);inspect.setOnClickListener(v->run("正在讀取裝置…",()->{JSONObject all=new JSONObject();all.put("camera",api().cameraInfo());all.put("storage",api().storage());all.put("serial",api().serial());return all;},o->{TextView raw=text(o.toString(2),14,false);raw.setTypeface(Typeface.MONOSPACE);raw.setTextIsSelectable(true);body.addView(raw);}));body.addView(inspect,lp(-1,dp(56)));TextView app=text("RE Lens 0.1.0\n非官方、獨立實作；未含社群平台與雲端硬碟整合。",14,false);body.addView(app);}
    private ReApi api(){String h=host.getText().toString().trim();if(api==null||!api.baseUrl().contains(h))api=new ReApi(h);return api;}
    private interface Work<T>{T get()throws Exception;}private interface Done<T>{void ok(T x)throws Exception;}private <T>void run(String busy,Work<T>w,Done<T>d){status.setText(busy);io.execute(()->{try{T x=w.get();runOnUiThread(()->{try{status.setText("已連線");d.ok(x);}catch(Exception e){error(e);}});}catch(Exception e){runOnUiThread(()->error(e));}});}
    private void clear(String title,String subtitle){body.removeAllViews();body.addView(text(title,28,true));TextView sub=text(subtitle,15,false);sub.setPadding(0,dp(8),0,dp(24));body.addView(sub);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}private LinearLayout row(){LinearLayout l=column();l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(ink);v.setGravity(Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setTextColor(primary?Color.WHITE:ink);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?signal:Color.rgb(229,233,228)));return b;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}private void error(Exception e){status.setText("連線失敗");toast(e.getMessage()==null?e.toString():e.getMessage());}
    @Override public void onStatus(String s){runOnUiThread(()->status.setText(s));}@Override public void onFound(String n,String a){runOnUiThread(()->{status.setText("找到 "+n);toast(n+" · "+a);});}
    private void requestPermissionsIfNeeded(){if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},7);else requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},7);}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}

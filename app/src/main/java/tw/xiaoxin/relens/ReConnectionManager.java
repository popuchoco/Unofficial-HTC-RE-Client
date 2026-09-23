package tw.xiaoxin.relens;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.*;
import android.os.*;
import java.util.*;

final class ReConnectionManager {
    interface Listener { void onStatus(String status); void onFound(String name, String address); }
    private final Context context; private final Listener listener; private BluetoothLeScanner scanner; private ScanCallback scanCallback;
    ReConnectionManager(Context c, Listener l){context=c;listener=l;}
    void scanBle(){
        BluetoothManager bm=context.getSystemService(BluetoothManager.class);
        if(bm==null||bm.getAdapter()==null||!bm.getAdapter().isEnabled()){listener.onStatus("請先開啟藍牙");return;}
        if(Build.VERSION.SDK_INT>=31&&context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){listener.onStatus("需要附近裝置權限");return;}
        scanner=bm.getAdapter().getBluetoothLeScanner(); if(scanner==null){listener.onStatus("BLE 掃描器不可用");return;}
        listener.onStatus("正在尋找 HTC RE…"); scanCallback=new ScanCallback(){@Override public void onScanResult(int t,ScanResult r){BluetoothDevice d=r.getDevice(); String n="HTC RE"; try{if(d.getName()!=null)n=d.getName();}catch(SecurityException ignored){} listener.onFound(n,d.getAddress());}}; scanner.startScan(scanCallback);
        new Handler(Looper.getMainLooper()).postDelayed(()->{try{if(scanner!=null&&scanCallback!=null)scanner.stopScan(scanCallback);}catch(Exception ignored){}},10000);
    }
    void createP2pGroup(){
        WifiP2pManager m=context.getSystemService(WifiP2pManager.class); if(m==null){listener.onStatus("此手機不支援 Wi‑Fi Direct");return;}
        WifiP2pManager.Channel ch=m.initialize(context,context.getMainLooper(),()->listener.onStatus("Wi‑Fi Direct 通道中斷"));
        try{m.createGroup(ch,new WifiP2pManager.ActionListener(){public void onSuccess(){
            m.requestGroupInfo(ch,group->{int frequency=Build.VERSION.SDK_INT>=29&&group!=null?group.getFrequency():0;listener.onStatus("Wi‑Fi Direct 群組已建立"+(frequency>0?" · "+frequency+" MHz":"")+"，等待 RE 加入");});
        }public void onFailure(int r){listener.onStatus("建立 P2P 群組失敗（"+r+"）");}});}catch(SecurityException e){listener.onStatus("需要位置權限以建立 Wi‑Fi Direct 群組");}
    }
}

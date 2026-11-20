package com.rhaytransfer;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.content.res.AssetFileDescriptor;
import android.os.*;
import android.content.pm.ServiceInfo;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferService extends Service {
    private static final String CHANNEL="rhay_transfer_running";
    private static final int NOTIFICATION_ID=2201;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock cpuLock;
    private android.net.wifi.WifiManager.WifiLock wifiLock;
    private volatile boolean running=false;
    private final DecimalFormat df=new DecimalFormat("0.0");

    @Override public void onCreate(){super.onCreate();createChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf(startId);return START_NOT_STICKY;}
        String host=intent.getStringExtra("host");
        ArrayList<String> names=intent.getStringArrayListExtra("names");
        ArrayList<Uri> uris;
        if(Build.VERSION.SDK_INT>=33) uris=intent.getParcelableArrayListExtra("uris",Uri.class);
        else uris=intent.getParcelableArrayListExtra("uris");
        String common=intent.getStringExtra("common_dir");
        if(host==null||uris==null||names==null||uris.isEmpty()||names.size()!=uris.size()){stopSelf(startId);return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        running=true;
        Notification initial=buildNotification("Preparando transferência","Rhay Transfer está enviando arquivos ao PS4",0,false);
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,initial,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION_ID,initial);
        acquireLocks();
        final String destination=common==null?"/data/Rhay Transfer":common;
        worker.execute(()->transfer(host,uris,names,destination));
        return START_NOT_STICKY;
    }
    private void acquireLocks(){
        try{PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);cpuLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RhayTransfer:Cpu");cpuLock.acquire(6*60*60*1000L);}catch(Exception ignored){}
        try{android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);wifiLock=wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,"RhayTransfer:Wifi");wifiLock.acquire(6*60*60*1000L);}catch(Exception ignored){}
    }
    private void transfer(String host,ArrayList<Uri> uris,ArrayList<String> names,String common){
        FtpClient ftp=null;int completed=0;
        try{
            ftp=new FtpClient(host,2121);ftp.connectAndLogin();
            for(int idx=0;idx<uris.size();idx++){
                Uri uri=uris.get(idx);String name=names.get(idx);
                String folder=name.toLowerCase(Locale.ROOT).endsWith(".pkg")?"/data/pkg":common;
                long total=length(uri);long started=System.currentTimeMillis();
                long[] lastBytes={0},lastAt={started};double[] samples={0,0,0,0,0};int[] sampleCount={0},samplePos={0};
                final int current=idx+1,all=uris.size();
                updateNotification("Enviando "+current+"/"+all,name,0,true);
                ftp.upload(new FtpClient.UriSource(){
                    public InputStream open()throws IOException{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException("Não foi possível abrir "+name);return in;}
                    public long length(){return total;}
                },folder+"/"+name,(sent,size)->{
                    long now=System.currentTimeMillis();
                    if(now-lastAt[0]>=300||sent==size){
                        double instant=(sent-lastBytes[0])/1048576d/Math.max(.001,(now-lastAt[0])/1000d);
                        lastBytes[0]=sent;lastAt[0]=now;
                        samples[samplePos[0]]=instant;samplePos[0]=(samplePos[0]+1)%samples.length;if(sampleCount[0]<samples.length)sampleCount[0]++;
                        double avg=0;for(int j=0;j<sampleCount[0];j++)avg+=samples[j];avg/=Math.max(1,sampleCount[0]);
                        int pct=size>0?(int)Math.min(100,sent*100/size):0;
                        long remaining=size>sent&&avg>0?(long)((size-sent)/(avg*1048576d)):-1;
                        String eta=remaining<0?"Calculando tempo restante":"Restam "+formatTime(remaining);
                        String prog=size>0?pct+"% • "+human(sent)+" / "+human(size):human(sent)+" enviados";
                        String detail="Arquivo "+current+"/"+all+": "+name;
                        sendProgress(avg,pct,prog,eta,detail);
                        updateNotification(detail,df.format(avg)+" MB/s • "+prog,pct,true);
                    }
                });
                completed++;
                saveHistory(name,folder,total);
            }
            broadcast("done",0,100,"100%","Fila concluída","Concluído: "+completed+" arquivo(s) enviados ao PS4.");
            updateNotification("Transferência concluída",completed+" arquivo(s) enviados ao PS4",100,false);
            stopForeground(false);
            stopSelf();
        }catch(Exception e){
            String error=e.getMessage()==null?"Erro desconhecido":e.getMessage();
            broadcast("error",0,0,"0%","—","Falha no envio: "+error);
            updateNotification("Falha na transferência",error,0,false);
            stopForeground(false);stopSelf();
        }finally{if(ftp!=null)ftp.close();releaseLocks();running=false;}
    }
    private void sendProgress(double speed,int pct,String progress,String eta,String detail){
        Intent i=new Intent("com.rhaytransfer.TRANSFER");i.setPackage(getPackageName());i.putExtra("state","progress");i.putExtra("speed",speed);i.putExtra("percent",pct);i.putExtra("progress",progress);i.putExtra("eta",eta);i.putExtra("detail",detail);sendBroadcast(i);
    }
    private void broadcast(String state,double speed,int pct,String progress,String eta,String detail){
        Intent i=new Intent("com.rhaytransfer.TRANSFER");i.setPackage(getPackageName());i.putExtra("state",state);i.putExtra("speed",speed);i.putExtra("percent",pct);i.putExtra("progress",progress);i.putExtra("eta",eta);i.putExtra("detail",detail);sendBroadcast(i);
    }
    private Notification buildNotification(String title,String body,int pct,boolean ongoing){
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(title).setContentText(body).setContentIntent(pi).setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing).setCategory(Notification.CATEGORY_PROGRESS);
        if(ongoing)b.setProgress(100,Math.max(0,Math.min(100,pct)),false);
        return b.build();
    }
    private void updateNotification(String title,String body,int pct,boolean ongoing){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.notify(NOTIFICATION_ID,buildNotification(title,body,pct,ongoing));
    }
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Transferência em andamento",NotificationManager.IMPORTANCE_LOW);c.setDescription("Mostra o andamento dos envios ao PS4");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    private long length(Uri uri){try(AssetFileDescriptor a=getContentResolver().openAssetFileDescriptor(uri,"r")){return a==null?-1:a.getLength();}catch(Exception e){return -1;}}
    private String human(long n){if(n<0)return "tamanho desconhecido";if(n<1048576)return df.format(n/1024d)+" KB";if(n<1073741824)return df.format(n/1048576d)+" MB";return df.format(n/1073741824d)+" GB";}
    private String formatTime(long s){return s>=3600?(s/3600)+"h "+(s%3600/60)+"m":s>=60?(s/60)+"m "+(s%60)+"s":s+"s";}
    private void saveHistory(String name,String path,long bytes){
        android.content.SharedPreferences p=getSharedPreferences("rhay_transfer",MODE_PRIVATE);String old=p.getString("history","");String line="OK|"+System.currentTimeMillis()+"|"+name+"|"+path+"|"+bytes;String all=line+(old.isEmpty()?"":"\n"+old);String[] lines=all.split("\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(50,lines.length);i++){if(i>0)b.append("\n");b.append(lines[i]);}p.edit().putString("history",b.toString()).putInt("sent_count",p.getInt("sent_count",0)+1).apply();
    }
    private void releaseLocks(){try{if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();}catch(Exception ignored){}try{if(cpuLock!=null&&cpuLock.isHeld())cpuLock.release();}catch(Exception ignored){}}
    @Override public void onTaskRemoved(Intent rootIntent){super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){releaseLocks();worker.shutdown();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}

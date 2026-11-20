package com.rhaytransfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(4,9,25), PANEL=Color.rgb(10,22,48), LIGHT=Color.rgb(17,39,78);
    private static final int WHITE=Color.rgb(250,252,255), SILVER=Color.rgb(105,174,255), MUTED=Color.rgb(145,169,210), BLUE=Color.rgb(0,112,209), CYAN=Color.rgb(0,183,255);
    private static final int PICK=100, PICK_MULTI=101;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Uri> queue=new ArrayList<>();
    private final ArrayList<String> queueNames=new ArrayList<>();
    private final DecimalFormat df=new DecimalFormat("0.00");
    private SharedPreferences prefs;
    private LinearLayout content;
    private EditText ipAddress;
    private TextView connectionStatus, selectedFile, speedValue, progressLabel, etaLabel, transferStatus, sentCount, heroSpeed, heroSent;
    private ProgressBar progressBar;
    private Uri selectedUri;
    private String commonDirectory="/data/Rhay Transfer";
    private boolean sending=false;
    private boolean receiverRegistered=false;
    private final BroadcastReceiver transferReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){String state=i.getStringExtra("state");if(state==null)return; if(state.equals("progress")){double speed=i.getDoubleExtra("speed",0);int pct=i.getIntExtra("percent",0);String detail=i.getStringExtra("detail");speedValue.setText(df.format(speed)+" MB/s");if(heroSpeed!=null)heroSpeed.setText(df.format(speed)+" MB/s");String prog=i.getStringExtra("progress");progressBar.setIndeterminate(pct<=0&&prog!=null&&prog.contains("total desconhecido"));if(!progressBar.isIndeterminate())progressBar.setProgress(pct);progressLabel.setText(prog==null?pct+"%":prog);etaLabel.setText(i.getStringExtra("eta"));transferStatus.setText(detail);}else if(state.equals("done")){sending=false;speedValue.setText("0.00 MB/s");if(heroSpeed!=null)heroSpeed.setText("0.00 MB/s");progressBar.setProgress(100);progressLabel.setText("100%");etaLabel.setText("Fila concluída");transferStatus.setText(i.getStringExtra("detail"));selectedFile.setText("ENVIO CONCLUÍDO");queue.clear();queueNames.clear();selectedUri=null;if(heroSent!=null)heroSent.setText(String.valueOf(prefs.getInt("sent_count",0)));}else if(state.equals("error")){sending=false;transferStatus.setText(i.getStringExtra("detail"));etaLabel.setText("Verifique a conexão e tente novamente.");}}};
    private long lastUiUpdate=0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("rhay_transfer",MODE_PRIVATE);
        commonDirectory=prefs.getString("common_dir","/data/Rhay Transfer");
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        buildDashboard();
        createNotificationChannel();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},2020);
    }

    private void buildDashboard() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        content=new LinearLayout(this); content.setOrientation(1); content.setPadding(dp(20),dp(18),dp(20),dp(28)); scroll.addView(content); setContentView(scroll);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(1);
        brand.addView(text("PS4 TRANSFER",22,WHITE,true)); TextView sub=text("RHAY • GAMING FILE TRANSFER",9,SILVER,true); sub.setLetterSpacing(.13f); brand.addView(sub);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView ver=text("v2.5",11,SILVER,true); ver.setPadding(dp(12),dp(7),dp(12),dp(7)); ver.setBackground(round(LIGHT,30,SILVER,1)); top.addView(ver); content.addView(top);
        space(20);
        LinearLayout hero=card(PANEL); hero.setPadding(dp(17),dp(17),dp(17),dp(17));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.addView(text("●  PLAYSTATION 4",10,WHITE,true),new LinearLayout.LayoutParams(0,-2,1)); row.addView(text("REDE LOCAL",9,SILVER,true)); hero.addView(row);
        spaceIn(hero,12); hero.addView(text("Transfira. Conecte. Jogue.",22,WHITE,true)); hero.addView(text("Seu hub de arquivos para PS4",12,MUTED,false)); spaceIn(hero,16);
        LinearLayout stats=new LinearLayout(this); stats.addView(stat("0.00 MB/s","VELOCIDADE",true),new LinearLayout.LayoutParams(0,-2,1)); stats.addView(stat(String.valueOf(prefs.getInt("sent_count",0)),"ENVIOS",false),new LinearLayout.LayoutParams(0,-2,1)); hero.addView(stats); content.addView(hero);
        section("CONEXÃO DO CONSOLE","01"); LinearLayout conn=card(PANEL); conn.setPadding(dp(15),dp(15),dp(15),dp(15));
        LinearLayout cr=new LinearLayout(this); cr.setGravity(Gravity.CENTER_VERTICAL); cr.addView(text("PlayStation 4",16,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        connectionStatus=text("●  NÃO TESTADO",9,MUTED,true); connectionStatus.setPadding(dp(8),dp(6),dp(8),dp(6)); connectionStatus.setBackground(round(LIGHT,20,MUTED,1)); cr.addView(connectionStatus); conn.addView(cr);
        spaceIn(conn,12); conn.addView(text("ENDEREÇO IP",9,MUTED,true)); spaceIn(conn,5);
        ipAddress=new EditText(this); ipAddress.setSingleLine(true); ipAddress.setText(prefs.getString("ip","192.168.1.224")); ipAddress.setTextSize(15); ipAddress.setTextColor(WHITE); ipAddress.setSelectAllOnFocus(true); ipAddress.setPadding(dp(12),0,dp(12),0); ipAddress.setBackground(round(LIGHT,12,Color.rgb(75,75,82),1)); conn.addView(ipAddress,new LinearLayout.LayoutParams(-1,dp(48)));
        spaceIn(conn,10); Button test=button("TESTAR CONEXÃO FTP",BLUE,WHITE); conn.addView(test,new LinearLayout.LayoutParams(-1,dp(47))); test.setOnClickListener(v->testFtp());
        content.addView(conn);

        section("CENTRAL DE TRANSFERÊNCIA","02"); LinearLayout transfer=card(PANEL); transfer.setPadding(dp(15),dp(15),dp(15),dp(15));
        transfer.addView(text("Selecione seus arquivos",16,WHITE,true)); spaceIn(transfer,4); transfer.addView(text("PKG, fotos, vídeos, músicas e documentos",12,MUTED,false));
        spaceIn(transfer,8); transfer.addView(text("PKG → /data/pkg",10,SILVER,true)); transfer.addView(text("Outros → "+commonDirectory,10,SILVER,false)); spaceIn(transfer,12);
        selectedFile=text("Nenhum arquivo selecionado",12,MUTED,false); selectedFile.setPadding(dp(11),dp(12),dp(11),dp(12)); selectedFile.setBackground(round(LIGHT,11,Color.TRANSPARENT,0)); transfer.addView(selectedFile);
        spaceIn(transfer,10); Button one=button("＋  ESCOLHER ARQUIVO",LIGHT,WHITE); transfer.addView(one,new LinearLayout.LayoutParams(-1,dp(46))); one.setOnClickListener(v->chooseFiles(false));
        spaceIn(transfer,7); Button many=button("＋  SELECIONAR VÁRIOS",LIGHT,WHITE); transfer.addView(many,new LinearLayout.LayoutParams(-1,dp(46))); many.setOnClickListener(v->chooseFiles(true));
        spaceIn(transfer,10); Button send=button("INICIAR FILA DE ENVIO  →",BLUE,WHITE); transfer.addView(send,new LinearLayout.LayoutParams(-1,dp(50))); send.setOnClickListener(v->startQueue());
        content.addView(transfer);

        section("TRANSFERÊNCIA EM TEMPO REAL","03"); LinearLayout activity=card(PANEL); activity.setPadding(dp(15),dp(15),dp(15),dp(15));
        LinearLayout speedRow=new LinearLayout(this); speedRow.setGravity(Gravity.CENTER_VERTICAL); speedRow.addView(text("VELOCIDADE ATUAL",9,MUTED,true),new LinearLayout.LayoutParams(0,-2,1));
        speedValue=text("0.00 MB/s",22,WHITE,true); speedRow.addView(speedValue); activity.addView(speedRow);
        spaceIn(activity,12); progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progressBar.setMax(100); progressBar.setProgress(0); progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN)); progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LIGHT)); activity.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(8)));
        spaceIn(activity,8); LinearLayout pr=new LinearLayout(this); pr.addView(text("Progresso",11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1)); progressLabel=text("0%",12,WHITE,true); pr.addView(progressLabel); activity.addView(pr);
        spaceIn(activity,5); etaLabel=text("Tempo restante: —",11,MUTED,false); activity.addView(etaLabel);
        spaceIn(activity,8); transferStatus=text("Aguardando transferência",11,MUTED,false); activity.addView(transferStatus);
        content.addView(activity);

        section("FERRAMENTAS DO PS4","04"); LinearLayout tools=new LinearLayout(this); tools.setOrientation(1);
        Button browse=button("▣  GERENCIADOR DE ARQUIVOS DO PS4",LIGHT,WHITE); tools.addView(browse,new LinearLayout.LayoutParams(-1,dp(46))); browse.setOnClickListener(v->openRemoteManager());
        spaceIn(tools,7); Button history=button("◷  HISTÓRICO DE TRANSFERÊNCIAS",LIGHT,WHITE); tools.addView(history,new LinearLayout.LayoutParams(-1,dp(46))); history.setOnClickListener(v->showHistory());
        spaceIn(tools,7); Button clear=button("LIMPAR FILA DE ENVIO",LIGHT,WHITE); tools.addView(clear,new LinearLayout.LayoutParams(-1,dp(42))); clear.setOnClickListener(v->{if(!sending){queue.clear();queueNames.clear();selectedUri=null;selectedFile.setText("Fila vazia");progressBar.setProgress(0);progressLabel.setText("0%");}else Toast.makeText(this,"Aguarde o envio atual terminar.",Toast.LENGTH_SHORT).show();});
        content.addView(tools);
        space(20); TextView foot=text("PS4 TRANSFER  •  POWERED BY RHAY",9,MUTED,true); foot.setGravity(Gravity.CENTER); foot.setLetterSpacing(.08f); content.addView(foot);
    }

    private void chooseFiles(boolean multiple) {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        if(multiple){i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_MULTI);}else startActivityForResult(i,PICK);
    }
    private String fileName(Uri uri) {
        String n=uri.getLastPathSegment();
        try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int x=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(x>=0)n=c.getString(x);}}catch(Exception ignored){}
        return n==null?"arquivo":n.replaceAll("[\\\\/\\r\\n]","_");
    }
    private long fileLength(Uri uri) {
        try(android.content.res.AssetFileDescriptor a=getContentResolver().openAssetFileDescriptor(uri,"r")){return a==null?-1:a.getLength();}catch(Exception e){return -1;}
    }
    private String ip(){String s=ipAddress.getText().toString().trim();if(s.isEmpty())throw new IllegalArgumentException("Informe o IP do PS4.");prefs.edit().putString("ip",s).apply();return s;}
    private void testFtp(){
        final String host;try{host=ip();}catch(Exception e){toast(e.getMessage());return;}
        connectionStatus.setText("● TESTANDO..."); connectionStatus.setTextColor(SILVER);
        io.execute(()->{FtpClient f=new FtpClient(host,2121);try{String r=f.connectAndLogin();main.post(()->{connectionStatus.setText("● CONECTADO");connectionStatus.setTextColor(WHITE);toast(r);});}
        catch(Exception e){main.post(()->{connectionStatus.setText("● FALHA");connectionStatus.setTextColor(MUTED);toast("Falha: "+e.getMessage());});}finally{f.close();}});
    }
    private void startQueue(){
        if(sending){toast("Já existe uma transferência em andamento.");return;}
        if(queue.isEmpty()&&selectedUri!=null){queue.add(selectedUri);queueNames.add(fileName(selectedUri));}
        if(queue.isEmpty()){toast("Selecione pelo menos um arquivo.");return;}
        final String host;try{host=ip();}catch(Exception e){toast(e.getMessage());return;}
        ArrayList<Uri> items=new ArrayList<>(queue);ArrayList<String> names=new ArrayList<>(queueNames);
        Intent service=new Intent(this,TransferService.class);
        service.putExtra("host",host);service.putStringArrayListExtra("names",names);service.putParcelableArrayListExtra("uris",items);
        service.putExtra("common_dir",commonDirectory);
        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);sending=true;transferStatus.setText("Transferência em segundo plano iniciada");selectedFile.setText(items.size()+" arquivo(s) na fila");toast("Envio iniciado. Você pode sair do Rhay Transfer; ele continuará em segundo plano.");}
        catch(Exception e){sending=false;toast("Não foi possível iniciar o envio: "+e.getMessage());}
    }
    private void addHistory(String name,String path,boolean success,long bytes){
        synchronized(this){String old=prefs.getString("history","");String line=(success?"OK":"ERRO")+"|"+System.currentTimeMillis()+"|"+name+"|"+path+"|"+bytes;
            String all=line+(old.isEmpty()?"":"\n"+old);String[] lines=all.split("\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(50,lines.length);i++){if(i>0)b.append("\n");b.append(lines[i]);}prefs.edit().putString("history",b.toString()).apply();int count=prefs.getInt("sent_count",0)+(success?1:0);prefs.edit().putInt("sent_count",count).apply();}
    }
    private void showHistory(){
        String raw=prefs.getString("history","");if(raw.isEmpty()){new AlertDialog.Builder(this).setTitle("Histórico").setMessage("Nenhuma transferência registrada ainda.").setPositiveButton("OK",null).show();return;}
        StringBuilder b=new StringBuilder();for(String line:raw.split("\n")){String[] p=line.split("\\|",5);if(p.length>=5){String date=new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date(Long.parseLong(p[1])));b.append(p[0].equals("OK")?"✓ ":"✕ ").append(date).append("  ").append(p[2]).append("\n").append(p[3]).append(" • ").append(human(parseLong(p[4]))).append("\n\n");}}
        new AlertDialog.Builder(this).setTitle("Histórico de transferências").setMessage(b.toString()).setPositiveButton("Fechar",null).setNeutralButton("Limpar histórico",(d,w)->prefs.edit().remove("history").putInt("sent_count",0).apply()).show();
    }
    private void openRemoteManager(){final String host;try{host=ip();}catch(Exception e){toast(e.getMessage());return;}browseDirectory(host,"/data");}
    private void browseDirectory(String host,String path){
        io.execute(()->{FtpClient f=new FtpClient(host,2121);try{f.connectAndLogin();List<FtpClient.RemoteEntry> entries=f.listDirectory(path);main.post(()->{
            ArrayList<String> labels=new ArrayList<>();for(FtpClient.RemoteEntry e:entries)labels.add((e.directory?"📁  ":"📄  ")+e.name);
            String[] items=labels.toArray(new String[0]);AlertDialog.Builder dlg=new AlertDialog.Builder(this).setTitle("PS4 • "+path).setItems(items,(d,which)->{FtpClient.RemoteEntry e=entries.get(which);if(e.directory)browseDirectory(host,(path.equals("/")?"":path)+"/"+e.name);else toast("Arquivo: "+e.name);});
            dlg.setPositiveButton("Usar esta pasta para arquivos comuns",(d,w)->{commonDirectory=path;prefs.edit().putString("common_dir",path).apply();toast("Destino atualizado: "+path);rebuild();});
            dlg.setNegativeButton("Fechar",null);if(!path.equals("/"))dlg.setNeutralButton("Voltar",(d,w)->{int slash=path.lastIndexOf('/');browseDirectory(host,slash<=0?"/":path.substring(0,slash));});dlg.show();
        });}catch(Exception e){main.post(()->toast("Não foi possível listar "+path+": "+e.getMessage()));}finally{f.close();}});
    }
    private void rebuild(){content.removeAllViews();buildDashboard();}
    private void notifyDone(String title,String message){
        try{NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification.Builder n=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(this,"rhay_transfer_done_v25"):new android.app.Notification.Builder(this);
            n.setSmallIcon(R.drawable.ic_stat_transfer_v25).setContentTitle(title).setContentText(message).setAutoCancel(true).setContentIntent(pi);nm.notify(1,n.build());
        }catch(Exception ignored){}
    }
    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("rhay_transfer_done_v25","PS4 Transfer • avisos",NotificationManager.IMPORTANCE_DEFAULT);((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    private String human(long n){if(n<0)return "tamanho desconhecido";if(n<1024)return n+" B";if(n<1048576)return df.format(n/1024d)+" KB";if(n<1073741824)return df.format(n/1048576d)+" MB";return df.format(n/1073741824d)+" GB";}
    private String formatTime(long s){return s>=3600?(s/3600)+"h "+(s%3600/60)+"m":s>=60?(s/60)+"m "+(s%60)+"s":s+"s";}
    private long parseLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private LinearLayout stat(String value,String label,boolean speed){LinearLayout x=new LinearLayout(this);x.setOrientation(1);TextView v=text(value,20,WHITE,true);if(speed)heroSpeed=v;else heroSent=v;x.addView(v);x.addView(text(label,9,MUTED,true));return x;}
    private void section(String s,String n){space(19);LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(text(s,10,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));r.addView(text(n,10,SILVER,true));content.addView(r);space(8);}
    private LinearLayout card(int c){LinearLayout x=new LinearLayout(this);x.setOrientation(1);x.setBackground(round(c,17,Color.rgb(58,58,64),1));return x;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(fg);b.setTypeface(null,Typeface.BOLD);b.setBackground(round(bg,12,bg,0));return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private GradientDrawable round(int c,int r,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(sw>0)d.setStroke(dp(sw),stroke);return d;}
    private void space(int h){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void spaceIn(LinearLayout p,int h){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private void persistReadPermission(Uri uri){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;
        if(req==PICK){Uri u=data.getData();if(u!=null){persistReadPermission(u);selectedUri=u;queue.clear();queueNames.clear();queue.add(u);queueNames.add(fileName(u));selectedFile.setText("1 arquivo: "+fileName(u));}}
        else if(req==PICK_MULTI){queue.clear();queueNames.clear();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++){Uri u=data.getClipData().getItemAt(i).getUri();persistReadPermission(u);queue.add(u);queueNames.add(fileName(u));}}else if(data.getData()!=null){persistReadPermission(data.getData());queue.add(data.getData());queueNames.add(fileName(data.getData()));}selectedUri=null;selectedFile.setText(queue.size()+" arquivo(s) na fila");}
    }
    @Override protected void onStart(){super.onStart();if(!receiverRegistered){IntentFilter f=new IntentFilter("com.rhaytransfer.TRANSFER");if(Build.VERSION.SDK_INT>=33)registerReceiver(transferReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(transferReceiver,f);receiverRegistered=true;}restoreProgress();}
    @Override protected void onStop(){if(receiverRegistered){try{unregisterReceiver(transferReceiver);}catch(Exception ignored){}receiverRegistered=false;}super.onStop();}
    private void restoreProgress(){String state=prefs.getString("last_state","");if(state.equals("progress")){double speed=prefs.getFloat("last_speed",0f);int pct=prefs.getInt("last_percent",0);String prog=prefs.getString("last_progress","0%");speedValue.setText(df.format(speed)+" MB/s");if(heroSpeed!=null)heroSpeed.setText(df.format(speed)+" MB/s");progressBar.setIndeterminate(pct<=0&&prog.contains("total desconhecido"));if(!progressBar.isIndeterminate())progressBar.setProgress(pct);progressLabel.setText(prog);etaLabel.setText(prefs.getString("last_eta","Calculando tempo restante"));transferStatus.setText(prefs.getString("last_detail","Transferência em andamento"));}else if(state.equals("done")){progressBar.setIndeterminate(false);progressBar.setProgress(100);progressLabel.setText("100%");etaLabel.setText("Fila concluída");transferStatus.setText(prefs.getString("last_detail","Transferência concluída"));}else if(state.equals("error")){progressBar.setIndeterminate(false);transferStatus.setText(prefs.getString("last_detail","Falha na transferência"));}}
    @Override protected void onDestroy(){super.onDestroy();io.shutdownNow();}
}

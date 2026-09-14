package it.lorenzo.tracciagps;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView state,stats,battery,waypoints;
    private Button record,point,export,clear;
    private Store store;
    private LocationManager manager;
    private LocationListener single;
    private String pendingAction;
    private boolean starting=false;
    private static volatile boolean exporting=false;
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=Store.get(this);manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(!TrackingService.running){store.recover();TrackingService.status="Pronto · eventuali registrazioni interrotte sono conservate";}
        if(b!=null)pendingAction=b.getString("pendingAction");
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(246,245,240));
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(20),dp(22),dp(20),dp(28));scroll.addView(layout);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        text(layout,"TRACCIA GPS OFFLINE",26,true);
        text(layout,"Registra il cammino, anche a schermo spento",16,false);
        state=text(layout,"",16,true);stats=text(layout,"",21,true);
        record=button(layout,"Avvia traccia",()->{if(TrackingService.running)new AlertDialog.Builder(this).setTitle("Fermare la traccia?").setMessage("I punti registrati resteranno salvati.").setPositiveButton("Ferma",(d,w)->stopService(new Intent(this,TrackingService.class))).setNegativeButton("Continua",null).show();else permissions("track");});
        point=button(layout,"Salva punto qui",()->permissions("point"));
        export=button(layout,"Esporta tutti i dati in GPX",()->{
            if(!store.hasPoints()){message("Nessun punto da esportare");return;}
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/gpx+xml").putExtra(Intent.EXTRA_TITLE,"traccia_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ITALY).format(new Date())+".gpx");
            try{startActivityForResult(i,30);}catch(ActivityNotFoundException e){message("Nessun gestore documenti disponibile");}
        });
        text(layout,"Registrazione a schermo spento",20,true);
        battery=text(layout,"",15,true);
        text(layout,"Prima di partire, disattiva il risparmio energetico del telefono. Nelle impostazioni di questa app scegli Batteria → Senza restrizioni (o Nessuna restrizione). Su Xiaomi / HyperOS controlla anche l’avvio automatico in background, se presente. I nomi dei menu possono cambiare.",15,false);
        button(layout,"Apri impostazioni di questa app",()->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        button(layout,"Apri ottimizzazione batteria",()->openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        button(layout,"Apri risparmio energetico",()->openSettings(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        text(layout,"Dopo l’avvio puoi spegnere lo schermo. Controlla la notifica di registrazione. Non usare Arresto forzato e non chiudere l’app con strumenti di pulizia. Dopo un riavvio del telefono riapri l’app e avvia una nuova registrazione.",15,false);
        text(layout,"Punti singoli",20,true);waypoints=text(layout,"",15,false);waypoints.setTextIsSelectable(true);
        clear=button(layout,"Cancella tutti i dati",()->{
            if(TrackingService.running||starting||single!=null||exporting){message("Prima ferma la registrazione e attendi le operazioni in corso");return;}
            new AlertDialog.Builder(this).setTitle("Cancellare tutti i dati?").setMessage("Esporta prima il GPX. La cancellazione non può essere annullata.").setPositiveButton("Cancella",(d,w)->{if(TrackingService.running||starting||single!=null||exporting)return;try{store.clear();render();}catch(Exception e){message("Cancellazione fallita: "+e.getMessage());}}).setNegativeButton("Annulla",null).show();
        });
        text(layout,"Tutto resta sul dispositivo: nessun account, nessuna pubblicità, nessun invio delle posizioni. Esporta prima di disinstallare o cancellare i dati dell’app. La distanza è una stima e può aumentare per l’oscillazione del GPS; i fix oltre ±100 m vengono scartati. Nessuna mappa inclusa. Versione beta: prova prima una breve registrazione a schermo spento.",13,false);
        if(!getPreferences(0).getBoolean("intro",false)){
            new AlertDialog.Builder(this).setTitle("Prima di registrare").setMessage("Disattiva il risparmio energetico e imposta questa app su Batteria → Senza restrizioni. Consenti posizione precisa e notifiche. L’app non può cambiare queste impostazioni al posto tuo.").setPositiveButton("Ho capito",(d,w)->getPreferences(0).edit().putBoolean("intro",true).apply()).show();
        }
    }
    private TextView text(LinearLayout l,String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(31,61,46));if(bold)t.setTypeface(null,1);t.setPadding(0,dp(9),0,dp(9));l.addView(t);return t;}
    private Button button(LinearLayout l,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(54));b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(47,90,66)));l.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->r.run());return b;}
    private void openSettings(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){message("Menu non disponibile: apri Impostazioni → App → Traccia GPS Offline → Batteria");}}
    private void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();}
    private void render(){
        if(state==null)return;
        state.setText(TrackingService.status);stats.setText(store.stats());waypoints.setText(store.waypoints());
        record.setText(TrackingService.running?"Ferma traccia":starting?"Avvio in corso…":"Avvia traccia");record.setEnabled(!starting);point.setEnabled(single==null);
        export.setEnabled(!exporting);clear.setEnabled(!TrackingService.running&&!starting&&single==null&&!exporting);
        PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
        String power=p.isPowerSaveMode()?"⚠ Risparmio energetico ATTIVO: disattivalo.":"Risparmio energetico generale disattivato.";
        power+=p.isIgnoringBatteryOptimizations(getPackageName())?"\nEsclusione dall’ottimizzazione Android attiva.":"\n⚠ App soggetta all’ottimizzazione Android: controlla le impostazioni.";
        battery.setText(power+"\nLe restrizioni aggiuntive del produttore vanno verificate manualmente.");
    }
    private void permissions(String action){
        pendingAction=action;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("notificationAsked",false)){
            getPreferences(0).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);return;
        }
        pendingAction=null;
        if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){new AlertDialog.Builder(this).setMessage("Attiva la posizione GPS e riprova.").setPositiveButton("Impostazioni",(d,w)->openSettings(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Annulla",null).show();return;}
        if("track".equals(action)){
            if(TrackingService.running||starting)return;
            Runnable start=()->{try{starting=true;render();startForegroundService(new Intent(this,TrackingService.class));handler.postDelayed(()->{starting=false;render();},1000);}catch(Exception e){starting=false;message("Avvio fallito: "+e.getMessage());render();}};
            PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
            if(p.isPowerSaveMode() || !p.isIgnoringBatteryOptimizations(getPackageName()))new AlertDialog.Builder(this).setTitle("Controlla la batteria").setMessage("Per registrare a schermo spento disattiva il risparmio energetico e scegli Senza restrizioni per questa app.").setPositiveButton("Impostazioni",(d,w)->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNeutralButton("Avvia comunque",(d,w)->start.run()).setNegativeButton("Annulla",null).show();else start.run();
        }else if("point".equals(action))singlePoint();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] results){super.onRequestPermissionsResult(r,p,results);
        if(r==10){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingAction=null;new AlertDialog.Builder(this).setMessage("Serve la posizione precisa. Apri Autorizzazioni → Posizione e consenti durante l’uso dell’app, con posizione precisa.").setPositiveButton("Impostazioni",(d,w)->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Annulla",null).show();return;}}
        if(r==11 && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Notifiche disattivate: non vedrai il comando Ferma nella tendina",Toast.LENGTH_LONG).show();
        if(pendingAction!=null)permissions(pendingAction);
    }
    private void singlePoint(){
        if(single!=null)return;
        single=new LocationListener(){
            public void onLocationChanged(Location p){
                if(single!=this || SystemClock.elapsedRealtimeNanos()-p.getElapsedRealtimeNanos()>10000000000L || !p.hasAccuracy() || p.getAccuracy()>100)return;
                cancelSingle();
                String name="Punto "+new SimpleDateFormat("dd/MM HH:mm:ss",Locale.ITALY).format(new Date(p.getTime()));
                try{store.add(p,null,name,0,false);message("Punto salvato · precisione ±"+Math.round(p.getAccuracy())+" m. Usa Esporta per salvarlo nel GPX.");}catch(Exception e){message("Salvataggio fallito: "+e.getMessage());}render();
            }
            public void onProviderDisabled(String p){} public void onProviderEnabled(String p){} public void onStatusChanged(String p,int s,Bundle b){}
        };
        point.setText("Ricerca posizione…");point.setEnabled(false);
        try{manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,single,Looper.getMainLooper());
            LocationListener request=single;handler.postDelayed(()->{if(single==request){cancelSingle();message("Nessun fix preciso entro 30 secondi. Vai all’aperto e riprova.");}},30000);
        }catch(Exception e){cancelSingle();message("GPS non disponibile: "+e.getMessage());}
    }
    private void cancelSingle(){if(single!=null){manager.removeUpdates(single);single=null;}if(point!=null){point.setText("Salva punto qui");point.setEnabled(true);}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==30 && result==RESULT_OK && data!=null && data.getData()!=null){if(exporting){message("Esportazione già in corso");return;}Uri uri=data.getData();exporting=true;render();
            io.execute(()->{String outcome;try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("Destinazione non disponibile");try(Writer w=new OutputStreamWriter(out,java.nio.charset.StandardCharsets.UTF_8)){store.export(w);}outcome="GPX esportato correttamente.";
            }catch(Exception e){outcome="Esportazione fallita: "+e.getMessage()+". I dati nell’app sono conservati; riprova.";}
                String resultText=outcome;runOnUiThread(()->{exporting=false;if(!isDestroyed()){render();message(resultText);}});
            });
        }
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(refresh);cancelSingle();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("pendingAction",pendingAction);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);cancelSingle();io.shutdown();super.onDestroy();}
}

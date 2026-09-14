package it.lorenzo.tracciagps;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;

public final class TrackingService extends Service implements LocationListener {
    public static volatile boolean running=false;
    public static volatile String status="Pronto a registrare";
    public static final String STOP="it.lorenzo.tracciagps.STOP";
    private LocationManager manager;
    private Location last;
    private long session=-1;
    private boolean foreground=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable watchdog=new Runnable(){public void run(){
        if(!running)return;
        if(last==null || SystemClock.elapsedRealtimeNanos()-last.getElapsedRealtimeNanos()>60000000000L){
            status="In attesa di un fix GPS recente. Vai all’aperto e controlla la posizione.";notifyStatus();
        }
        handler.postDelayed(this,15000);
    }};
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        NotificationChannel c=new NotificationChannel("tracking","Registrazione GPS",NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Stato GPS e comando per fermare la registrazione");getSystemService(NotificationManager.class).createNotificationChannel(c);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null && STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        try{
            status="Registrazione avviata · in attesa dei satelliti";
            if(Build.VERSION.SDK_INT>=29)startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(1,notification());
            foreground=true;
            if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER))throw new IllegalStateException("Attiva la posizione GPS dalle impostazioni");
            session=Store.get(this).begin();
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000,0,this,Looper.getMainLooper());
            running=true;handler.postDelayed(watchdog,15000);
        }catch(Exception e){status="Registrazione non avviata: "+e.getMessage();stopSelf();}
        // Nessuna ripartenza nascosta dopo un arresto del sistema: i punti restano nel database.
        return START_NOT_STICKY;
    }
    @Override public void onLocationChanged(Location p){
        if(!running)return;
        if(!Double.isFinite(p.getLatitude())||!Double.isFinite(p.getLongitude())||Math.abs(p.getLatitude())>90||Math.abs(p.getLongitude())>180||!p.hasAccuracy())return;
        if(SystemClock.elapsedRealtimeNanos()-p.getElapsedRealtimeNanos()>30000000000L)return;
        if(p.getAccuracy()>100){status="Segnale debole (±"+Math.round(p.getAccuracy())+" m): punto non registrato";notifyStatus();return;}
        if(last!=null && p.getElapsedRealtimeNanos()<=last.getElapsedRealtimeNanos())return;
        boolean gap=last==null || p.getElapsedRealtimeNanos()-last.getElapsedRealtimeNanos()>60000000000L;
        double distance=gap?0:last.distanceTo(p);
        try{Store.get(this).add(p,session,null,distance,gap);last=new Location(p);
            status="Registrazione attiva · precisione ±"+Math.round(p.getAccuracy())+" m";notifyStatus();
        }catch(Exception e){status="Salvataggio fallito: "+e.getMessage();stopSelf();}
    }
    @Override public void onProviderDisabled(String provider){status="GPS disattivato: riattiva la posizione";notifyStatus();}
    @Override public void onProviderEnabled(String provider){status="GPS attivato · ricerca satelliti";notifyStatus();}
    @Override public void onStatusChanged(String provider,int state,Bundle extras){}
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,TrackingService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"tracking").setSmallIcon(R.drawable.ic_notification).setContentTitle("Traccia GPS Offline").setContentText(status).setStyle(new Notification.BigTextStyle().bigText(status)).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(null,"Ferma registrazione",stop).build()).build();
    }
    private void notifyStatus(){if(foreground)getSystemService(NotificationManager.class).notify(1,notification());}
    @Override public void onDestroy(){
        running=false;handler.removeCallbacksAndMessages(null);
        if(manager!=null){try{manager.removeUpdates(this);}catch(SecurityException ignored){}}
        if(session>=0){try{Store.get(this).finish(session);}catch(Exception e){status="Errore chiusura: esporta i dati appena possibile";}}
        if(status.startsWith("Registrazione attiva")||status.startsWith("Registrazione avviata")||status.startsWith("In attesa"))status="Registrazione fermata · dati conservati";
        if(foreground)stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}

package it.lorenzo.tracciagps;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.location.Location;
import java.io.*;

public final class Store extends SQLiteOpenHelper {
    private static Store instance;
    public static synchronized Store get(Context c) { if(instance==null) instance=new Store(c.getApplicationContext()); return instance; }
    private Store(Context c) { super(c,"tracks.db",null,1); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, end INTEGER)");
        d.execSQL("CREATE TABLE points(id INTEGER PRIMARY KEY, session INTEGER, lat REAL NOT NULL, lon REAL NOT NULL, alt REAL, time INTEGER NOT NULL, accuracy REAL NOT NULL, name TEXT, distance REAL DEFAULT 0, gap INTEGER DEFAULT 0)");
        d.execSQL("CREATE INDEX point_session ON points(session,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int oldV,int newV) { throw new IllegalStateException("Migrazione richiesta"); }
    public synchronized void recover() {
        getWritableDatabase().execSQL("UPDATE sessions SET end=MAX(start,COALESCE((SELECT MAX(time) FROM points WHERE session=sessions.id),start)) WHERE end IS NULL");
    }
    public synchronized long begin() {
        recover(); ContentValues v=new ContentValues();v.put("start",System.currentTimeMillis());return getWritableDatabase().insertOrThrow("sessions",null,v);
    }
    public synchronized void finish(long id) {
        ContentValues v=new ContentValues();v.put("end",System.currentTimeMillis());getWritableDatabase().update("sessions",v,"id=?",new String[]{""+id});
    }
    public synchronized void add(Location p,Long session,String name,double distance,boolean gap) {
        ContentValues v=new ContentValues(); if(session!=null)v.put("session",session);
        v.put("lat",p.getLatitude());v.put("lon",p.getLongitude());if(p.hasAltitude())v.put("alt",p.getAltitude());
        v.put("time",p.getTime());v.put("accuracy",p.getAccuracy());v.put("name",name);v.put("distance",distance);v.put("gap",gap?1:0);
        getWritableDatabase().insertOrThrow("points",null,v);
    }
    public synchronized String stats() {
        long n=0,wp=0,duration=0;double distance=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(distance),0),SUM(CASE WHEN session IS NULL THEN 1 ELSE 0 END) FROM points",null)){
            if(c.moveToFirst()){n=c.getLong(0);distance=c.getDouble(1);wp=c.getLong(2);}
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT start,end FROM sessions",null)) {while(c.moveToNext())duration+=Math.max(0,(c.isNull(1)?System.currentTimeMillis():c.getLong(1))-c.getLong(0));}
        long sec=duration/1000;
        return String.format(java.util.Locale.ITALY,"%.2f km  ·  %dh %02dm %02ds\n%d punti traccia  ·  %d punti singoli",distance/1000,sec/3600,sec/60%60,sec%60,n-wp,wp);
    }
    public synchronized boolean hasPoints() {try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM points LIMIT 1",null)){return c.moveToFirst();}}
    public synchronized void clear() {SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("points",null,null);d.delete("sessions",null,null);d.setTransactionSuccessful();}finally{d.endTransaction();}}
    public synchronized String waypoints() {
        StringBuilder s=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name,lat,lon,accuracy FROM points WHERE session IS NULL ORDER BY id DESC LIMIT 30",null)){
            while(c.moveToNext())s.append(c.getString(0)).append('\n').append(String.format(java.util.Locale.ITALY,"%.6f, %.6f · ±%.0f m\n\n",c.getDouble(1),c.getDouble(2),c.getDouble(3)));
        }
        return s.length()==0?"Nessun punto singolo salvato.":s.toString();
    }
    public void export(Writer w) throws IOException {
        long maxId=0;
        try(Cursor top=getReadableDatabase().rawQuery("SELECT COALESCE(MAX(id),0) FROM points",null)){if(top.moveToFirst())maxId=top.getLong(0);}
        Gpx.header(w);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT lat,lon,alt,time,name,accuracy FROM points WHERE session IS NULL AND id<=? ORDER BY id",new String[]{""+maxId})){while(c.moveToNext())emit(w,c,"wpt");}
        try(Cursor sessions=getReadableDatabase().rawQuery("SELECT id,start FROM sessions WHERE EXISTS (SELECT 1 FROM points WHERE session=sessions.id AND points.id<=?) ORDER BY id",new String[]{""+maxId})){
            while(sessions.moveToNext()){
                w.write("<trk><name>Traccia "+java.time.Instant.ofEpochMilli(sessions.getLong(1))+"</name><trkseg>\n");
                try(Cursor c=getReadableDatabase().rawQuery("SELECT lat,lon,alt,time,name,accuracy,gap FROM points WHERE session=? AND id<=? ORDER BY id",new String[]{sessions.getString(0),""+maxId})){
                    boolean first=true;while(c.moveToNext()){if(!first && c.getInt(6)==1)w.write("</trkseg><trkseg>\n");emit(w,c,"trkpt");first=false;}
                }
                w.write("</trkseg></trk>\n");
            }
        }
        w.write("</gpx>\n");w.flush();
    }
    private void emit(Writer w,Cursor c,String tag) throws IOException {Gpx.point(w,tag,c.getDouble(0),c.getDouble(1),c.isNull(2)?null:c.getDouble(2),c.getLong(3),c.getString(4),c.getDouble(5));}
}

package it.lorenzo.tracciagps;

import java.io.*;
import java.time.Instant;

public final class Gpx {
    private Gpx() {}
    public static String escape(String s) {
        StringBuilder b = new StringBuilder();
        s.codePoints().filter(c -> c==9 || c==10 || c==13 || (c>=32 && c<=0xd7ff) || (c>=0xe000 && c<=0xfffd) || (c>=0x10000 && c<=0x10ffff)).forEach(b::appendCodePoint);
        return b.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }
    public static void header(Writer w) throws IOException {
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Traccia GPS Android\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
    }
    public static void point(Writer w, String tag, double lat, double lon, Double alt, long time, String name, double accuracy) throws IOException {
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180) throw new IllegalArgumentException("Coordinate non valide");
        w.write("<"+tag+" lat=\""+lat+"\" lon=\""+lon+"\">");
        if(alt!=null && Double.isFinite(alt)) w.write("<ele>"+alt+"</ele>");
        w.write("<time>"+Instant.ofEpochMilli(time)+"</time>");
        if(name!=null) w.write("<name>"+escape(name)+"</name>");
        w.write("<desc>Precisione orizzontale: "+Math.round(accuracy)+" m</desc></"+tag+">\n");
    }
}

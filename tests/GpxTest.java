import it.lorenzo.tracciagps.Gpx;
import java.io.*;
import javax.xml.parsers.*;
public class GpxTest {
    public static void main(String[] args) throws Exception {
        StringWriter w=new StringWriter();Gpx.header(w);
        Gpx.point(w,"wpt",45.1,7.2,null,1700000000000L,"Rocca & <Punto> \"A\"\u0001",4);
        w.write("<trk><name>Traccia</name><trkseg>");
        Gpx.point(w,"trkpt",45.1,7.2,1200.0,1700000000000L,null,5);
        w.write("</trkseg><trkseg>");
        Gpx.point(w,"trkpt",45.2,7.3,1300.0,1700000120000L,null,5);
        w.write("</trkseg></trk></gpx>");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
        var d=f.newDocumentBuilder().parse(new ByteArrayInputStream(w.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String ns="http://www.topografix.com/GPX/1/1";
        if(d.getElementsByTagNameNS(ns,"trkseg").getLength()!=2)throw new AssertionError("Segmenti");
        if(!d.getElementsByTagNameNS(ns,"name").item(0).getTextContent().equals("Rocca & <Punto> \"A\""))throw new AssertionError("Escape XML");
        if(d.getElementsByTagNameNS(ns,"ele").getLength()!=2)throw new AssertionError("Quota nulla");
        try{Gpx.point(w,"wpt",Double.NaN,7.2,null,0,"err",1);throw new AssertionError("Accetta NaN");}catch(IllegalArgumentException expected){}
        try{Gpx.point(w,"wpt",91,7.2,null,0,"err",1);throw new AssertionError("Accetta latitudine fuori intervallo");}catch(IllegalArgumentException expected){}
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),w.toString());
        System.out.println("PASS: GPX, segmenti, escaping, quota assente, coordinate non valide.");
    }
}

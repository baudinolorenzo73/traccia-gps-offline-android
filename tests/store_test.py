import sqlite3, re, pathlib
source=pathlib.Path(__file__).parents[1]/'app/src/main/java/it/lorenzo/tracciagps/Store.java'
s=source.read_text()
db=sqlite3.connect(':memory:')
for q in re.findall(r'd.execSQL\("([^"]+)"\)',s): db.execute(q)
db.execute('INSERT INTO sessions VALUES (1,1000,NULL)')
db.execute('INSERT INTO points(session,lat,lon,time,accuracy) VALUES (1,45,7,2000,5)')
db.execute('INSERT INTO sessions VALUES (2,3000,NULL)')
recovery=re.search(r'getWritableDatabase\(\).execSQL\("([^"]+)"\)',s)[1]
db.execute(recovery)
assert db.execute('SELECT end FROM sessions ORDER BY id').fetchall()==[(2000,),(3000,)]
max_id=db.execute('SELECT MAX(id) FROM points').fetchone()[0]
db.execute('INSERT INTO points(session,lat,lon,time,accuracy) VALUES (1,45,7,2500,5)')
q=re.search(r'SELECT lat,lon,alt,time,name,accuracy,gap[^"\n]+',s)[0]
assert len(db.execute(q,(1,max_id)).fetchall())==1
print('PASS: recupero interruzione con e senza fix; esportazione limitata ai punti presenti all’avvio.')

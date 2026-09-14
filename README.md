# Traccia GPS Offline — Android 1.0 beta 1

App Android nativa per registrare tracce e punti singoli e salvarli in GPX. Compatibile con Android 8.0 o successivo su dispositivi con GPS. Interfaccia in italiano; nessuna mappa e nessuna connessione necessaria per registrare. Non richiede Google Play Services, account o root.

## Download beta

[Scarica Traccia GPS Offline beta per Android](downloads/Traccia-GPS-Offline-beta.apk)

Prima dell'uso in escursione, eseguire una prova di 10–15 minuti all'aperto con lo schermo spento e impostare l'app su **Batteria → Senza restrizioni**.

## Installazione e primo utilizzo

1. Copia sul telefono `Traccia-GPS-Offline-beta.apk` e aprilo. Se Android lo richiede, autorizza l’installazione da questa specifica app (file manager o browser); puoi revocarla dopo l’installazione.
2. Apri Traccia GPS Offline e consenti **posizione precisa durante l’uso**. Il servizio avviato mentre l’app è visibile prosegue a schermo spento. Consenti le notifiche per avere il comando Ferma nella tendina.
3. **Disattiva il risparmio energetico generale**. In **Impostazioni → App → Traccia GPS Offline → Batteria** scegli **Senza restrizioni / Nessuna restrizione**. I pulsanti nell’app aprono le impostazioni pertinenti; alcuni produttori usano nomi diversi.
4. Su **Xiaomi / HyperOS**, verifica anche l’**avvio automatico in background**, se disponibile. L’app non può verificare tutte le restrizioni proprietarie. L’indicazione di esenzione Android non certifica l’assenza di altre limitazioni.
5. All’aperto premi **Avvia traccia**, attendi la conferma di un fix recente, controlla la notifica, quindi spegni lo schermo.
6. Premi **Ferma traccia** nell’app o nella notifica. Usa **Esporta tutti i dati in GPX** e scegli dove salvare (anche una cartella locale senza rete).

## Prima prova sul telefono

Questa beta va verificata sul dispositivo reale prima di usarla in un’escursione:

- Avvia all’aperto e cammina 10–15 minuti con schermo spento.
- Riapri: numero punti e distanza devono essere aumentati. Esporta e controlla il GPX, ad esempio in OsmAnd.
- Ripeti passando a un’altra app, quindi tornando alla registrazione.
- Verifica Ferma dalla notifica: i punti devono smettere di aumentare.
- Prova un’interruzione del GPS e il successivo recupero. Buchi oltre 60 secondi producono segmenti distinti, senza linee di collegamento inventate.
- Dopo un riavvio/arresto del sistema, controlla che i punti precedenti siano conservati e avvia manualmente una nuova sessione.

## Dati e comportamento

Ogni fix accettato viene inserito in SQLite nel contenitore privato dell’app. L’interfaccia può essere chiusa senza interrompere il servizio. Il servizio di localizzazione in primo piano espone una notifica con arresto. Nessun riavvio automatico dopo riavvio del telefono, arresto forzato o chiusura del processo da parte del sistema: si riparte esplicitamente dall’app, conservando i dati già acquisiti.

L’app richiede aggiornamenti GPS circa ogni 5 secondi. L’intervallo effettivo dipende dal sistema. Scarta fix vecchi oltre 30 secondi o con precisione peggiore di 100 m; non inventa posizioni. La distanza è una stima basata sui fix e può aumentare da fermi per rumore GPS. Non include i collegamenti tra sessioni o i buchi oltre 60 secondi. La durata somma le sessioni avviate, inclusa l’attesa GPS: non è il solo tempo in movimento. Dopo una terminazione imprevista recupera fino all’ultimo fix, senza conteggiare ore a telefono spento.

I punti singoli richiedono l’app visibile e attendono un fix fino a 30 secondi; se passi a un’altra app la richiesta viene annullata. L’elenco mostra gli ultimi 30, l’esportazione li include tutti. Ogni avvio crea una traccia distinta; l’esportazione include tutte le sessioni e tutti i waypoint disponibili quando inizia la lettura. Non sovrascrive continuamente un file esterno: il salvataggio continuo è nel database; il GPX esterno si crea con Esporta.

Nessun permesso Internet, pubblicità, analytics o backup cloud. Le posizioni non vengono trasmesse. Disinstallare o cancellare i dati dell’app elimina il database: **esporta prima**. I dati del precedente HTML non vengono importati automaticamente.

## Compilazione

Il progetto può essere aperto con Android Studio (SDK 35, JDK 17; Android Gradle Plugin 8.7.3, Gradle 8.9). Non usa librerie esterne. Non è incluso un Gradle Wrapper; usare una distribuzione Gradle 8.9 installata (`gradle :app:assembleDebug`) o il metodo diretto seguente.

Per riprodurre la beta senza Gradle, installare Android SDK Platform 35 e Build Tools 35.0.0, JDK 17 e i comandi zip; poi eseguire:

```sh
export ANDROID_SDK_ROOT=/percorso/android-sdk
./tools/build-apk.sh
```

Il risultato è `Traccia-GPS-Offline-beta.apk`. La prima esecuzione genera una chiave **di prova** in `.signing/beta.keystore`, password standard `android`. Conservare la chiave per aggiornare la stessa beta senza disinstallarla. È esclusa dal pacchetto sorgente e da git e non va usata per una release su store. Le build Gradle usano normalmente una diversa chiave di debug: per installarle sopra questa beta occorre configurare la stessa firma o esportare i GPX prima di disinstallare.

## Verifiche

Sono inclusi test Java del GPX e test Python delle query di recupero/esportazione. I test non sostituiscono le prove Android reali a schermo spento.

```sh
mkdir -p build/tests
javac -d build/tests app/src/main/java/it/lorenzo/tracciagps/Gpx.java tests/GpxTest.java
java -cp build/tests GpxTest build/test.gpx
python3 tests/store_test.py
```

## Pubblicazione

L’APK consegnato è una **beta per installazione diretta**, non una release Play Store. Per una pubblicazione su store servono firma di produzione conservata dal proprietario, controlli sul target SDK richiesto al momento della pubblicazione, test su dispositivi reali e dichiarazioni di servizio/permessi e privacy. Il sorgente può essere pubblicato su GitHub senza `.signing`, file personali e directory build. Non è stata effettuata alcuna pubblicazione esterna.

Documentazione tecnica di riferimento:
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/sensors-and-location/location/permissions
- https://developer.android.com/training/monitoring-device-state/doze-standby

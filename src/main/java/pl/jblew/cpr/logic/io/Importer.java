/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import com.j256.ormlite.dao.Dao;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.ImportPanel;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.MFile_Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.util.IdManager;

/**
 *
 * @author teofil
 */
public class Importer {
    private static final FilenameFilter fnFilter = (File dir, String name) -> !name.startsWith(".");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final File[] filesToImport;
    private final Context context;
    private final long size;
    private final AtomicReference<File> deviceRoot = new AtomicReference<>(null);
    private final AtomicReference<String> deviceName = new AtomicReference<>(null);
    private final AtomicReference<String> eventName = new AtomicReference<>(IdManager.getSessionSafe() + "");

    public Importer(Context context, File[] rawFilesToImport) {
        this.context = context;

        long size_ = 0;
        List<File> out = new LinkedList<>();
        for (File f : rawFilesToImport) {
            size_ += makeListCalculateSpace(out, f);
        }
        this.size = size_;

        this.filesToImport = out.stream().sorted((File o1, File o2) -> {
            long lm1 = o1.lastModified();
            long lm2 = o2.lastModified();
            return (lm1 == lm2 ? 0 : (lm1 > lm2 ? 1 : 0));
        }).toArray(File[]::new);
    }

    public File[] getFilesToImport() {
        return filesToImport;
    }

    public long getSize() {
        return size;
    }

    public void tryDevice(final Context c, final String deviceName, File deviceRoot) throws DeviceNotWritableException, NotACarrierException, NotEnoughSpaceException {
        if (!(deviceRoot.exists() && deviceRoot.canWrite() && deviceRoot.canWrite())) {
            throw new DeviceNotWritableException();
        }

        final AtomicBoolean isCarrier = new AtomicBoolean(false);

        try {
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    List<Carrier> result = c.dbManager.getDaos().getCarrierDao().queryForEq("name", deviceName);
                    isCarrier.set(result.size() > 0);
                } catch (SQLException ex) {
                    Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (!isCarrier.get()) {
            throw new NotACarrierException();
        }

        if (size >= deviceRoot.getFreeSpace()) {
            throw new NotEnoughSpaceException();
        }
    }

    public void setDevice(String deviceName, File deviceRoot) {
        this.deviceName.set(deviceName);
        this.deviceRoot.set(deviceRoot);
    }

    public String getDeviceName() {
        return this.deviceName.get();
    }

    public void setEventName(String eventName) {
        this.eventName.set(eventName);
    }
    
    public String getEventName() {
        return eventName.get();
    }

    public void startAsync(ImportPanel.ProgressChangedCallback callback) {
        executor.submit(() -> {
            try {
                callback.progressChanged(0, "Dodawanie wydarzenia do bazy danych i tworzenie folderu...");

                Event newEvent = new Event();
                newEvent.setName(eventName.get());
                newEvent.setType(Event.Type.UNSORTED);
                newEvent.setDateTime(LocalDateTime.ofEpochSecond(filesToImport[0].lastModified() / 1000l, 0, ZoneOffset.UTC));
                AtomicBoolean created = new AtomicBoolean(false);
                context.dbManager.executeInDBThreadAndWait(() -> {
                    try {
                        List<Event> result = context.dbManager.getDaos().getEventDao().queryForEq("name", newEvent.getName());
                        if (result.isEmpty()) {
                            try {
                                context.dbManager.getDaos().getEventDao().create(newEvent);
                                created.set(true);
                            } catch (SQLException ex) {
                                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });

                if (!created.get()) {
                    callback.progressChanged(0, "Błąd: Istnieje już wydarzenie o tej nazwie (lub wystąpił błąd SQL)");
                    return;
                }

                String basePath = newEvent.getProperPath(deviceRoot.get());
                if (new File(basePath).exists()) {
                    callback.progressChanged(0, "Błąd: Folder tego wydarzenia istnieje na wskazanym dysku!");
                    return;
                }

                new File(basePath).mkdirs();

                callback.progressChanged(0, "Obliczanie prawidłowych nazw...");

                final Map<File, File> targetFilesMap = new HashMap<>();

                for (int i = 0; i < filesToImport.length; i++) {
                    File f = filesToImport[i];
                    if (f.exists() && f.canRead() && !f.isDirectory()) {
                        String name = String.format("%1$" + 4 + "s", i + "").replace(' ', '0') //pads number with zeros to four digits
                                + "." + f.getName().substring(f.getName().lastIndexOf('.') + 1).toLowerCase(); //adds original extension in lower case
                        File targetFile = new File(basePath + File.separator + name);
                        targetFilesMap.put(f, targetFile);
                    }
                }

                callback.progressChanged(0, "Rozpoczynam kopiowanie plików");

                final Map<File, String> md5sums = new HashMap<>();
                if (!writeFiles(targetFilesMap, md5sums, callback)) {
                    return; //fills md5sums
                }
                callback.progressChanged(99, "Dodaję pliki do bazy danych...");

                int numOfExisting = registerFilesInDB(targetFilesMap, md5sums, newEvent);

                callback.progressChanged(100, "Gotowe! (" + numOfExisting + " znajdowało się już wcześniej w bazie!)");
            } catch (InterruptedException ex) {
                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private boolean writeFiles(Map<File, File> targetFilesMap, Map<File, String> md5SumsToFill, ImportPanel.ProgressChangedCallback callback) {
        int numOfFiles = targetFilesMap.size();
        callback.progressChanged(0, "Importowanie 0/" + numOfFiles);

        long sTime = System.currentTimeMillis();
        int i = 0;
        for (File sourceFile : targetFilesMap.keySet()) {
            File targetFile = targetFilesMap.get(sourceFile);
            float percentF = ((float) i) / ((float) numOfFiles);
            int percent = (int) (percentF * 100f);
            if(percent == 100) percent = 99; //in order not to disable progress bar
            
            try {
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                md5SumsToFill.put(targetFile, MD5Util.calculateMD5(targetFile));
                ThumbnailLoader.loadThumbnail(targetFile, true, null);
                
                long elapsedTime = System.currentTimeMillis()-sTime;
                int percentLeft = 100-percent;
                long leftTimeMulti = (long)elapsedTime*(long)percentLeft;
                long leftTime = (percent > 0? leftTimeMulti/(long)percent : 1);
                
                callback.progressChanged(percent, "Importowanie " + i + "/" + numOfFiles + " (" + percent + "%) Zostało: "+DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.ofEpochSecond(leftTime/1000l, 0, ZoneOffset.UTC)));
            } catch (IOException ex) {
                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, "Błąd przy kopiowaniu plików", ex);
                callback.progressChanged(percent, " Błąd przy importowaniu pliku: " + i + "/" + numOfFiles + ": " + ex.getLocalizedMessage());
                return false;
            }
            i++;
        }
        return true;
    }

    private int registerFilesInDB(Map<File, File> targetFilesMap, Map<File, String> md5sums, Event event) {
        final AtomicInteger numOfExistingFiles = new AtomicInteger(0);
        context.dbManager.executeInDBThread(() -> {
            try {
                Dao<Carrier, Integer> carrierDao = context.dbManager.getDaos().getCarrierDao();
                Dao<Event, Integer> eventDao = context.dbManager.getDaos().getEventDao();
                Dao<MFile, Integer> mfileDao = context.dbManager.getDaos().getMfileDao();
                Dao<Event_Localization, Integer> event_localizationDao = context.dbManager.getDaos().getEvent_LocalizationDao();
                Dao<MFile_Event, Integer> mfile_eventDao = context.dbManager.getDaos().getMfile_EventDao();

                Carrier carrier = carrierDao.queryForEq("name", deviceName).get(0);

                Event_Localization el = new Event_Localization();
                el.setCarrierId(carrier.getId());
                el.setEvent(event);
                el.setPath(event.getProperPath(deviceRoot.get()));
                event_localizationDao.create(el);

                for (File sourceFile : targetFilesMap.keySet()) {
                    File targetFile = targetFilesMap.get(sourceFile);
                    String md5 = md5sums.get(targetFile);

                    MFile mf = null;
                    if (!md5.isEmpty()) {
                        MFile probe = new MFile();
                        probe.setDateTime(LocalDateTime.ofEpochSecond(/*->*/sourceFile/*<-*/.lastModified() / 1000l, 0, ZoneOffset.UTC));
                        probe.setMd5(md5);
                        List<MFile> result = mfileDao.queryForMatching(probe);
                        if (result.size() > 0) {
                            mf = result.get(0);
                        }
                    }

                    long mfId;
                    if (mf != null) {
                        mfId = mf.getId();
                        numOfExistingFiles.incrementAndGet();
                    } else {
                        mf = new MFile();
                        mf.setName(targetFile.getName());
                        mf.setDateTime(LocalDateTime.ofEpochSecond(/*->*/sourceFile/*<-*/.lastModified() / 1000l, 0, ZoneOffset.UTC));

                        if (!md5.isEmpty()) {
                            mf.setMd5(md5);
                        } else {
                            mf.setMd5("-");
                        }

                        mfileDao.create(mf);
                        mfId = mf.getId();
                    }

                    MFile_Event mfe = new MFile_Event();
                    mfe.setEvent(event);
                    mfe.setMFile(mf);
                    mfile_eventDao.create(mfe);

                }

                context.eBus.post(new EventsNode.EventsListChanged());
            } catch (SQLException ex) {
                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        return numOfExistingFiles.get();
    }

    private static long makeListCalculateSpace(List<File> list, File f) {
        if (f.isDirectory()) {
            long l = 0;
            for (File c : f.listFiles(fnFilter)) {
                if (c.isDirectory()) {
                    l += makeListCalculateSpace(list, c);
                } else if (checkIfFileSupported(c)) {
                    list.add(c);
                    l += c.length();
                }
            }
            return l;
        } else {
            if (checkIfFileSupported(f)) {
                list.add(f);
                return f.length();
            } else {
                return 0;
            }
        }
    }

    public static boolean checkIfFileSupported(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
    }

    public static class DeviceNotWritableException extends Exception {
    }

    public static class NotACarrierException extends Exception {
    }

    public static class NotEnoughSpaceException extends Exception {
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import pl.jblew.cpr.logic.integritycheck.Validator;
import com.j256.ormlite.dao.Dao;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.gui.windows.ImportWindow;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.util.IdManager;
import pl.jblew.cpr.util.ImageCreationDateLoader;

/**
 *
 * @author teofil
 */
public class Importer {
    private static final String IMPORTED_LIST_FILENAME = "Zaimportowane.txt";
    private static final FilenameFilter fnFilter = (File dir, String name) -> !name.startsWith(".");
    private final File[] filesToImport;
    private final Context context;
    private final long size;
    private final AtomicReference<File> deviceRoot = new AtomicReference<>(null);
    private final AtomicReference<String> deviceName = new AtomicReference<>(null);
    private final AtomicReference<String> eventName = new AtomicReference<>(IdManager.getSessionSafe() + "");
    private boolean deleteAfterAdd = false;

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

    public void setDeleteAfterAdd(boolean deleteAfterAdd) {
        this.deleteAfterAdd = deleteAfterAdd;
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

    public void startAsync(ImportWindow.ProgressChangedCallback callback) {
        context.cachedExecutor.submit(() -> {
            try {
                callback.progressChanged(0, 1, "Dodawanie wydarzenia do bazy danych i tworzenie folderu...", false);

                Carrier carrier = Carrier.forName(context, deviceName.get());
                if (carrier == null) {
                    throw new RuntimeException("Selected device is not a carrier");
                }

                Event targetEvent = findOrCreateTargetEvent(callback);
                if (targetEvent == null) {
                    throw new RuntimeException("Could not load/create target event while importing photos");
                }

                Event_Localization targetLocalization = findOrCreateEventLocalization(callback, carrier, targetEvent);
                if (targetLocalization == null) {
                    throw new RuntimeException("Could not load/create event_localization while importing photos");
                }

                String basePath = targetLocalization.getFullEventPath(context);
                File baseDir = new File(basePath);
                Logger.getLogger(getClass().getName()).info("Creating dirs for basePath: " + baseDir);
                baseDir.mkdirs();

                callback.progressChanged(0, 1, "Obliczanie prawidłowych nazw...", false);

                Map<File, MFile> targetFilesMap = createFileMap(callback, targetEvent, basePath);
                
                callback.progressChanged(0, 1, "Rozwiązywanie konfliktów...", false);
                resolveConflicts(targetFilesMap, targetLocalization, basePath);

                callback.progressChanged(0, 1, "Rozpoczynam kopiowanie plików", false);

                if (!writeFiles(targetFilesMap, targetLocalization, basePath, callback)) {
                    return;
                }
                callback.progressChanged(99, 100, "Dodaję pliki do bazy danych...", false);

                int numOfExisting = registerFilesInDB(targetFilesMap, targetEvent, targetLocalization, carrier, callback);

                callback.progressChanged(99, 100, "Zapisuję listy zaimportowanych plików", false);

                markImported(targetFilesMap);

                targetEvent.recalculateAndUpdateTimeBounds(context);
                /*targetEvent.getLocalizations().stream()
                        .filter(el -> el.getCarrier(context).isConnected(context)).forEachOrdered(el -> {
                            try {
                                Validator.validateEventLocalization(context, el);
                            } catch (Validator.MissingOrBrokenFilesException ex) {
                                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });*/
                targetEvent.update(context);//this also marks localizations for revalidation

                callback.progressChanged(100, 100, "Gotowe! (" + numOfExisting + " znajdowało się już wcześniej w bazie!)", false);
            } catch (InterruptedException ex) {
                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private Event findOrCreateTargetEvent(ImportWindow.ProgressChangedCallback callback) throws InterruptedException {
        Event targetEvent = Event.forName(context, eventName.get());

        if (targetEvent == null) {//create new event
            targetEvent = Event.createEvent(context, Event.Type.UNSORTED, eventName.get(), null);
        }
        return targetEvent;
    }

    private Event_Localization findOrCreateEventLocalization(ImportWindow.ProgressChangedCallback callback, Carrier carrier, Event targetEvent) throws InterruptedException {
        Event_Localization targetLocalization = targetEvent.getLocalizations().stream()
                .filter(el -> el.getCarrier(context).getId() == carrier.getId())
                .findFirst().orElse(null);

        if (targetLocalization == null) {
            Event_Localization newLocalization = new Event_Localization();
            newLocalization.setCarrierId(carrier.getId());
            newLocalization.setEvent(targetEvent);
            newLocalization.setDirName(targetEvent.getName());
            newLocalization.setActualEventType(targetEvent.getType());

            AtomicBoolean created = new AtomicBoolean(false);
            context.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    List<Event_Localization> result = context.dbManager.getDaos().getEvent_LocalizationDao()
                            .queryBuilder().where().eq("eventId", targetEvent.getId()).and().eq("carrierId", carrier.getId()).query();
                    if (result.isEmpty()) {
                        try {
                            context.dbManager.getDaos().getEvent_LocalizationDao().create(newLocalization);
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
                callback.progressChanged(0, 1, "Błąd: Nie udało się utworzyć wydarzenia", true);
                return null;
            }
            targetLocalization = newLocalization;
        }

        return targetLocalization;
    }

    private Map<File, MFile> createFileMap(ImportWindow.ProgressChangedCallback callback, Event event, String basePath) {
        Map<File, MFile> targetFilesMap = new HashMap<>();

        for (int i = 0; i < filesToImport.length; i++) {
            callback.progressChanged(0, 1, "Obliczanie prawidłowych nazw i sprawdzanie ...("+(i+1)+"/"+filesToImport.length+")", false);
            
            File sourceFile = filesToImport[i];
            if (sourceFile.exists() && sourceFile.canRead() && !sourceFile.isDirectory()) {
                /*VALIDATE IMAGE*/
                try {
                    BufferedImage buf = ImageIO.read(sourceFile);
                    if (buf.getWidth() < 1 || buf.getHeight() < 1) {
                        System.err.println("Image " + sourceFile + " is invalid: (w<=0||h<=0)");
                        break;//BREAK
                    }
                } catch (Exception e) {
                    System.err.println("Image " + sourceFile + " is invalid: " + e);
                    break;//BREAK
                }

                String sourceExtension = sourceFile.getName().substring(sourceFile.getName().lastIndexOf('.') + 1).toLowerCase();

                MFile targetMf = new MFile();
                targetMf.setEvent(event);
                targetMf.setMd5(MD5Util.calculateMD5(sourceFile));
                targetMf.setDateTime(ImageCreationDateLoader.getCreationDateTime(sourceFile));
                targetMf.calculateAndSetFilename(basePath, sourceExtension);

                targetFilesMap.put(sourceFile, targetMf);
            }
        }

        return targetFilesMap;
    }
    
    private void resolveConflicts(Map<File, MFile> targetFilesMap, Event_Localization eventLocalization, String basePath) {
        for (File sourceFile : targetFilesMap.keySet()) {
            MFile targetMFile = targetFilesMap.get(sourceFile);
            File targetFile = targetMFile.getFile(context, eventLocalization);
            
            int i = 0;
            while(targetFile.exists()) {
                String expectedMD5 = targetMFile.getMd5();
                String actualMD5 = MD5Util.calculateMD5(targetFile);
                
                if(!actualMD5.equals(expectedMD5)) {//if MD5 is not equal, these are different files
                    String extension = targetFile.getName().substring(targetFile.getName().lastIndexOf('.') + 1).toLowerCase();
                    targetFile = new File(targetMFile.calculateAndSetFilename(basePath, extension, i));
                }
                i++;
            }
        }
    }

    private boolean writeFiles(Map<File, MFile> targetFilesMap, Event_Localization eventLocalization, String basePath, ImportWindow.ProgressChangedCallback callback) {
        int numOfFiles = targetFilesMap.size();
        callback.progressChanged(0, 1, "Importowanie 0/" + numOfFiles, false);

        int i = 0;
        for (File sourceFile : targetFilesMap.keySet()) {
            MFile targetMFile = targetFilesMap.get(sourceFile);
            File targetFile = targetMFile.getFile(context, eventLocalization);

            try {
                if (targetFile.exists()) {
                    targetFile.delete();
                    targetFile = targetMFile.getFile(context, eventLocalization);//reload File object
                }
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                //Logger.getLogger(getClass().getName()).info(sourceFile.toPath() + " -> " + targetFile.toPath());
                ThumbnailLoader.loadThumbnail(targetFile, true, null);

                callback.progressChanged(i, numOfFiles+1, "Importowanie "+eventLocalization.getOrLoadFullEvent(context).getName() + " " + i + "/" + numOfFiles, false);
            } catch (IOException ex) {
                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, "Błąd przy kopiowaniu plików", ex);
                callback.progressChanged(i, numOfFiles+1, " Błąd przy importowaniu pliku: " + i + "/" + numOfFiles + ": " + ex.getLocalizedMessage(), true);
                return false;
            }
            i++;
        }
        return true;
    }

    private int registerFilesInDB(Map<File, MFile> targetFilesMap, Event targetEvent, Event_Localization targetEventLocalization, Carrier carrier, ImportWindow.ProgressChangedCallback callback) {
        final AtomicInteger numOfExistingFiles = new AtomicInteger(0);
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                callback.progressChanged(99, 100, "Dodawanie plików do bazy danych...", false);
                try {
                    Dao<MFile, Integer> mfileDao = context.dbManager.getDaos().getMfileDao();

                    int i = 0;
                    for (File sourceFile : targetFilesMap.keySet()) {
                        float percentF = ((float) i) / ((float) targetFilesMap.keySet().size());
                        int percent = (int) (percentF * 100f);
                        
                        MFile targetMFile = targetFilesMap.get(sourceFile);
                        mfileDao.create(targetMFile);

                        /**
                         * *DELETING FILES **
                         */
                        if (this.deleteAfterAdd) {
                            try {
                                sourceFile.delete();
                            } catch (Exception e) {

                            }
                        }

                        callback.progressChanged(99, 100, "Dodawanie plików do bazy danych... ("+percent+"%)", false);

                        i++;
                    }

                    context.eBus.post(new EventsNode.EventsListChanged());
                } catch (SQLException ex) {
                    Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return numOfExistingFiles.get();
    }

    private void markImported(Map<File, MFile> targetFilesMap) {
        File[] dirs = targetFilesMap.keySet().stream().map(f -> f.getParentFile()).distinct().toArray(File[]::new);
        for (File dir : dirs) {

            try {
                File importedListFile = new File(dir.getAbsolutePath() + File.separator + IMPORTED_LIST_FILENAME);
                Set<String> alreadyMarked = new HashSet<>();
                if (importedListFile.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(importedListFile))) {
                        for (String line; (line = br.readLine()) != null;) {
                            if (!line.isEmpty()) {
                                alreadyMarked.add(line);
                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                String childrenToWrite = targetFilesMap.keySet().stream().filter(f -> f.getParentFile().equals(dir)).filter(f -> !alreadyMarked.contains(f.getName())).map(f -> f.getName()).reduce("", (a, b) -> a + "\n" + b);
                try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(importedListFile)))) {
                    pw.write(childrenToWrite);
                    pw.flush();
                } catch (IOException ex) {
                    Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (Exception ex) {
                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
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

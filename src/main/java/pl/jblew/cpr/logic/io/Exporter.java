/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import com.j256.ormlite.dao.Dao;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.gui.windows.RedundantCopyWindow;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.Event_Localization;

/**
 *
 * @author teofil
 */
public class Exporter {
    private final Event event;
    private final Context context;
    private final AtomicReference<MFile.Localized[]> localizedMFiles;
    private RedundantCopyWindow.ProgressChangedCallback progressChangedCallback;
    private Carrier targetCarrier;
    private long size = 0;

    public Exporter(Context context_, Event e) {
        context = context_;
        event = e;
        localizedMFiles = new AtomicReference<>(e.getLocalizedMFiles(context));
    }

    public int getNumOfFiles() {
        return localizedMFiles.get().length;
    }

    public long calculateSize() {
        size = Arrays.stream(localizedMFiles.get()).filter(mfl -> mfl.getFile() != null && mfl.getFile().exists()).mapToLong(mfl -> mfl.getFile().length()).sum();
        return size;
    }

    public void setTargetCarrier(Carrier carrier) {
        this.targetCarrier = carrier;
    }

    public long getSize() {
        return size;
    }

    public void setProgressChangedCallback(RedundantCopyWindow.ProgressChangedCallback callback) {
        progressChangedCallback = callback;
    }

    public void startAsync() {
        startAsync(null);
    }

    public void startAsync(Runnable finishedCallback) {
        if (localizedMFiles.get() == null) {
            throw new IllegalArgumentException("localizedMFiles is null!");
        }
        if (targetCarrier == null) {
            throw new IllegalArgumentException("TargetCarrier is null!");
        }
        if (progressChangedCallback == null) {
            throw new IllegalArgumentException("Callback is null!");
        }

        context.cachedExecutor.submit(() -> {
            try {
                progressChangedCallback.progressChanged(0, 1, "Ładowanie potrzebnych danych...", false);
                
                event.recalculateAndUpdateTimeBounds(context);
                

                Event_Localization targetLocalization = new Event_Localization();
                targetLocalization.setEvent(event);
                targetLocalization.setCarrierId(targetCarrier.getId());
                targetLocalization.setDirName(event.calculateProperDirName());
                targetLocalization.setActualEventType(event.getType());

                
                String targetPath = targetLocalization.getFullEventPath(context);
                File potentialTargetFile = new File(targetPath);

                
                if (!potentialTargetFile.exists()) {
                    potentialTargetFile.mkdirs();
                }
                
                if (!potentialTargetFile.exists()) {
                    progressChangedCallback.progressChanged(0, 1, "Nie można było utworzyć katalogu", true);
                    return;
                }
                

                /**
                 * * CALCULATE NAMES AND PATHS **
                 */
                writeFiles(targetLocalization);
                

                registerFilesInDB(targetLocalization);
                
                context.eBus.post(new EventsNode.EventsListChanged());
            }catch (Exception ex) {
                Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, "Błąd przy kopiowaniu plików", ex);
                progressChangedCallback.progressChanged(0, 1, " Błąd przy eksportowaniu plików: " + ex, true);
                throw new RuntimeException("Could not finish", ex);
            }
            finally {
                if (finishedCallback != null) {
                    finishedCallback.run();
                }
            }
        });
    }

    private void writeFiles(Event_Localization targetEventLocalization) {
        for (int i = 0; i < localizedMFiles.get().length; i++) {
            MFile.Localized sourceLocalizedMFile = localizedMFiles.get()[i];
            File sourceFile = sourceLocalizedMFile.getFile();
            File targetFile = sourceLocalizedMFile.getMFile().getFile(context, targetEventLocalization);

            
            float percentF = ((float) i) / ((float) localizedMFiles.get().length);
            int percent = (int) (percentF * 100f);

            
            if(targetFile.exists()) {//we delete files with same name
                targetFile.delete();
                //reload targetFile
                targetFile = sourceLocalizedMFile.getMFile().getFile(context, targetEventLocalization);
            }
            
            try {
                if(sourceFile == null) throw new RuntimeException("sourceFile is null, mFile.getFile(for targetEL)="+sourceLocalizedMFile.getMFile().getFile(context, targetEventLocalization));
                else if(targetFile == null) throw new RuntimeException("targetFile is null!");
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                ThumbnailLoader.loadThumbnail(targetFile, true, null);
                if (progressChangedCallback != null) {
                    progressChangedCallback.progressChanged(i, localizedMFiles.get().length, "Eksportowanie " + i + "/" + localizedMFiles.get().length + " (" + percent + "%) ~ "+targetEventLocalization.getOrLoadFullEvent(context).getName(), false);
                }
            } catch (IOException ex) {
                Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, "Błąd przy kopiowaniu plików", ex);
                progressChangedCallback.progressChanged(i, localizedMFiles.get().length, " Błąd przy Eksportowaniu pliku: " + i + "/" + localizedMFiles.get().length + ": " + ex.getLocalizedMessage(), true);
                throw new RuntimeException("Could not finish", ex);
            }
        }
    }

    private void registerFilesInDB(Event_Localization targetEventLocalization) {
        context.dbManager.executeInDBThread(() -> {
            Dao<Event_Localization, Integer> event_localizationDao = context.dbManager.getDaos().getEvent_LocalizationDao();
            try {
                event_localizationDao.create(targetEventLocalization);
                progressChangedCallback.progressChanged(1, 1, "Gotowe!", false);
            } catch (SQLException ex) {
                Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, null, ex);
                progressChangedCallback.progressChanged(99, 100, "Błąd: Nie można było dodać do bazy danych!", true);
            }
        });
    }

    public static boolean checkIfFileSupported(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
    }

    public void tryDevice(Context c, Carrier carrier, Event event) throws DeviceNotWritableException, NotEnoughSpaceException {
        File deviceRoot = c.deviceDetector.getDeviceRoot(carrier.getName());

        if (!(deviceRoot.exists() && deviceRoot.canWrite() && deviceRoot.canWrite())) {
            throw new DeviceNotWritableException();
        }

        if (size >= deviceRoot.getFreeSpace()) {
            throw new NotEnoughSpaceException();
        }
    }

    public static class DeviceNotWritableException extends Exception {
    }

    public static class NotEnoughSpaceException extends Exception {
    }

    public static class MissingFilesException extends Exception {
        private final File directory;

        public MissingFilesException(File directory) {
            this.directory = directory;
        }

        public File getDirectory() {
            return directory;
        }
    }
}

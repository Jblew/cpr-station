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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.ExportPanel;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class Exporter {
    private static final FilenameFilter fnFilter = (File dir, String name) -> !name.startsWith(".");
    private final Event event;
    private final Context context;
    private final AtomicReference<MFile.Localized[]> localizedMFiles;
    private ExportPanel.ProgressChangedCallback progressChangedCallback;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TwoTuple<String, File> device = null;
    private long size = 0;

    public Exporter(Context context_, Event e) {
        context = context_;
        event = e;
        localizedMFiles = new AtomicReference<>(e.getLocalizedMFiles(context));
    }

    public int getNumOfFiles() {
        return localizedMFiles.get().length;
    }

    public Carrier[] checkFileAccessibilityAndGetMissingCarriers() throws MissingFilesException {
        localizedMFiles.set(event.getLocalizedMFiles(context));
        File eventAccessibleDir = event.getAccessibleDir(context);
        if (eventAccessibleDir != null) {
            if (Arrays.stream(localizedMFiles.get()).anyMatch(mfl -> mfl.getFile() == null)) {
                throw new MissingFilesException(eventAccessibleDir);
            }
            return null;
        } else {
            return event.getLocalizations().stream().map((Event_Localization el) -> el.getCarrier(context)).toArray(Carrier[]::new);
        }
    }

    public long calculateSize() {
        size = Arrays.stream(localizedMFiles.get()).filter(mfl -> mfl.getFile() != null && mfl.getFile().exists()).mapToLong(mfl -> mfl.getFile().length()).sum();
        return size;
    }

    public TwoTuple<String, File> getDevice() {
        return device;
    }

    public void setDevice(TwoTuple<String, File> device) {
        this.device = device;
    }

    public long getSize() {
        return size;
    }

    public void setProgressChangedCallback(ExportPanel.ProgressChangedCallback callback) {
        progressChangedCallback = callback;
    }

    public void startAsync() {
        if (localizedMFiles.get() == null) {
            throw new IllegalArgumentException("FilesToExport is null!");
        }
        if (device == null) {
            throw new IllegalArgumentException("Device is null!");
        }
        if (progressChangedCallback == null) {
            throw new IllegalArgumentException("Calback is null!");
        }
        String targetPath = event.getProperPath(device.getB());

        if (new File(targetPath).exists()) {
            throw new RuntimeException("File already exists!");
        }

        executor.submit(() -> {
            new File(targetPath).mkdirs();

            if (!new File(targetPath).exists()) {
                progressChangedCallback.progressChanged(0, "Nie można było utworzyć katalogu");
                return;
            }

            /**
             * * CALCULATE NAMES AND PATHS **
             */
            writeFiles();

            registerFilesInDB();
        });
    }

    private void writeFiles() {
        for (int i = 0; i < localizedMFiles.get().length; i++) {
            MFile.Localized sourceLocalizedMFile = localizedMFiles.get()[i];
            File sourceFile = sourceLocalizedMFile.getFile();
            File targetFile = new File(sourceLocalizedMFile.getMFile().getProperPath(device.getB(), event));

            float percentF = ((float) i) / ((float) localizedMFiles.get().length);
            int percent = (int) (percentF * 100f);
            if (percent == 100) {
                percent = 99;
            }

            try {
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                ThumbnailLoader.loadThumbnail(targetFile, true, null);
                if (progressChangedCallback != null) {
                    progressChangedCallback.progressChanged(percent, "Eksportowanie " + i + "/" + localizedMFiles.get().length + " (" + percent + "%)");
                }
            } catch (IOException ex) {
                Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, "Błąd przy kopiowaniu plików", ex);
                progressChangedCallback.progressChanged(percent, " Błąd przy Eksportowaniu pliku: " + i + "/" + localizedMFiles.get().length + ": " + ex.getLocalizedMessage());
                throw new RuntimeException("Could not finish", ex);
            }
        }
    }

    private void registerFilesInDB() {
        final TwoTuple<String, File> deviceF = device;
        context.dbManager.executeInDBThread(() -> {
            Dao<Carrier, Integer> carrierDao = context.dbManager.getDaos().getCarrierDao();
            Dao<Event_Localization, Integer> event_localizationDao = context.dbManager.getDaos().getEvent_LocalizationDao();
            try {
                Carrier c;
                List<Carrier> result = carrierDao.queryForEq("name", device.getA());
                if (result.size() > 0) {
                    c = result.get(0);
                } else {
                    Carrier newC = new Carrier();
                    newC.setName(device.getA());
                    newC.setType(Carrier.Type.UNKNOWN);
                    newC.setLastChecked(new Date());
                    carrierDao.create(newC);
                    c = newC;
                }

                Event_Localization el = new Event_Localization();
                el.setEvent(event);
                el.setPath(event.getProperPath(deviceF.getB()));
                el.setCarrierId(c.getId());
                event_localizationDao.create(el);

                progressChangedCallback.progressChanged(100, "Gotowe!");
            } catch (SQLException ex) {
                Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, null, ex);
                progressChangedCallback.progressChanged(99, "Błąd: Nie można było dodać do bazy danych!");
            }
        });
    }

    public static boolean checkIfFileSupported(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
    }

    public void tryDevice(final Context c, final String deviceName, File deviceRoot, Event event) throws DeviceNotWritableException, NotEnoughSpaceException, FileAlreadyExists {
        if (!(deviceRoot.exists() && deviceRoot.canWrite() && deviceRoot.canWrite())) {
            throw new DeviceNotWritableException();
        }

        if (size >= deviceRoot.getFreeSpace()) {
            throw new NotEnoughSpaceException();
        }

        if (new File(event.getProperPath(deviceRoot)).exists()) {
            throw new FileAlreadyExists();
        }
    }

    public static class DeviceNotWritableException extends Exception {
    }

    public static class NotEnoughSpaceException extends Exception {
    }

    public static class FileAlreadyExists extends Exception {
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.j256.ormlite.dao.Dao;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.ExportPanel;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.MFile_Event;
import pl.jblew.cpr.logic.MFile_Localization;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class Exporter {
    private static final FilenameFilter fnFilter = (File dir, String name) -> !name.startsWith(".");
    private final Event event;
    private final Context context;
    private ExportPanel.ProgressChangedCallback progressChangedCallback;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HashBiMap<File, MFile> filesToExport = HashBiMap.create();
    private TwoTuple<String, File> device = null;
    private long size = 0;

    public Exporter(Context context_, Event e) {
        context = context_;
        event = e;
    }

    public Map<Carrier, Integer> checkFileAccessibilityAndGetMissingCarriers() {
        final HashMap<Carrier, Integer> out = new HashMap<>();
        MFile[] missingMFiles = Arrays.stream(event.getMFiles(context)).filter(mf -> mf.getAccessibleFile(context) == null).toArray(MFile[]::new);
        Arrays.stream(missingMFiles).flatMap(mf -> mf.getLocalizations().stream()).forEach(mfl -> {
            Carrier c = mfl.getCarrier(context);
            if (c != null) {
                int has;
                if (out.containsKey(c)) {
                    has = out.get(c) + 1;
                    out.put(c, has);
                } else {
                    has = 1;
                    out.put(c, has);
                }
                if (has == missingMFiles.length) {
                    out.put(c, -1);
                }
            }
        });

        return out;
    }

    public long calculateSize() {
        Arrays.stream(event.getMFiles(context)).forEach(mf -> {
            File f = mf.getAccessibleFile(context);
            if (f != null) {
                filesToExport.put(f, mf);
            }
        });
        size = filesToExport.keySet().stream().mapToLong(f -> f.length()).sum();
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
        if (filesToExport == null) {
            throw new IllegalArgumentException("FilesToExport is null!");
        }
        if (device == null) {
            throw new IllegalArgumentException("Device is null!");
        }
        if (progressChangedCallback == null) {
            throw new IllegalArgumentException("Calback is null!");
        }
        String sortedPath = (event.getType() == Event.Type.SORTED ? FileStructureUtil.PATH_SORTED_PHOTOS : FileStructureUtil.PATH_UNSORTED_PHOTOS);
        String targetPath = device.getB().getAbsolutePath() + File.separator + sortedPath + File.separator + event.getName();

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
             * * SORT **
             */
            File[] sortedFiles = filesToExport.keySet().stream().sorted((File o1, File o2) -> {
                long lm1 = o1.lastModified();
                long lm2 = o2.lastModified();
                return (lm1 == lm2 ? 0 : (lm1 > lm2 ? 1 : 0));
            }).toArray(File[]::new);

            /**
             * * CALCULATE NAMES AND PATHS **
             */
            final Map<File, File> targetFiles = new HashMap<>();
            IntStream.range(0, sortedFiles.length).forEach(i -> {
                String name = String.format("%1$" + 4 + "s", i + "").replace(' ', '0')
                        + "." + sortedFiles[i].getName().substring(sortedFiles[i].getName().lastIndexOf('.') + 1).toLowerCase();
                targetFiles.put(sortedFiles[i], new File(targetPath + File.separator + name));
            });

            writeFiles(targetFiles);

            progressChangedCallback.progressChanged(99, "Aktualizowanie bazy danych...");
            Map<File, MFile> filesToRegister = new HashMap<>();
            targetFiles.forEach((sourceFile, targetFile) -> {
                filesToRegister.put(targetFile, filesToExport.get(sourceFile));
            });
            registerFilesInDB(filesToRegister, device);
        });
    }

    private void writeFiles(Map<File, File> filesToWrite) {
        int i = 0;
        for (File sourceFile : filesToWrite.keySet()) {
            File targetFile = filesToWrite.get(sourceFile);
            float percentF = ((float) i) / ((float) filesToWrite.size());
            int percent = (int) (percentF * 100f);
            if(percent == 100) percent = 99;
            try {
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                ThumbnailLoader.loadThumbnail(targetFile, true, null);
                if (progressChangedCallback != null) {
                    progressChangedCallback.progressChanged(percent, "Eksportowanie " + i + "/" + filesToWrite.size() + " (" + percent + "%)");
                }
            } catch (IOException ex) {
                Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, "Błąd przy kopiowaniu plików", ex);
                progressChangedCallback.progressChanged(percent, " Błąd przy Eksportowaniu pliku: " + i + "/" + filesToWrite.size() + ": " + ex.getLocalizedMessage());
                throw new RuntimeException("Could not finish", ex);
            }
            i++;
        }
    }

    private void registerFilesInDB(Map<File, MFile> filesToRegister, TwoTuple<String, File> device) {
        context.dbManager.executeInDBThread(() -> {
            Dao<Carrier, Integer> carrierDao = context.dbManager.getDaos().getCarrierDao();
            Dao<MFile_Localization, Integer> mfile_localizationDao = context.dbManager.getDaos().getMfile_Localization();
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

                AtomicBoolean ok = new AtomicBoolean(true);
                filesToRegister.forEach((targetFile, mfile) -> {
                    MFile_Localization newMfl = new MFile_Localization();
                    newMfl.setCarrierId(c.getId());
                    newMfl.setMfile(mfile);
                    String relativePath = device.getB().toPath().relativize(targetFile.toPath()).toString();
                    newMfl.setPath(relativePath);

                    try {
                        mfile_localizationDao.create(newMfl);
                    } catch (SQLException ex) {
                        Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, null, ex);
                        progressChangedCallback.progressChanged(99, "Błąd: Nie można było dodać do bazy danych!");
                        ok.set(false);
                    }
                });

                if(ok.get()) progressChangedCallback.progressChanged(100, "Gotowe!");
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
        
        String sortedPath = (event.getType() == Event.Type.SORTED ? FileStructureUtil.PATH_SORTED_PHOTOS : FileStructureUtil.PATH_UNSORTED_PHOTOS);
        String targetPath = deviceRoot.getAbsolutePath() + File.separator + sortedPath + File.separator + event.getName();

        if (new File(targetPath).exists()) {
            throw new FileAlreadyExists();
        }
    }

    public static class DeviceNotWritableException extends Exception {
    }

    public static class NotEnoughSpaceException extends Exception {
    }
    
    public static class FileAlreadyExists extends Exception {
    }
}

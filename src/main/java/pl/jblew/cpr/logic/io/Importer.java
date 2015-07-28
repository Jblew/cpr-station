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
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.ImportPanel;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.MFile_Event;
import pl.jblew.cpr.logic.MFile_Localization;

/**
 *
 * @author teofil
 */
public class Importer {
    private static final FilenameFilter fnFilter = (File dir, String name) -> !name.startsWith(".");

    private final Context context;
    private final ImportPanel.ProgressChangedCallback progressChangedCallback;
    private final List<File> filesToImport;
    private final File deviceRoot;
    private final String deviceName;
    private final String eventName;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public Importer(Context context_, List<File> filesToImport_, String deviceName_, File deviceRoot_, String eventName_, ImportPanel.ProgressChangedCallback progressChangedCallback_) {
        context = context_;
        filesToImport = filesToImport_;
        deviceRoot = deviceRoot_;
        eventName = eventName_;
        progressChangedCallback = progressChangedCallback_;
        deviceName = deviceName_;
    }

    public void startAsync() {
        executor.submit(() -> {
            Collections.sort(filesToImport, (File o1, File o2) -> {
                long lm1 = o1.lastModified();
                long lm2 = o2.lastModified();
                return (lm1 == lm2 ? 0 : (lm1 > lm2 ? 1 : 0));
            });
            String targetPath = deviceRoot.getAbsolutePath() + File.separator + FileStructureUtil.PATH_UNSORTED_PHOTOS + File.separator + eventName;
            new File(targetPath).mkdirs();
            int numOfFiles = filesToImport.size();
            progressChangedCallback.progressChanged(0, "Importowanie 0/" + numOfFiles);
            boolean filesOk = true;
            final Map<File, File> targetFiles = new HashMap<>();
            int j = 0;
            for (File f : filesToImport) {
                j++;
                String name = String.format("%1$" + 4 + "s", j + "").replace(' ', '0')
                        + "." + f.getName().substring(f.getName().lastIndexOf('.') + 1).toLowerCase();
                //System.out.println("New filename: "+name);
                targetFiles.put(f, new File(targetPath + File.separator + name));
            }
            final Map<File, String> md5sums = new HashMap<>();
            int i = 0;
            for (File f : filesToImport) {
                float percentF = ((float) i) / ((float) numOfFiles);
                int percent = (int) (percentF * 100f);
                try {
                    Files.copy(f.toPath(), targetFiles.get(f).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    md5sums.put(f, MD5Util.calculateMD5(targetFiles.get(f)));
                    
                    progressChangedCallback.progressChanged(percent, "Importowanie " + i + "/" + numOfFiles + " (" + percent + "%)");
                } catch (IOException ex) {
                    Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, "Błąd przy kopiowaniu plików", ex);
                    progressChangedCallback.progressChanged(percent, " Błąd przy importowaniu pliku: " + i + "/" + numOfFiles + ": " + ex.getLocalizedMessage());
                    filesOk = false;
                    return;//////////////////!!!!!!!!!!!!!!!!!
                }
                i++;
            }
            progressChangedCallback.progressChanged(100, "Dodawanie do bazy danych");
            final AtomicInteger numOfExistingFiles = new AtomicInteger(0);
            if (filesOk) {
                System.out.println("Entering DB Thread");
                context.dbManager.executeInDBThread(() -> {
                    System.out.println("In DB Thread");
                            try {
                                Dao<Carrier, Integer> carrierDao = context.dbManager.getDaos().getCarrierDao();
                                Dao<Event, Integer> eventDao = context.dbManager.getDaos().getEventDao();
                                Dao<MFile, Integer> mfileDao = context.dbManager.getDaos().getMfileDao();
                                Dao<MFile_Localization, Integer> mfile_localizationDao = context.dbManager.getDaos().getMfile_Localization();
                                Dao<MFile_Event, Integer> mfile_eventDao = context.dbManager.getDaos().getMfile_EventDao();

                                Event e = new Event();
                                e.setName(eventName);
                                e.setDateTime(LocalDateTime.ofEpochSecond(filesToImport.get(0).lastModified()/1000l, 0, ZoneOffset.UTC));
                                e.setType(Event.Type.UNSORTED);

                                Carrier c = carrierDao.queryForEq("name", deviceName).get(0);

                                //eventDao.update(e);
                                eventDao.create(e);
                                long eventId = e.getId();

                                for (File f : filesToImport) {
                                    File target = targetFiles.get(f);
                                    String md5 = md5sums.get(f);

                                    MFile mf = null;
                                    if (!md5.isEmpty()) {
                                        MFile probe = new MFile();
                                        probe.setDateTime(LocalDateTime.ofEpochSecond(f.lastModified()/1000l, 0, ZoneOffset.UTC));
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
                                        mf.setName(target.getName());
                                        mf.setDateTime(LocalDateTime.ofEpochSecond(f.lastModified()/1000l, 0, ZoneOffset.UTC));

                                        if (!md5.isEmpty()) {
                                            mf.setMd5(md5);
                                        } else {
                                            mf.setMd5("-");
                                        }

                                        mfileDao.create(mf);
                                        mfId = mf.getId();
                                    }

                                    MFile_Event mfe = new MFile_Event();
                                    mfe.setEvent(e);
                                    mfe.setMfile(mf);

                                    mfile_eventDao.create(mfe);

                                    MFile_Localization mfl = new MFile_Localization();
                                    mfl.setCarrierId(c.getId());
                                    mfl.setMfile(mf);
                                    String relativePath = deviceRoot.toPath().relativize(target.toPath()).toString();
                                    mfl.setPath(relativePath);

                                    mfile_localizationDao.create(mfl);
                                }

                                context.eBus.post(new EventsNode.EventsListChanged());

                                progressChangedCallback.progressChanged(100, "Gotowe! Znaleziono "+numOfExistingFiles.get()+" duplikatów");
                            } catch (SQLException ex) {
                                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                            }
                });
            }
        });
    }

    private static long makeListCalculateSpace(List<File> list, File f) {
        //System.out.println("Looking at "+f.getAbsolutePath());
        if (f.isDirectory()) {
            long l = 0;
            for (File c : f.listFiles(fnFilter)) {
                if (c.isDirectory()) {
                    l += makeListCalculateSpace(list, c);
                } else if (checkIfFileSupported(c)) {
                    list.add(c);
                    //System.out.println("Adding "+f.getAbsolutePath());
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

    public static List<File> getAllImportableFiles(File[] paths) {
        List<File> out = new LinkedList<>();
        for (File f : paths) {
            makeListCalculateSpace(out, f);
        }
        return out;
    }

    public static void tryDevice(final Context c, final String deviceName, File deviceRoot, List<File> filesToImport) throws DeviceNotWritableException, NotACarrierException, NotEnoughSpaceException {
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

        long neededSpace = 0;
        for (File f : filesToImport) {
            neededSpace += f.length();
        }

        if (neededSpace >= deviceRoot.getFreeSpace()) {
            throw new NotEnoughSpaceException();
        }
    }

    public static class DeviceNotWritableException extends Exception {
    }

    public static class NotACarrierException extends Exception {
    }

    public static class NotEnoughSpaceException extends Exception {
    }
}

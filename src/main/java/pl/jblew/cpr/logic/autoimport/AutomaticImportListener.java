/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.autoimport;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.io.FileStructureUtil;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public class AutomaticImportListener implements StorageDevicePresenceListener {
    private final Context context;
    private final Map<String, Path> observedDirs = Collections.synchronizedMap(new HashMap<>());
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1, new NamingThreadFactory("automatic-import-listener-scheduled"));
    private final AutomaticImportProcessor importProcessor;

    public AutomaticImportListener(Context context) {
        this.context = context;

        importProcessor = new AutomaticImportProcessor(context);

        scheduledExecutor.scheduleWithFixedDelay(() -> {
            Map<String, Path> pathsToProcess = new HashMap<>();

            for (Entry<String, Path> entry : observedDirs.entrySet()) {
                try {
                    File dir = entry.getValue().toFile();
                    if (dir != null && dir.exists() && dir.canRead()) {
                        boolean containsFiles = false;
                        for (File child : dir.listFiles((File f, String name) -> !name.startsWith("."))) {
                            if (child.exists() && child.canRead()) {
                                containsFiles = true;
                                break;
                            }
                        }
                        if (containsFiles) {
                            importProcessor.process(dir.toPath(), entry.getKey());
                        }
                    }
                } catch (Exception ex) {
                    Logger.getLogger(AutomaticImportListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }, 7, 5, TimeUnit.SECONDS);
        
        context.bootstrap.addShutdownTask(() -> {
            scheduledExecutor.shutdown();
        });
    }

    @Override
    public void storageDeviceConnected(File rootFile, String deviceName) {
        try {
            if (Carrier.forName(context, deviceName) != null) {//import only if device is registered carrier
                Path autoImportDir = new File(rootFile.toString() + File.separator + FileStructureUtil.PATH_UNSORTED_AUTOIMPORT).toPath();

                if (autoImportDir.toFile().exists() && autoImportDir.toFile().canRead()) {
                    //System.out.println("Registering watcher for " + autoImportDir);
                    observedDirs.put(deviceName, autoImportDir);
                }
            }

        } catch (Exception ex) {
            Logger.getLogger(AutomaticImportListener.class.getName()).log(Level.SEVERE, null, ex + " for path " + rootFile.toURI());
        }
    }

    @Override
    public void storageDeviceDisconnected(File rootFile, String deviceName) {
        if (observedDirs.containsKey(deviceName)) {
            observedDirs.remove(deviceName);
        }
    }

}

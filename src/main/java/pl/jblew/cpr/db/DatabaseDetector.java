/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import java.io.File;
import java.util.logging.Logger;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.util.ListenersManager;

/**
 *
 * @author teofil
 */
public class DatabaseDetector implements StorageDevicePresenceListener {
    public static final String DB_FILE_NAME = "cpr-s-db";
    public static final String DB_FILE_EXTENSION = ".h2.db";
    private final ListenersManager<DatabaseDetectedListener> listenersManager = new ListenersManager<>();

    public DatabaseDetector() {

    }

    public void addDatabaseDetectedListener(DatabaseDetectedListener l) {
        listenersManager.addListener(l);
    }

    public void removeDatabaseDetectedListener(DatabaseDetectedListener l) {
        listenersManager.removeListener(l);
    }

    @Override
    public void storageDeviceConnected(File root, final String deviceName) {
        if (root.isDirectory() && root.canRead()) {
            final File potentialDbFile = getPotentialDBFile(root);
            final File potentialDbFileJDBCPath = getPotentialDbFileJDBCPath(root);
            Logger.getLogger(getClass().getName()).info("Checking for DB on "+potentialDbFile);
            
            if (potentialDbFile.exists()) {
                if (potentialDbFile.canWrite() && potentialDbFile.canRead()) {
                    Logger.getLogger(getClass().getName()).info("Found DB file in " + potentialDbFile);

                    listenersManager.callListeners((DatabaseDetectedListener listener) -> {
                        listener.databaseDetected(deviceName, potentialDbFileJDBCPath);
                    });
                } else {
                    Logger.getLogger(getClass().getName()).info("Found DB file in " + potentialDbFile + " but is not writable or not readable");
                }
            }
        }
    }

    @Override
    public void storageDeviceDisconnected(File rootFile, String deviceName) {
        
    }
    
    public static File getPotentialDBFile(File deviceRoot) {
        return new File(deviceRoot.getAbsolutePath() + File.separator + DB_FILE_NAME+DB_FILE_EXTENSION);
    }
    
    public static File getPotentialDbFileJDBCPath(File deviceRoot) {
        return new File(deviceRoot.getAbsolutePath() + File.separator + DB_FILE_NAME);
    }
}

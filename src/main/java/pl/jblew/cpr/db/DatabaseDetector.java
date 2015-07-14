/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import java.io.File;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.treenodes.NodeChangeListener;
import pl.jblew.cpr.util.ListenersManager;

/**
 *
 * @author teofil
 */
public class DatabaseDetector implements StorageDevicePresenceListener {
    public static final String DF_FILE_NAME = "cpr-s-db.h2.h2.db";
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
            final File potentialDbFile = new File(root.getAbsolutePath() + File.separator + DF_FILE_NAME);
            System.out.println("Checking for DB on "+potentialDbFile);
            
            if (potentialDbFile.exists()) {
                if (potentialDbFile.canWrite() && potentialDbFile.canRead()) {
                    System.out.println("Found DB file in " + potentialDbFile);

                    listenersManager.callListeners(new ListenersManager.ListenerCaller<DatabaseDetectedListener>() {
                        @Override
                        public void callListener(DatabaseDetectedListener listener) {
                            listener.databaseDetected(deviceName, potentialDbFile);
                        }

                    });
                } else {
                    System.out.println("Found DB file in " + potentialDbFile + " but is not writable or not readable");
                }
            }
        }
    }

    @Override
    public void storageDeviceDisconnected(File rootFile, String deviceName) {
        
    }
}

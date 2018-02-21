/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import java.io.File;
import pl.jblew.cpr.util.PrintableBusMessage;

/**
 *
 * @author teofil
 */
public class DatabaseChanged implements PrintableBusMessage {
    private final boolean isNull;
    private final String deviceName;
    private final File path;
    private final DatabaseManager databaseManager;

    public DatabaseChanged(boolean isNull, String deviceName, File path, DatabaseManager databaseManager) {
        this.isNull = isNull;
        this.deviceName = deviceName;
        this.path = path;
        this.databaseManager = databaseManager;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public File getPath() {
        return path;
    }

    public boolean isNull() {
        return isNull;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}

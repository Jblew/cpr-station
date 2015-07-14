/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import java.sql.*;
import com.google.common.eventbus.EventBus;
import java.io.File;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.util.MessageToStatusBar;

/**
 *
 * @author teofil
 */
public class DatabaseManager implements DatabaseDetectedListener {
    private final EventBus eBus;
    private final AtomicReference<Connection> connectionSourceRef = new AtomicReference<>(null);

    public DatabaseManager(EventBus eBus) {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            eBus.post(new DatabaseChanged(true, "Brak sterownika h2", null, this));
        }

        this.eBus = eBus;
    }

    @Override
    public void databaseDetected(String deviceName, File dbFile) {
        if (!isConnected()) {
            connect(dbFile, deviceName);
        }
    }

    public void connect(File dbFile, String deviceName) {
        try {
            Connection connectionSource = DriverManager.getConnection("jdbc:h2:" + dbFile.getAbsolutePath());
            connectionSource.setAutoCommit(true);
            synchronized (connectionSourceRef) {
                connectionSourceRef.set(connectionSource);
                eBus.post(new DatabaseChanged(false, deviceName, dbFile, this));
            }
        } catch (SQLException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            eBus.post(new MessageToStatusBar("Nie można połączyć z bazą danych: " + ex, MessageToStatusBar.Type.ERROR));
        }
    }
    
    public Connection getConnection() throws SQLException {
        return connectionSourceRef.get();
    }

    public void close() {
        if (connectionSourceRef.get() != null) {
            try {
                connectionSourceRef.get().close();
                eBus.post(new DatabaseChanged(true, "Rozłączono (możesz podłączyć klucz)", null, this));
            } catch (SQLException ex) {
                Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, "Exception while closing DB ConnectionSource", ex);
            }
            connectionSourceRef.set(null);
        }
    }

    public boolean isConnected() {
        return (connectionSourceRef.get() != null);
    }
}

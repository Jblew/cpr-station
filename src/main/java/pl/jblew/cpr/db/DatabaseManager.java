/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import java.sql.*;
import com.google.common.eventbus.EventBus;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import java.io.File;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.util.MessageToStatusBar;

/**
 *
 * @author teofil
 */
public class DatabaseManager implements DatabaseDetectedListener {
    private static final boolean DEBUG = true;
    private final AtomicInteger taskNum = new AtomicInteger(0);
    private final EventBus eBus;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Runnable> executeWhenDBConnected = new LinkedBlockingQueue<>();
    private final AtomicReference<ConnectionSource> connectionSourceRef = new AtomicReference<>(null);
    private final Daos daos;

    public DatabaseManager(EventBus eBus) {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            eBus.post(new DatabaseChanged(true, "Brak sterownika h2", null, this));
        }
        this.daos = new Daos(this);
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
            //Connection connectionSource = DriverManager.getConnection("jdbc:h2:" + dbFile.getAbsolutePath());
            ConnectionSource connectionSource = new JdbcConnectionSource("jdbc:h2:" + dbFile.getAbsolutePath());
            daos.init(connectionSource);
            connectionSourceRef.set(connectionSource);
            eBus.post(new DatabaseChanged(false, deviceName, dbFile, this));
            executeInDBThread(null);
        } catch (SQLException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            eBus.post(new MessageToStatusBar("Nie można połączyć z bazą danych: " + ex, MessageToStatusBar.Type.ERROR));
        }
    }

    public ConnectionSource getConnectionSource() throws SQLException {
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

    public void executeInDBThread(final Runnable r) {
        if(r == null) System.out.println("Initial execution in DB Thread");
        else System.out.println("Execute in DB Thread");
        
        if (isConnected()) {
            if (!executeWhenDBConnected.isEmpty()) {
                while (!executeWhenDBConnected.isEmpty()) {
                    try {
                        System.out.println("Executing task from queue");
                        final Runnable takenTask = executeWhenDBConnected.take();
                        if (DEBUG) {
                            dbExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    int myNum = taskNum.incrementAndGet();
                                    long s = System.currentTimeMillis();
                                    System.out.println("<" + myNum + "> Starting task " + myNum);
                                    takenTask.run();
                                    System.out.println("<" + myNum + "> Finished task " + myNum + ", Time: " + (System.currentTimeMillis() - s) + "ms");

                                }

                            });
                        } else {
                            dbExecutor.execute(takenTask);
                        }
                    } catch (InterruptedException ex) {
                        Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            if (r != null) {
                if (DEBUG) {
                    dbExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            int myNum = taskNum.incrementAndGet();
                            long s = System.currentTimeMillis();
                            System.out.println("<" + myNum + "> Starting task " + myNum);
                            r.run();
                            System.out.println("<" + myNum + "> Finished task " + myNum + ", Time: " + (System.currentTimeMillis() - s) + "ms");

                        }

                    });
                } else {
                    dbExecutor.execute(r);
                }
            }
        } else {
            try {
                executeWhenDBConnected.put(r);
            } catch (InterruptedException ex) {
                Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public void executeInDBThreadAndWait(final Runnable r) throws InterruptedException {
        if(!isConnected()) throw new DBNotConnectedException();
        else {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            dbExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    r.run();
                    countDownLatch.countDown();
                }
            
            });
            countDownLatch.await();
        }
    }
    
    public void executeInDBThreadAndWait(TimeUnit u, long timeout, final Runnable r) throws InterruptedException {
        if(!isConnected()) throw new DBNotConnectedException();
        else {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            dbExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    r.run();
                    countDownLatch.countDown();
                }
            
            });
            countDownLatch.await(timeout, u);
        }
    }

    public Daos getDaos() {
        return daos;
    }

    public void createAndConnect(File deviceRoot, String deviceName) {
        connect(new File(deviceRoot.getAbsoluteFile() + File.separator + DatabaseDetector.DB_FILE_NAME), deviceName);
    }
    
    public static class DBNotConnectedException extends RuntimeException{}
}

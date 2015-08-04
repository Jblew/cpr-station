/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import com.google.common.eventbus.EventBus;
import com.google.common.io.Files;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.util.MessageToStatusBar;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public class DatabaseManager implements DatabaseDetectedListener {
    private static final boolean DEBUG = false;
    private final AtomicInteger taskNum = new AtomicInteger(0);
    private final EventBus eBus;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("dbthread"));
    private final BlockingQueue<Runnable> executeWhenDBConnected = new LinkedBlockingQueue<>();
    private final AtomicReference<ConnectionSource> connectionSourceRef = new AtomicReference<>(null);
    private final AtomicReference<File> currentDBFile = new AtomicReference<>();
    private final AtomicReference<String> currentDBDeviceName = new AtomicReference<>();
    private final Daos daos;
    private final DBBackupManager backupManager;

    public DatabaseManager(EventBus eBus) {
        try {
            Class.forName("org.h2.Driver").newInstance();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            eBus.post(new DatabaseChanged(true, "Brak sterownika h2", null, this));
        } catch (InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            eBus.post(new DatabaseChanged(true, "Brak sterownika h2", null, this));
        }
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        this.backupManager = new DBBackupManager(eBus);
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
            ConnectionSource connectionSource = backupManager.createProxy(new JdbcConnectionSource("jdbc:h2:" + dbFile.getAbsolutePath()));
            //JdbcConnectionSource jcs = new JdbcConnectionSource("jdbc:mysql://feynman.jblew.pl/fiszki?user=fiszki");
            //jcs.setPassword("F1#3ki%&^");
            //ConnectionSource connectionSource = backupManager.createProxy(jcs);

            daos.init(connectionSource);

            connectionSourceRef.set(connectionSource);
            currentDBFile.set(new File(dbFile.getAbsolutePath()+DatabaseDetector.DB_FILE_EXTENSION));
            currentDBDeviceName.set(deviceName);

            eBus.post(new DatabaseChanged(false, deviceName, dbFile, this));

            executeInDBThread(null);
        } catch (SQLException ex) {
            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            eBus.post(new MessageToStatusBar("Nie można połączyć z bazą danych: " + ex, MessageToStatusBar.Type.ERROR));
        }
    }

    public void dumpDB(File deviceRoot) {
        if (this.isConnected()) {
            File dbBackupFile = new File(DatabaseDetector.getPotentialDBFile(deviceRoot).getAbsolutePath()+".backup-"+DateTimeFormatter.ofPattern("YYYY.MM.dd ").format(LocalDateTime.now()));
            close();

            try {
                Files.copy(currentDBFile.get(), dbBackupFile);
            } catch (IOException ex) {
                Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
            }

            connect(currentDBFile.get(), currentDBDeviceName.get());
        } else {
            throw new DBNotConnectedException();
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

    private void simplyExecute(final Runnable r, final CountDownLatch latch) {
        Runnable fullTask = () -> {
            int myNum = taskNum.incrementAndGet();
            long s = System.currentTimeMillis();
            if (DEBUG) {
                System.out.println("<" + myNum + "> Starting task " + myNum);
            }

            r.run();

            if (DEBUG) {
                System.out.println("<" + myNum + "> Finished task " + myNum + ", Time: " + (System.currentTimeMillis() - s) + "ms");
            }

            if (latch != null) {
                latch.countDown();
            }
        };
        if (Thread.currentThread().getName().startsWith("dbthread")) {
            if (DEBUG) {
                System.out.println("Already in dbthread");
            }
            fullTask.run();
        }
        dbExecutor.execute(fullTask);
    }

    public void executeInDBThread(final Runnable r) {
        if (r == null) {
            if (DEBUG) {
                System.out.println("Initial execution in DB Thread");
            }
        } else {
            if (DEBUG) {
                System.out.println("Execute in DB Thread");
            }
        }

        if (isConnected()) {
            if (!executeWhenDBConnected.isEmpty()) {
                while (!executeWhenDBConnected.isEmpty()) {
                    try {
                        if (DEBUG) {
                            System.out.println("Executing task from queue");
                        }
                        simplyExecute(executeWhenDBConnected.take(), null);

                    } catch (InterruptedException ex) {
                        Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            if (r != null) {
                simplyExecute(r, null);
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
        if (!isConnected()) {
            throw new DBNotConnectedException();
        } else {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            simplyExecute(r, countDownLatch);
            countDownLatch.await();
        }
    }

    public void executeInDBThreadAndWait(TimeUnit u, long timeout, final Runnable r) throws InterruptedException {
        if (!isConnected()) {
            throw new DBNotConnectedException();
        } else {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            simplyExecute(r, countDownLatch);
            countDownLatch.await(timeout, u);
        }
    }

    public Daos getDaos() {
        return daos;
    }

    public void createAndConnect(File deviceRoot, String deviceName) {
        connect(new File(deviceRoot.getAbsoluteFile() + File.separator + DatabaseDetector.DB_FILE_NAME), deviceName);
    }

    public static class DBNotConnectedException extends RuntimeException {
    }
}

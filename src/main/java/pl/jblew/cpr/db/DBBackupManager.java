/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import com.google.common.eventbus.EventBus;
import com.j256.ormlite.dao.ObjectCache;
import com.j256.ormlite.db.DatabaseType;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.GenericRowMapper;
import com.j256.ormlite.stmt.StatementBuilder;
import com.j256.ormlite.support.CompiledStatement;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.support.GeneratedKeyHolder;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.util.NamingThreadFactory;
import pl.jblew.cpr.util.PrintableBusMessage;

/**
 *
 * @author teofil
 */
public class DBBackupManager {
    private final boolean BACKUP_ENABLED = false;
    private final EventBus eBus;
    private final BlockingQueue<ModificationOperation> moQueue = new LinkedBlockingQueue<>();
    private final AtomicReference<BackupSourceProxy> proxy = new AtomicReference<>(null);
    private final ExecutorService executor = Executors.newCachedThreadPool(new NamingThreadFactory("db-backup-mgr"));
    private final ConnectionSource backupConnectionSource;
    private final ExecutorService dbBackupThreadExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("db-backup-thread"));
    private final AtomicBoolean backupThreadRunning = new AtomicBoolean(false);

    public DBBackupManager(EventBus eBus) {
        this.eBus = eBus;

        JdbcConnectionSource backupConnectionSource_ = null;
        try {
            backupConnectionSource_ = new JdbcConnectionSource("jdbc:mysql://feynman.jblew.pl/fiszki?user=fiszki");
            backupConnectionSource_.setPassword("F1#3ki%&^");
            Daos.staticInit(backupConnectionSource_);
        } catch (SQLException ex) {
            Logger.getLogger(DBBackupManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        backupConnectionSource = backupConnectionSource_;
    }

    public ConnectionSource createProxy(ConnectionSource target) {
        proxy.set(new BackupSourceProxy(target));
        return proxy.get();
    }

    public int getCount() {
        return moQueue.size();
    }

    private void asyncFireCountChanged() {
        int left = moQueue.size();
        executor.submit(() -> {
            eBus.post(new DBBackupManager.BackupStateChanged((left == 0), left, "Niepołączony z backupem online"));
        });

        if (BACKUP_ENABLED && (left > 0) && !backupThreadRunning.get()) {
            dbBackupThreadExecutor.submit(() -> {
                backupThreadRunning.set(true);

                while (true) {
                    try {
                        ModificationOperation mo = moQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (mo == null) {
                            break;
                        }

                        boolean done = false;
                        int undoneCount = 0;
                        while (!done) {
                            if (undoneCount > 0) {
                                TimeUnit.MILLISECONDS.sleep(Math.max(10 * 1000, undoneCount * 500));
                            }

                            try {
                                DatabaseConnection conn = backupConnectionSource.getReadWriteConnection();
                                if (conn != null) {
                                    switch (mo.type) {
                                        case INSERT:
                                            conn.insert(mo.statement, mo.args, mo.argfieldTypes, mo.keyHolder);
                                            break;

                                        case UPDATE:
                                            conn.update(mo.statement, mo.args, mo.argfieldTypes);
                                            break;

                                        case DELETE:
                                            conn.delete(mo.statement, mo.args, mo.argfieldTypes);
                                            break;

                                        case EXECUTE_STATEMENT:
                                            conn.executeStatement(mo.statement, mo.resultFlags);
                                            break;
                                    }
                                    done = true;
                                }

                            } catch (SQLException ex) {
                                Logger.getLogger(DBBackupManager.class.getName()).log(Level.SEVERE, null, ex);
                            }

                            undoneCount++;
                        }
                        asyncFireCountChanged();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(DBBackupManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                backupThreadRunning.set(false);
            });
        }
    }

    private class BackupSourceProxy implements ConnectionSource {
        private final ConnectionSource target;
        private final AtomicReference<DatabaseConnection> proxy = new AtomicReference<>(null);

        public BackupSourceProxy(ConnectionSource target) {
            this.target = target;
        }

        @Override
        public DatabaseConnection getReadOnlyConnection() throws SQLException {
            return target.getReadOnlyConnection();
        }

        @Override
        public DatabaseConnection getReadWriteConnection() throws SQLException {
            if (proxy.get() == null) {
                proxy.set(new BackupConnectionProxy(target.getReadWriteConnection()));
            }
            return proxy.get();
        }

        @Override
        public void releaseConnection(DatabaseConnection dc) throws SQLException {
            target.releaseConnection(dc);
        }

        @Override
        public boolean saveSpecialConnection(DatabaseConnection dc) throws SQLException {
            return target.saveSpecialConnection(dc);
        }

        @Override
        public void clearSpecialConnection(DatabaseConnection dc) {
            target.clearSpecialConnection(dc);
        }

        @Override
        public DatabaseConnection getSpecialConnection() {
            return target.getSpecialConnection();
        }

        @Override
        public void close() throws SQLException {
            target.close();
        }

        @Override
        public void closeQuietly() {
            target.closeQuietly();
        }

        @Override
        public DatabaseType getDatabaseType() {
            return target.getDatabaseType();
        }

        @Override
        public boolean isOpen() {
            return target.isOpen();
        }

        private class BackupConnectionProxy implements DatabaseConnection {
            private final DatabaseConnection proxy;

            public BackupConnectionProxy(DatabaseConnection proxy) {
                this.proxy = proxy;
            }

            @Override
            public boolean isTableExists(String tableName) throws SQLException {
                return proxy.isTableExists(tableName);
            }

            @Override
            public boolean isClosed() throws SQLException {
                return proxy.isClosed();
            }

            @Override
            public void closeQuietly() {
                proxy.closeQuietly();
            }

            @Override
            public void close() throws SQLException {
                proxy.close();
            }

            @Override
            public long queryForLong(String statement, Object[] args, FieldType[] argFieldTypes) throws SQLException {
                return proxy.queryForLong(statement, args, argFieldTypes);
            }

            @Override
            public long queryForLong(String statement) throws SQLException {
                return proxy.queryForLong(statement);
            }

            @Override
            public <T> Object queryForOne(String statement, Object[] args, FieldType[] argfieldTypes, GenericRowMapper<T> rowMapper, ObjectCache objectCache) throws SQLException {
                return proxy.queryForOne(statement, args, argfieldTypes, rowMapper, objectCache);
            }

            @Override
            public int delete(String statement, Object[] args, FieldType[] argfieldTypes) throws SQLException {
                ModificationOperation mo = new ModificationOperation();
                mo.type = ModificationOperation.Type.DELETE;
                mo.statement = statement;
                mo.args = args;
                mo.argfieldTypes = argfieldTypes;
                moQueue.add(mo);
                asyncFireCountChanged();

                return proxy.delete(statement, args, argfieldTypes);
            }

            @Override
            public int update(String statement, Object[] args, FieldType[] argfieldTypes) throws SQLException {
                ModificationOperation mo = new ModificationOperation();
                mo.type = ModificationOperation.Type.UPDATE;
                mo.statement = statement;
                mo.args = args;
                mo.argfieldTypes = argfieldTypes;
                moQueue.add(mo);
                asyncFireCountChanged();

                return proxy.update(statement, args, argfieldTypes);
            }

            @Override
            public int insert(String statement, Object[] args, FieldType[] argfieldTypes, GeneratedKeyHolder keyHolder) throws SQLException {
                ModificationOperation mo = new ModificationOperation();
                mo.type = ModificationOperation.Type.INSERT;
                mo.statement = statement;
                mo.args = args;
                mo.argfieldTypes = argfieldTypes;
                mo.keyHolder = keyHolder;
                moQueue.add(mo);
                asyncFireCountChanged();

                return proxy.insert(statement, args, argfieldTypes, keyHolder);
            }

            @Override
            public CompiledStatement compileStatement(String statement, StatementBuilder.StatementType type, FieldType[] argFieldTypes, int resultFlags) throws SQLException {
                return proxy.compileStatement(statement, type, argFieldTypes, resultFlags);
            }

            @Override
            public int executeStatement(String statementStr, int resultFlags) throws SQLException {
                ModificationOperation mo = new ModificationOperation();
                mo.type = ModificationOperation.Type.EXECUTE_STATEMENT;
                mo.statement = statementStr;
                mo.resultFlags = resultFlags;
                moQueue.add(mo);
                asyncFireCountChanged();

                return proxy.executeStatement(statementStr, resultFlags);
            }

            @Override
            public void rollback(Savepoint savePoint) throws SQLException {
                proxy.rollback(savePoint);
            }

            @Override
            public void commit(Savepoint savePoint) throws SQLException {
                proxy.commit(savePoint);
            }

            @Override
            public Savepoint setSavePoint(String name) throws SQLException {
                return proxy.setSavePoint(name);
            }

            @Override
            public void setAutoCommit(boolean autoCommit) throws SQLException {
                proxy.setAutoCommit(autoCommit);
            }

            @Override
            public boolean isAutoCommit() throws SQLException {
                return proxy.isAutoCommit();
            }

            @Override
            public boolean isAutoCommitSupported() throws SQLException {
                return proxy.isAutoCommitSupported();
            }

        }
    }

    private static class ModificationOperation {
        private Type type;
        private String statement = null;
        private Object[] args = null;
        private FieldType[] argfieldTypes;
        private GeneratedKeyHolder keyHolder;
        private int resultFlags;

        public static enum Type {
            DELETE, UPDATE, INSERT, EXECUTE_STATEMENT
        }
    }

    public static class BackupStateChanged {
        private final boolean safe;
        private final int remaningChanges;
        private final String msg;

        BackupStateChanged(boolean safe, int remaningChanges, String msg) {
            this.safe = safe;
            this.remaningChanges = remaningChanges;
            this.msg = msg;
        }

        public boolean isSafe() {
            return safe;
        }

        public int getRemaningChanges() {
            return remaningChanges;
        }

        public String getMsg() {
            return msg;
        }
    }
}

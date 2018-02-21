/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.MFile_Localized;

/**
 *
 * @author teofil
 */
public class Daos {
    private final DatabaseManager dbManager;
    
    private final AtomicReference<Dao<Carrier, Integer>> carrierDao = new AtomicReference<>(null);
    private final AtomicReference<Dao<Event, Integer>> eventDao = new AtomicReference<>(null);
    private final AtomicReference<Dao<MFile, Integer>> mfileDao = new AtomicReference<>(null);
    //private final AtomicReference<Dao<MFile_Localized, Integer>> mfile_LocalizedDao = new AtomicReference<>(null);
    private final AtomicReference<Dao<Event_Localization, Integer>> event_LocalizationDao = new AtomicReference<>(null);

    Daos(DatabaseManager mgr) {
        this.dbManager = mgr;
    }

    void init(ConnectionSource connectionSource) {
        carrierDao.set(initDao(Carrier.class, connectionSource, null));
        eventDao.set(initDao(Event.class, connectionSource, null));
        mfileDao.set(initDao(MFile.class, connectionSource, null));
        //mfile_LocalizedDao.set(initDao(MFile_Localized.class, connectionSource, null));
        event_LocalizationDao.set(initDao(Event_Localization.class, connectionSource, null));
    }
    
    static void staticInit(ConnectionSource src) {
        initDao(Carrier.class, src, null);
        initDao(Event.class, src, null);
        initDao(MFile.class, src, null);
        initDao(Event_Localization.class, src, null);
        //initDao(MFile_Localized.class, src, () -> MFile_Localized.initTable(this, ));
    }
    
    private static <A> Dao<A, Integer> initDao(Class<A> clazz, ConnectionSource connectionSource, Runnable initTableCallback) {
        try {
            Dao<A, Integer> dao = DaoManager.createDao(connectionSource, clazz);
            if (!dao.isTableExists()) {
                Logger.getLogger(Dao.class.getName()).log(Level.INFO, "Creating table for {0}", clazz.getName());
                TableUtils.createTable(connectionSource, clazz);
                if(initTableCallback != null) initTableCallback.run();
            }
            dao.setAutoCommit(connectionSource.getReadWriteConnection(), true);
            return dao;
        } catch (SQLException ex) {
            Logger.getLogger(Daos.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public Dao<Carrier, Integer> getCarrierDao() {
        Dao<Carrier, Integer> ret = carrierDao.get();
        if(ret == null) throw new RuntimeException("Carrier DAO not loaded");
        return ret;
    }

    public Dao<Event, Integer> getEventDao() {
        Dao<Event, Integer> ret = eventDao.get();
        if(ret == null) throw new RuntimeException("Event DAO not loaded");
        return ret;
    }

    public Dao<MFile, Integer> getMfileDao() {
        Dao<MFile, Integer> ret = mfileDao.get();
        if(ret == null) throw new RuntimeException("MFile DAO not loaded");
        return ret;
    }

    public Dao<Event_Localization, Integer> getEvent_LocalizationDao() {
        Dao<Event_Localization, Integer> ret = event_LocalizationDao.get();
        if(ret == null) throw new RuntimeException("Event_Localization DAO not loaded");
        return ret;
    }

    /*public Dao<MFile_Localized, Integer> getMfile_LocalizedDao() {
        Dao<MFile_Localized, Integer> ret = mfile_LocalizedDao.get();
        if(ret == null) throw new RuntimeException("MFile_Localized DAO not loaded");
        return ret;
    }*/
}

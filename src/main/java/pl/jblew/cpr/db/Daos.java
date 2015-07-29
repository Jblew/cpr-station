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
import pl.jblew.cpr.logic.MFile_Event;

/**
 *
 * @author teofil
 */
public class Daos {
    private final AtomicReference<Dao<Carrier, Integer>> carrierDao = new AtomicReference<>(null);
    private final AtomicReference<Dao<Event, Integer>> eventDao = new AtomicReference<>(null);
    private final AtomicReference<Dao<MFile, Integer>> mfileDao = new AtomicReference<>(null);
    private final AtomicReference<Dao<MFile_Event, Integer>> mfile_EventDao = new AtomicReference<>(null);
    private final AtomicReference<Dao<Event_Localization, Integer>> event_LocalizationDao = new AtomicReference<>(null);

    Daos(DatabaseManager mgr) {

    }

    void init(ConnectionSource connectionSource) {
        carrierDao.set(initDao(Carrier.class, connectionSource));
        eventDao.set(initDao(Event.class, connectionSource));
        mfileDao.set(initDao(MFile.class, connectionSource));
        mfile_EventDao.set(initDao(MFile_Event.class, connectionSource));
        event_LocalizationDao.set(initDao(Event_Localization.class, connectionSource));
    }
    
    private <A> Dao<A, Integer> initDao(Class<A> clazz, ConnectionSource connectionSource) {
        try {
            Dao<A, Integer> dao = DaoManager.createDao(connectionSource, clazz);
            if (!dao.isTableExists()) {
                Logger.getLogger(getClass().getName()).log(Level.INFO, "Creating table for {0}", clazz.getName());
                TableUtils.createTable(connectionSource, clazz);
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

    public Dao<MFile_Event, Integer> getMfile_EventDao() {
        Dao<MFile_Event, Integer> ret = mfile_EventDao.get();
        if(ret == null) throw new RuntimeException("MFile_Event DAO not loaded");
        return ret;
    }

    public Dao<Event_Localization, Integer> getEvent_LocalizationDao() {
        Dao<Event_Localization, Integer> ret = event_LocalizationDao.get();
        if(ret == null) throw new RuntimeException("Event_Localization DAO not loaded");
        return ret;
    }
    
    
}

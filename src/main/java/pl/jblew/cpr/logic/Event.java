/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.DatabaseTable;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.EventPanel;
import pl.jblew.cpr.logic.io.Exporter;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "events")
public class Event {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false)
    private String name;

    @DatabaseField(canBeNull = false, dataType = DataType.LONG)
    private long unixTime;

    @DatabaseField(canBeNull = false, dataType = DataType.ENUM_INTEGER)
    private Type type;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getDateTime() {
        return LocalDateTime.ofEpochSecond(unixTime, 0, ZoneOffset.UTC);
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.unixTime = dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public MFile [] getMFiles(Context context) {
        long sT = System.currentTimeMillis();
        final List<MFile> result = new LinkedList<>();
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    Dao<MFile, Integer> mfileDao = context.dbManager.getDaos().getMfileDao();// context.dbManager.getDaos().getMfile_EventDao().queryForEq("eventId", event.getId());
                    Dao<MFile_Event, Integer> mfile_EventDao = context.dbManager.getDaos().getMfile_EventDao();
                    
                    QueryBuilder<MFile_Event, Integer> queryToJoin = mfile_EventDao.queryBuilder();
                    queryToJoin.where().eq("eventId", getId());
                    QueryBuilder<MFile, Integer> qb = mfileDao.queryBuilder().orderBy("unixTime", true).join(queryToJoin);
                    result.addAll(qb.query());
                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
        return result.stream().toArray(MFile[]::new);
        } finally {
            System.out.println("t(getMFiles)="+(System.currentTimeMillis()-sT)+"ms");
        }
    }
    

    public File[] getFiles(Context c) {
        return Arrays.stream(getMFiles(c)).map((mf) -> mf.getAccessibleFile(c)).filter((f) -> f != null).toArray(File[]::new);
    }

    public FullEventData getFullEventData(Context context) {
        final AtomicReference<FullEventData> out = new AtomicReference<>(null);
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    Dao<MFile, Integer> mfileDao = context.dbManager.getDaos().getMfileDao();// context.dbManager.getDaos().getMfile_EventDao().queryForEq("eventId", event.getId());
                    Dao<MFile_Event, Integer> mfile_EventDao = context.dbManager.getDaos().getMfile_EventDao();
                    
                    QueryBuilder<MFile_Event, Integer> queryToJoin = mfile_EventDao.queryBuilder();
                    queryToJoin.where().eq("eventId", getId());
                    QueryBuilder<MFile, Integer> qb = mfileDao.queryBuilder().orderBy("unixTime", true).join(queryToJoin);
                    final List<MFile> mfiles = qb.query();
                    
                    LocalDateTime earliestDate = null;
                    LocalDateTime latestDate = null;
                    int minRedundancy = Integer.MAX_VALUE;
                    int maxRedundancy = 0;
                    for (MFile mf : mfiles) {
                        if (earliestDate == null || mf.getDateTime().isBefore(earliestDate)) {
                            earliestDate = mf.getDateTime();
                        } else if (latestDate == null || mf.getDateTime().isAfter(earliestDate)) {
                            latestDate = mf.getDateTime();
                        }

                        int redundancy = 0;
                        Set<String> carrierIds = new HashSet<>();
                        for (MFile_Localization mfl : mf.getLocalizations()) {
                            if (!carrierIds.contains(mfl.getCarrierId() + "")) {
                                redundancy++;
                                carrierIds.add(mfl.getCarrierId() + "");
                            }
                        }
                        if (redundancy < minRedundancy) {
                            minRedundancy = redundancy;
                        }
                        if (redundancy > maxRedundancy) {
                            maxRedundancy = redundancy;
                        }
                    }
                    out.set(new FullEventData(minRedundancy, maxRedundancy, earliestDate, latestDate, mfiles.toArray(new MFile [] {})));
                } catch (SQLException ex) {
                    Logger.getLogger(EventPanel.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
        }
        return out.get();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + (int) (this.id ^ (this.id >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Event other = (Event) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }
    
    public static Event createEvent(Context c, Event.Type type, String name) {
        try {
            AtomicBoolean hasNewEvent = new AtomicBoolean(false);
            final Event newEvent = new Event();
            newEvent.setDateTime(LocalDateTime.now());
            newEvent.setType(type);
            newEvent.setName(name);
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    c.dbManager.getDaos().getEventDao().create(newEvent);
                    hasNewEvent.set(true);
                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
            if(hasNewEvent.get()) return newEvent;
            else return null;
        } catch (InterruptedException ex) {
            Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static class FullEventData {
        public final int minRedundancy;
        public final int maxRedundancy;
        public final LocalDateTime startDate;
        public final LocalDateTime endDate;
        public final MFile[] mfiles;

        public FullEventData(int minRedundancy, int maxRedundancy, LocalDateTime startDate, LocalDateTime endDate, MFile[] mfiles) {
            this.minRedundancy = minRedundancy;
            this.maxRedundancy = maxRedundancy;
            this.startDate = startDate;
            this.endDate = endDate;
            this.mfiles = mfiles;
        }

    }

    public static enum Type {
        SORTED, UNSORTED
    }
}

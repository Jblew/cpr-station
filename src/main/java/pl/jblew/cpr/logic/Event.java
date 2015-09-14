/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.DatabaseTable;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.EventPanel;
import pl.jblew.cpr.logic.io.Exporter;
import pl.jblew.cpr.logic.io.FileStructureUtil;

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

    @ForeignCollectionField(eager = false, foreignFieldName = "event")
    private ForeignCollection<Event_Localization> localizations;

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

    public MFile.Localized[] getLocalizedMFiles(Context context) {
        return getLocalizedMFiles(context, getAccessibleDir(context));
    }

    public MFile.Localized[] getLocalizedMFiles(Context context, Event_Localization localization) {
        return getLocalizedMFiles(context, new File(getProperPath(context.deviceDetector.getDeviceRoot(localization.getCarrier(context).getName()))));
    }

    private MFile.Localized[] getLocalizedMFiles(Context context, File accessibleEventDir) {
        long sT = System.currentTimeMillis();

        //File accessibleEventDir = new File(getProperPath(context.deviceDetector.getDeviceRoot(carrier.getName())
        final List<MFile.Localized> result = new LinkedList<>();
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    Dao<MFile, Integer> mfileDao = context.dbManager.getDaos().getMfileDao();// context.dbManager.getDaos().getMfile_EventDao().queryForEq("eventId", event.getId());
                    Dao<MFile_Event, Integer> mfile_EventDao = context.dbManager.getDaos().getMfile_EventDao();

                    QueryBuilder<MFile_Event, Integer> queryToJoin = mfile_EventDao.queryBuilder();
                    queryToJoin.where().eq("eventId", getId());
                    QueryBuilder<MFile, Integer> qb = mfileDao.queryBuilder().orderBy("unixTime", true).orderBy("name", true).orderBy("id", true).join(queryToJoin);
                    //System.out.println(">>>"+qb.prepareStatementString());
                    List<MFile> mfiles = qb.query();

                    mfiles.stream().map(mf -> {
                        if (accessibleEventDir == null) {
                            return new MFile.Localized(mf, null);
                        }
                        File potentialFile = new File(accessibleEventDir.getAbsolutePath() + File.separator + mf.getName());
                        if (potentialFile.exists() && !potentialFile.isDirectory() && potentialFile.canRead()) {
                            return new MFile.Localized(mf, potentialFile);
                        }
                        return new MFile.Localized(mf, null);
                    }).forEachOrdered(mfl -> {
                        result.add(mfl);
                        //System.out.println(mfl);
                    });

                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            return result.stream().sorted().toArray(MFile.Localized[]::new);

        } finally {
            System.out.println("t(getMFiles)=" + (System.currentTimeMillis() - sT) + "ms");
        }
    }

    public MFile_Event[] getMFileLinks(Context context) {
        final List<MFile_Event> result = new LinkedList<>();
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    Dao<MFile_Event, Integer> mfile_EventDao = context.dbManager.getDaos().getMfile_EventDao();

                    result.addAll(mfile_EventDao.queryForEq("eventId", this.getId()));
                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, null, ex);
        }

        return result.stream().sorted().toArray(MFile_Event[]::new);
    }

    @Deprecated
    public int getRedundancy() {
        return getLocalizations().size();
    }

    public ForeignCollection<Event_Localization> getLocalizations() {
        return localizations;
    }

    public void setLocalizations(ForeignCollection<Event_Localization> localizations) {
        this.localizations = localizations;
    }

    public Event rename(Context c, String newName) {
        setName(newName);
        final Event me = this;
        try {
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    c.dbManager.getDaos().getEventDao().update(me);
                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
        }
        return this;
    }

    public String getProperPath(File deviceRoot) {
        String sortedPath = (getType() == Event.Type.SORTED ? FileStructureUtil.PATH_SORTED_PHOTOS : FileStructureUtil.PATH_UNSORTED_PHOTOS);
        return deviceRoot.getAbsolutePath() + File.separator + sortedPath + File.separator + getName();
    }

    public File getAccessibleDir(Context context) {
        return getLocalizations().stream().map(el -> el.getCarrier(context)).filter(carrier -> carrier != null)
                .map(carrier -> {
                    File deviceRoot = context.deviceDetector.getDeviceRoot(carrier.getName());
                    if (deviceRoot != null) {
                        return new File(getProperPath(deviceRoot));
                    } else {
                        return null;
                    }
                }
                )
                .filter(dir -> dir != null && dir.exists() && dir.isDirectory()).findFirst().orElse(null);
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
        return this.id == other.id;
    }

    @Override
    public String toString() {
        return "Event{" + "id=" + id + ", name=" + name + ", type=" + type + '}';
    }

    public static Event createEvent(Context c, Event.Type type, String name, Carrier carrier) {
        try {
            AtomicReference<Event> resEvent = new AtomicReference<Event>(null);
            final Event newEvent = new Event();
            newEvent.setDateTime(LocalDateTime.now());
            newEvent.setType(type);
            newEvent.setName(name);
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    c.dbManager.getDaos().getEventDao().create(newEvent);

                    Event_Localization el = new Event_Localization();
                    el.setEvent(newEvent);
                    el.setCarrierId(carrier.getId());
                    el.setPath("");

                    c.dbManager.getDaos().getEvent_LocalizationDao().create(el);

                    List<Event> result = c.dbManager.getDaos().getEventDao().queryForEq("id", newEvent.getId());
                    if (result.size() > 0) {
                        resEvent.set(result.get(0));
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
            return resEvent.get();
        } catch (InterruptedException ex) {
            Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static Event[] getAllEvents(Context c, Event.Type type) {
        AtomicReference<Event[]> out = new AtomicReference<>(null);
        try {
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    out.set(c.dbManager.getDaos().getEventDao().queryForEq("type", type).toArray(new Event[]{}));
                } catch (SQLException ex) {
                    Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
        }
        return out.get();
    }

    public void delete(Context context, Runnable successCallback) {
        if (this.getMFileLinks(context).length > 0) {
            throw new RuntimeException("To wydarzenie zawiera zdjęcia! Nie można go usunąć.");
        }

        context.dbManager.executeInDBThread(() -> {
            try {
                DeleteBuilder<Event_Localization, Integer> localizationDeleteBuilder = context.dbManager.getDaos().getEvent_LocalizationDao().deleteBuilder();
                localizationDeleteBuilder.where().eq("eventId", getId());
                localizationDeleteBuilder.delete();

                context.dbManager.getDaos().getEventDao().delete(this);
                successCallback.run();
            } catch (SQLException ex) {
                Logger.getLogger(EventPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    public static String formatName(LocalDateTime dt, String text) {
        return "[" + DateTimeFormatter.ofPattern("YYYY.MM.dd").format(dt) + "] " + text;
    }

    public static Event forName(Context c, String name) {
        AtomicReference<Event> result = new AtomicReference<>(null);
        try {
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    List<Event> res = c.dbManager.getDaos().getEventDao().queryForEq("name", name);
                    if (res.size() > 0) {
                        result.set(res.get(0));
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result.get();
    }

    public static enum Type {
        SORTED, UNSORTED
    }
}

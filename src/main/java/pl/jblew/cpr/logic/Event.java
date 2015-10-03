/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.stmt.DeleteBuilder;
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
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.logic.integritycheck.Validator;
import pl.jblew.cpr.logic.io.Exporter;
import pl.jblew.cpr.util.TimeUtils;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "events")
public class Event implements Comparable<Event> {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false)
    private String name;

    @DatabaseField(canBeNull = false, dataType = DataType.LONG)
    private long earliestUnixTime;

    @DatabaseField(canBeNull = false, dataType = DataType.LONG)
    private long latestUnixTime;

    @DatabaseField(canBeNull = false, dataType = DataType.ENUM_STRING)
    private Type type;

    @ForeignCollectionField(eager = false, foreignFieldName = "event")
    private ForeignCollection<Event_Localization> localizations;

    public Event() {
        
    }
    
    
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

    public LocalDateTime getEarliestDateTime() {
        return LocalDateTime.ofEpochSecond(earliestUnixTime, 0, ZoneOffset.UTC);
    }

    private void setEarliestDateTime(LocalDateTime dateTime) {
        this.earliestUnixTime = dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public LocalDateTime getLatestDateTime() {
        return LocalDateTime.ofEpochSecond(latestUnixTime, 0, ZoneOffset.UTC);
    }

    private void setLatestDateTime(LocalDateTime dateTime) {
        this.latestUnixTime = dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void recalculateAndUpdateTimeBounds(Context context) {
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    List<MFile> mfiles = context.dbManager.getDaos().getMfileDao().queryForEq("eventId", getId());
                    MFile[] sortedMFiles = mfiles.stream().sorted().toArray(MFile[]::new);

                    setEarliestDateTime(sortedMFiles[0].getDateTime());
                    setLatestDateTime(sortedMFiles[sortedMFiles.length - 1].getDateTime());

                    context.dbManager.getDaos().getEventDao().update(this);
                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String calculateProperDirName() {
        return "[" + TimeUtils.formatDateRange(getEarliestDateTime(), getLatestDateTime()) + "] " + getName();
    }

    public String getDisplayName() {
        return calculateProperDirName();
    }

    public MFile.Localized[] getLocalizedMFiles(Context context) {
        Event_Localization accessibleLocalization = getLocalizations().stream()
                .filter(el -> el.getCarrier(context).isConnected(context)).findAny().orElse(null);

        return getLocalizedMFiles(context, accessibleLocalization);
    }

    public MFile.Localized[] getLocalizedMFiles(Context context, Event_Localization localization) {
        //long sT = System.currentTimeMillis();

        final List<MFile.Localized> result = new LinkedList<>();
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    List<MFile> mfiles = context.dbManager.getDaos().getMfileDao().queryForEq("eventId", getId());

                    mfiles.stream().map(mf -> {
                        if (localization == null) {
                            return new MFile.Localized(mf, null);
                        }

                        File potentialFile = mf.getFile(context, localization);
                        if (potentialFile.exists() && !potentialFile.isDirectory() && potentialFile.canRead()) {
                            return new MFile.Localized(mf, potentialFile);
                        }

                        return new MFile.Localized(mf, null);
                    }).forEachOrdered(mfl -> {
                        result.add(mfl);
                    });

                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, null, ex);
        }
        //try {
            return result.stream().sorted().toArray(MFile.Localized[]::new);

        //} finally {
        //    Logger.getLogger(getClass().getName()).info("t(getMFiles)=" + (System.currentTimeMillis() - sT) + "ms");
        //}
    }

    public ForeignCollection<Event_Localization> getLocalizations() {
        return localizations;

    }

    public void setLocalizations(ForeignCollection<Event_Localization> localizations) {
        this.localizations = localizations;
    }

    public File getAccessibleDir(Context context) {
        return getLocalizations().stream().map(el -> {
            Carrier carrier = el.getCarrier(context);
            if (carrier != null && carrier.isConnected(context)) {
                return new File(el.getFullEventPath(context));
            } else {
                return null;
            }
        }
        )
                .filter(dir -> dir != null && dir.exists() && dir.isDirectory() && dir.canRead()).findFirst().orElse(null);
    }

    public boolean hasProblems() {
        return getLocalizations().size() < 2;
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

    @Override
    public int compareTo(Event o) {
        return Long.compare(earliestUnixTime, o.earliestUnixTime);
    }

    public static Event createEvent(Context c, Event.Type type, String name, Carrier carrier) {
        if(name.isEmpty()) throw new IllegalArgumentException("Event name is null!");
        try {
            AtomicReference<Event> resEvent = new AtomicReference<>(null);
            final Event newEvent = new Event();
            newEvent.setEarliestDateTime(LocalDateTime.now());
            newEvent.setLatestDateTime(LocalDateTime.now());
            newEvent.setType(type);
            newEvent.setName(name);
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    c.dbManager.getDaos().getEventDao().create(newEvent);

                    if (carrier != null) {
                        Event_Localization el = new Event_Localization();
                        el.setEvent(newEvent);
                        el.setCarrierId(carrier.getId());
                        el.setDirName(name);
                        el.setActualEventType(newEvent.getType());

                        c.dbManager.getDaos().getEvent_LocalizationDao().create(el);
                    }

                    List<Event> result = c.dbManager.getDaos().getEventDao().queryForEq("id", newEvent.getId());
                    if (result.size() > 0) {
                        resEvent.set(result.get(0));//in order to obtain id
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

    public void update(Context context) {
        context.dbManager.executeInDBThread(() -> {
            try {
                context.dbManager.getDaos().getEventDao().update(this);

                context.cachedExecutor.submit(() -> {
                    ProgressListPanel.ProgressEntity pe = new ProgressListPanel.ProgressEntity();
                    context.eBus.post(pe);
                    pe.setText("Weryfikowanie " + getName());
                    pe.setValue(1,2);
                    for (Event_Localization el : getLocalizations()) {
                        Validator.validateEventLocalizationOrMarkForValidation(context, el);
                    }
                    pe.setValue(2, 2);
                    pe.markFinished();
                });
            } catch (SQLException ex) {
                Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    /*public static String formatName(LocalDateTime dt, String text) {
     return "[" + DateTimeFormatter.ofPattern("YYYY.MM.dd").format(dt) + "] " + text;
     }*/
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

    public static String makeNameValid(Context context, String name) {
        /*< > : " / \ | ? **/
        String newName = name.replace('<', ' ').replace('>', ' ').replace(':', ' ')
                .replace('"', ' ').replace('/', ' ').replace('\\', ' ')
                .replace('|', ' ').replace('?', ' ').replace('*', ' ');
        AtomicReference<String> resultName = new AtomicReference<>(newName + System.currentTimeMillis());
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                String out = newName;
                try {
                    while (true) {

                        long count = context.dbManager.getDaos().getEventDao().queryBuilder().where().eq("name", out).countOf();
                        if (count > 0) {
                            out += "|";
                        } else {
                            resultName.set(out);
                            break;
                        }
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Event.class.getName()).log(Level.SEVERE, null, ex);
        }
        return resultName.get();
    }

    public static enum Type {
        SORTED, UNSORTED
    }
}

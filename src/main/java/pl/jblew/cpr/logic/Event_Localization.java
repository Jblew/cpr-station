/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.components.browser.MFileBrowser;
import pl.jblew.cpr.logic.io.FileStructureUtil;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "event_localization")
public class Event_Localization {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false, foreign = true, columnName = "eventId")
    private Event event;
    private final Object eventSync = new Object();

    @DatabaseField(canBeNull = false)
    private long carrierId;

    @DatabaseField(canBeNull = false)
    private String dirName;

    private final AtomicReference<Carrier> cachedCarrier = new AtomicReference<>(null);

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Event getEvent() {
        synchronized (eventSync) {
            return event;
        }
    }

    public Event getOrLoadFullEvent(Context c) {
        String name;
        long id;
        synchronized (eventSync) {
            name = event.getName();
            id = event.getId();
        }
        if (name == null) {
            try {
                c.dbManager.executeInDBThreadAndWait(() -> {
                    try {
                        Event result = c.dbManager.getDaos().getEventDao().queryForId((int) id);
                        if (result != null) {
                            setEvent(result);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(Event_Localization.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } catch (InterruptedException ex) {
                Logger.getLogger(Event_Localization.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        synchronized (eventSync) {
            return event;
        }
    }

    public void setEvent(Event event) {
        synchronized (eventSync) {
            this.event = event;
        }
    }

    public long getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(long carrierId) {
        this.carrierId = carrierId;
    }

    public String getDirName() {
        return dirName;
    }

    public void setDirName(String dirName) {
        this.dirName = dirName;
    }
    
    public Carrier getCarrier(Context context) {
        if (cachedCarrier.get() != null) {
            return cachedCarrier.get();
        } else {
            try {
                context.dbManager.executeInDBThreadAndWait(() -> {
                    Carrier c = null;
                    try {
                        List<Carrier> res = context.dbManager.getDaos().getCarrierDao().queryForEq("id", getCarrierId());
                        if (res.size() > 0) {
                            cachedCarrier.set(res.get(0));
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(MFileBrowser.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } catch (InterruptedException ex) {
                Logger.getLogger(Event_Localization.class.getName()).log(Level.SEVERE, null, ex);
            }

            return cachedCarrier.get();
        }
    }

    public String getFullEventPath(Context context) {
        Event evt = getOrLoadFullEvent(context);
        String sortedPath = (evt.getType() == Event.Type.SORTED ? FileStructureUtil.PATH_SORTED_PHOTOS : FileStructureUtil.PATH_UNSORTED_PHOTOS);
        Carrier carrier = getCarrier(context);
        return context.deviceDetector.getDeviceRoot(carrier.getName()) + File.separator + sortedPath + File.separator + getDirName();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (int) (this.id ^ (this.id >>> 32));
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
        final Event_Localization other = (Event_Localization) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Event_Localization{" + "id=" + id + ", event=" + event + ", carrierId=" + carrierId + ", path=" + dirName + '}';
    }
}

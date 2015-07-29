/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "mfile_event")
public class MFile_Event implements Comparable<MFile_Event> {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;
    
    @DatabaseField(columnName = "fileId", foreign = true, canBeNull = false)
    private MFile mfile;
    
    @DatabaseField(columnName = "eventId", foreign = true, canBeNull = false)
    private Event event;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public MFile getMFile() {
        return mfile;
    }

    public void setMFile(MFile mfile) {
        this.mfile = mfile;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (int) (this.id ^ (this.id >>> 32));
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
        final MFile_Event other = (MFile_Event) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(MFile_Event o) {
        return getMFile().compareTo(o.getMFile());
    }

    @Override
    public String toString() {
        return "MFile_Event{" + "id=" + id + ", mfile=" + mfile + ", event=" + event + '}';
    }
}

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
public class MFile_Event {
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

    public MFile getMfile() {
        return mfile;
    }

    public void setMfile(MFile mfile) {
        this.mfile = mfile;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }
}

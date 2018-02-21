/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import pl.jblew.cpr.db.Daos;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName="mfile_localized")
public class MFile_Localized {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false)
    private String currentFilename;

    @DatabaseField(canBeNull = false)
    private String currentMD5;

    @DatabaseField(canBeNull = false, dataType = DataType.LONG)
    private long currentUnixTime;

    @DatabaseField(canBeNull = false, foreign = true, columnName = "currentEventId")
    private Event currentEvent;
    private final Object currentEventSync = new Object();
    
    @DatabaseField(canBeNull = false, foreign = true, columnName = "currentEventLocalizationId")
    private Event_Localization currentEventLocalization;
    private final Object currentEventLocalizationSync = new Object();
    
    @DatabaseField(canBeNull = false, foreign = true, columnName = "properMFileId")
    private MFile properMFile;
    private final Object properMFileSync = new Object();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCurrentFilename() {
        return currentFilename;
    }

    public void setCurrentFilename(String currentFilename) {
        this.currentFilename = currentFilename;
    }

    public String getCurrentMD5() {
        return currentMD5;
    }

    public void setCurrentMD5(String currentMD5) {
        this.currentMD5 = currentMD5;
    }

    public long getCurrentUnixTime() {
        return currentUnixTime;
    }

    public void setCurrentUnixTime(long currentUnixTime) {
        this.currentUnixTime = currentUnixTime;
    }

    public Event getCurrentEvent() {
        return currentEvent;
    }

    public void setCurrentEvent(Event currentEvent) {
        this.currentEvent = currentEvent;
    }

    public Event_Localization getCurrentEventLocalization() {
        return currentEventLocalization;
    }

    public void setCurrentEventLocalization(Event_Localization currentEventLocalization) {
        this.currentEventLocalization = currentEventLocalization;
    }

    public MFile getProperMFile() {
        return properMFile;
    }

    public void setProperMFile(MFile properMFile) {
        this.properMFile = properMFile;
    }
    
    public static void initTable(Daos daos) {
        
    }
}

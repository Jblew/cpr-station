/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.Date;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "mfiles")
public class MFile {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;
    
    @DatabaseField(canBeNull = false)
    private String name;
    
    @DatabaseField(canBeNull = false)
    private String md5;
    
    @DatabaseField(canBeNull = false)
    private java.util.Date date;
    
    @ForeignCollectionField(eager = false, foreignFieldName="mfile")
    private ForeignCollection<MFile_Localization> localizations;
    
    public MFile() {
        
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

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public ForeignCollection<MFile_Localization> getLocalizations() {
        return localizations;
    }

    public void setLocalizations(ForeignCollection<MFile_Localization> localizations) {
        this.localizations = localizations;
    }
}

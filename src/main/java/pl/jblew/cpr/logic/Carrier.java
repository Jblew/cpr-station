/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.util.Date;
import pl.jblew.cpr.bootstrap.Context;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "carriers")
public class Carrier {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private int id;
    
    @DatabaseField(canBeNull = false, unique = true)
    private String name;
    
    @DatabaseField(canBeNull = true, unique = false)
    private Date lastChecked;
    
    @DatabaseField(canBeNull = false, unique = false, dataType = DataType.ENUM_STRING)
    private Type type;
    
    public Carrier() {
        
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getLastChecked() {
        return lastChecked;
    }

    public void setLastChecked(Date lastChecked) {
        this.lastChecked = lastChecked;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
    
    public static enum Type {
        USB, USB_FLASH, USB_HDD, OPTICAL_DISC, UNKNOWN
    }
}

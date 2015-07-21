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
@DatabaseTable(tableName = "carriers")
public class Carrier {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private int id;
    
    @DatabaseField(canBeNull = false, unique = true)
    private String name;
    
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
}

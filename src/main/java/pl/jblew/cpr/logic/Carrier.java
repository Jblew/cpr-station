/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
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
    
    public static Carrier [] getAllCarriers(Context c) {
        AtomicReference<Carrier []> out = new AtomicReference<>(null);
        try {
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    out.set(c.dbManager.getDaos().getCarrierDao().queryForAll().toArray(new Carrier[] {}));
                } catch (SQLException ex) {
                    Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
        }
        return out.get();
    }
    
    public static Carrier [] getCarriersSortedByNumOfMFiles(Context context, MFile [] mfiles) {
        final HashMap<Carrier, Integer> out = new HashMap<>();
        Arrays.stream(mfiles).flatMap(mf -> mf.getLocalizations().stream()).forEach(mfl -> {
            Carrier c = mfl.getCarrier(context);
            if (c != null) {
                if (out.containsKey(c)) {
                    out.put(c, out.get(c) + 1);
                } else {
                    out.put(c, 1);
                }
            }
        });

        return out.entrySet().stream().sorted((entry1, entry2) -> -Integer.compare(entry1.getValue(), entry2.getValue()))
                .map(entry -> entry.getKey()).toArray(Carrier[]::new);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + this.id;
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
        final Carrier other = (Carrier) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }
    
    public static enum Type {
        USB, USB_FLASH, USB_HDD, OPTICAL_DISC, UNKNOWN
    }
}

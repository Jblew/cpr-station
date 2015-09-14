/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    @DatabaseField(canBeNull = false, dataType = DataType.LONG)
    private long lastChecked;
    
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

    public LocalDateTime getLastChecked() {
        return LocalDateTime.ofEpochSecond(lastChecked, 0, ZoneOffset.UTC);
    }

    public void setLastChecked(LocalDateTime dateTime) {
        this.lastChecked = dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
    
    public Event_Localization [] getEvents(Context c) {
        AtomicReference<Event_Localization []> out = new AtomicReference<>(null);
        try {
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    out.set(c.dbManager.getDaos().getEvent_LocalizationDao().queryForEq("carrierId", id).toArray(new Event_Localization []{}));
                } catch (SQLException ex) {
                    Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
        }
        return out.get();
    }
    
    public void update(Context context) {
        context.dbManager.executeInDBThread(() -> {
            try {
                context.dbManager.getDaos().getCarrierDao().update(this);
            } catch (SQLException ex) {
                Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }
    
    public boolean isConnected(Context context) {
        File root = context.deviceDetector.getDeviceRoot(getName());
        return root != null && root.exists();
    }
    
    @Override
    public String toString() {
        return "Carrier{" + "id=" + id + ", name=" + name + ", type=" + type + '}';
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
    
    public static Carrier forName(Context c, String name) {
        AtomicReference<Carrier> result = new AtomicReference<>(null);
        try {
            c.dbManager.executeInDBThreadAndWait(() -> {
                try {
                    List<Carrier> res = c.dbManager.getDaos().getCarrierDao().queryForEq("name", name);
                    if(res.size() > 0) result.set(res.get(0));
                } catch (SQLException ex) {
                    Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result.get();
    }
    
    /*public static Carrier [] getCarriersSortedByNumOfMFiles(Context context, MFile [] mfiles) {
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
    }*/
    
    
    public static enum Type {
        USB, USB_FLASH, USB_HDD, OPTICAL_DISC, UNKNOWN
    }
}

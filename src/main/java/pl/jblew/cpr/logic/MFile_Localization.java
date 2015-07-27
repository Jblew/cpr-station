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
import pl.jblew.cpr.gui.components.MFileBrowser;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "mfile_localization")
public class MFile_Localization {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false, foreign = true, columnName = "fileId")
    private MFile mfile;

    @DatabaseField(canBeNull = false)
    private long carrierId;

    @DatabaseField(canBeNull = false)
    private String path;

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

    public long getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(long carrierId) {
        this.carrierId = carrierId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Carrier getCarrier(Context context) {
        final AtomicReference<Carrier> result = new AtomicReference<>(null);
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                Carrier c = null;
                try {
                    List<Carrier> res = context.dbManager.getDaos().getCarrierDao().queryForEq("id", getCarrierId());
                    if(res.size() > 0) result.set(res.get(0));
                } catch (SQLException ex) {
                    Logger.getLogger(MFileBrowser.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(MFile_Localization.class.getName()).log(Level.SEVERE, null, ex);
        }

        return result.get();
    }

    public File getFile(final Context context) {
        final AtomicReference<File> result = new AtomicReference<>(null);
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {
                Carrier c = null;
                try {
                    List<Carrier> res = context.dbManager.getDaos().getCarrierDao().queryForEq("id", getCarrierId());
                    if (res.size() > 0) {
                        c = res.get(0);
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(MFileBrowser.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (c != null) {
                    File root = context.deviceDetector.getDeviceRoot(c.getName());
                    if (root != null) {
                        File me = new File(root.getAbsolutePath() + File.separator + getPath());
                        if (me.exists() && me.canRead()) {
                            result.set(me);
                        }
                    }
                }
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(MFile_Localization.class.getName()).log(Level.SEVERE, null, ex);
        }

        return result.get();
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
        final MFile_Localization other = (MFile_Localization) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }
    
    
}

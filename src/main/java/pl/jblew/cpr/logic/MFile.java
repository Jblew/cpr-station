/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.table.DatabaseTable;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "mfiles")
public class MFile implements Comparable<MFile> {
    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false)
    private String name;

    @DatabaseField(canBeNull = false)
    private String md5;

    @DatabaseField(canBeNull = false, dataType = DataType.LONG)
    private long unixTime;

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

    public LocalDateTime getDateTime() {
        return LocalDateTime.ofEpochSecond(unixTime, 0, ZoneOffset.UTC);
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.unixTime = dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public String getProperPath(File deviceRoot, Event e) {
        return e.getProperPath(deviceRoot) + File.separator + getName();
    }
    
    public File getFile(Context context, Event event, Event_Localization eventLocalization) {
        return new File(event.getProperPath(context.deviceDetector.getDeviceRoot(eventLocalization.getCarrier(context).getName()))+File.separator+this.getName());
    }

    public void delete(Context context) {
        context.dbManager.executeInDBThread(() -> {
            try {
                DeleteBuilder<MFile_Event, Integer> linkDeleteBuilder = context.dbManager.getDaos().getMfile_EventDao().deleteBuilder();
                linkDeleteBuilder.where().eq("fileId", getId());
                linkDeleteBuilder.delete();
                
                context.dbManager.getDaos().getMfileDao().delete(this);
            } catch (SQLException ex) {
                Logger.getLogger(MFile.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + (int) (this.id ^ (this.id >>> 32));
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
        final MFile other = (MFile) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(MFile o) {
        if (o.getDateTime() == null) {
            return -1;
        }
        if (getDateTime() == null) {
            return 0;
        }
        return getDateTime().compareTo(o.getDateTime());
    }

    @Override
    public String toString() {
        return "MFile{" + "id=" + id + ", name=" + name + '}';
    }

    public static class Localized implements Comparable<MFile.Localized> {
        private final MFile mfile;
        private final File file;

        public Localized(MFile mfile, File file) {
            this.mfile = mfile;
            this.file = file;
        }

        public MFile getMFile() {
            return mfile;
        }

        public File getFile() {
            return file;
        }

        @Override
        public int compareTo(MFile.Localized o) {
            if (o.getMFile() == null) {
                return -1;
            }
            if (getMFile() == null) {
                return 0;
            }
            return getMFile().compareTo(o.getMFile());
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.mfile);
            hash = 29 * hash + Objects.hashCode(this.file);
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
            final Localized other = (Localized) obj;
            if (!Objects.equals(this.mfile, other.mfile)) {
                return false;
            }
            if (!Objects.equals(this.file.toPath(), other.file.toPath())) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "MFile.Localized{" + "mfile=" + mfile + ", file=" + file + '}';
        }
    }
}

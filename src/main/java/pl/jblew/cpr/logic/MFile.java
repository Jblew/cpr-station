/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic;

import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
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

    @ForeignCollectionField(eager = false, foreignFieldName = "mfile")
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

    public LocalDateTime getDateTime() {
        return LocalDateTime.ofEpochSecond(unixTime, 0, ZoneOffset.UTC);
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.unixTime = dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    public ForeignCollection<MFile_Localization> getLocalizations() {
        return localizations;
    }

    public void setLocalizations(ForeignCollection<MFile_Localization> localizations) {
        this.localizations = localizations;
    }

    public File getAccessibleFile(Context c) {
        if (localizations == null) {
            throw new RuntimeException("Localizations not loaded! name=" + name + ", id=" + id);
        }
        Stream<MFile_Localization> s = localizations.stream();
        if (s == null) {
            throw new RuntimeException("ForeignCollection returns nullStream");
        }
        Optional<File> res = s.map((mfl) -> mfl.getFile(c)).filter((f) -> f != null && f.canRead()).findFirst();
        return (res.isPresent() ? res.get() : null);
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

}

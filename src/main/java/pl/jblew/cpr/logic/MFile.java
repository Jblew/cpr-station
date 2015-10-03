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
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.logic.integritycheck.Validator;

/**
 *
 * @author teofil
 */
@DatabaseTable(tableName = "mfiles")
public class MFile implements Comparable<MFile> {
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd-HH.mm.ss");

    @DatabaseField(canBeNull = false, unique = true, generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false)
    private String filename;

    @DatabaseField(canBeNull = false)
    private String md5;

    @DatabaseField(canBeNull = false, dataType = DataType.LONG)
    private long unixTime;

    @DatabaseField(canBeNull = false, foreign = true, columnName = "eventId")
    private Event event;
    private final Object eventSync = new Object();

    public MFile() {

    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    /**
     * DateTime *MUST* be set before copying the file
     */
    public String calculateAndSetFilename(String basePath, String extension) {
        String baseFileName = FILENAME_FORMATTER.format(getDateTime());
        File targetFile = new File(basePath + File.separator + baseFileName + "." + extension);

        if (targetFile.exists()) {
            int fileNameI = 0;
            while (true) {
                targetFile = new File(calculateAndSetFilename(basePath, extension, fileNameI));

                if (!targetFile.exists()) {
                    break;
                }
                fileNameI++;
            }
        }
        this.filename = targetFile.getName();

        return this.filename;
    }
    
    public String calculateAndSetFilename(String basePath, String extension, int addNumber) {
        String baseFileName = FILENAME_FORMATTER.format(getDateTime());
        String targetPath = basePath + File.separator + baseFileName + "_" + addNumber + "." + extension;

        this.filename = targetPath;

        return this.filename;
    }

    public Event getOrLoadFullEvent(Context c) {
        String name;
        long id;
        synchronized (eventSync) {
            name = event.getName();
            id = event.getId();
        }
        if (name == null) {
            try {
                c.dbManager.executeInDBThreadAndWait(() -> {
                    try {
                        Event result = c.dbManager.getDaos().getEventDao().queryForId((int) id);
                        if (result != null) {
                            setEvent(result);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(Event_Localization.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } catch (InterruptedException ex) {
                Logger.getLogger(Event_Localization.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        synchronized (eventSync) {
            return event;
        }
    }

    public File getFile(Context context, Event_Localization localization) {
        String path = localization.getFullEventPath(context) + File.separator + getFilename();

        return new File(path);
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

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public void delete(Context context) {
        context.dbManager.executeInDBThread(() -> {
            try {
                context.dbManager.getDaos().getMfileDao().delete(this);
            } catch (SQLException ex) {
                Logger.getLogger(MFile.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }
    
    public void update(Context context) {
        context.dbManager.executeInDBThread(() -> {
            try {
                context.dbManager.getDaos().getMfileDao().update(this);
            } catch (SQLException ex) {
                Logger.getLogger(Carrier.class.getName()).log(Level.SEVERE, null, ex);
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
        return "MFile{" + "id=" + id + ", name=" + filename + '}';
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

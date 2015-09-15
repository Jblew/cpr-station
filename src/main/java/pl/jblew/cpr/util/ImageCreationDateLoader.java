/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author teofil
 */
public class ImageCreationDateLoader {
    private static final DateTimeFormatter EXIF_DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private static final DateLoadingStrategy[] STRATEGIES = new DateLoadingStrategy[]{
        (imgFile, metadata) -> { //get Exif SubIFD date/time
            if (!metadata.containsDirectoryOfType(ExifSubIFDDirectory.class)) {
                return null;
            }

            for (ExifSubIFDDirectory dir : metadata.getDirectoriesOfType(ExifSubIFDDirectory.class)) {

                if (dir.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                    String dt = dir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                    if (dt != null && !dt.isEmpty()) {
                        try {
                            return LocalDateTime.parse(dt.trim(), EXIF_DT_FORMATTER);
                        } catch (DateTimeParseException ex) {
                            Logger.getLogger(ImageCreationDateLoader.class.getName()).log(Level.SEVERE, "For '"+dt+"'", ex);
                        }
                    }

                }

                if (dir.containsTag(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED)) {
                    String dt = dir.getString(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
                    try {
                        return LocalDateTime.parse(dt, EXIF_DT_FORMATTER);
                    } catch (DateTimeParseException ex) {
                        Logger.getLogger(ImageCreationDateLoader.class.getName()).log(Level.SEVERE, "For "+dt, ex);
                    }
                }

                if (dir.containsTag(ExifSubIFDDirectory.TAG_DATETIME)) {
                    String dt = dir.getString(ExifSubIFDDirectory.TAG_DATETIME);
                    try {
                        return LocalDateTime.parse(dt, EXIF_DT_FORMATTER);
                    } catch (DateTimeParseException ex) {
                        Logger.getLogger(ImageCreationDateLoader.class.getName()).log(Level.SEVERE, "For "+dt, ex);
                    }
                }
            }

            return null;
        },
        (imgFile, metadata) -> { //get Exif IFD0 date/time
            if (!metadata.containsDirectoryOfType(ExifIFD0Directory.class)) {
                return null;
            }

            for (ExifIFD0Directory dir : metadata.getDirectoriesOfType(ExifIFD0Directory.class)) {

                if (dir.containsTag(ExifIFD0Directory.TAG_DATETIME_ORIGINAL)) {
                    String dt = dir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                    try {
                        return LocalDateTime.parse(dt, EXIF_DT_FORMATTER);
                    } catch (DateTimeParseException ex) {
                        Logger.getLogger(ImageCreationDateLoader.class.getName()).log(Level.SEVERE, "For "+dt, ex);
                    }
                }

                if (dir.containsTag(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED)) {
                    String dt = dir.getString(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
                    try {
                        return LocalDateTime.parse(dt, EXIF_DT_FORMATTER);
                    } catch (DateTimeParseException ex) {
                        Logger.getLogger(ImageCreationDateLoader.class.getName()).log(Level.SEVERE, "For "+dt, ex);
                    }
                }

                if (dir.containsTag(ExifSubIFDDirectory.TAG_DATETIME)) {
                    String dt = dir.getString(ExifSubIFDDirectory.TAG_DATETIME);
                    try {
                        return LocalDateTime.parse(dt, EXIF_DT_FORMATTER);
                    } catch (DateTimeParseException ex) {
                        Logger.getLogger(ImageCreationDateLoader.class.getName()).log(Level.SEVERE, "For "+dt, ex);
                    }
                }
            }

            return null;
        },
        (imgFile, metadata) -> { //get file modification date
            long lastModified = imgFile.lastModified();
            if (lastModified == 0) {
                return LocalDateTime.now();
            } else {
                return LocalDateTime.ofEpochSecond(lastModified/1000l, 0, ZoneOffset.UTC);
            }
        }
    };

    private ImageCreationDateLoader() {
    }

    public static LocalDateTime getCreationDateTime(File imgFile) throws ImageProcessingException, IOException {
        if (!imgFile.exists()) {
            throw new RuntimeException(new FileNotFoundException());
        }

        Metadata metadata = ImageMetadataReader.readMetadata(imgFile);

        for (DateLoadingStrategy strategy : STRATEGIES) {
            LocalDateTime result = strategy.getCreationTime(imgFile, metadata);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private static interface DateLoadingStrategy {
        public LocalDateTime getCreationTime(File imgFile, Metadata metadata);
    }
}

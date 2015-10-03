/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.integritycheck;

import com.google.common.io.Files;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.io.MD5Util;

/**
 *
 * @author teofil
 */
public class Validator {
    private Validator() {
    }

    public static void validateEventLocalizationOrMarkForValidation(Context context, Event_Localization el) {
        if (el.getCarrier(context).isConnected(context)) {
            try {
                validateEventLocalization(context, el, true);
            } catch (MissingOrBrokenFilesException ex) {
                Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            el.setNeedValidation(true);
            try {
                context.dbManager.executeInDBThreadAndWait(() -> {
                    try {
                        context.dbManager.getDaos().getEvent_LocalizationDao().update(el);
                    } catch (SQLException ex) {
                        Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } catch (InterruptedException ex) {
                Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void validateEventLocalization(Context context, Event_Localization el) throws MissingOrBrokenFilesException {
        validateEventLocalization(context, el, false);
    }

    public static void validateEventLocalization(Context context, Event_Localization el, boolean ignoreMissingOrBrokenFiles) throws MissingOrBrokenFilesException {
        Event event = el.getOrLoadFullEvent(context);

        event.recalculateAndUpdateTimeBounds(context);

        for (MFile.Localized mfl : event.getLocalizedMFiles(context, el)) {
            boolean missingOrBroken = false;
            if (mfl.getFile() == null) {
                missingOrBroken = true;
                Logger.getLogger(Validator.class.getName()).warning("Missing file " + mfl.getMFile().getFile(context, el));
            } else {
                String properMD5 = mfl.getMFile().getMd5();
                String actualMD5 = MD5Util.calculateMD5(mfl.getFile());
                if (!actualMD5.equals(properMD5)) {
                    try {
                        BufferedImage img = ImageIO.read(mfl.getFile());
                        if(img.getWidth() > 0 && img.getHeight() > 0) {
                            img.getRGB(img.getWidth()-1, img.getHeight()-1);
                            
                            mfl.getMFile().setMd5(actualMD5);
                            mfl.getMFile().update(context);
                        }
                        else {
                            missingOrBroken = true;
                        }
                    } catch (IOException ex) {
                        missingOrBroken = true;
                        Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, "Image not loadable", ex);
                    }
                    
                    
                    if(missingOrBroken) Logger.getLogger(Validator.class.getName()).warning("MD5 mismatch for file " + mfl.getMFile().getFile(context, el) + ": Proper: \"" + properMD5 + "\", actual: \"" + actualMD5 + "\"");
                    else Logger.getLogger(Validator.class.getName()).warning("MD5 mismatch for file " + mfl.getMFile().getFile(context, el) + ": Proper: \"" + properMD5 + "\", actual: \"" + actualMD5 + "\". Img was valid, changed MD5 to actual");
                }
            }

            if (missingOrBroken) {
                if (!ignoreMissingOrBrokenFiles) {
                    el.setNeedValidation(true);
                    el.update(context);
                    throw new MissingOrBrokenFilesException();
                }
            }
        }

        if (el.getActualEventType() != event.getType()) {
            Event.Type oldActualType = el.getActualEventType();
            File oldDir = new File(el.getFullEventPath(context));

            el.setActualEventType(event.getType());
            File newDir = new File(el.getFullEventPath(context));
            if (newDir.exists()) {
                throw new RuntimeException("New directory already exists! (Cannot change actual event type of event localization)");
            }
            File newDirParent = newDir.getParentFile();
            if (!newDirParent.exists()) {
                newDirParent.mkdirs();
            }

            try {
                Files.move(oldDir, newDir);

                try {
                    context.dbManager.executeInDBThreadAndWait(() -> {
                        try {
                            context.dbManager.getDaos().getEvent_LocalizationDao().update(el);
                        } catch (SQLException ex) {
                            Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                } catch (InterruptedException ex) {
                    Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
                el.setActualEventType(oldActualType);
            }
        }

        String properDirName = event.calculateProperDirName();
        if (!el.getDirName().equals(properDirName)) {
            File oldDir = new File(el.getFullEventPath(context));
            if (oldDir.exists() && oldDir.canWrite()) {
                File newDir = new File(oldDir.getParentFile().getAbsolutePath() + File.separator + properDirName);
                if (!newDir.exists()) {
                    try {
                        context.dbManager.executeInDBThreadAndWait(() -> {
                            try {
                                Files.move(oldDir, newDir);
                                el.setDirName(properDirName);
                                context.dbManager.getDaos().getEvent_LocalizationDao().update(el);
                            } catch (IOException | SQLException ex) {
                                Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Validator.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        el.setNeedValidation(false);
        el.update(context);
    }

    public static class MissingOrBrokenFilesException extends Exception {

    }
}

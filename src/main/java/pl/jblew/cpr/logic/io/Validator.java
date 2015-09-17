/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.MFile;

/**
 *
 * @author teofil
 */
public class Validator {
    private Validator() {
    }

    public static void validateEventLocalization(Context context, Event_Localization el) throws MissingOrBrokenFilesException {
        Event event = el.getOrLoadFullEvent(context);

        event.recalculateAndUpdateTimeBounds(context);

        for (MFile.Localized mfl : event.getLocalizedMFiles(context, el)) {
            if (mfl.getFile() == null || !MD5Util.calculateMD5(mfl.getFile()).equals(mfl.getMFile().getMd5())) {
                throw new MissingOrBrokenFilesException();
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
    }

    public static class MissingOrBrokenFilesException extends Exception {

    }
}

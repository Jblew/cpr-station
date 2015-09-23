/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.integritycheck;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.gui.windows.RepairWindow;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public class IntegrityCheckerProcessor {
    private final Context context;
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(new NamingThreadFactory("integritychecker-processor-sched"));
    private final Set<String> devicesInProgress = Collections.synchronizedSet(new HashSet<>());

    public IntegrityCheckerProcessor(Context context) {
        this.context = context;
    }

    public void check(Carrier c, File rootFile, String deviceName) {
        if (!devicesInProgress.contains(deviceName)) {
            devicesInProgress.add(deviceName);
            context.cachedExecutor.submit(() -> {
                ProgressListPanel.ProgressEntity progressEntity = new ProgressListPanel.ProgressEntity();
                context.eBus.post(progressEntity);

                progressEntity.setText("Sprawdzam " + deviceName);

                List<Event_Localization> missingLocalizations = new LinkedList<>();

                Event_Localization[] eventLocalizations = c.getEvents(context);
                int i = 0;
                for (Event_Localization el : eventLocalizations) {
                    int percent = (int) (((float) i / (float) eventLocalizations.length) * 100f);
                    progressEntity.setPercent(percent);
                    System.out.println("Checking " + el.getOrLoadFullEvent(context).getName() + " on " + deviceName);

                    try {
                        Validator.validateEventLocalization(context, el);
                    } catch (Validator.MissingOrBrokenFilesException ex) {
                        missingLocalizations.add(el);
                    }

                    i++;
                }
                progressEntity.setText("Zakończono sprawdzanie " + deviceName);
                progressEntity.setPercent(100);
                if (missingLocalizations.isEmpty()) {
                    c.setLastChecked(LocalDateTime.now());
                    c.update(context);
                    progressEntity.markFinished();
                } else {
                    CarrierIntegrityChecker.FilesMissingOnCarrier missingFiles = new CarrierIntegrityChecker.FilesMissingOnCarrier(c, missingLocalizations.toArray(new Event_Localization[]{}));
                    //progressEntity.markError();
                    progressEntity.setText("Uszkodzone pliki na " + deviceName + ".");
                    new RepairWindow(context, missingFiles);
                    context.eBus.post(missingFiles);
                }

                //scheduledExecutor.scheduleAtFixedRate(() -> {
                devicesInProgress.remove(deviceName);
                //}, 10, 10, TimeUnit.SECONDS);
            });
        }
    }

    public void quickCheck(Carrier c, File rootFile, String deviceName) {
        if (!devicesInProgress.contains(deviceName)) {
            devicesInProgress.add(deviceName);
            context.dbManager.executeInDBThread(() -> {
                try {
                    List<Event_Localization> result = context.dbManager.getDaos().getEvent_LocalizationDao().queryBuilder()
                            .where().eq("carrierId", c.getId()).and().eq("needValidation", "1").query();

                    context.cachedExecutor.execute(() -> {
                        ProgressListPanel.ProgressEntity progressEntity = new ProgressListPanel.ProgressEntity();
                        context.eBus.post(progressEntity);

                        List<Event_Localization> missingLocalizations = new LinkedList<>();

                        progressEntity.setText("Szybkie sprawdzanie wydarzeń na " + c.getName());
                        int i = 0;
                        for (Event_Localization el : result) {
                            progressEntity.setText("Szybkie sprawdzanie wydarzeń na " + c.getName() + " (" + i + "/" + result.size() + ")");
                            progressEntity.setPercent((int) ((float) i / (float) result.size() * 100f));
                            if (el.getCarrier(context).isConnected(context)) {
                                try {
                                    Validator.validateEventLocalization(context, el);

                                } catch (Validator.MissingOrBrokenFilesException ex) {
                                    missingLocalizations.add(el);
                                }
                            }
                            i++;
                        }
                        progressEntity.setText("Zakończono szybkie sprawdzanie sprawdzanie " + deviceName);
                        progressEntity.setPercent(100);
                        if (missingLocalizations.isEmpty()) {
                            progressEntity.markFinished();
                        } else {
                            CarrierIntegrityChecker.FilesMissingOnCarrier missingFiles = new CarrierIntegrityChecker.FilesMissingOnCarrier(c, missingLocalizations.toArray(new Event_Localization[]{}));
                            //progressEntity.markError();
                            progressEntity.setText("Uszkodzone pliki na " + deviceName + ".");
                            new RepairWindow(context, missingFiles);
                            context.eBus.post(missingFiles);
                        }
                        devicesInProgress.remove(deviceName);
                    });

                } catch (SQLException ex) {
                    Logger.getLogger(CarrierIntegrityChecker.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        }
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.integritycheck;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.io.MD5Util;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
class IntegrityCheckerProcessor {
    private final Context context;
    private final ExecutorService cachedExecutor = Executors.newCachedThreadPool(new NamingThreadFactory("integritychecker-processor-cached"));
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(new NamingThreadFactory("integritychecker-processor-sched"));
    private final Set<String> devicesInProgress = Collections.synchronizedSet(new HashSet<>());

    public IntegrityCheckerProcessor(Context context) {
        this.context = context;
    }

    public void check(Carrier c, File rootFile, String deviceName) {
        if (!devicesInProgress.contains(deviceName)) {
            devicesInProgress.add(deviceName);
            cachedExecutor.submit(() -> {
                ProgressListPanel.ProgressEntity progressEntity = new ProgressListPanel.ProgressEntity();
                context.eBus.post(progressEntity);

                progressEntity.setText("Sprawdzam " + deviceName);

                List<Event_Localization> missingLocalizations = new LinkedList<>();

                Event_Localization[] eventLocalizations = c.getEvents(context);
                int i = 0;
                for (Event_Localization el : eventLocalizations) {
                    int percent = (int) (((float) i / (float) eventLocalizations.length) * 100f);
                    progressEntity.setPercent(percent);
                    System.out.println("Checking "+el.getOrLoadFullEvent(context).getName()+" on "+deviceName);
                    for (MFile.Localized mfl : el.getOrLoadFullEvent(context).getLocalizedMFiles(context, el)) {
                        if (mfl.getFile() == null || !MD5Util.calculateMD5(mfl.getFile()).equals(mfl.getMFile().getMd5())) {
                            missingLocalizations.add(el);
                            break;
                        }
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
                    progressEntity.markError();
                    progressEntity.setText("Uszkodzone pliki na " + deviceName + ". Kliknij, aby naprawić");
                    progressEntity.setAssociatedPanel(new RepairPanel(context, missingFiles));
                    context.eBus.post(missingFiles);
                }

                scheduledExecutor.scheduleAtFixedRate(() -> {
                    devicesInProgress.remove(deviceName);
                }, 10, 10, TimeUnit.SECONDS);
            });
        }
    }

}

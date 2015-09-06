/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import com.google.common.io.Files;
import com.j256.ormlite.dao.ForeignCollection;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.integritycheck.CarrierIntegrityChecker;
import pl.jblew.cpr.logic.io.Repairer.Solution.TryAgainException;

/**
 *
 * @author teofil
 */
public class Repairer {
    private final CarrierIntegrityChecker.FilesMissingOnCarrier missingFiles;
    private final Context context;

    public Repairer(Context context, CarrierIntegrityChecker.FilesMissingOnCarrier missingFiles) {
        this.context = context;
        this.missingFiles = missingFiles;
    }

    public Map<Event_Localization, Repairer.Solution[]> calculateSolutions() {
        Map<Event_Localization, Repairer.Solution[]> result = new HashMap<>();

        for (Event_Localization missingEl : missingFiles.eventLocalizations) {
            Event event = missingEl.getOrLoadFullEvent(context);

            ForeignCollection<Event_Localization> allLocalizationsOfEvent = event.getLocalizations();
            if (allLocalizationsOfEvent.size() > 1) {
                result.put(missingEl, allLocalizationsOfEvent.stream().filter(el -> el.getId() != missingEl.getId())
                        .sorted((el1, el2) -> {
                            return el2.getCarrier(context).getLastChecked().compareTo(el1.getCarrier(context).getLastChecked());
                        }).map(el -> {
                            return new Solution(missingEl, "Odzyskaj uszkodzone pliki z " + el.getCarrier(context).getName(),
                            "Czy na pewno chcesz odzyskać uszkodzone pliki z " + el.getCarrier(context).getName()+"?",
                            new RecoverMissingSolution(missingEl, el));
                        }).toArray(Repairer.Solution[]::new));
            } else {
                result.put(missingEl, new Solution[]{
                    new Solution(missingEl, "Usuń brakujące pliki z bazy danych", "Czy na pewno chcesz usunąć uszkodzone pliki?", 
                            new DeleteMissingSolution(missingEl))
                });
            }
        }

        return result;
    }
    
    private class DeleteMissingSolution implements Callable<String> {
        private final Event_Localization el;
        
        DeleteMissingSolution(Event_Localization el) {
            this.el = el;
        }
        
        @Override
        public String call() throws Exception {
            AtomicInteger errors = new AtomicInteger(0);
            Arrays.stream(el.getOrLoadFullEvent(context).getLocalizedMFiles(context, el))
                    .filter(mfl -> mfl.getFile() == null || !mfl.getFile().exists() || !MD5Util.calculateMD5(mfl.getFile()).equals(mfl.getMFile().getMd5()))
                    .forEachOrdered((mfl) -> {
                        try {
                            mfl.getMFile().delete(context);
                        } catch(Exception e) {
                            System.out.println("Could not delete mfile: "+e);
                            errors.incrementAndGet();
                        }
                    });
            return "Usunięto "+(errors.get() == 0? "wszystkie" : "nie wszystkie (Błąd przy usuwaniu "+errors.get()+" plików)")+" uszkodzone pliki";
        }
    }
    
    private class RecoverMissingSolution implements Callable<String> {
        private final Event_Localization el;
        private final Event_Localization recoverySourceLocalization;
        
        RecoverMissingSolution(Event_Localization el, Event_Localization recoverySourceLocalization) {
            this.el = el;
            this.recoverySourceLocalization = recoverySourceLocalization;
        }
        
        @Override
        public String call() throws Exception {
            File root = context.deviceDetector.getDeviceRoot(recoverySourceLocalization.getCarrier(context).getName());
            if(root == null || !root.exists() || !root.canRead()) {
                throw new TryAgainException(new RuntimeException("Podłącz nośnik \""+recoverySourceLocalization.getCarrier(context).getName()+"\""));
            }
            
            MFile.Localized [] localizedTargetMFiles = recoverySourceLocalization.getOrLoadFullEvent(context).getLocalizedMFiles(context, recoverySourceLocalization);
            
            AtomicInteger errors = new AtomicInteger(0);
            Arrays.stream(el.getOrLoadFullEvent(context).getLocalizedMFiles(context, el))
                    .filter(mfl -> mfl.getFile() == null || !mfl.getFile().exists() || !MD5Util.calculateMD5(mfl.getFile()).equals(mfl.getMFile().getMd5()))
                    .forEachOrdered((mfl) -> {
                        try {
                           File recoveryFile = Arrays.stream(localizedTargetMFiles).filter(recoveryMfl -> mfl.getMFile().getId() == recoveryMfl.getMFile().getId()).findFirst()
                                   .orElseThrow(() -> new RuntimeException("Recovery file not found (1)")).getFile();
                           
                           if(recoveryFile != null && recoveryFile.exists() && recoveryFile.canRead()) {
                               File properFile = mfl.getMFile().getFile(context, el.getOrLoadFullEvent(context), recoverySourceLocalization);
                               Files.copy(recoveryFile, properFile);
                           }
                           else throw new RuntimeException("Recovery file not found (2)");
                        } catch(Exception e) {
                            System.out.println("Could not recover mfile: "+e);
                            errors.incrementAndGet();
                        }
                    });
            return "Odzyskano "+(errors.get() == 0? "wszystkie" : "nie wszystkie (Błąd przy odzyskiwaniu "+errors.get()+" plików)")+" uszkodzone pliki";
        }
    }

    public static class Solution {
        public final Event_Localization eventLocalization;
        public final String name;
        public final String ask;
        public final Callable<String> task;

        Solution(Event_Localization eventLocalization, String name, String ask, Callable<String> task) {
            this.eventLocalization = eventLocalization;
            this.name = name;
            this.ask = ask;
            this.task = task;
        }
        
        public static class TryAgainException extends Exception {
            public TryAgainException(Exception target) {
                super(target);
            }
        }
    }
}

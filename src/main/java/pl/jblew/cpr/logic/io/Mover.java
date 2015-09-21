/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.windows.MoveWindow;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.MFile;

/**
 *
 * @author teofil
 */
public class Mover {
    private final Context context;
    private final Event sourceEvent;
    private final MFile[] mfilesToMove;
    private final AtomicReference<Event> targetEvent = new AtomicReference<>(null);
    private final AtomicReference<Stage> currentStage = new AtomicReference<Stage>(Stage.WAIT_FOR_SOURCE_DEVICE);
    private final Set<Event_Localization> leftTargetLocalizationsToWrite = Collections.synchronizedSet(new HashSet<Event_Localization>());
    private final Set<Event_Localization> leftSourceLocalizationsToDelete = Collections.synchronizedSet(new HashSet<Event_Localization>());
    private final AtomicBoolean databaseUpdated = new AtomicBoolean(false);
    private final AtomicBoolean cleanupDone = new AtomicBoolean(false);

    public Mover(Context context, Event sourceEvent, MFile[] mfilesToMove) {
        this.context = context;
        this.sourceEvent = sourceEvent;
        this.mfilesToMove = mfilesToMove;

    }

    public Event getSourceEvent() {
        return sourceEvent;
    }

    public MFile[] getMfilesToMove() {
        return mfilesToMove;
    }

    public Event getTargetEvent() {
        return targetEvent.get();
    }

    public void setTargetEvent(Event targetEvent) {
        this.targetEvent.set(targetEvent);
    }

    public Step getNextStep() {
        Stage s = currentStage.get();
        System.out.println("getNextStep() stage=" + s.name() + "; leftTargetLocalizationsToWrite.size()=" + leftTargetLocalizationsToWrite.size() + ";"
                + " leftSourceLocalizationsToDelete.size()=" + leftSourceLocalizationsToDelete.size());
        if (s == Stage.WAIT_FOR_SOURCE_DEVICE) {
            Carrier[] connectedSourceDevices = context.deviceDetector.getConnectedOfCarriers(sourceEvent.getLocalizations().stream().map(el -> el.getCarrier(context)).toArray(Carrier[]::new));
            if (connectedSourceDevices.length > 0) {
                currentStage.set(Stage.WRITE_TARGET_DEVICE);
                leftSourceLocalizationsToDelete.addAll(sourceEvent.getLocalizations());
                leftTargetLocalizationsToWrite.addAll(targetEvent.get().getLocalizations());
                return getNextStep();
            } else {
                return new Step("Podłącz którekolwiek z urządzeń źródłowych: " + sourceEvent.getLocalizations().stream().map(el -> el.getCarrier(context).getName()).reduce("", (a, b) -> a + ", " + b), true);
            }
        } else if (s == Stage.WRITE_TARGET_DEVICE) {
            if (!leftTargetLocalizationsToWrite.isEmpty()) {
                Carrier[] targetCarriers = context.deviceDetector.getConnectedOfCarriers(leftTargetLocalizationsToWrite.stream().map(el -> el.getCarrier(context)).toArray(Carrier[]::new));
                if (targetCarriers.length > 0) {
                    Carrier sourceCarrier = context.deviceDetector.getConnectedOfCarriers(sourceEvent.getLocalizations().stream().map(el -> el.getCarrier(context)).toArray(Carrier[]::new))[0];
                    Carrier carrierToCopy = targetCarriers[0];

                    Event_Localization sourceLocalization = sourceEvent.getLocalizations().stream().filter((Event_Localization el) -> el.getCarrierId() == sourceCarrier.getId()).findFirst().get();
                    Event_Localization targetLocalization = targetEvent.get().getLocalizations().stream().filter((Event_Localization el) -> el.getCarrierId() == carrierToCopy.getId()).findFirst().get();
                    return new Step("Kopiowanie do " + carrierToCopy.getName(), (MoveWindow.ProgressChangedCallback callback) -> {
                        if (writeFiles(carrierToCopy, callback, sourceLocalization, targetLocalization)) {
                            leftTargetLocalizationsToWrite.remove(targetLocalization);
                            callback.progressChanged(100, "Gotowe!", false);
                        }
                    });
                } else {
                    return new Step("Podłącz którekolwiek z urządzeń docelowych: " + leftTargetLocalizationsToWrite.stream().map(el -> el.getCarrier(context).getName()).reduce("", (a, b) -> a + ", " + b), true);
                }
            } else {
                currentStage.set(Stage.DELETE_FROM_SOURCE_DEVICE);
                return getNextStep();
            }
        } else if (s == Stage.DELETE_FROM_SOURCE_DEVICE) {
            if (!leftSourceLocalizationsToDelete.isEmpty()) {
                Carrier carrierToDelete = context.deviceDetector.getConnectedOfCarriers(leftSourceLocalizationsToDelete.stream().map(el -> el.getCarrier(context)).toArray(Carrier[]::new))[0];
                if (carrierToDelete != null) {
                    Event_Localization sourceLocalization = sourceEvent.getLocalizations().stream().filter((Event_Localization el) -> el.getCarrierId() == carrierToDelete.getId()).findFirst().get();
                    return new Step("Usuwanie z " + carrierToDelete.getName(), (MoveWindow.ProgressChangedCallback callback) -> {
                        if (deleteFiles(carrierToDelete, callback, sourceLocalization)) {
                            leftSourceLocalizationsToDelete.remove(sourceLocalization);
                            callback.progressChanged(100, "Gotowe!", false);
                        }
                    });
                } else {
                    return new Step("Podłącz którekolwiek z urządzeń źródłowych: " + leftSourceLocalizationsToDelete.stream().map(el -> el.getCarrier(context).getName()).reduce("", (a, b) -> a + ", " + b), true);
                }
            } else {
                currentStage.set(Stage.UPDATE_DB);
                return getNextStep();
            }
        } else if (s == Stage.UPDATE_DB) {
            if (!databaseUpdated.get()) {
                return new Step("Aktualizowanie bazy danych ", (MoveWindow.ProgressChangedCallback callback) -> {
                    updateDB(callback);
                    databaseUpdated.set(true);
                    currentStage.set(Stage.DO_CLEANUP);
                    callback.progressChanged(100, "Gotowe!", false);
                });
            } else {
                currentStage.set(Stage.DO_CLEANUP);
                return getNextStep();
            }
        } else if (s == Stage.DO_CLEANUP) {
            if (!cleanupDone.get()) {
                return new Step("Sprzątanie", (MoveWindow.ProgressChangedCallback callback) -> {
                    doCleanup(callback);
                    cleanupDone.set(true);
                    callback.progressChanged(100, "Gotowe!", false);
                });
            } else {
                return null;//EVERYTHING DONE!
            }
        } else {
            throw new RuntimeException("Unknown stage: " + s);
        }
    }

    private boolean writeFiles(Carrier targetCarrier, MoveWindow.ProgressChangedCallback callback, Event_Localization sourceLocalization, Event_Localization targetLocalization) {
        System.out.println("Writing files...");

        System.out.println(mfilesToMove.length + " plików do skopiowania");

        for (int i = 0; i < mfilesToMove.length; i++) {
            MFile sourceMFile = mfilesToMove[i];
            File sourceFile = mfilesToMove[i].getFile(context, sourceLocalization);
            File targetFile = mfilesToMove[i].getFile(context, targetLocalization);

            System.out.println("sourceFile=" + sourceFile + ", targetFile=" + targetFile);

            float percentF = ((float) i) / ((float) mfilesToMove.length);
            int percent = (int) (percentF * 100f);
            if (percent == 100) {
                percent = 99;
            }

            try {
                File parent = targetFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                
                if(targetFile.exists() && !MD5Util.calculateMD5(targetFile).equals(sourceMFile.getMd5())) {
                    targetFile.delete();
                }
                
                if(!targetFile.exists()) {
                System.out.println("Kopiowanie pliku z " + sourceFile.toPath() + " do " + targetFile.toPath());
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                ThumbnailLoader.loadThumbnail(targetFile, true, null);
                }
                if (callback != null) {
                    callback.progressChanged(percent, "Kopiowanie " + i + "/" + mfilesToMove.length + " (" + percent + "%)", false);
                }
            } catch (IOException ex) {
                Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, "Błąd przy kopiowaniu plików", ex);
                callback.progressChanged(percent, " Błąd przy kopiowaniu pliku: " + i + "/" + mfilesToMove.length + ": " + ex.getLocalizedMessage(), true);
                return false;
            }
        }

        /*try {
         context.dbManager.executeInDBThreadAndWait(() -> {
         try {
         Event tempEvent = new Event();
         tempEvent.setType(targetEvent.get().getType());
         tempEvent.setName(targetEvent.get().getName() + "." + targetCarrier.getName() + "-" + DateTimeFormatter.ofPattern("YYYY.MM.dd-HH.mm.ss.SSS").format(LocalDateTime.now()));
         context.dbManager.getDaos().getEventDao().create(tempEvent);
         tempEvents.add(tempEvent);
         System.out.println("Dodano tymczasowe wydarzenie: " + tempEvent);

         Event_Localization tempEl = new Event_Localization();
         tempEl.setCarrierId(targetCarrier.getId());
         tempEl.setEvent(tempEvent);
         tempEl.setDirName(tempEvent.getName());
         context.dbManager.getDaos().getEvent_LocalizationDao().create(tempEl);
         System.out.println("Dodano lokalizację tymczasowego wydarzenia: " + tempEl);

         for (MFile mf : mfilesToMove) {
         mf.setEvent(tempEvent);
         context.dbManager.getDaos().getMfileDao().update(mf);
         System.out.println("   Połączono MFile do tymczasowego wydarzenia: " + mf);
         }
         } catch (SQLException ex) {
         Logger.getLogger(Mover.class.getName()).log(Level.SEVERE, null, ex);
         }
         System.out.println("Koniec kopiowania plików");
         });
         } catch (InterruptedException ex) {
         Logger.getLogger(Mover.class.getName()).log(Level.SEVERE, null, ex);
         return false;
         }*/
        return true;
    }

    private boolean deleteFiles(Carrier targetCarrier, MoveWindow.ProgressChangedCallback callback, Event_Localization sourceLocalization) {
        System.out.println("Usuwanie plików");
        for (int i = 0; i < mfilesToMove.length; i++) {
            MFile sourceMFile = mfilesToMove[i];
            File sourceFile = sourceMFile.getFile(context, sourceLocalization);

            float percentF = ((float) i) / ((float) mfilesToMove.length);
            int percent = (int) (percentF * 100f);
            if (percent == 100) {
                percent = 99;
            }

            try {
                System.out.println("Usuwam " + sourceFile.toPath());
                Files.delete(sourceFile.toPath());

                if (sourceFile.getParentFile().listFiles((f, name) -> !name.startsWith(".")).length == 0) {
                    try {
                        sourceFile.getParentFile().delete();
                    } catch (Exception ex) {
                        Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, "Nie można usunąć pustego katalogu " + sourceFile.getParentFile(), ex);
                    }
                }

                if (callback != null) {
                    callback.progressChanged(percent, "Usuwanie " + i + "/" + mfilesToMove.length + " (" + percent + "%)", false);
                }
            } catch (IOException ex) {
                Logger.getLogger(Exporter.class.getName()).log(Level.SEVERE, "Błąd przy usuwaniu plików", ex);
                callback.progressChanged(percent, " Błąd przy usuwaniu pliku: " + i + "/" + mfilesToMove.length + ": " + ex.getLocalizedMessage(), true);
                return false;
            }
        }

        return true;
    }

    private void updateDB(MoveWindow.ProgressChangedCallback callback) {
        try {
            context.dbManager.executeInDBThreadAndWait(() -> {

                callback.progressChanged(50, "Linkuję zdjęcia do nowego wydarzenia", false);

                for (MFile mf : mfilesToMove) {
                    mf.setEvent(targetEvent.get());
                    try {
                        context.dbManager.getDaos().getMfileDao().update(mf);
                    } catch (SQLException ex) {
                        Logger.getLogger(Mover.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                targetEvent.get().update(context);

                callback.progressChanged(100, "Gotowe!", false);
            });
        } catch (InterruptedException ex) {
            Logger.getLogger(Mover.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void doCleanup(MoveWindow.ProgressChangedCallback callback) {
        try {
            CountDownLatch awaitDelete = new CountDownLatch(1);
            if (sourceEvent.getLocalizedMFiles(context).length == 0) {
                sourceEvent.delete(context, () -> {
                    awaitDelete.countDown();
                });
            }
            awaitDelete.await();

            callback.progressChanged(100, "Gotowe!", false);
        } catch (InterruptedException ex) {
            Logger.getLogger(Mover.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private enum Stage {
        WAIT_FOR_SOURCE_DEVICE, WRITE_TARGET_DEVICE, DELETE_FROM_SOURCE_DEVICE, UPDATE_DB, DO_CLEANUP
    }

    public static class Step {
        public final String msg;
        public final Processable processable;
        public final boolean notifyOnNewDevice;

        public Step(String msg) {
            this.msg = msg;
            this.processable = null;
            this.notifyOnNewDevice = false;
            System.out.println("Constructing step: msg=" + msg + ", processable=" + processable + ", notifyOnNewDevice=" + notifyOnNewDevice);
        }

        public Step(String msg, Processable processable) {
            this.msg = msg;
            this.processable = processable;
            this.notifyOnNewDevice = false;
            System.out.println("Constructing step: msg=" + msg + ", processable=" + processable + ", notifyOnNewDevice=" + notifyOnNewDevice);
        }

        public Step(String msg, boolean notifyOnNewDevice) {
            this.msg = msg;
            this.processable = null;
            this.notifyOnNewDevice = notifyOnNewDevice;
            System.out.println("Constructing step: msg=" + msg + ", processable=" + processable + ", notifyOnNewDevice=" + notifyOnNewDevice);
        }

        public boolean isProcessable() {
            return processable != null;
        }
    }

    public static interface Processable {
        public void process(MoveWindow.ProgressChangedCallback callback);
    }

    public static interface DeviceWaiter {
        public boolean isSatisfied();
    }
}

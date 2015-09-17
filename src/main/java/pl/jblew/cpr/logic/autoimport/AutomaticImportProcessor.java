/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.autoimport;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.io.Importer;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public class AutomaticImportProcessor {
    private final Context context;
    private final ExecutorService cachedExecutor = Executors.newCachedThreadPool(new NamingThreadFactory("autoimport-processor"));
    private final List<String> pathsBeingProcessed = new LinkedList<>();

    public AutomaticImportProcessor(Context context) {
        this.context = context;
    }

    public void process(Path p, String deviceName) {
        synchronized (pathsBeingProcessed) {
            if (!pathsBeingProcessed.contains(p.toString())) {
                pathsBeingProcessed.add(p.toString());

                cachedExecutor.submit(() -> {
                    File topDir = p.toFile();

                    if (topDir.exists() && topDir.canRead()) {
                        doProcess(topDir, deviceName);
                    }

                    pathsBeingProcessed.remove(p.toString());
                });
            }
        }
    }

    private void doProcess(File topDir, String deviceName) {
        String topEventName = "Import z" + deviceName;

        List<File> allImportableFiles = new LinkedList<>();
        walkDir(allImportableFiles, topDir);

        Map<String, List<File>> eventMapping = new HashMap<>();

        /**
         * CALCULATING BASE EVENT NAME (WITHOUT DATE) *
         */
        for (File f : allImportableFiles) {
            String baseEventName = topEventName;
            if (f.getParentFile().equals(topDir)) {
                baseEventName = topEventName;
            } else {
                baseEventName = f.getParentFile().getName();
            }

            //process event name
            baseEventName = baseEventName.trim();
            if (baseEventName.matches("^\\[[0-9\\.\\-]+\\](.*)")) {
                baseEventName = baseEventName.substring(baseEventName.indexOf("]") + 1).trim();
            }

            //baseEventName = Event.formatName(LocalDateTime.now(), baseEventName);

            /*while(Event.forName(context, eventName) != null) {
             eventName += "|";
             }*/
            if (!eventMapping.containsKey(baseEventName)) {
                eventMapping.put(baseEventName, new LinkedList<>());
            }
            eventMapping.get(baseEventName).add(f);
        }

        /**
         * ADD DATE TO EVENT NAME *
         */
        /*for (Entry<String, List<File>> entry : eventMapping.entrySet()) {
            LocalDateTime[] timeBounds = entry.getValue().stream()
                    .map(f -> ImageCreationDateLoader.getCreationDateTime(f)).sorted().toArray(LocalDateTime[]::new);

            LocalDateTime startDT = timeBounds[0];
            LocalDateTime endDT = timeBounds[timeBounds.length - 1];

            String oldEventName = entry.getKey();
            String newEventName = TimeUtils.formatDateRange(startDT, endDT) + " " + oldEventName;

            eventMapping.remove(oldEventName);
            eventMapping.put(newEventName, entry.getValue());
        }*/

        for (String eventName : eventMapping.keySet()) {
            List<File> files = eventMapping.get(eventName);

            ProgressListPanel.ProgressEntity progressEntity = new ProgressListPanel.ProgressEntity();
            context.eBus.post(progressEntity);

            progressEntity.setText("Importuję " + eventName);

            Importer importer = new Importer(context, files.toArray(new File[]{}));
            importer.setDeleteAfterAdd(true);
            File deviceRoot = context.deviceDetector.getDeviceRoot(deviceName);
            try {
                importer.tryDevice(context, deviceName, deviceRoot);
                importer.setDevice(deviceName, deviceRoot);

                importer.setEventName(eventName);

                CountDownLatch cdLatch = new CountDownLatch(1);
                importer.startAsync((final int percent, final String msg, boolean error) -> {
                    SwingUtilities.invokeLater(() -> {
                        progressEntity.setPercent(percent);
                        progressEntity.setText("(Import) " + msg);

                        if (percent == 100) {
                            progressEntity.markFinished();
                            cdLatch.countDown();
                        } else if (error) {
                            JOptionPane.showMessageDialog(null, "Błąd podczas importowania " + eventName + " na " + deviceName + ": " + msg, "Błąd", JOptionPane.ERROR_MESSAGE);
                            cdLatch.countDown();
                        }
                    });
                });
                cdLatch.await(); //make it synchronous again

            } catch (Importer.DeviceNotWritableException ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Nie można importować " + eventName + " na " + deviceName + ": Urządzenie jest niezapisywalne.", "Błąd", JOptionPane.ERROR_MESSAGE);
                    progressEntity.markFinished();
                });
            } catch (Importer.NotACarrierException ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Nie można importować " + eventName + " na " + deviceName + ": Urządzenie nie jest nośnikiem (wejdź w panel urządzenia i kliknij \"Dodaj jako nośnik\", aby móc importować pliki na to urządzenie.", "Błąd", JOptionPane.ERROR_MESSAGE);
                    progressEntity.markFinished();
                });
            } catch (Importer.NotEnoughSpaceException ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Nie można importować " + eventName + " na " + deviceName + ": Zbyt mało miejsca na tym urządzeniu.", "Błąd", JOptionPane.ERROR_MESSAGE);
                    progressEntity.markFinished();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Nie można importować " + eventName + " na " + deviceName + ": Nieznany błąd: " + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                    progressEntity.markFinished();
                });
            }
        }

    }

    private void walkDir(List<File> allFiles, File f) {
        if (f.canRead()) {
            if (f.isDirectory()) {
                for (File child : f.listFiles((File dir, String name) -> !name.startsWith("."))) {
                    walkDir(allFiles, child);
                }
            } else {
                if (f.canRead() && Importer.checkIfFileSupported(f)) {
                    allFiles.add(f);
                }
            }
        }
    }
}

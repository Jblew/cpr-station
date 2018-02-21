/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.dircrawler;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.windows.CrawlerWindow;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.autoimport.AutomaticImportProcessor;
import pl.jblew.cpr.logic.io.Importer;

/**
 *
 * @author teofil
 */
public class DirCrawler {
    private final Context context;
    private final File deviceRoot;
    private final AtomicReference<CrawlerWindow.ProgressChangedCallback> callbackRef = new AtomicReference<>(null);
    private final LinkedBlockingQueue<File> dirList = new LinkedBlockingQueue<>();

    public DirCrawler(Context context, File deviceRoot) {
        this.context = context;
        this.deviceRoot = deviceRoot;
    }

    public void crawlAsync(Carrier targetCarrier) {
        context.cachedExecutor.execute(() -> {
            Logger.getLogger(getClass().getName()).info("Starting async crawler...");
            callBack(0, 100, "Ładowanie crawlera...", "Ładowanie crawlera...", false);

            if (deviceRoot == null || !deviceRoot.exists() || !deviceRoot.canRead()) {
                callBack(0, 0, "Nie można odczytać lokalizacji", "Nie można odczytać " + deviceRoot.getAbsolutePath(), true);
                return;
            }

            callBack(0, 100, "Skanowanie listy katalogów", "Skanowanie listy katalogów", false);
            crawlInto(deviceRoot);
            int numOfDirs = dirList.size();
            callBack(0, numOfDirs, "Skanowanie listy katalogów", "Katalogów do przeskanowania: " + dirList.size(), false);

            int unimportedCount = 0;

            for (int i = 0; i < numOfDirs; i++) {
                File parent = dirList.poll();
                String inlinelog = "[" + i + "/" + numOfDirs + "]>\"" + parent.getAbsolutePath() + "\"> ";

                System.out.println(inlinelog);
                
                int unimportedDirCount = 0;
                
                for (File child : parent.listFiles()) {
                    if (!child.isDirectory() && child.canRead()) {
                        if (Importer.checkIfFileSupported(child) && !MFile.checkIfImported(context, child)) {
                            unimportedCount++;unimportedDirCount++;
                            System.out.println(child.getAbsolutePath());

                            if (targetCarrier != null) {
                                importFile(child, targetCarrier);
                            }
                        }
                    }
                }
                inlinelog += "<; znaleziono: "+unimportedDirCount+"; w sumie: " + unimportedCount + ">";
                System.out.println(inlinelog);
                callBack(i, numOfDirs, "(" + i + "/" + numOfDirs + ") Znaleziono: " + unimportedCount + "; Skanowanie " + parent.getAbsolutePath(), inlinelog, false);

            }
            callBack(numOfDirs, numOfDirs, "Skończono skanowanie: Znaleziono " + unimportedCount + " niezaimportowanych plików",
                    "Skończono skanowanie: Znaleziono " + unimportedCount + " niezaimportowanych plików", false);

        });
    }

    private void importFile(File f, Carrier targetCarrier) {
        String eventName = AutomaticImportProcessor.calculateEventName(f, deviceRoot.getName(), deviceRoot);
        System.out.println("Importowanie "+f+" do "+eventName);

        Importer importer = new Importer(context, new File[]{f});
        importer.setDeleteAfterAdd(false);
        File deviceRoot = context.deviceDetector.getDeviceRoot(targetCarrier.getName());
        try {
            importer.tryDevice(context, targetCarrier.getName(), deviceRoot);
            importer.setDevice(targetCarrier.getName(), deviceRoot);

            importer.setEventName(eventName);

            CountDownLatch cdLatch = new CountDownLatch(1);
            importer.startAsync((int value, int maximum, String msg, boolean error) -> {
                SwingUtilities.invokeLater(() -> {
                    if (value == maximum) {
                        cdLatch.countDown();
                    } else if (error) {
                        JOptionPane.showMessageDialog(null, "Błąd podczas importowania " + eventName + " na " + deviceRoot.getName() + ": " + msg, "Błąd", JOptionPane.ERROR_MESSAGE);
                        cdLatch.countDown();
                    }
                });
            });
            cdLatch.await(); //make it synchronous again

        } catch (Importer.DeviceNotWritableException ex) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Nie można importować " + eventName + " na " + deviceRoot.getName() + ": Urządzenie jest niezapisywalne.", "Błąd", JOptionPane.ERROR_MESSAGE);
            });
        } catch (Importer.NotACarrierException ex) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Nie można importować " + eventName + " na " + deviceRoot.getName() + ": Urządzenie nie jest nośnikiem (wejdź w panel urządzenia i kliknij \"Dodaj jako nośnik\", aby móc importować pliki na to urządzenie.", "Błąd", JOptionPane.ERROR_MESSAGE);
            });
        } catch (Importer.NotEnoughSpaceException ex) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Nie można importować " + eventName + " na " + deviceRoot.getName() + ": Zbyt mało miejsca na tym urządzeniu.", "Błąd", JOptionPane.ERROR_MESSAGE);
            });
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Nie można importować " + eventName + " na " + deviceRoot.getName() + ": Nieznany błąd: " + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void crawlInto(File dir) {
        try {
            //System.out.println("put dir " + dir);
            dirList.put(dir);
        } catch (InterruptedException ex) {
            Logger.getLogger(DirCrawler.class.getName()).log(Level.SEVERE, null, ex);
        }

        for (File f : dir.listFiles()) {
            if (!f.getName().startsWith(".") && !f.getName().equals("$RECYCLE.BIN")) {
                if (f.isDirectory() && f.canRead()) {
                    crawlInto(f);
                }
            }
        }
    }

    private void callBack(int value, int maximum, String barMsg, String logMsg, boolean error) {
        CrawlerWindow.ProgressChangedCallback callback = callbackRef.get();
        if (callback != null) {
            callback.progressChanged(value, maximum, barMsg, logMsg, error);
        }
    }

    public void setCallback(CrawlerWindow.ProgressChangedCallback callback) {
        this.callbackRef.set(callback);
    }
}

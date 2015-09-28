/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.bootstrap;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.db.DatabaseDetector;
import pl.jblew.cpr.db.DatabaseManager;
import pl.jblew.cpr.logic.autoimport.AutomaticImportListener;
import pl.jblew.cpr.file.DeviceDetectorProcess;
import pl.jblew.cpr.gui.GUI;
import pl.jblew.cpr.logic.integritycheck.CarrierIntegrityChecker;
import pl.jblew.cpr.util.MessageToStatusBar;
import pl.jblew.cpr.util.NamingThreadFactory;
import pl.jblew.cpr.util.PrintableBusMessage;
import pl.jblew.cpr.util.log.LogManager;

/**
 *
 * @author teofil
 */
public class Bootstrap {
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final LinkedBlockingQueue<Runnable> shutdownTasks = new LinkedBlockingQueue<Runnable>();

    public void synchronousStart() {
        EventBus mainBus = setupEventBus();
        LogManager logMgr = new LogManager();
        DatabaseManager dbManager = new DatabaseManager(mainBus);
        DeviceDetectorProcess deviceDetectorProcess = new DeviceDetectorProcess(mainBus);
        JFrame frame = createFrame();
        
        Context context = createContextAndGUI(mainBus, logMgr, dbManager, deviceDetectorProcess, frame);

        mainBus.post(new MessageToStatusBar("Czekam na polecenia", MessageToStatusBar.Type.INFO));

        awaitShutdown();
        doShutdown(context);
    }

    public void addShutdownTask(Runnable r) {
        shutdownTasks.add(r);
    }

    private void shutdown() {
        Logger.getLogger(getClass().getName()).info("[SHUTDOWN] Shutdown unlocked");
        shutdownLatch.countDown();
    }
    
    private EventBus setupEventBus() {
        EventBus mainBus = new EventBus();
        mainBus.register(new Object() {
            @Subscribe
            public void printableMessageArrivedOnBus(PrintableBusMessage e) {
                Logger.getLogger(Bootstrap.class.getName()).info(e.toString());
            }
        });
        
        return mainBus;
    }
    
    private JFrame createFrame() {
        JFrame frame = new JFrame();
        SwingUtilities.invokeLater(() -> {
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    frame.setTitle("Zamykanie...");
                    shutdown();
                }
            });
        });
        return frame;
    }
    
    private Context createContextAndGUI(EventBus mainBus, LogManager logMgr, DatabaseManager dbManager, DeviceDetectorProcess deviceDetectorProcess, JFrame frame) {
        Context context = new Context(mainBus, logMgr, dbManager, deviceDetectorProcess, Executors.newCachedThreadPool(new NamingThreadFactory("main-cached-executor")), frame, this);
        CarrierIntegrityChecker integrityChecker = new CarrierIntegrityChecker(context);
        context.integrityChecker = integrityChecker;
        
        GUI gui = new GUI(context);
        gui.start();

        gui.executeWhenLoaded(() -> {
            context.deviceDetector.addStorageDevicePresenceListener(gui.getTreePanel().getDevicesNode());
            context.deviceDetector.addStorageDevicePresenceListener(gui.getTreePanel().getCarriersNode());
            context.deviceDetector.addStorageDevicePresenceListener(new AutomaticImportListener(context));
            context.deviceDetector.addStorageDevicePresenceListener(integrityChecker);
            context.deviceDetector.start();
        });

        DatabaseDetector dbDetector = new DatabaseDetector();
        dbDetector.addDatabaseDetectedListener(dbManager);
        deviceDetectorProcess.addStorageDevicePresenceListener(dbDetector);
        
        return context;
    }
    
    private void awaitShutdown() {
        //WE NEED TO PREVENT exitting main method, because this starts "DestroyJavaVM" Thread
        try {
            shutdownLatch.await();
        } catch (InterruptedException ex) {
            Logger.getLogger(Bootstrap.class.getName()).log(Level.SEVERE, "Awaiting on shutdown latch was interrupted", ex);
        }
    }
    
    private void doShutdown(Context context) {
        Logger.getLogger(getClass().getName()).info("[SHUTDOWN] Performing basic shutdown tasks");
        //basic shutdown tasks
        context.dbManager.shutdown();
        context.deviceDetector.shutdown();
        
        SwingUtilities.invokeLater(() -> {
            context.frame.setVisible(false);
        });

        int i = 0;
        for (Runnable r : shutdownTasks) {
            try {
                Logger.getLogger(getClass().getName()).info("[SHUTDOWN " + i + "] Starting");
                r.run();
                Logger.getLogger(getClass().getName()).info("[SHUTDOWN " + i + "] Done");
                i++;
            } catch (Exception ex) {
                Logger.getLogger(Bootstrap.class.getName()).log(Level.SEVERE, "Exception in shutdown task", ex);

            }
        }
        
        context.cachedExecutor.shutdown();
        try {
            context.cachedExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(Bootstrap.class.getName()).log(Level.SEVERE, null, ex);
        }
        context.cachedExecutor.shutdownNow();
        
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException ex) {
            Logger.getLogger(Bootstrap.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.exit(1);
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.bootstrap;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javax.swing.JFrame;
import pl.jblew.cpr.db.DatabaseDetector;
import pl.jblew.cpr.db.DatabaseManager;
import pl.jblew.cpr.file.DeviceDetectorProcess;
import pl.jblew.cpr.gui.GUI;
import pl.jblew.cpr.util.MessageToStatusBar;
import pl.jblew.cpr.util.PrintableBusMessage;

/**
 *
 * @author teofil
 */
public class Bootstrap {
    public void synchronousStart() {
        EventBus mainBus = new EventBus();
        mainBus.register(new Object() {
            @Subscribe
            public void printableMessageArrivedOnBus(PrintableBusMessage e) {
                System.out.println(e);
            }
        });
        
        DatabaseManager dbManager = new DatabaseManager(mainBus);
        
        DeviceDetectorProcess deviceDetectorProcess = new DeviceDetectorProcess(mainBus);
        
        final Context context = new Context(mainBus, dbManager, deviceDetectorProcess, new JFrame());
        
        final GUI gui = new GUI(context);
        gui.start();
        
        gui.executeWhenLoaded(() -> {
            context.deviceDetector.addStorageDevicePresenceListener(gui.getTreePanel().getDevicesNode());
            context.deviceDetector.addStorageDevicePresenceListener(gui.getTreePanel().getCarriersNode());
            context.deviceDetector.start();
        });
        
        
        
        DatabaseDetector dbDetector = new DatabaseDetector();
        dbDetector.addDatabaseDetectedListener(dbManager);
        deviceDetectorProcess.addStorageDevicePresenceListener(dbDetector);
        
        mainBus.post(new MessageToStatusBar("Czekam na polecenia", MessageToStatusBar.Type.INFO));
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.bootstrap;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
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

        GUI gui = new GUI(mainBus);
        gui.start();
        
        DeviceDetectorProcess deviceDetectorProcess = new DeviceDetectorProcess(mainBus);
        deviceDetectorProcess.addStorageDevicePresenceListener(gui.getTreePanel().getDevicesNode());
        deviceDetectorProcess.start();
        
        mainBus.post(new MessageToStatusBar("Czekam na polecenia", MessageToStatusBar.Type.INFO));
    }
}

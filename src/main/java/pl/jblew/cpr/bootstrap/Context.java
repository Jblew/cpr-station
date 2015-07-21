/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.bootstrap;

import com.google.common.eventbus.EventBus;
import javax.swing.JFrame;
import pl.jblew.cpr.db.DatabaseManager;
import pl.jblew.cpr.file.DeviceDetectorProcess;

/**
 *
 * @author teofil
 */
public class Context {
    public final EventBus eBus;
    public final DatabaseManager dbManager;
    public final DeviceDetectorProcess deviceDetector;
    public final JFrame frame;

    Context(EventBus eBus, DatabaseManager dbManager, DeviceDetectorProcess deviceDetector, JFrame frame) {
        this.eBus = eBus;
        this.dbManager = dbManager;
        this.deviceDetector = deviceDetector;
        this.frame = frame;
    }
}

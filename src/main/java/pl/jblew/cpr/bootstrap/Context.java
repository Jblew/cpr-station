/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.bootstrap;

import com.google.common.eventbus.EventBus;
import java.util.concurrent.ExecutorService;
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
    public final ExecutorService cachedExecutor;
    public final JFrame frame;
    public final Bootstrap bootstrap;

    Context(EventBus eBus, DatabaseManager dbManager, DeviceDetectorProcess deviceDetector, ExecutorService cachedExecutor, JFrame frame, Bootstrap bootstrap) {
        this.eBus = eBus;
        this.dbManager = dbManager;
        this.deviceDetector = deviceDetector;
        this.cachedExecutor = cachedExecutor;
        this.frame = frame;
        this.bootstrap = bootstrap;
    }
}

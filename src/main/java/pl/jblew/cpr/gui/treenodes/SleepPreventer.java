/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.treenodes;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public class SleepPreventer {
    private final Context context;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Robot robot;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamingThreadFactory("sleep-preventer"));
    
    public SleepPreventer(Context context) {
        this.context = context;
        
        Robot robot_ = null;
        try {
            robot_ = new Robot();
        } catch (AWTException ex) {
            Logger.getLogger(SleepPreventer.class.getName()).log(Level.SEVERE, null, ex);
        }
        robot = robot_;
        
        context.bootstrap.addShutdownTask(() -> {
            enabled.set(false);
            executor.shutdown();
            executor.shutdownNow();
        });
    }
    
    public void setEnabled(boolean isEnabled) {
        enabled.set(isEnabled);
        
        if(isEnabled) {
            executor.schedule(() -> _do(), 10, TimeUnit.SECONDS);
        }
    }
    
    private void _do() {
        if(robot != null) {
            Point location = MouseInfo.getPointerInfo().getLocation();
            robot.mouseMove(location.x+(Math.random() > 0.5? 1 : -1), location.y+(Math.random() > 0.5? 1 : -1));
        }
        
        if(enabled.get()) {
            executor.schedule(() -> _do(), 10, TimeUnit.SECONDS);
        }
    }
}

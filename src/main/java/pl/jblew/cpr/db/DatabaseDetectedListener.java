/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.db;

import java.io.File;
import pl.jblew.cpr.util.ListenersManager;

/**
 *
 * @author teofil
 */
public interface DatabaseDetectedListener extends ListenersManager.Listener {
    public void databaseDetected(String deviceName, File dbFile);
}

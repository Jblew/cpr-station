/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.file;

import java.io.File;

/**
 *
 * @author teofil
 */
public interface StorageDevicePresenceListener {
    public void storageDeviceConnected(File rootFile, String deviceName);
    public void storageDeviceDisconnected(File rootFile, String deviceName);
}

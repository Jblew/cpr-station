/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.file;

import com.google.common.eventbus.EventBus;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import net.samuelcampos.usbdrivedectector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedectector.USBStorageDevice;
import net.samuelcampos.usbdrivedectector.events.DeviceEventType;
import net.samuelcampos.usbdrivedectector.events.IUSBDriveListener;
import net.samuelcampos.usbdrivedectector.events.USBStorageEvent;
import pl.jblew.cpr.util.MessageToStatusBar;

/**
 *
 * @author teofil
 */
public class DeviceDetectorProcess {
    private final EventBus eBus;
    private final ArrayList<StorageDevicePresenceListener> listeners = new ArrayList<StorageDevicePresenceListener>();

    public DeviceDetectorProcess(EventBus eBus_) {
        eBus = eBus_;
    }

    public void start() {
        USBDeviceDetectorManager detectorManager = new USBDeviceDetectorManager(2 * 1000);
        for (USBStorageDevice device : detectorManager.getRemovableDevices()) {
            System.out.println("USB device detected: \"" + device.getDeviceName() + "\". Writable: " + device.getRootDirectory().canWrite());
            StorageDevicePresenceListener[] tmpList;
            synchronized (listeners) {
                tmpList = listeners.toArray(new StorageDevicePresenceListener[]{});
            }
            for (StorageDevicePresenceListener listener : tmpList) {
                listener.storageDeviceConnected(device.getRootDirectory(), device.getDeviceName());
            }
        }
        detectorManager.addDriveListener(new IUSBDriveListener() {
            @Override
            public void usbDriveEvent(USBStorageEvent usbse) {
                StorageDevicePresenceListener[] tmpList;
                synchronized (listeners) {
                    tmpList = listeners.toArray(new StorageDevicePresenceListener[]{});
                }
                for (StorageDevicePresenceListener listener : tmpList) {
                    if (usbse.getEventType() == DeviceEventType.CONNECTED) {
                        listener.storageDeviceConnected(usbse.getStorageDevice().getRootDirectory(), usbse.getStorageDevice().getDeviceName());
                    } else if (usbse.getEventType() == DeviceEventType.REMOVED) {
                        listener.storageDeviceDisconnected(usbse.getStorageDevice().getRootDirectory(), usbse.getStorageDevice().getDeviceName());
                    }
                }
            }
        });
        /*File[] roots = File.listRoots();

         if (roots == null) {
         eBus.post(new MessageToStatusBar("Nie można odczytać listy urządzeń", MessageToStatusBar.Type.ERROR));
         }

         for(Path p:FileSystems.getDefault().getRootDirectories()) {
         System.out.println("File root detected: \""+p+"\". Writable: "+p.toFile().canWrite());
         }*/
    }

    public void addStorageDevicePresenceListener(StorageDevicePresenceListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeStorageDevicePresenceListener(StorageDevicePresenceListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public void stop() {

    }
}

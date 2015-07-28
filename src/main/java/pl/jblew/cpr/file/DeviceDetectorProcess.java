/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.file;

import com.google.common.eventbus.EventBus;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.samuelcampos.usbdrivedectector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedectector.USBStorageDevice;
import net.samuelcampos.usbdrivedectector.events.DeviceEventType;
import net.samuelcampos.usbdrivedectector.events.IUSBDriveListener;
import net.samuelcampos.usbdrivedectector.events.USBStorageEvent;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class DeviceDetectorProcess {
    private final EventBus eBus;
    private final ArrayList<StorageDevicePresenceListener> listeners = new ArrayList<>();
    private final Map<String, File> devices = new HashMap<>();

    public DeviceDetectorProcess(EventBus eBus_) {
        eBus = eBus_;
    }

    public void start() {
        new Thread(() -> {
            USBDeviceDetectorManager detectorManager = new USBDeviceDetectorManager(2 * 1000);
            for (USBStorageDevice device : detectorManager.getRemovableDevices()) {
                System.out.println("USB device detected: \"" + device.getDeviceName() + "\". Writable: " + device.getRootDirectory().canWrite());
                
                synchronized(devices) {
                    devices.put(device.getDeviceName(), device.getRootDirectory());
                }
                
                StorageDevicePresenceListener[] tmpList;
                synchronized (listeners) {
                    tmpList = listeners.toArray(new StorageDevicePresenceListener[]{});
                }
                for (StorageDevicePresenceListener listener : tmpList) {
                    listener.storageDeviceConnected(device.getRootDirectory(), device.getDeviceName());
                }
            }
            detectorManager.addDriveListener((USBStorageEvent usbse) -> {
                StorageDevicePresenceListener[] tmpList;
                
                synchronized(devices) {
                    if (usbse.getEventType() == DeviceEventType.CONNECTED) {
                        devices.put(usbse.getStorageDevice().getDeviceName(), usbse.getStorageDevice().getRootDirectory());
                    } else if (usbse.getEventType() == DeviceEventType.REMOVED) {
                        devices.remove(usbse.getStorageDevice().getDeviceName());
                    }
                    
                }
                
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
            });
        }).start();
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
    
    public List<TwoTuple<String, File>> getConnectedDevices() {
        synchronized(devices) {
            List<TwoTuple<String, File>> out = new LinkedList<>();
            
            for(String deviceName : devices.keySet()) {
                File rootFile = devices.get(deviceName);
                out.add(new TwoTuple<>(deviceName, rootFile));
            }
            
            return out;
        }
    }
    
    public Carrier [] getConnectedCarriers(Carrier [] carrierList) {
        return Arrays.stream(carrierList).filter(carrier -> (getDeviceRoot(carrier.getName()) != null)).toArray(Carrier[]::new);
    }
    
    public File getDeviceRoot(String deviceName) {
        if(devices.containsKey(deviceName)) return devices.get(deviceName);
        else return null;
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.file;

import com.google.common.eventbus.EventBus;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.samuelcampos.usbdrivedectector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedectector.USBStorageDevice;
import net.samuelcampos.usbdrivedectector.events.DeviceEventType;
import net.samuelcampos.usbdrivedectector.events.USBStorageEvent;
import pl.jblew.cpr.db.DatabaseManager;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.util.NamingThreadFactory;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class DeviceDetectorProcess {
    private final EventBus eBus;
    private final ArrayList<Object> listeners = new ArrayList<>();
    private final Map<String, File> devices = new HashMap<>();
    private final BlockingQueue<Runnable> executeOnDevicesChange = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new NamingThreadFactory("device-detector"));

    public DeviceDetectorProcess(EventBus eBus_) {
        eBus = eBus_;
    }

    public void start() {
        new Thread(() -> {
            USBDeviceDetectorManager detectorManager = new USBDeviceDetectorManager(2 * 1000);
            for (USBStorageDevice device : detectorManager.getRemovableDevices()) {
                Logger.getLogger(getClass().getName()).info("USB device detected: \"" + device.getSystemDisplayName() + "\". Writable: " + device.getRootDirectory().canWrite());

                synchronized (devices) {
                    devices.put(device.getSystemDisplayName(), device.getRootDirectory());
                }

                Object[] tmpList;
                synchronized (listeners) {
                    tmpList = listeners.toArray(new Object[]{});
                }
                for (Object listenerO : tmpList) {
                    if (listenerO instanceof StorageDevicePresenceListener) {
                        ((StorageDevicePresenceListener) listenerO).storageDeviceConnected(device.getRootDirectory(), device.getSystemDisplayName());
                    } else if (listenerO instanceof WeakReference) {
                        WeakReference<StorageDevicePresenceListener> ref = (WeakReference<StorageDevicePresenceListener>) listenerO;
                        StorageDevicePresenceListener listener = ref.get();
                        if (listener != null) {
                            listener.storageDeviceConnected(device.getRootDirectory(), device.getSystemDisplayName());
                        } else {
                            synchronized (listeners) {
                                listeners.remove(listenerO);
                            }
                        }
                    }
                }
            }
            detectorManager.addDriveListener((USBStorageEvent usbse) -> {

                synchronized (devices) {
                    if (usbse.getEventType() == DeviceEventType.CONNECTED) {
                        devices.put(usbse.getStorageDevice().getSystemDisplayName(), usbse.getStorageDevice().getRootDirectory());
                    } else if (usbse.getEventType() == DeviceEventType.REMOVED) {
                        devices.remove(usbse.getStorageDevice().getSystemDisplayName());
                    }

                }

                Object[] tmpList;
                synchronized (listeners) {
                    tmpList = listeners.toArray(new StorageDevicePresenceListener[]{});
                }
                for (Object listenerO : tmpList) {
                    StorageDevicePresenceListener listener = null;
                    if (listenerO instanceof StorageDevicePresenceListener) {
                        listener = ((StorageDevicePresenceListener) listenerO);

                    } else if (listenerO instanceof WeakReference) {
                        WeakReference<StorageDevicePresenceListener> ref = (WeakReference<StorageDevicePresenceListener>) listenerO;
                        listener = ref.get();
                        if (listener == null) {
                            synchronized (listeners) {
                                listeners.remove(listenerO);
                            }
                        }
                    }
                    if (listener != null) {
                        if (usbse.getEventType() == DeviceEventType.CONNECTED) {
                            listener.storageDeviceConnected(usbse.getStorageDevice().getRootDirectory(), usbse.getStorageDevice().getSystemDisplayName());
                        } else if (usbse.getEventType() == DeviceEventType.REMOVED) {
                            listener.storageDeviceDisconnected(usbse.getStorageDevice().getRootDirectory(), usbse.getStorageDevice().getSystemDisplayName());
                        }
                    }
                }

                if (!executeOnDevicesChange.isEmpty()) {
                    while (!executeOnDevicesChange.isEmpty()) {
                        try {
                            executor.submit(executeOnDevicesChange.take());
                        } catch (InterruptedException ex) {
                            Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            });
            
        }).start();
        
        /*File[] roots = File.listRoots();

         if (roots == null) {
         eBus.post(new MessageToStatusBar("Nie można odczytać listy urządzeń", MessageToStatusBar.Type.ERROR));
         }

         for(Path p:FileSystems.getDefault().getRootDirectories()) {
         Logger.getLogger(getClass().getName()).info("File root detected: \""+p+"\". Writable: "+p.toFile().canWrite());
         }*/
    }

    public void addStorageDevicePresenceListener(StorageDevicePresenceListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }
    
    public void addWeakStorageDevicePresenceListener(StorageDevicePresenceListener l) {
        synchronized (listeners) {
            listeners.add(new WeakReference<StorageDevicePresenceListener>(l));
        }
    }

    public void addStorageDeviceChangeListener(StorageDeviceChangeListener l) {
        synchronized (listeners) {
            listeners.add(new StorageDevicePresenceListener() {
                @Override
                public void storageDeviceConnected(File rootFile, String deviceName) {
                    l.apply();
                }

                @Override
                public void storageDeviceDisconnected(File rootFile, String deviceName) {
                    l.apply();
                }
            });
        }
    }

    public void removeStorageDevicePresenceListener(StorageDevicePresenceListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public void executeOnDevicesChange(Runnable r) {
        executeOnDevicesChange.add(r);
    }

    public void shutdown() {
        executor.shutdown();
    }

    public List<TwoTuple<String, File>> getConnectedDevices() {
        synchronized (devices) {
            List<TwoTuple<String, File>> out = new LinkedList<>();

            for (String deviceName : devices.keySet()) {
                File rootFile = devices.get(deviceName);
                out.add(new TwoTuple<>(deviceName, rootFile));
            }

            return out;
        }
    }

    public Carrier[] getConnectedOfCarriers(Carrier[] carrierList) {
        return Arrays.stream(carrierList).filter(carrier -> (getDeviceRoot(carrier.getName()) != null)).toArray(Carrier[]::new);
    }

    public File getDeviceRoot(String deviceName) {
        if (devices.containsKey(deviceName)) {
            return devices.get(deviceName);
        } else {
            return null;
        }
    }
}

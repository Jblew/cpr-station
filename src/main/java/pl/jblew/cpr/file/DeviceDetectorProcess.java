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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.samuelcampos.usbdrivedectector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedectector.USBStorageDevice;
import net.samuelcampos.usbdrivedectector.events.DeviceEventType;
import net.samuelcampos.usbdrivedectector.events.USBStorageEvent;
import pl.jblew.cpr.bootstrap.Bootstrap;
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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public DeviceDetectorProcess(EventBus eBus_) {
        eBus = eBus_;
    }

    public void start(Bootstrap bootstrap) {
        new Thread(() -> {
            USBDeviceDetectorManager detectorManager = new USBDeviceDetectorManager(2 * 1000);
            for (USBStorageDevice device : detectorManager.getRemovableDevices()) {
                changeDeviceState(device.getSystemDisplayName(), device.getRootDirectory(),
                        DeviceChange.CONNECTED);
            }
            detectorManager.addDriveListener((USBStorageEvent usbse) -> {

                if (usbse.getEventType() == DeviceEventType.CONNECTED) {
                    if (!devices.containsKey(usbse.getStorageDevice().getSystemDisplayName())) {
                        changeDeviceState(usbse.getStorageDevice().getSystemDisplayName(), usbse.getStorageDevice().getRootDirectory(),
                                DeviceChange.CONNECTED);
                    }
                } else if (usbse.getEventType() == DeviceEventType.REMOVED) {
                    changeDeviceState(usbse.getStorageDevice().getSystemDisplayName(), usbse.getStorageDevice().getRootDirectory(),
                            DeviceChange.DISCONNECTED);
                }

            });

        }).start();

        bootstrap.addShutdownTask(() -> {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Logger.getLogger(DeviceDetectorProcess.class.getName()).log(Level.SEVERE, null, ex);
            }
            scheduler.shutdownNow();
        });

        boolean isOSX = (System.getProperty("os.name").startsWith("Mac OS"));
        scheduler.scheduleAtFixedRate(() -> {
            if (isOSX) {
                for (File deviceRoot : new File("/Volumes").listFiles((f, name) -> !name.startsWith("."))) {
                    synchronized (devices) {
                        if (!devices.containsKey(deviceRoot.getName())) {
                            System.out.println("!!!OSX poll found new device: " + deviceRoot.getName());
                            changeDeviceState(deviceRoot.getName(), deviceRoot, DeviceChange.CONNECTED);
                        }
                    }
                }

                synchronized (devices) {
                    for (String deviceName : devices.keySet()) {
                        File deviceRoot = devices.get(deviceName);
                        if (!deviceRoot.exists() || !deviceRoot.canRead()) {
                            Logger.getLogger(getClass().getName()).info("Cannot read from device \"" + deviceRoot + "\". Removing device from list.");

                            changeDeviceState(deviceRoot.getName(), deviceRoot, DeviceChange.DISCONNECTED);
                        }
                    }
                }
            }
        }, 2, 2, TimeUnit.SECONDS);

        /*File[] roots = File.listRoots();

         if (roots == null) {
         eBus.post(new MessageToStatusBar("Nie można odczytać listy urządzeń", MessageToStatusBar.Type.ERROR));
         }

         for(Path p:FileSystems.getDefault().getRootDirectories()) {
         Logger.getLogger(getClass().getName()).info("File root detected: \""+p+"\". Writable: "+p.toFile().canWrite());
         }*/
    }

    private void changeDeviceState(String name, File rootDirectory, DeviceChange changeType) {
        Logger.getLogger(getClass().getName()).info("USB device detected: \"" + name + "\". Writable: " + rootDirectory.canWrite());

        synchronized (devices) {
            if (changeType == DeviceChange.CONNECTED) {
                devices.put(name, rootDirectory);
            } else {
                devices.remove(name);
            }
        }

        Object[] tmpList;
        synchronized (listeners) {
            tmpList = listeners.toArray(new Object[]{});
        }
        for (Object listenerO : tmpList) {
            if (listenerO instanceof StorageDevicePresenceListener) {
                if (changeType == DeviceChange.CONNECTED) {
                    ((StorageDevicePresenceListener) listenerO).storageDeviceConnected(rootDirectory, name);
                } else {
                    ((StorageDevicePresenceListener) listenerO).storageDeviceDisconnected(rootDirectory, name);
                }
            } else if (listenerO instanceof WeakReference) {
                WeakReference<StorageDevicePresenceListener> ref = (WeakReference<StorageDevicePresenceListener>) listenerO;
                StorageDevicePresenceListener listener = ref.get();
                if (listener != null) {
                    if (changeType == DeviceChange.CONNECTED) {
                        listener.storageDeviceConnected(rootDirectory, name);
                    } else {
                        listener.storageDeviceDisconnected(rootDirectory, name);
                    }
                } else {
                    synchronized (listeners) {
                        listeners.remove(listenerO);
                    }
                }
            }
        }

        notifyDeviceChangedListeners();
    }

    private void notifyDeviceChangedListeners() {
        if (!executeOnDevicesChange.isEmpty()) {
            while (!executeOnDevicesChange.isEmpty()) {
                try {
                    executor.submit(executeOnDevicesChange.take());
                } catch (InterruptedException ex) {
                    Logger.getLogger(DatabaseManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public void addStorageDevicePresenceListener(StorageDevicePresenceListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void addWeakStorageDevicePresenceListener(StorageDevicePresenceListener l) {
        synchronized (listeners) {
            listeners.add(new WeakReference<>(l));
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

    private enum DeviceChange {
        CONNECTED, DISCONNECTED
    }
}

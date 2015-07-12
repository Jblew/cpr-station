/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import net.samuelcampos.usbdrivedectector.events.DeviceEventType;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.util.ListenersManager.Listener;

/**
 *
 * @author teofil
 */
public class ListenersManager<E extends Listener> {
    private final ArrayList<E> listeners = new ArrayList<E>();

    public void addListener(E l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeListener(E l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public void callListeners(ListenerCaller<E> listenerCaller) {
        Object[] tmpList;
        synchronized (listeners) {
            tmpList = listeners.toArray();
        }
        for (Object listenerO : tmpList) {
            E listener = (E) listenerO;
            listenerCaller.callListener(listener);
        }
    }

    public static interface Listener {
    }

    public static interface ListenerCaller<V> {
        public void callListener(V listener);
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.treenodes;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.db.DatabaseManager;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.TreePanel;
import pl.jblew.cpr.gui.TreePanel.IconTreeNode;
import pl.jblew.cpr.gui.TreePanel.SelectableIconTreeNode;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.util.ListenersManager;
import pl.jblew.cpr.util.PrintableBusMessage;

/**
 *
 * @author teofil
 */
public class CarriersNode extends IconTreeNode implements StorageDevicePresenceListener, TreePanel.AddTopMargin {
    private final Map<Carrier, SelectableIconTreeNode> carriers = new HashMap<>();
    private final ListenersManager<NodeChangeListener> listenersManager = new ListenersManager<>();
    private final Set<String> connectedDevices = new HashSet<>();
    private final CarriersNode me = this;
    private final EventBus eBus;
    private final DatabaseManager dbManager;

    public CarriersNode(DatabaseManager dbMgr, EventBus eBus_) {
        super("Wszystkie nośniki", new ImageIcon(TreePanel.class.getClassLoader().getResource("images/carriers.png")));
        eBus = eBus_;
        eBus.register(this);
        dbManager = dbMgr;
        carriersListChanged(null);
    }

    public void addNodeChangeListener(NodeChangeListener l) {
        listenersManager.addListener(l);
    }

    public void removeNodeChangeListener(NodeChangeListener l) {
        listenersManager.removeListener(l);
    }

    @Override
    public void storageDeviceConnected(final File rootFile, final String deviceName) {
        synchronized (connectedDevices) {
            connectedDevices.add(deviceName);
        }
        SwingUtilities.invokeLater(() -> {
            synchronized (carriers) {
                for (Carrier c : carriers.keySet()) {
                    if (c.getName().equals(deviceName)) {
                        SelectableIconTreeNode node = carriers.get(c);
                        node.setActive(true);
                        fireNodeChanged();
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void storageDeviceDisconnected(final File rootFile, final String deviceName) {
        synchronized (connectedDevices) {
            connectedDevices.remove(deviceName);
        }
        SwingUtilities.invokeLater(() -> {
            synchronized (carriers) {
                for (Carrier c : carriers.keySet()) {
                    if (c.getName().equals(deviceName)) {
                        SelectableIconTreeNode node = carriers.get(c);
                        node.setActive(false);
                        fireNodeChanged();
                        break;
                    }
                }
            }
        });
    }

    private void fireNodeChanged() {
        listenersManager.callListeners((NodeChangeListener listener) -> {
            listener.nodeChanged(me);
        });

    }

    private void addCarrierNode(final Carrier c) {
        SwingUtilities.invokeLater(() -> {
            SelectableIconTreeNode node = new SelectableIconTreeNode(c.getName(), new ImageIcon(TreePanel.class.getClassLoader().getResource("images/dbsave16.png"))) {
                @Override
                public void nodeSelected(JTree tree) {
                    SwingUtilities.invokeLater(() -> {
                        //onClick
                        //onClick
                    });
                }
            };
            node.setActive(false);
            synchronized (connectedDevices) {
                if (connectedDevices.contains(c.getName())) {
                    node.setActive(true);
                }
            }
            add(node);
            synchronized (carriers) {
                carriers.put(c, node);
            }
        });
    }

    @Subscribe
    public void carriersListChanged(CarriersListChanged evt) {
        dbManager.executeInDBThread(() -> {
            try {
                removeAllChildren();
                synchronized (carriers) {
                    carriers.clear();
                    for (Carrier c : dbManager.getDaos().getCarrierDao().queryForAll()) {
                        addCarrierNode(c);
                    }
                }
                
                fireNodeChanged();
            } catch (SQLException ex) {
                Logger.getLogger(CarriersNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    public static class CarriersListChanged implements PrintableBusMessage {
        public CarriersListChanged() {
        }
    }
}

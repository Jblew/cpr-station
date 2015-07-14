/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.treenodes;

import com.google.common.eventbus.EventBus;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.MutableTreeNode;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.TreePanel;
import pl.jblew.cpr.gui.TreePanel.IconTreeNode;
import pl.jblew.cpr.gui.TreePanel.SelectableIconTreeNode;
import pl.jblew.cpr.gui.panels.CarrierPanel;
import pl.jblew.cpr.util.ListenersManager;

/**
 *
 * @author teofil
 */
public class DevicesNode extends IconTreeNode implements StorageDevicePresenceListener {
    private final Map<String, MutableTreeNode> devices = new HashMap<String, MutableTreeNode>();
    private final ListenersManager<NodeChangeListener> listenersManager = new ListenersManager<>();
    private final DevicesNode me = this;
    private final EventBus eBus;

    public DevicesNode(EventBus eBus_) {
        super("Urządzenia", new ImageIcon(TreePanel.class.getClassLoader().getResource("images/devices.png")));

        eBus = eBus_;
    }

    public void addNodeChangeListener(NodeChangeListener l) {
        listenersManager.addListener(l);
    }

    public void removeNodeChangeListener(NodeChangeListener l) {
        listenersManager.removeListener(l);
    }

    @Override
    public void storageDeviceConnected(final File rootFile, final String deviceName) {
        //System.out.println("Adding device to tree");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (!devices.containsKey(rootFile.getPath())) {
                    MutableTreeNode node = new SelectableIconTreeNode(deviceName, new ImageIcon(TreePanel.class.getClassLoader().getResource("images/usb16.png"))) {
                        @Override
                        public void nodeSelected(JTree tree) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    eBus.post(new ChangeMainPanel(new CarrierPanel(deviceName, rootFile)));
                                }
                            });
                        }
                    };
                    add(node);
                    devices.put(rootFile.getPath(), node);
                    fireNodeChanged();
                }
            }
        });
    }

    @Override
    public void storageDeviceDisconnected(final File rootFile, final String deviceName) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (devices.containsKey(rootFile.getPath())) {
                    remove(devices.get(rootFile.getPath()));
                    devices.remove(rootFile.getPath());
                    fireNodeChanged();
                }
            }
        });
    }

    private void fireNodeChanged() {
        listenersManager.callListeners(new ListenersManager.ListenerCaller<NodeChangeListener>() {
            @Override
            public void callListener(NodeChangeListener listener) {
                listener.nodeChanged(me);
            }

        });
    }
}

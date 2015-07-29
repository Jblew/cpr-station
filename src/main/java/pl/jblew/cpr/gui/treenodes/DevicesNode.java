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
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.db.DatabaseManager;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.TreePanel;
import pl.jblew.cpr.gui.TreePanel.IconTreeNode;
import pl.jblew.cpr.gui.TreePanel.SelectableIconTreeNode;
import pl.jblew.cpr.gui.panels.DevicePanel;
import pl.jblew.cpr.util.ListenersManager;

/**
 *
 * @author teofil
 */
public class DevicesNode extends IconTreeNode implements StorageDevicePresenceListener {
    private final Context context;
    private final Map<String, MutableTreeNode> devices = new HashMap<String, MutableTreeNode>();
    private final ListenersManager<NodeChangeListener> listenersManager = new ListenersManager<>();
    private final DevicesNode me = this;

    public DevicesNode(Context context_) {
        super("Urządzenia", new ImageIcon(TreePanel.class.getClassLoader().getResource("images/devices.png")));
        this.context = context_;

        final File homeFile = new File(System.getProperty("user.home"));
        if (homeFile.exists()) {
            MutableTreeNode node = new SelectableIconTreeNode("Katalog domowy", new ImageIcon(TreePanel.class.getClassLoader().getResource("images/pc16.gif"))) {
                @Override
                public void nodeSelected(JTree tree) {
                    SwingUtilities.invokeLater(() -> {
                        context.eBus.post(new ChangeMainPanel(new DevicePanel(context, "Katalog domowy", homeFile)));
                    });
                }
            };
            add(node);
            devices.put(homeFile.getPath(), node);
            fireNodeChanged();
        }
    }

    public void addNodeChangeListener(NodeChangeListener l) {
        listenersManager.addListener(l);
    }

    public void removeNodeChangeListener(NodeChangeListener l) {
        listenersManager.removeListener(l);
    }

    @Override
    public void storageDeviceConnected(final File rootFile, final String deviceName) {
        SwingUtilities.invokeLater(() -> {
            if (!devices.containsKey(rootFile.getPath())) {
                MutableTreeNode node = new SelectableIconTreeNode(deviceName, new ImageIcon(TreePanel.class.getClassLoader().getResource("images/usb16.png"))) {
                    @Override
                    public void nodeSelected(JTree tree) {
                        SwingUtilities.invokeLater(() -> {
                            context.eBus.post(new ChangeMainPanel(new DevicePanel(context, deviceName, rootFile)));
                        });
                    }
                };
                add(node);
                devices.put(rootFile.getPath(), node);
                fireNodeChanged();
            }
        });
    }

    @Override
    public void storageDeviceDisconnected(final File rootFile, final String deviceName) {
        SwingUtilities.invokeLater(() -> {
            if (devices.containsKey(rootFile.getPath())) {
                remove(devices.get(rootFile.getPath()));
                devices.remove(rootFile.getPath());
                fireNodeChanged();
            }
        });
    }

    private void fireNodeChanged() {
        listenersManager.callListeners((NodeChangeListener listener) -> {
            listener.nodeChanged(me);
        });
    }
}

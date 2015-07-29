/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.treenodes.CarriersNode;
import pl.jblew.cpr.gui.treenodes.DevicesNode;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.gui.treenodes.NodeChangeListener;
import pl.jblew.cpr.logic.Event;

/**
 *
 * @author teofil
 */
public class TreePanel extends JPanel {
    private final JScrollPane scrollPane;
    private final JTree tree;
    private final DevicesNode devicesNode;
    private final CarriersNode carriersNode;
    private final EventsNode sortedPhotosNode;
    private final EventsNode unsortedPhotosNode;
    //private final ExecutorService loadingExecutor = Executors.newSingleThreadExecutor();

    public TreePanel(Context context) {
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("CPR-station");

        devicesNode = new DevicesNode(context);
        carriersNode = new CarriersNode(context.dbManager, context.eBus);
        sortedPhotosNode = new EventsNode(context, Event.Type.SORTED);
        unsortedPhotosNode = new EventsNode(context, Event.Type.UNSORTED);

        addMainNodesAndListeners(top);

        tree = new JTree(top);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.setRootVisible(false);
        setTreeRenderer(tree);
        addTreeListeners(tree);

        scrollPane = new JScrollPane(tree);

        setLayout(new GridLayout(1, 1));
        add(scrollPane);

        tree.expandPath(new TreePath(devicesNode.getPath()));
        tree.expandPath(new TreePath(unsortedPhotosNode.getPath()));
        tree.expandPath(new TreePath(carriersNode.getPath()));
        tree.expandPath(new TreePath(sortedPhotosNode.getPath()));
        
    }

    private void addMainNodesAndListeners(DefaultMutableTreeNode top) {
        top.add(devicesNode);
        top.add(carriersNode);
        top.add(sortedPhotosNode);
        top.add(unsortedPhotosNode);
        

        NodeChangeListener nodeChangeListener = (DefaultMutableTreeNode node) -> {
            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
            model.reload(node);
            SwingUtilities.invokeLater(() -> {
                tree.expandPath(new TreePath(devicesNode.getPath()));
                tree.expandPath(new TreePath(unsortedPhotosNode.getPath()));
                tree.expandPath(new TreePath(carriersNode.getPath()));
                tree.expandPath(new TreePath(sortedPhotosNode.getPath()));
                tree.revalidate();
                revalidate();
                repaint();
            });
        };

        devicesNode.addNodeChangeListener(nodeChangeListener);
        carriersNode.addNodeChangeListener(nodeChangeListener);
        sortedPhotosNode.addNodeChangeListener(nodeChangeListener);
        unsortedPhotosNode.addNodeChangeListener(nodeChangeListener);
    }

    public DevicesNode getDevicesNode() {
        return devicesNode;
    }

    public CarriersNode getCarriersNode() {
        return carriersNode;
    }

    private void setTreeRenderer(JTree tree) {
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;

                if (node instanceof IconTreeNode) {
                    setIcon(((IconTreeNode) node).getIcon());
                    if (!((IconTreeNode) node).isActive()) {
                        this.setForeground(Color.GRAY);
                    }
                } else if (tree.getModel().getRoot().equals(node)) {
                    setIcon(TreeIcons.ROOT.getIcon());
                } else if (node.getChildCount() > 0) {
                    setIcon(TreeIcons.PARENT.getIcon());
                } else {
                    setIcon(TreeIcons.LEAF.getIcon());
                }

                if (node instanceof AddTopMargin) {
                    setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
                } else {
                    setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
                }

                return this;
            }

        });
    }

    private void addTreeListeners(final JTree tree_) {
        tree_.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {

            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                if (event.getPath().getPathCount() == 2) {
                    tree_.expandPath(event.getPath());
                }

            }
        });

        tree_.addTreeSelectionListener((TreeSelectionEvent e) -> {
            Object selected = e.getPath().getLastPathComponent();
            
            if (selected instanceof SelectableIconTreeNode) {
                SelectableIconTreeNode selectableNode = (SelectableIconTreeNode) selected;
                selectableNode.nodeSelected(tree);
            }
        });
    }

    private enum TreeIcons {
        ROOT("root"), PARENT("parent"), LEAF("leaf");

        private final String iconName;
        private final ImageIcon icon;

        private TreeIcons(String iconName_) {
            iconName = iconName_;
            ClassLoader cl = TreePanel.class.getClassLoader();

            icon = new ImageIcon(cl.getResource("images/" + iconName + ".png"));
            if (icon == null) {
                throw new RuntimeException("Icon not found!");
            }
        }

        public Icon getIcon() {
            return icon;
        }
    }

    public static class IconTreeNode extends DefaultMutableTreeNode {
        private final Icon icon;
        private final AtomicBoolean active = new AtomicBoolean(true);

        public IconTreeNode(String text, Icon icon_) {
            super(text);

            icon = icon_;
        }

        public Icon getIcon() {
            return icon;
        }

        public boolean isActive() {
            return active.get();
        }

        public void setActive(boolean active_) {
            active.set(active_);
        }
    }

    public static abstract class SelectableIconTreeNode extends IconTreeNode {

        public SelectableIconTreeNode(String text, Icon icon_) {
            super(text, icon_);
        }

        public abstract void nodeSelected(JTree tree);
    }

    public static interface AddTopMargin {
    }
}

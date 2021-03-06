/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.components.SearchField;
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
    private final AtomicReference<SelectableIconTreeNode> lastSelected = new AtomicReference<>(null);
    private final SearchField searchField;

    public TreePanel(Context context) {
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("CPR-station");

        devicesNode = new DevicesNode(context);
        carriersNode = new CarriersNode(context);
        sortedPhotosNode = new EventsNode(context, Event.Type.SORTED);
        unsortedPhotosNode = new EventsNode(context, Event.Type.UNSORTED);

        addMainNodesAndListeners(top);

        this.setMaximumSize(new Dimension(300, Integer.MAX_VALUE));

        tree = new JTree(top);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.setRootVisible(false);
        setTreeRenderer(tree);
        addTreeListeners(tree);

        scrollPane = new JScrollPane(tree);
        scrollPane.setMaximumSize(new Dimension(300, Integer.MAX_VALUE));
        scrollPane.setPreferredSize(new Dimension(300, 500));

        //setLayout(new GridLayout(1, 1));
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        
        searchField = new SearchField((s) -> {
            sortedPhotosNode.filter(s);
            unsortedPhotosNode.filter(s);
        });
        add(searchField, BorderLayout.NORTH);

        tree.expandPath(new TreePath(devicesNode.getPath()));
        tree.expandPath(new TreePath(unsortedPhotosNode.getPath()));
        tree.expandPath(new TreePath(carriersNode.getPath()));
        tree.expandPath(new TreePath(sortedPhotosNode.getPath()));

    }

    private NodeChangeListener addMainNodesAndListeners(DefaultMutableTreeNode top) {
        top.add(devicesNode);
        top.add(sortedPhotosNode);
        top.add(unsortedPhotosNode);
        top.add(carriersNode);

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
        
        return nodeChangeListener;
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
                    else this.setForeground(((IconTreeNode) node).getColor());
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
            TreePath newPath = e.getNewLeadSelectionPath();
            if (newPath != null) {//newPath may be null if selection was cleared
                Object selected = newPath.getLastPathComponent();
                
                if (selected instanceof SelectableIconTreeNode) {
                    SelectableIconTreeNode selectableNode = (SelectableIconTreeNode) selected;
                    selectableNode.nodeSelected(tree);
                }

                Timer t = new Timer(800, (ev) -> tree.clearSelection());
                t.setRepeats(false);
                t.start();
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
        private final AtomicReference<Color> color = new AtomicReference<>(Color.BLACK);

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
        
        public void setColor(Color newColor) {
            color.set(newColor);
        }
        
        public Color getColor() {
            return color.get();
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import com.google.common.eventbus.EventBus;
import java.awt.Component;
import java.awt.GridLayout;
import java.io.File;
import java.net.URL;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.treenodes.DevicesNode;
import pl.jblew.cpr.gui.treenodes.NodeChangeListener;

/**
 *
 * @author teofil
 */
public class TreePanel extends JPanel {
    private final JScrollPane scrollPane;
    private final JTree tree;
    private final DevicesNode devicesNode;
    private final DefaultMutableTreeNode carriersNode;
    private final DefaultMutableTreeNode sortedPhotosNode;
    private final DefaultMutableTreeNode unsortedPhotosNode;

    public TreePanel(EventBus eBus) {
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("CPR-station");

        devicesNode = new DevicesNode(eBus);
        carriersNode = new IconTreeNode("Wszystkie nośniki",
                new ImageIcon(TreePanel.class.getClassLoader().getResource("images/carriers.png")));
        sortedPhotosNode = new IconTreeNode("Posegregowane",
                new ImageIcon(TreePanel.class.getClassLoader().getResource("images/sorted-photos.png")));
        unsortedPhotosNode = new IconTreeNode("Nieposegregowane",
                new ImageIcon(TreePanel.class.getClassLoader().getResource("images/unsorted-photos.png")));

        addMainNodesAndListeners(top);

        tree = new JTree(top);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.setRootVisible(false);
        setTreeRenderer(tree);
        addTreeListeners(tree);

        scrollPane = new JScrollPane(tree);

        setLayout(new GridLayout(1, 1));
        add(scrollPane);
    }

    private void addMainNodesAndListeners(DefaultMutableTreeNode top) {
        top.add(devicesNode);
        top.add(carriersNode);
        top.add(sortedPhotosNode);
        top.add(unsortedPhotosNode);
        devicesNode.addNodeChangeListener(new NodeChangeListener() {
            @Override
            public void nodeChanged(DefaultMutableTreeNode node) {
                DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
                DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
                model.reload(root);
                
                TreePath devicesPath = new TreePath(devicesNode.getPath());
                tree.expandPath(devicesPath);
            }
        });
    }
    
    public DevicesNode getDevicesNode() {
        return devicesNode;
    }

    private void setTreeRenderer(JTree tree) {
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;

                if (node instanceof IconTreeNode) {
                    setIcon(((IconTreeNode) node).getIcon());
                } else if (tree.getModel().getRoot().equals(node)) {
                    setIcon(TreeIcons.ROOT.getIcon());
                } else if (node.getChildCount() > 0) {
                    setIcon(TreeIcons.PARENT.getIcon());
                } else {
                    setIcon(TreeIcons.LEAF.getIcon());
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
                //System.out.println("Collapsed path count: "+);
                if(event.getPath().getPathCount() == 2) {
                    tree_.expandPath(event.getPath());
                }

                //for(Object o : event.getPath().getPath()) {
                //    System.out.println("elem: "+o);
                //}
            }
        });
        
        tree_.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                Object selected = e.getPath().getLastPathComponent();
                
                if(selected instanceof SelectableIconTreeNode) {
                    SelectableIconTreeNode selectableNode = (SelectableIconTreeNode) selected;
                    selectableNode.nodeSelected(tree);
                }
                
                //TreePath devicesPath = new TreePath(devicesNode.getPath());
                //if(devicesPath.isDescendant(e.getPath()) && !devicesPath.equals(e.getPath())) {
                //    
                //}
                
                //if(devicesPath.isDescendant(devicesPath)) {
                //    System.out.println("devicesNode is child of devicesNode");
                //}
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
            //ClassLoader cl = TreePanel.class.getClassLoader();
            //URL resource = cl.getResource("images/"+iconName+".png");
            //System.out.println(resource);
            //ImageIcon icon = new ImageIcon(resource);
            //if(icon == null) throw new RuntimeException("Could not find icon at: "+resource);
            return icon;
        }
    }

    public static class IconTreeNode extends DefaultMutableTreeNode {
        private final Icon icon;

        public IconTreeNode(String text, Icon icon_) {
            super(text);

            icon = icon_;
        }

        public Icon getIcon() {
            return icon;
        }
    }
    
    public static abstract class SelectableIconTreeNode extends IconTreeNode {

        public SelectableIconTreeNode(String text, Icon icon_) {
            super(text, icon_);
        }
        
        public abstract void nodeSelected(JTree tree);
    }
}

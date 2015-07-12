/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.treenodes;

import javax.swing.tree.DefaultMutableTreeNode;
import pl.jblew.cpr.util.ListenersManager;

/**
 *
 * @author teofil
 */
public interface NodeChangeListener extends ListenersManager.Listener {
    public void nodeChanged(DefaultMutableTreeNode node);
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import com.google.common.eventbus.EventBus;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author teofil
 */
public class GUI {
    private final MainWindow window;
    private final TreePanel treePanel;
    private final EventBus eBus;

    public GUI(EventBus eBus_) {
        eBus = eBus_;

        setLookAndFeel();

        window = new MainWindow(eBus);

        treePanel = new TreePanel(eBus);
        window.addTreePane(treePanel);
    }

    public void start() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                window.show();
            }
        });
    }

    public TreePanel getTreePanel() {
        return treePanel;
    }

    private void setLookAndFeel() {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(GUI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(GUI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(GUI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedLookAndFeelException ex) {
            Logger.getLogger(GUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

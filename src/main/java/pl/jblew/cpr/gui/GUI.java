/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import com.google.common.eventbus.EventBus;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.db.DatabaseManager;

/**
 *
 * @author teofil
 */
public class GUI {
    private final BlockingQueue<Runnable> executeOnLoadQueue = new LinkedBlockingQueue<>();
    private final AtomicReference<MainWindow> window = new AtomicReference<>();
    private final AtomicReference<TreePanel> treePanel = new AtomicReference<>();
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final Context context;

    public GUI(Context context_) {
        context = context_;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setLookAndFeel();

                window.set(new MainWindow(context));

                treePanel.set(new TreePanel(context));
                window.get().addTreePane(treePanel.get());

                loaded.set(true);
                while (true) {
                    Runnable r = executeOnLoadQueue.poll();
                    if (r == null) {
                        break;
                    } else {
                        new Thread(r).start();
                    }
                }
            }
        });
    }

    public void start() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                window.get().show();
            }
        });
    }

    public void executeWhenLoaded(Runnable r) {
        if (!loaded.get()) {
            try {
                executeOnLoadQueue.put(r);
            } catch (InterruptedException ex) {
            }
        } else {
            new Thread(r).start();
        }
    }

    public TreePanel getTreePanel() {
        TreePanel tp = treePanel.get();
        if (tp == null) {
            throw new RuntimeException("GUI not yet loaded! Cannot getTreePanel");
        }
        return tp;
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
        System.setProperty("sun.java2d.noddraw", Boolean.TRUE.toString());
    }
}

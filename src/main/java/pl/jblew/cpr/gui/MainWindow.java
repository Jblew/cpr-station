/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.db.DBBackupManager;
import pl.jblew.cpr.db.DatabaseChanged;
import pl.jblew.cpr.gui.panels.HomePanel;
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.gui.treenodes.SleepPreventer;
import pl.jblew.cpr.gui.windows.LogWindow;
import pl.jblew.cpr.util.MessageToStatusBar;

/**
 *
 * @author teofil
 */
public class MainWindow {
    private final Context context;
    private final JFrame frame;
    private final MainContentPane mainContentPane;
    private final JDialog lockDialog;

    public MainWindow(Context context_) {
        context = context_;

        mainContentPane = new MainContentPane();
        context.eBus.register(mainContentPane);

        frame = context.frame;
        frame.setSize(1000, 800);
        frame.setLocationRelativeTo(null);
        frame.setIconImage(IconLoader.LOGO_256.load().getImage());

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setContentPane(mainContentPane);

        lockDialog = new JDialog(frame, "Ładowanie...", Dialog.ModalityType.APPLICATION_MODAL);
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
        });
    }

    //public void setMainPanel(MainPanel newMainPanel) {
    //    mainContentPane.setMainPanel(newMainPanel);
    //}
    void addTreePane(JPanel treePane) {
        //JScrollPane sp = new JScrollPane(treePane);
        //sp.setPreferredSize(new Dimension(300, 900));
        mainContentPane.add(treePane, BorderLayout.WEST);
    }

    private class MainContentPane extends JPanel {
        private final JLabel statusBar;
        private final JLabel dbLabel;
        private final JLabel dbBackupLabel;
        private MainPanel mainPanel = new HomePanel();

        MainContentPane() {
            statusBar = new JLabel("Ładowanie...");
            statusBar.setBorder(BorderFactory.createLoweredBevelBorder());

            dbLabel = new JLabel("Brak bazy danych, podłącz klucz z bazą");
            dbBackupLabel = new JLabel("");
            dbLabel.setForeground(Color.RED);

            JToolBar toolBar = new JToolBar();
            toolBar.add(dbLabel);
            toolBar.addSeparator();
            toolBar.add(dbBackupLabel);
            toolBar.addSeparator();

            JButton logButton = new JButton("Pokaż log");
            logButton.addActionListener((evt) -> {
                new LogWindow(context);
            });
            toolBar.add(logButton);
            
            toolBar.addSeparator();

            SleepPreventer preventer = new SleepPreventer(context);
            JCheckBox preventSleepButton = new JCheckBox("Nie usypiaj");
            preventSleepButton.addActionListener((evt) -> {
                SwingUtilities.invokeLater(() -> {
                    boolean selected = preventSleepButton.getModel().isSelected();
                    preventer.setEnabled(selected);
                });
            });
            toolBar.add(preventSleepButton);

            this.setLayout(new BorderLayout());
            this.add(toolBar, BorderLayout.NORTH);
            this.add(mainPanel, BorderLayout.CENTER);
            //this.add(statusBar, BorderLayout.SOUTH);
            this.add(new ProgressListPanel(context), BorderLayout.SOUTH);
        }

        public void setMainPanel(final MainPanel newMainPanel) {
            SwingUtilities.invokeLater(() -> {
                long sT = System.currentTimeMillis();
                MainPanel prevMainPanel = mainPanel;
                prevMainPanel.inactivate();

                remove(prevMainPanel);
                add(newMainPanel, BorderLayout.CENTER);

                mainPanel = newMainPanel;
                mainPanel.activate();

                revalidate();
                repaint();

                Logger.getLogger(getClass().getName()).info("Changing main panel: " + (System.currentTimeMillis() - sT) + "ms");
                //setVisible(false);
                //setVisible(true);
                //frame.repaint();
            });
        }

        @Subscribe
        public void messageToStatusBar(final MessageToStatusBar e) {
            SwingUtilities.invokeLater(() -> {
                statusBar.setText(e.getMsg());
                if (e.getType() == MessageToStatusBar.Type.ERROR) {
                    statusBar.setForeground(Color.RED);
                } else if (e.getType() == MessageToStatusBar.Type.INFO) {
                    statusBar.setForeground(Color.BLACK);
                }
            });
        }

        @Subscribe
        public void changeMainPanel(ChangeMainPanel e) {
            setMainPanel(e.getMainPanel());
        }

        @Subscribe
        public void databaseChanged(final DatabaseChanged e) {
            SwingUtilities.invokeLater(() -> {
                dbLabel.setForeground((e.isNull() ? Color.RED : Color.BLACK));
                dbLabel.setText("Baza danych: " + e.getDeviceName());
            });
        }

        @Subscribe
        public void dbBackupStateChanged(final DBBackupManager.BackupStateChanged e) {
            SwingUtilities.invokeLater(() -> {
                dbBackupLabel.setForeground((e.isSafe() ? Color.GREEN.darker() : Color.RED));
                dbBackupLabel.setText(e.getMsg() + " (" + e.getRemaningChanges() + ")");
            });
        }

        @Subscribe
        public void setUILocked(SetUILocked l) {
            SwingUtilities.invokeLater(() -> {
                lockDialog.setVisible(l.locked);
            });
        }

    }

    public static class SetUILocked {
        public final boolean locked;

        public SetUILocked(boolean locked) {
            this.locked = locked;
        }
    }
}

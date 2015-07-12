/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;
import pl.jblew.cpr.gui.panels.HomePanel;
import pl.jblew.cpr.util.MessageToStatusBar;
import pl.jblew.cpr.util.PrintableBusMessage;

/**
 *
 * @author teofil
 */
public class MainWindow {
    private final EventBus eBus;
    private final JFrame frame;
    private final MainContentPane mainContentPane;

    public MainWindow(EventBus eBus_) {
        eBus = eBus_;

        mainContentPane = new MainContentPane();
        eBus.register(mainContentPane);

        frame = new JFrame();
        frame.setSize(800, 600);
        frame.setContentPane(mainContentPane);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public void show() {
        frame.setVisible(true);
    }

    public void setMainPanel(MainPanel newMainPanel) {
        mainContentPane.setMainPanel(newMainPanel);
    }

    public void addTreePane(JPanel treePane) {
        mainContentPane.add(treePane, BorderLayout.WEST);
    }

    private class MainContentPane extends JPanel {
        private final JLabel statusBar;

        private MainPanel mainPanel = new HomePanel();

        MainContentPane() {
            statusBar = new JLabel("Loading...");
            statusBar.setBorder(BorderFactory.createLoweredBevelBorder());

            //JToolBar toolBar = new JToolBar();
            //JButton prevButton
            this.setLayout(new BorderLayout());
            //this.add(toolBar, BorderLayout.NORTH);
            this.add(mainPanel, BorderLayout.CENTER);
            this.add(statusBar, BorderLayout.SOUTH);
        }

        public void setMainPanel(final MainPanel newMainPanel) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    MainPanel prevMainPanel = mainPanel;
                    prevMainPanel.inactivate();

                    remove(prevMainPanel);
                    add(newMainPanel, BorderLayout.CENTER);

                    mainPanel = newMainPanel;
                    mainPanel.activate();
                }
            });
        }

        @Subscribe
        public void messageToStatusBar(final MessageToStatusBar e) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    statusBar.setText(e.getMsg());
                    if (e.getType() == MessageToStatusBar.Type.ERROR) {
                        statusBar.setForeground(Color.RED);
                    } else if (e.getType() == MessageToStatusBar.Type.INFO) {
                        statusBar.setForeground(Color.BLACK);
                    }
                }
            });
        }

        @Subscribe
        public void changeMainPanel(ChangeMainPanel e) {
            setMainPanel(e.getMainPanel());
        }
    }
}

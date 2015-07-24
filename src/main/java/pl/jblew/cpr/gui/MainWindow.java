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
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.db.DatabaseChanged;
import pl.jblew.cpr.gui.panels.HomePanel;
import pl.jblew.cpr.util.MessageToStatusBar;

/**
 *
 * @author teofil
 */
public class MainWindow {
    private final Context context;
    private final JFrame frame;
    private final MainContentPane mainContentPane;

    public MainWindow(Context context_) {
        context = context_;

        mainContentPane = new MainContentPane();
        context.eBus.register(mainContentPane);

        frame = context.frame;
        frame.setSize(1000, 800);
        
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        frame.setContentPane(mainContentPane);
    }

    public void show() {
        frame.setVisible(true);
    }

    //public void setMainPanel(MainPanel newMainPanel) {
    //    mainContentPane.setMainPanel(newMainPanel);
    //}

    void addTreePane(JPanel treePane) {
        mainContentPane.add(treePane, BorderLayout.WEST);
    }

    private class MainContentPane extends JPanel {
        private final JLabel statusBar;
        private final JLabel dbLabel;
        private MainPanel mainPanel = new HomePanel();

        MainContentPane() {
            statusBar = new JLabel("Ładowanie...");
            statusBar.setBorder(BorderFactory.createLoweredBevelBorder());

            dbLabel = new JLabel("Brak bazy danych, podłącz klucz z bazą");
            dbLabel.setForeground(Color.RED);
            
            JToolBar toolBar = new JToolBar();
            toolBar.add(dbLabel);
            
            this.setLayout(new BorderLayout());
            this.add(toolBar, BorderLayout.NORTH);
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
                    
                    repaint();
                    setVisible(false);
                    setVisible(true);
                    //frame.repaint();
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
        
        @Subscribe
        public void databaseChanged(final DatabaseChanged e) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    dbLabel.setForeground((e.isNull()? Color.RED : Color.BLACK));
                    dbLabel.setText("Baza danych: "+e.getDeviceName());
                }
            });
        }
    }
}

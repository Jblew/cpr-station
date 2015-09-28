/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.windows;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;

/**
 *
 * @author teofil
 */
public class LogWindow {
    private final Context context;
    private final JFrame frame;
    private final AtomicBoolean listenToLog = new AtomicBoolean(true);

    public LogWindow(Context context) {
        this.context = context;

        this.frame = new JFrame("Log");

        SwingUtilities.invokeLater(() -> {
            frame.setSize(500, 500);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new MainPanel());
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent evt) {
                    listenToLog.set(false);
                    frame.setVisible(false);
                }
            });

            frame.setVisible(true);
        });

    }

    private final class MainPanel extends JPanel {
        public MainPanel() {
            setLayout(new BorderLayout());

            JTextArea ta = new JTextArea();
            ta.setEditable(false);

            JScrollPane scrollPane = new JScrollPane(ta);

            add(scrollPane, BorderLayout.CENTER);

            Runnable reloadLog = () -> {
                StringBuilder lb = new StringBuilder();
                for(String line : context.logManager.getLogTail()) {
                    if(line != null) lb.append(line);
                    else lb.append('\n');
                }
                ta.setText(lb.toString());
            };

            reloadLog.run();

            context.cachedExecutor.submit(() -> {
                while (listenToLog.get()) {
                    reloadLog.run();
                    
                    CountDownLatch cdl = new CountDownLatch(1);
                    context.logManager.getLogTail().addListenerLatch(cdl);
                    
                    try {
                        cdl.await();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(LogWindow.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        }
    }

    public static interface ProgressChangedCallback {
        public void progressChanged(int percent, String msg, boolean error);
    }
}

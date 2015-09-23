/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.util.CPRProgressBarUI;

/**
 *
 * @author teofil
 */
public class ProgressListPanel extends JPanel {
    private final List<ProgressPanel> progresses = new LinkedList<>();
    private final ProgressListPanel meProgressListPanel = this;
    private final Context context;

    public ProgressListPanel(Context context) {
        this.context = context;

        this.setPreferredSize(new Dimension(500, 50));
        this.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        this.setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

        context.eBus.register(this);
    }

    @Subscribe
    public void progressStarted(ProgressListPanel.ProgressEntity pe) {
        SwingUtilities.invokeLater(() -> {
            synchronized (progresses) {
                if (!pe.finished.get()) {
                    ProgressPanel pp = new ProgressPanel(pe);
                    progresses.add(pp);
                    this.add(pp);
                }
                revalidate();
                repaint();
            }
        });
    }

    private class ProgressPanel extends JProgressBar {
        private final ProgressEntity progressEntity;

        public ProgressPanel(ProgressEntity progressEntity) {
            super(0, 100);

            this.progressEntity = progressEntity;
            progressEntity.progressPanelRef.set(this);

            this.setUI(new CPRProgressBarUI());
            this.setBorderPainted(true);
            this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            this.setStringPainted(true);

            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (progressEntity.associatedPanel.get() != null) {
                        context.eBus.post(new ChangeMainPanel(progressEntity.associatedPanel.get()));
                    }
                }
            });

            changed();
        }

        private void changed() {
            SwingUtilities.invokeLater(() -> {
                String text = progressEntity.text.get();
                if (text != null) {
                    this.setString(text);
                    this.setToolTipText(text);
                }

                this.setValue(progressEntity.percent.get());

                if (progressEntity.error.get()) {
                    this.setForeground(Color.RED);
                }

                revalidate();
                repaint();
            });
        }

        private void finished() {
            SwingUtilities.invokeLater(() -> {
                System.out.println("Removing from progress panel: " + progressEntity.text.get());
                meProgressListPanel.remove(this);
                meProgressListPanel.revalidate();
                meProgressListPanel.repaint();
            });
        }
    }

    public static class ProgressEntity {
        private final AtomicReference<ProgressPanel> progressPanelRef = new AtomicReference<>(null);
        private final AtomicInteger percent = new AtomicInteger();
        private final AtomicReference<String> text = new AtomicReference<>("");
        private final AtomicReference<MainPanel> associatedPanel = new AtomicReference<>(null);
        private final AtomicBoolean error = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);

        public ProgressEntity() {

        }

        public void setPercent(int percent) {
            this.percent.set(percent);
            ProgressPanel ppSafe = progressPanelRef.get();
            if (ppSafe != null) {
                ppSafe.changed();
            }
        }

        public void setText(String text) {
            this.text.set(text);
            ProgressPanel ppSafe = progressPanelRef.get();
            if (ppSafe != null) {
                ppSafe.changed();
            }
        }

        public void markFinished() {
            finished.set(true);

            ProgressPanel ppSafe = progressPanelRef.get();
            if (ppSafe != null) {
                System.out.println("Marking finished: " + text.get());
                ppSafe.finished();
            }
        }

        public void markError() {
            error.set(true);
            ProgressPanel ppSafe = progressPanelRef.get();
            if (ppSafe != null) {
                ppSafe.changed();
            }
        }

        public void setAssociatedPanel(MainPanel mp) {
            associatedPanel.set(mp);
        }
    }
}

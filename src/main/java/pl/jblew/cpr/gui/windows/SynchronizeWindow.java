/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.windows;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.panels.EventPanel;
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.gui.util.CPRProgressBarUI;
import pl.jblew.cpr.gui.util.PanelDisabler;
import pl.jblew.cpr.gui.util.ValidableLabel;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.io.Exporter;

/**
 *
 * @author teofil
 */
public class SynchronizeWindow {
    private final Carrier carrier;
    private final Context context;
    private final JFrame frame;
    private final AtomicBoolean windowCloseEnabled = new AtomicBoolean(true);
    private final File deviceRoot;
    private final AtomicReference<Carrier> targetCarrier = new AtomicReference<>(null);

    public SynchronizeWindow(Context context, Carrier carrier) {
        this.carrier = carrier;
        this.context = context;

        //hold device root to prevent disconnecting
        deviceRoot = context.deviceDetector.getDeviceRoot(carrier.getName());

        this.frame = new JFrame("Synchronizacja \"" + carrier.getName() + "\"");

        SwingUtilities.invokeLater(() -> {
            frame.setSize(500, 500);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new MainPanel());
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent evt) {
                    if (windowCloseEnabled.get()) {
                        frame.setVisible(false);
                    } else {
                        frame.setVisible(true);
                    }
                }
            });

            frame.setVisible(true);
        });

    }

    private final class MainPanel extends JPanel {
        public MainPanel() {
            Event_Localization[] eventLocalizations = carrier.getEvents(context);

            //Exporter exporter = new Exporter(context, event);
            ProgressListPanel.ProgressEntity progressEntity = new ProgressListPanel.ProgressEntity();

            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

            JPanel infoPanel = new JPanel(new FlowLayout());
            JPanel destinationSelectionPanel = new JPanel(new FlowLayout());
            JPanel confirmationPanel = new JPanel(new FlowLayout());
            JPanel progressPanel = new JPanel(new FlowLayout());
            JPanel finishPanel = new JPanel(new FlowLayout());

            JComboBox devicesCombo = new JComboBox(new String[]{"Wybierz urządzenie"});

            /**
             *
             *
             *
             *
             * INFO PANEL *
             */
            JLabel infoLabel = new JLabel("<html><p width=\"480\">Tworzenie kopii WSZYSTKICH wydarzeń na nośniku <b>" + carrier.getName() + "</b>. "
                    + "Te z wydarzeń, które już wcześniej skopiowano na nośnik nie zostaną skopiowane powtórnie.</p>");
            infoPanel.add(infoLabel);

            /**
             *
             *
             *
             *
             *
             * DESTINATION PANEL *
             */
            JLabel destinationSelectionLabel = new JLabel("Na jakim urządzeniu zapisać?");
            destinationSelectionPanel.add(destinationSelectionLabel);

            DefaultComboBoxModel<String> comboModel = new DefaultComboBoxModel();

            Runnable updateCombo = () -> {
                SwingUtilities.invokeLater(() -> {
                    comboModel.removeAllElements();

                    Arrays.stream(context.deviceDetector.getConnectedOfCarriers(Carrier.getAllCarriers(context)))
                            .filter(c -> c.getId() != carrier.getId()).forEachOrdered(c -> comboModel.addElement(c.getName()));

                });

            };
            context.deviceDetector.addWeakStorageDevicePresenceListener(new StorageDevicePresenceListener() {
                @Override
                public void storageDeviceConnected(File rootFile, String deviceName) {
                    updateCombo.run();
                }

                @Override
                public void storageDeviceDisconnected(File rootFile, String deviceName) {
                    updateCombo.run();
                }
            });
            updateCombo.run();
            devicesCombo.setModel(comboModel);

            JLabel comboErrorLabel = new JLabel("");

            comboErrorLabel.setForeground(Color.RED);

            ActionListener l = (ActionEvent e) -> {
                SwingUtilities.invokeLater(() -> {
                    Object selected_ = devicesCombo.getSelectedItem();
                    if (selected_ != null && selected_ instanceof String && !((String) selected_).isEmpty()) {
                        String selected = (String) selected_;
                        Carrier selectedCarrier = Carrier.forName(context, selected);
                        targetCarrier.set(selectedCarrier);

                        if (selectedCarrier.isConnected(context)) {
                            PanelDisabler.setEnabled(confirmationPanel, true);
                        } else {
                            PanelDisabler.setEnabled(confirmationPanel, false);
                        }
                    } else {
                        PanelDisabler.setEnabled(confirmationPanel, false);
                    }
                });
            };
            l.actionPerformed(null);
            devicesCombo.addActionListener(l);

            destinationSelectionPanel.add(devicesCombo);
            destinationSelectionPanel.add(comboErrorLabel);

            /**
             *
             *
             *
             *
             *
             * CONFIRMATION PANEL *
             */
            JLabel confirmationLabel = new JLabel("Czy jesteś pewien?");
            confirmationPanel.add(confirmationLabel);

            JButton confirmationButton = new JButton("Rozpocznij kopiowanie");
            confirmationPanel.add(confirmationButton);

            /**
             *
             *
             *
             *
             *
             * PROGRESS PANEL *
             */
            progressPanel.setLayout(new BorderLayout());
            progressPanel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));

            JProgressBar progressBar = new JProgressBar(0, 100);

            progressBar.setUI(new CPRProgressBarUI());
            progressBar.setStringPainted(true);
            progressBar.setString("Ładowanie...");
            progressEntity.setText("(Eksport) Ładowanie...");
            progressPanel.add(progressBar, BorderLayout.CENTER);

            JButton finishButton = new JButton("Zamknij okno");
            finishPanel.add(finishButton);
            finishButton.addActionListener((evt) -> {
                context.eBus.post(new EventsNode.EventsListChanged());
                frame.setVisible(false);
            });

            /**
             *
             *
             *
             *
             * ADD PANELS *
             */
            add(infoPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(destinationSelectionPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(confirmationPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(progressPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(finishPanel);

            PanelDisabler.setEnabled(confirmationPanel, false);
            PanelDisabler.setEnabled(progressPanel, false);
            PanelDisabler.setEnabled(finishPanel, false);

            /**
             *
             *
             *
             *
             *
             * ACTION!
             */
            confirmationButton.addActionListener((evt) -> {
                Carrier selectedCarrier = targetCarrier.get();
                if (selectedCarrier != null) {
                    PanelDisabler.setEnabled(destinationSelectionPanel, false);
                    PanelDisabler.setEnabled(confirmationPanel, false);
                    PanelDisabler.setEnabled(progressPanel, true);
                    windowCloseEnabled.set(false);

                    context.eBus.post(progressEntity);

                    context.cachedExecutor.submit(() -> {
                        int numOfDone = 0;
                        for (int i = 0; i < eventLocalizations.length; i++) {//one localization for each event
                            Event_Localization el = eventLocalizations[i];
                            Event event = el.getOrLoadFullEvent(context);

                            int i_ = i;
                            int numOfDone_ = numOfDone;
                            SwingUtilities.invokeLater(() -> {
                                progressEntity.setText("Kopiowanie " + event.getName() + ". Nieudane: " + (i_ - numOfDone_));
                                progressBar.setString("Kopiowanie " + event.getName() + ". Nieudane: " + (i_ - numOfDone_));
                                Logger.getLogger(getClass().getName()).info("Kopiowanie " + event.getName() + ". Nieudane: " + (i_ - numOfDone_));
                            });

                            Exporter exporter = new Exporter(context, event);
                            exporter.setProgressChangedCallback((percent, msg, error) -> {
                            });
                            try {
                                exporter.tryDevice(context, selectedCarrier, event);
                                exporter.setTargetCarrier(selectedCarrier);

                                if (!event.getLocalizations().stream()
                                        .filter(lEl -> lEl.getCarrierId() == selectedCarrier.getId())
                                        .findAny().isPresent()) {

                                    CountDownLatch awaiterLatch = new CountDownLatch(1);
                                    exporter.startAsync(() -> {
                                        awaiterLatch.countDown();
                                    });
                                    awaiterLatch.await();
                                }

                                int percent = (int) ((float) (i + 1) / (float) eventLocalizations.length * 100f);

                                SwingUtilities.invokeLater(() -> {
                                    progressEntity.setPercent(percent);
                                    progressBar.setValue(percent);
                                });

                                numOfDone++;
                            } catch (Exporter.DeviceNotWritableException ex) {
                                comboErrorLabel.setText("Urządzenie jest niezapisywalne.");
                                Logger.getLogger(SynchronizeWindow.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (Exporter.NotEnoughSpaceException ex) {
                                comboErrorLabel.setText("Zbyt mało miejsca na tym urządzeniu.");
                                Logger.getLogger(SynchronizeWindow.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (Exception ex) {
                                comboErrorLabel.setText(ex.toString());
                                Logger.getLogger(SynchronizeWindow.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        int numOfDone_ = numOfDone;
                        SwingUtilities.invokeLater(() -> {
                            progressEntity.setText("Zakończono. Nieudane: " + (eventLocalizations.length - numOfDone_));
                            progressBar.setString("Zakończono. Nieudane: " + (eventLocalizations.length - numOfDone_));
                            PanelDisabler.setEnabled(finishPanel, true);
                            progressEntity.markFinished();
                            windowCloseEnabled.set(true);
                        });
                    });
                }
            });
        }
    }

    public static interface ProgressChangedCallback {
        public void progressChanged(int percent, String msg, boolean error);
    }
}

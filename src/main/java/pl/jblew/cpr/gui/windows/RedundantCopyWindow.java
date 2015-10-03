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
import java.util.concurrent.atomic.AtomicBoolean;
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
import pl.jblew.cpr.logic.io.Exporter;

/**
 *
 * @author teofil
 */
public class RedundantCopyWindow {
    private final Event event;
    private final Context context;
    private final JFrame frame;
    private final AtomicBoolean windowCloseEnabled = new AtomicBoolean(true);

    public RedundantCopyWindow(Context context, Event event) {
        this.event = event;
        this.context = context;

        this.frame = new JFrame("Tworzenie kopii \"" + event.getName() + "\"");

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
                    }
                    else {
                        frame.setVisible(true);
                    }
                }
            });

            frame.setVisible(true);
        });

    }

    private final class MainPanel extends JPanel {
        public MainPanel() {
            Carrier[] carriers = event.getLocalizations().stream().map(el -> el.getCarrier(context)).filter(c -> c != null).toArray(Carrier[]::new);
            Exporter exporter = new Exporter(context, event);

            ProgressListPanel.ProgressEntity progressEntity = new ProgressListPanel.ProgressEntity();

            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

            JPanel infoPanel = new JPanel(new FlowLayout());
            JPanel fileAccessibilityPanel = new JPanel(new FlowLayout());
            JPanel destinationSelectionPanel = new JPanel(new FlowLayout());
            JPanel confirmationPanel = new JPanel(new FlowLayout());
            JPanel progressPanel = new JPanel(new FlowLayout());
            JPanel finishPanel = new JPanel(new FlowLayout());

            JComboBox devicesCombo = new JComboBox(new String[]{"Wybierz urządzenie"});
            devicesCombo.setEnabled(false);

            /**
             *
             *
             *
             *
             * INFO PANEL *
             */
            String devicesListS_ = Arrays.stream(carriers).map(c -> c.getName()).reduce("", (a, b) -> a + ", " + b);
            if (!devicesListS_.isEmpty()) {
                devicesListS_ = devicesListS_.substring(2);
            }
            String devicesListS = devicesListS_;
            JLabel infoLabel = new JLabel("<html><p width=\"480\">Tworzenie kopii <b>" + event.getName() + "</b> "
                    + " będzie wymagało podłączenia jednego z nośników: " + devicesListS + ", oraz innego nośnika,"
                    + " na którym jeszcze nie zapisano tego wydarzenia.</p>");
            infoPanel.add(infoLabel);

            /**
             *
             *
             *
             *
             * FILE ACCESSIBILITY PANEL *
             */
            ValidableLabel connectedLabel = new ValidableLabel(false, "Podłącz jeden z nośników: " + devicesListS);

            Runnable checker = () -> {
                Carrier[] connectedCarriers = context.deviceDetector.getConnectedOfCarriers(carriers);
                if (connectedCarriers.length > 0) {
                    SwingUtilities.invokeLater(() -> {
                        connectedLabel.setValid_TSafe(true);
                        connectedLabel.setText("Podłączono \"" + connectedCarriers[0].getName() + "\"");

                        devicesCombo.setEnabled(true);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        connectedLabel.setValid_TSafe(false);
                        connectedLabel.setText("Podłącz jeden z nośników: " + devicesListS);

                        devicesCombo.setEnabled(false);
                    });
                }
            };
            context.deviceDetector.addWeakStorageDevicePresenceListener(new StorageDevicePresenceListener() {
                @Override
                public void storageDeviceConnected(File rootFile, String deviceName) {
                    checker.run();
                }

                @Override
                public void storageDeviceDisconnected(File rootFile, String deviceName) {
                    checker.run();
                }
            });
            checker.run();

            fileAccessibilityPanel.add(connectedLabel);

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

                    List<Carrier> restrictedCarriers = Arrays.asList(carriers);//carriers which already have this event
                    Arrays.stream(context.deviceDetector.getConnectedOfCarriers(Carrier.getAllCarriers(context)))
                            .filter(c -> !restrictedCarriers.contains(c)).forEachOrdered(c -> comboModel.addElement(c.getName()));

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

                        if (selectedCarrier.isConnected(context)) {
                            boolean enableNext = false;
                            try {
                                exporter.tryDevice(context, selectedCarrier, event);
                                exporter.setTargetCarrier(selectedCarrier);
                                enableNext = true;
                                comboErrorLabel.setText("");
                            } catch (Exporter.DeviceNotWritableException ex) {
                                enableNext = false;
                                comboErrorLabel.setText("Urządzenie jest niezapisywalne.");
                            } catch (Exporter.NotEnoughSpaceException ex) {
                                enableNext = false;
                                comboErrorLabel.setText("Zbyt mało miejsca na tym urządzeniu.");
                            } catch (Exception ex) {
                                enableNext = false;
                                comboErrorLabel.setText(ex.toString());
                            }

                            if (enableNext) {
                                PanelDisabler.setEnabled(confirmationPanel, true);
                            } else {
                                PanelDisabler.setEnabled(confirmationPanel, false);
                            }
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
            JLabel confirmationLabel = new JLabel("Zapisujesz " + exporter.getNumOfFiles() + " plików. Kontynuować?");
            confirmationPanel.add(confirmationLabel);

            JButton confirmationButton = new JButton("Zapisz pliki na urządzeniu");
            confirmationPanel.add(confirmationButton);
            confirmationButton.addActionListener((evt) -> {
                PanelDisabler.setEnabled(destinationSelectionPanel, false);
                PanelDisabler.setEnabled(confirmationPanel, false);
                PanelDisabler.setEnabled(progressPanel, true);
                windowCloseEnabled.set(false);

                context.eBus.post(progressEntity);

                exporter.startAsync();
            });

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

            exporter.setProgressChangedCallback((value, maximum, msg, error) -> {
                progressBar.setString(msg);
                progressEntity.setText("(Eksport) " + msg);

                progressBar.setValue(value);
                progressBar.setMaximum(maximum);
                progressEntity.setValue(value, maximum);

                if (value == maximum) {
                    progressEntity.markFinished();
                    PanelDisabler.setEnabled(progressPanel, false);
                    PanelDisabler.setEnabled(finishPanel, true);
                    windowCloseEnabled.set(true);
                }
                if (error) {
                    progressEntity.markError();
                    windowCloseEnabled.set(true);
                }
            });

            JButton finishButton = new JButton("Zamknij okno");
            finishPanel.add(finishButton);
            finishButton.addActionListener((evt) -> {
                context.eBus.post(new EventsNode.EventsListChanged());
                context.eBus.post(new ChangeMainPanel(new EventPanel(context, event)));
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
            add(fileAccessibilityPanel);
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
        }
    }

    public static interface ProgressChangedCallback {
        public void progressChanged(int value, int maximum, String msg, boolean error);
    }
}

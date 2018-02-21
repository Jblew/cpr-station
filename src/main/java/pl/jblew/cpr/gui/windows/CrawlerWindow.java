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
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.gui.util.CPRProgressBarUI;
import pl.jblew.cpr.gui.util.PanelDisabler;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.dircrawler.DirCrawler;
import pl.jblew.cpr.logic.io.Exporter;

/**
 *
 * @author teofil
 */
public class CrawlerWindow {
    private final Context context;
    private final JFrame frame;
    private final AtomicBoolean windowCloseEnabled = new AtomicBoolean(true);
    private final File deviceRoot;
    private final String deviceName;
    private final AtomicReference<Carrier> targetCarrier = new AtomicReference<>(null);

    public CrawlerWindow(Context context, String deviceName, File deviceRoot) {
        this.deviceRoot = deviceRoot;
        this.deviceName = deviceName;
        this.context = context;

        this.frame = new JFrame("Całkowite sprawdzanie dysku \"" + deviceName + "\"");

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
            JLabel infoLabel = new JLabel("<html><p width=\"480\">Sprawdzanie, czy na dysku <b>" + deviceName + "</b> znajdują"
                    + "się jakiekolwiek zdjęcia. Jeżeli wybierzesz nośnik z listy, brakujące zdjęcia zostaną zaimportowane."
                    + "Te ze zdjęć, które już znajdują się w bazie będą pominięte. Możesz przerwać w dowolnym momencie, ale wtedy"
                    + " konieczne może być ponowne uruchomienie sprawdzania, aby się upewnić, że cały dysk jest sprawdzony.</p>");
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
            String doNotImportString = "Nie importuj";

            Runnable updateCombo = () -> {
                SwingUtilities.invokeLater(() -> {
                    comboModel.removeAllElements();

                    comboModel.addElement(doNotImportString);

                    Arrays.stream(context.deviceDetector.getConnectedOfCarriers(Carrier.getAllCarriers(context)))
                            .forEachOrdered(c -> comboModel.addElement(c.getName()));

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

                        if (selectedCarrier == null || selectedCarrier.isConnected(context)) {
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

            JButton confirmationButton = new JButton("Rozpocznij całkowite sprawdzanie");
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
            progressEntity.setText("(Crawler) Ładowanie...");
            progressPanel.add(progressBar, BorderLayout.NORTH);

            JButton finishButton = new JButton("Zamknij okno");
            finishPanel.add(finishButton);
            finishButton.addActionListener((evt) -> {
                context.eBus.post(new EventsNode.EventsListChanged());
                frame.setVisible(false);
            });

            JTextArea logTextArea = new JTextArea();
            logTextArea.setEditable(false);

            JScrollPane scrollPane = new JScrollPane(logTextArea);

            progressPanel.add(scrollPane, BorderLayout.CENTER);

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
                System.out.println("0");
                Carrier selectedCarrier = targetCarrier.get();//may be null
                
                PanelDisabler.setEnabled(destinationSelectionPanel, false);
                PanelDisabler.setEnabled(confirmationPanel, false);
                PanelDisabler.setEnabled(progressPanel, true);
                windowCloseEnabled.set(false);

                
                context.eBus.post(progressEntity);
                

                context.cachedExecutor.submit(() -> {
                    DirCrawler crawler = new DirCrawler(context, deviceRoot);

                    crawler.setCallback((value, maximum, progressBarMsg, logMsg, error) -> {
                        SwingUtilities.invokeLater(() -> {
                            progressEntity.setValue(value, maximum);
                            progressEntity.setText(progressBarMsg);

                            progressBar.setValue(value);
                            progressBar.setMaximum(maximum);
                            progressBar.setString(progressBarMsg);

                            logTextArea.setText(logTextArea.getText() + "\n" + logMsg);

                            if (error) {
                                progressEntity.markError();
                            }
                            if (value == maximum) {
                                PanelDisabler.setEnabled(finishPanel, true);
                                progressEntity.markFinished();
                                windowCloseEnabled.set(true);
                            }
                        });
                    });

                    crawler.crawlAsync(selectedCarrier);
                });

            });
        }
    }

    public static interface ProgressChangedCallback {
        public void progressChanged(int value, int maximum, String progressBarMsg, String logMsg, boolean error);
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.IconLoader;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.io.Exporter;
import pl.jblew.cpr.logic.io.Importer;
import pl.jblew.cpr.util.FileSizeFormatter;
import pl.jblew.cpr.util.NamingThreadFactory;
import pl.jblew.cpr.gui.util.PanelDisabler;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class ExportPanel extends MainPanel {
    private final JPanel fileAccessibilityPanel;
    private final JPanel destinationSelectionPanel;
    private final JPanel confirmationPanel;
    private final JPanel progressPanel;
    private final JPanel finishPanel;
    private final JLabel destinationSelectionLabel;
    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("ExportPanel-asyncExecutor"));

    public ExportPanel(Context context, Event event) {
        fileAccessibilityPanel = new JPanel();
        destinationSelectionPanel = new JPanel();
        confirmationPanel = new JPanel();
        progressPanel = new JPanel();
        finishPanel = new JPanel();
        destinationSelectionLabel = new JLabel();

        JLabel confirmationLabel = new JLabel();

        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

        add(fileAccessibilityPanel);
        add(new JSeparator(SwingConstants.HORIZONTAL));
        add(destinationSelectionPanel);
        add(new JSeparator(SwingConstants.HORIZONTAL));
        add(confirmationPanel);
        add(new JSeparator(SwingConstants.HORIZONTAL));
        add(progressPanel);
        add(new JSeparator(SwingConstants.HORIZONTAL));
        add(finishPanel);

        Exporter exporter = new Exporter(context, event);
        final int numOfFiles = event.getFiles(context).length;

        /**
         * ACCESSIBILITY PANEL*
         */
        fileAccessibilityPanel.setLayout(new BoxLayout(fileAccessibilityPanel, BoxLayout.PAGE_AXIS));
        JLabel accessibilityLabel = new JLabel("Eksportujesz " + numOfFiles + " plików. \n"
                + "Sprawdzam, czy wszystkie z nich są dostępne na podłączonych urządzeniach.");

        fileAccessibilityPanel.add(accessibilityLabel);

        checkFileAccessibility(exporter, numOfFiles, accessibilityLabel);
        context.deviceDetector.addStorageDevicePresenceListener(new StorageDevicePresenceListener() {
            @Override
            public void storageDeviceConnected(File rootFile, String deviceName) {
                checkFileAccessibility(exporter, numOfFiles, accessibilityLabel);
            }

            @Override
            public void storageDeviceDisconnected(File rootFile, String deviceName) {
            }

        });

        /**
         * DESTINATION SELECTION PANEL*
         */
        destinationSelectionPanel.setLayout(new FlowLayout());

        destinationSelectionLabel.setText("Na jakim urządzeniu zapisać?");
        destinationSelectionPanel.add(destinationSelectionLabel);

        final List<TwoTuple<String, File>> devicesList = context.deviceDetector.getConnectedDevices();
        String[] deviceNames = devicesList.stream().map(deviceTuple -> deviceTuple.getA()).toArray(String[]::new);

        final JComboBox devicesCombo = new JComboBox(deviceNames);
        final JLabel comboErrorLabel = new JLabel("");
        comboErrorLabel.setForeground(Color.RED);

        ActionListener l = (ActionEvent e) -> {
            SwingUtilities.invokeLater(() -> {
                TwoTuple<String, File> device = devicesList.get(devicesCombo.getSelectedIndex());
                boolean enableNext = false;
                try {
                    exporter.tryDevice(context, device.getA(), device.getB(), event);
                    enableNext = true;
                    comboErrorLabel.setText("");
                } catch (Exporter.DeviceNotWritableException ex) {
                    enableNext = false;
                    comboErrorLabel.setText("Urządzenie jest niezapisywalne.");
                } catch (Exporter.NotEnoughSpaceException ex) {
                    enableNext = false;
                    comboErrorLabel.setText("Zbyt mało miejsca na tym urządzeniu.");
                } catch (Exporter.FileAlreadyExists ex) {
                    enableNext = false;
                    comboErrorLabel.setText("Wydarzenie już zapisano na tym nośniku.");
                }
                
                if (enableNext) {
                    confirmationLabel.setText("Zapisujesz " + numOfFiles + " plików (" + FileSizeFormatter.format(exporter.getSize()) + ") na "
                            + device.getA() + ". Kontynuować?");
                    //PanelDisabler.setEnabled(destinationSelectionPanel, false);
                    PanelDisabler.setEnabled(confirmationPanel, true);
                    exporter.setDevice(device);
                }
            });
        };
        l.actionPerformed(null);
        devicesCombo.addActionListener(l);

        destinationSelectionPanel.add(devicesCombo);
        
        destinationSelectionPanel.add(comboErrorLabel);

        /**
         * CONFIRMATION PANEL*
         */
        confirmationPanel.setLayout(new FlowLayout());
        confirmationPanel.add(confirmationLabel);
        confirmationLabel.setText("Zapisujesz " + numOfFiles + " plików. Kontynuować?");

        JButton confirmationButton = new JButton("Zapisz pliki na urządzeniu");
        confirmationPanel.add(confirmationButton);
        confirmationButton.addActionListener((evt) -> {
            PanelDisabler.setEnabled(destinationSelectionPanel, false);
            PanelDisabler.setEnabled(confirmationPanel, false);
            PanelDisabler.setEnabled(progressPanel, true);
            
            exporter.startAsync();
        });
        
        /** PROGRESS PANEL **/
        progressPanel.setLayout(new BorderLayout());
        progressPanel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ładowanie...");
        progressPanel.add(progressBar, BorderLayout.CENTER);
        
        exporter.setProgressChangedCallback((percent, msg) -> {
            progressBar.setString(msg);
            progressBar.setValue(percent);
            
            if(percent == 100) {
                PanelDisabler.setEnabled(progressPanel, false);
        PanelDisabler.setEnabled(finishPanel, true);
            }
        });
        /** FINISH PANEL **/
        finishPanel.setLayout(new FlowLayout());
        finishPanel.add(new JLabel("Gotowe!"));

        PanelDisabler.setEnabled(fileAccessibilityPanel, true);
        PanelDisabler.setEnabled(destinationSelectionPanel, false);
        PanelDisabler.setEnabled(confirmationPanel, false);
        PanelDisabler.setEnabled(progressPanel, false);
        PanelDisabler.setEnabled(finishPanel, false);
    }

    @Override
    public void activate() {
    }

    @Override
    public void inactivate() {
    }

    private void checkFileAccessibility(Exporter exporter, int numOfFiles, JLabel accessibilityLabel) {
        asyncExecutor.submit(() -> {
            Map<Carrier, Integer> availableCarriers = exporter.checkFileAccessibilityAndGetMissingCarriers();
            SwingUtilities.invokeLater(() -> {
                final StringBuilder msg = new StringBuilder("Eksportujesz " + numOfFiles + " plików\n");
                if (availableCarriers.isEmpty()) {
                    msg.append("Wszystkie są dostępne na urządzeniach.\n");
                    PanelDisabler.setEnabled(fileAccessibilityPanel, false);
                    PanelDisabler.setEnabled(destinationSelectionPanel, true);
                    accessibilityLabel.setIcon(IconLoader.OK_16.load());

                    asyncExecutor.submit(() -> {
                        final long size = exporter.calculateSize();
                        SwingUtilities.invokeLater(() -> {
                            destinationSelectionLabel.setText("Na jakim urządzeniu zapisać? (" + FileSizeFormatter.format(size) + ")");
                        });
                    });
                } else {
                    msg.append("Musisz podłączyć któreś z poniższych urządzeń: \n");
                    availableCarriers.forEach((c, num) -> {
                        msg.append("#> " + c.getName() + " (" + (num < 0 ? "Wszystkie pliki" : num + " plików") + ")\n");
                    });
                    accessibilityLabel.setIcon(IconLoader.ERROR_16.load());
                }
                accessibilityLabel.setText(msg.toString());

            });
        });
    }

    public static interface ProgressChangedCallback {
        public void progressChanged(int percent, String msg);
    }
}

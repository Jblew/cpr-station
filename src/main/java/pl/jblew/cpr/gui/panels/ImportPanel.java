/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicProgressBarUI;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.logic.io.Importer;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.util.CPRProgressBarUI;
import pl.jblew.cpr.gui.util.PanelDisabler;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.util.FileSizeFormatter;

/**
 *
 * @author teofil
 */
public class ImportPanel extends MainPanel {
    private final Context context;
    private final Importer importer;

    public ImportPanel(Context context_, final File[] filesToImport_) {
        long sT = System.currentTimeMillis();
        
        this.context = context_;
        this.importer = new Importer(context, filesToImport_);

        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

        JPanel infoPanel = new JPanel(new FlowLayout());
        JPanel deviceSelectionPanel = new JPanel(new FlowLayout());
        JPanel eventNamePanel = new JPanel(new FlowLayout());
        JPanel progressPanel = new JPanel(new BorderLayout());
        JPanel finishPanel = new JPanel(new FlowLayout());

        JProgressBar progressBar = new JProgressBar();
        progressBar.setUI(new CPRProgressBarUI());
        
        /**
         * INFO PANEL *
         */
        String dirs = Arrays.stream(importer.getFilesToImport()).map(f -> f.getParent()).distinct().reduce("", (a, b) -> a + ", " + b);
        infoPanel.add(new JLabel("<html>Importowanie <b>" + importer.getFilesToImport().length + "</b> plików (<b>" + FileSizeFormatter.format(importer.getSize()) + "</b>) w katalogach: [" + dirs + "]."));
        
        /**
         * DEVICE SELECTION PANEL *
         */
        deviceSelectionPanel.add(new JLabel("Wybierz urządzenie: "));
        JLabel deviceSelectionErrorLabel = new JLabel();
        deviceSelectionErrorLabel.setForeground(Color.RED);
        final AtomicReference<String[]> devices = new AtomicReference<>(Arrays.stream(context.deviceDetector.getConnectedCarriers(Carrier.getAllCarriers(context))).map(c -> c.getName()).toArray(String[]::new));
        DefaultComboBoxModel deviceSelectionModel = new DefaultComboBoxModel(devices.get());
        JComboBox deviceSelection = new JComboBox(deviceSelectionModel);
        context.deviceDetector.addStorageDeviceChangeListener(() -> {
            deviceSelectionModel.removeAllElements();
            devices.set(Arrays.stream(context.deviceDetector.getConnectedCarriers(Carrier.getAllCarriers(context))).map(c -> c.getName()).toArray(String[]::new));
            Arrays.stream(devices.get()).forEach((name) -> deviceSelectionModel.addElement(name));
        });
        deviceSelection.addActionListener((evt) -> {
            String deviceName = devices.get()[deviceSelection.getSelectedIndex()];
            File deviceRoot = context.deviceDetector.getDeviceRoot(deviceName);
            try {
                importer.tryDevice(context, deviceName, deviceRoot);
                importer.setDevice(deviceName, deviceRoot);
                SwingUtilities.invokeLater(() -> {
                    deviceSelectionErrorLabel.setText("");
                    PanelDisabler.setEnabled(eventNamePanel, true);
                });

            } catch (Importer.DeviceNotWritableException ex) {
                SwingUtilities.invokeLater(() -> {
                    deviceSelectionErrorLabel.setText("Urządzenie jest niezapisywalne.");
                    PanelDisabler.setEnabled(eventNamePanel, false);
                });
            } catch (Importer.NotACarrierException ex) {
                SwingUtilities.invokeLater(() -> {
                    deviceSelectionErrorLabel.setText("Urządzenie nie jest nośnikiem (wejdź w panel urządzenia i kliknij \"Dodaj jako nośnik\", aby móc importować pliki na to urządzenie.");
                    PanelDisabler.setEnabled(eventNamePanel, false);
                });
            } catch (Importer.NotEnoughSpaceException ex) {
                SwingUtilities.invokeLater(() -> {
                    deviceSelectionErrorLabel.setText("Zbyt mało miejsca na tym urządzeniu.");
                    PanelDisabler.setEnabled(eventNamePanel, false);
                });
            }
        });
        deviceSelectionPanel.add(deviceSelection);
        deviceSelectionPanel.add(deviceSelectionErrorLabel);

        /**
         * EVENT NAME PANEL *
         */
        JLabel eventNameLabel = new JLabel("Podaj nazwę wydarzenia (w NIEPOSEGREGOWANYCH): ");
        eventNamePanel.add(eventNameLabel);

        JTextField eventNameField = new JTextField("[" + DateTimeFormatter.ofPattern("YYYY.MM.dd").format(LocalDateTime.now()) + "] ");
        eventNameField.setColumns(50);
        eventNamePanel.add(eventNameField);
        eventNamePanel.setPreferredSize(new Dimension(0, 200));

        LocalDateTime startTime = LocalDateTime.ofEpochSecond(importer.getFilesToImport()[0].lastModified() / 1000l, 0, ZoneOffset.UTC);
        LocalDateTime endTime = LocalDateTime.ofEpochSecond(importer.getFilesToImport()[importer.getFilesToImport().length - 1].lastModified() / 1000l, 0, ZoneOffset.UTC);
        JLabel timeRangeLabel = new JLabel("Zdjęcia zrobiono: " + DateTimeFormatter.ofPattern("YYYY.MM.dd").format(startTime) + " - " + DateTimeFormatter.ofPattern("YYYY.MM.dd").format(endTime));
        eventNamePanel.add(timeRangeLabel);

        JButton acceptButton = new JButton("Importuj >");
        eventNamePanel.add(acceptButton);
        acceptButton.addActionListener((evt) -> {
            SwingUtilities.invokeLater(() -> {
                importer.setEventName(eventNameField.getText());
                int option = JOptionPane.showConfirmDialog(context.frame, "<html>Zaimportować " + importer.getFilesToImport().length + " plików (" + FileSizeFormatter.format(importer.getSize()) + ")<br /> z katalogów [" + dirs + "]"
                        + " do wydarzenia <b>" + importer.getEventName() + "</b><br /> na <b>" + importer.getDeviceName() + "</b>?");
                if (option == JOptionPane.OK_OPTION) {
                    PanelDisabler.setEnabled(infoPanel, false);
                    PanelDisabler.setEnabled(deviceSelectionPanel, false);
                    PanelDisabler.setEnabled(eventNamePanel, false);
                    PanelDisabler.setEnabled(progressPanel, true);
                    importer.startAsync((final int percent, final String msg) -> {
                        SwingUtilities.invokeLater(() -> {
                            System.out.println("Progress bar: "+percent+"%, msg="+msg);
                            progressBar.setValue(percent);
                            progressBar.setString(msg);
                            progressBar.revalidate();
                            progressBar.repaint();
                            
                            if(percent == 100) {
                                PanelDisabler.setEnabled(progressPanel, false);
                                PanelDisabler.setEnabled(finishPanel, true);
                            }
                        });
                    });
                }
            });
        });

        /**
         * PROGRESS PANEL *
         */
        progressPanel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressBar.setStringPainted(true);
        progressBar.setString("Oczekiwanie...");
        
        /**
         * FINISH PANEL *
         */
        finishPanel.add(new JLabel("Gotowe!"));
        
        
        add(infoPanel);
        add(new JSeparator(JSeparator.HORIZONTAL));
        add(deviceSelectionPanel);
        add(new JSeparator(JSeparator.HORIZONTAL));
        add(eventNamePanel);
        add(new JSeparator(JSeparator.HORIZONTAL));
        add(progressPanel);
        add(new JSeparator(JSeparator.HORIZONTAL));
        add(finishPanel);
        
        

        PanelDisabler.setEnabled(infoPanel, true);
        PanelDisabler.setEnabled(deviceSelectionPanel, true);
        PanelDisabler.setEnabled(eventNamePanel, false);
        PanelDisabler.setEnabled(progressPanel, false);
        PanelDisabler.setEnabled(finishPanel, false);

                
        System.out.println("t(ImportPanel.new())"+(System.currentTimeMillis()-sT)+"ms");
    }

    @Override
    public void activate() {
        repaint();
    }

    @Override
    public void inactivate() {
    }
    
    public static interface ProgressChangedCallback {
        public void progressChanged(int percent, String msg);
    }
}

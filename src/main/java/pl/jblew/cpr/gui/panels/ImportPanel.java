/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.logic.io.Importer;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class ImportPanel extends MainPanel {
    private final Context context;
    private final List<File> filesToImport;
    private final String sources;
    private final File deviceRoot;
    private final AtomicInteger step = new AtomicInteger(0);

    public ImportPanel(Context context_, final File[] filesToImport_, String deviceName, File deviceRoot_) {
        this.context = context_;
        this.deviceRoot = deviceRoot_;

        this.filesToImport = Importer.getAllImportableFiles(filesToImport_);

        setLayout(new BorderLayout());

        JPanel navigationPanel = new JPanel();
        navigationPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        navigationPanel.setLayout(new BorderLayout());

        final JButton acceptButton = new JButton("Importuj");

        navigationPanel.add(new JPanel(), BorderLayout.CENTER);
        navigationPanel.add(acceptButton, BorderLayout.EAST);

        add(navigationPanel, BorderLayout.SOUTH);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        String sources_ = "";
        Set<File> dirs = new HashSet<>();
        for (File f : filesToImport) {
            File p = f.getParentFile();
            if (!dirs.contains(p)) {
                sources_ += "[" + p.getAbsolutePath() + "] ";
                dirs.add(p);
            }
        }
        sources = sources_;

        JLabel infoLabel = new JLabel("<html>Importowanie <b>" + filesToImport.size() + "</b> zdjęć z " + sources + ". Kontynuować?");
        add(infoLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();

        GridLayout settingsGridLayout = new GridLayout(3, 2);
        settingsGridLayout.setHgap(10);
        settingsGridLayout.setVgap(10);
        JPanel settingsGridPanel = new JPanel(settingsGridLayout);
        settingsGridPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        settingsGridPanel.add(new JLabel("Na jakim urządzeniu zapisać?"));

        final List<TwoTuple<String, File>> devicesList = context.deviceDetector.getConnectedDevices();

        String[] deviceNames = new String[devicesList.size()];

        int selectedIndex = -1;
        int i = 0;
        for (TwoTuple<String, File> device : devicesList) {
            deviceNames[i] = device.getA();
            if (device.getA().equals(deviceName)) {
                selectedIndex = i;
            }
            i++;
        }

        final JComboBox devicesCombo = new JComboBox(deviceNames);
        final JLabel comboErrorLabel = new JLabel("");
        comboErrorLabel.setForeground(Color.RED);
        if (selectedIndex >= 0) {
            devicesCombo.setSelectedIndex(selectedIndex);
        }

        devicesCombo.addActionListener((ActionEvent e) -> {
            SwingUtilities.invokeLater(() -> {
                TwoTuple<String, File> device = devicesList.get(devicesCombo.getSelectedIndex());
                try {
                    Importer.tryDevice(context, device.getA(), device.getB(), filesToImport);
                    acceptButton.setEnabled(true);
                    comboErrorLabel.setText("");
                } catch (Importer.DeviceNotWritableException ex) {
                    SwingUtilities.invokeLater(() -> {
                        acceptButton.setEnabled(false);
                        comboErrorLabel.setText("Urządzenie jest niezapisywalne.");
                    });
                } catch (Importer.NotACarrierException ex) {
                    SwingUtilities.invokeLater(() -> {
                        acceptButton.setEnabled(false);
                        comboErrorLabel.setText("Urządzenie nie jest nośnikiem (wejdź w panel urządzenia i kliknij \"Dodaj jako nośnik\", aby móc importować pliki na to urządzenie.");
                    });
                } catch (Importer.NotEnoughSpaceException ex) {
                    SwingUtilities.invokeLater(() -> {
                        acceptButton.setEnabled(false);
                        comboErrorLabel.setText("Zbyt mało miejsca na tym urządzeniu.");
                    });
                }
            });
        });

        settingsGridPanel.add(devicesCombo);
        settingsGridPanel.add(new JLabel(""));
        settingsGridPanel.add(comboErrorLabel);

        settingsGridPanel.add(new JLabel("Nazwa wydarzenia (w NIEPOSEGREGOWANYCH):"));

        GregorianCalendar now = new GregorianCalendar();
        final JTextField nameField = new JTextField("[" + now.get(Calendar.YEAR) + "." + now.get(Calendar.MONTH) + "." + now.get(Calendar.DAY_OF_MONTH) + "] " + deviceName);
        settingsGridPanel.add(nameField);

        centerPanel.add(settingsGridPanel);
        panel.add(centerPanel, BorderLayout.CENTER);

        add(panel, BorderLayout.CENTER);

        if (deviceNames.length == 0) {
            acceptButton.setEnabled(false);
            acceptButton.setText("Brak urządzeń");
        }

        acceptButton.addActionListener((ActionEvent e) -> {
            SwingUtilities.invokeLater(() -> {
                String msg = "Importowanie " + filesToImport.size() + " zdjęć z " + sources + ".\n"
                        + "Pliki zostaną zaimportowane do katalogu /NIEPOSEGREGOWANE/" + nameField.getText() + " "
                        + "na urządzeniu " + devicesCombo.getSelectedItem() + ".\n Kontynuować?";
                
                int n = JOptionPane.showConfirmDialog(context.frame,
                        msg, "Importowanie zdjęć",
                        JOptionPane.YES_NO_OPTION);
                if (n == JOptionPane.YES_OPTION) {
                    acceptButton.setEnabled(false);
                    showImportWindow(nameField.getText(), (String) devicesCombo.getSelectedItem(), devicesList.get(devicesCombo.getSelectedIndex()).getB());
                } else {
                    
                }
            });
        });
    }

    @Override
    public void activate() {
        repaint();
    }

    @Override
    public void inactivate() {
    }

    private void showImportWindow(String eventName, String deviceName, File deviceRoot) {
        JFrame importFrame = new JFrame("Importowanie zdjęć");
        importFrame.setSize(600, 170);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JLabel infoLabel = new JLabel("<html>Importowanie <b>" + filesToImport.size() + "</b> zdjęć z " + sources + ". "
                + "Pliki zostaną zaimportowane do katalogu <b>/NIEPOSEGREGOWANE/" + eventName + "</b> "
                + "na urządzeniu <b>" + deviceName + "</b>.");
        contentPanel.add(infoLabel, BorderLayout.NORTH);

        final JProgressBar progressBar = new JProgressBar(0, 100);
        //progressBar.setBorderPainted(true);
        //progressBar.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        contentPanel.add(progressBar, BorderLayout.CENTER);
        progressBar.setStringPainted(true);

        Importer imp = new Importer(context, filesToImport, deviceName, deviceRoot, eventName, (final int percent, final String msg) -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(percent);
                progressBar.setString(msg);
                progressBar.revalidate();
                progressBar.repaint();
                //System.out.println("Updating progress bar: "+percent+"% ["+msg+"]");
            });
        });
        progressBar.setString("Ładowanie...");
        imp.startAsync();

        importFrame.setContentPane(contentPanel);
        importFrame.setVisible(true);

    }

    public static interface ProgressChangedCallback {
        public void progressChanged(int percent, String msg);
    }
}

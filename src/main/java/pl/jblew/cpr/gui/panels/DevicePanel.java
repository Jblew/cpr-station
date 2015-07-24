/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.db.DatabaseDetector;
import pl.jblew.cpr.db.DatabaseManager;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.components.SwingFileBrowser;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.io.CarrierMaker;

/**
 *
 * @author teofil
 */
public class DevicePanel extends MainPanel {
    private final Context context;
    private final SwingFileBrowser browser;
    private final JProgressBar freeSpaceBar;
    private final String deviceName;
    private final File root;
    private final ExecutorService freeSpaceUpdateExecutor = Executors.newSingleThreadExecutor();

    public DevicePanel(Context context_, final String deviceName, File root_) {
        this.context = context_;
        this.deviceName = deviceName;
        this.root = root_;

        System.out.println("Changing main panel");

        setLayout(new BorderLayout());

        JPanel gridPanel = new JPanel();
        gridPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridLayout gridLayout = new GridLayout(0, 2);
        gridLayout.setHgap(10);
        gridLayout.setVgap(10);
        gridPanel.setLayout(gridLayout);
        prepareGridRow(gridPanel, "Urządzenie: ", new JLabel(deviceName));
        prepareGridRow(gridPanel, "Ścieżka: ", new JLabel(root.getAbsolutePath()));

        final JButton createStructureButton = new JButton("Dodaj jako nośnik i przygotuj katalogi");

        try {
            Carrier carrier = CarrierMaker.getAndCheckCarrier(context, deviceName, root);
            if (carrier != null) {
                createStructureButton.setEnabled(false);
            } else {
                CarrierMaker.tryMakeCarrier(context, deviceName, root);
            }
        } catch (CarrierMaker.BadFileStructureException ex) {
            createStructureButton.setForeground(Color.RED);
            createStructureButton.setText("Napraw katalogi tego nośnika");
        } catch (CarrierMaker.CarrierNotWritableException ex) {
            createStructureButton.setForeground(Color.RED);
            createStructureButton.setText("Dysk jest niezapisywalny");
            createStructureButton.setEnabled(false);
        } catch (CarrierMaker.NotConnectedToDatabaseException | DatabaseManager.DBNotConnectedException ex) {
            createStructureButton.setForeground(Color.RED);
            createStructureButton.setText("Nie połączono z bazą danych");
            createStructureButton.setEnabled(false);
        }

        createStructureButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    CarrierMaker.makeCarrier(context, deviceName, root);

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            createStructureButton.setEnabled(false);
                            createStructureButton.setForeground(Color.GREEN.darker());
                            browser.changeCWD(browser.getCWD());
                        }
                    });
                } catch (CarrierMaker.CannotCreateFileStructureException ex) {
                    createStructureButton.setEnabled(false);
                    createStructureButton.setForeground(Color.RED);
                    createStructureButton.setText("Nie można stworzyć struktury plików");
                } catch (CarrierMaker.CarrierNotWritableException ex) {
                    createStructureButton.setEnabled(false);
                    createStructureButton.setForeground(Color.RED);
                    createStructureButton.setText("Urządzenie nie jest zapisywalne");
                } catch (CarrierMaker.NotConnectedToDatabaseException ex) {
                    createStructureButton.setEnabled(false);
                    createStructureButton.setForeground(Color.RED);
                    createStructureButton.setText("Nie połączono z bazą danych");
                } catch (CarrierMaker.CannotSaveCarrierToDatabase ex) {
                    createStructureButton.setEnabled(false);
                    createStructureButton.setForeground(Color.RED);
                    createStructureButton.setText("Nie można zapisać do bazy danych");
                }
            }
        });

        final JButton createDbButton = new JButton("Stwórz tu bazę danych");
        if (context.dbManager.isConnected()) {
            createDbButton.setEnabled(false);
            createDbButton.setToolTipText("Najpierw odłącz poprzednią bazę");
        } else {
            createDbButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    context.dbManager.createAndConnect(root, deviceName);
                    if (context.dbManager.isConnected()) {
                        createDbButton.setEnabled(false);
                        createDbButton.setForeground(Color.GREEN.darker());
                    }
                }
            });
        }

        JButton backupDbButton = new JButton("Zgraj tu kopię zapasową bazy danych");

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridLayout buttonPanelLayout = new GridLayout(0, 1);
        buttonPanelLayout.setHgap(10);
        buttonPanelLayout.setVgap(10);
        buttonPanel.setLayout(buttonPanelLayout);
        buttonPanel.add(createStructureButton);
        buttonPanel.add(createDbButton);
        buttonPanel.add(backupDbButton);

        GridLayout northPanelLayout = new GridLayout(1, 2);
        northPanelLayout.setVgap(10);
        JPanel northPanel = new JPanel(northPanelLayout);
        northPanel.add(gridPanel);
        northPanel.add(buttonPanel);
        add(northPanel, BorderLayout.NORTH);

        JButton importSelectedButton = new JButton("Importuj zaznaczone");
        importSelectedButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        File[] selectedFiles = browser.getSelectedFiles();
                        if (selectedFiles.length > 0) {
                            context.eBus.post(new ChangeMainPanel(new ImportPanel(context, selectedFiles, deviceName, root)));
                        }
                    }
                });

            }
        });
        JButton importDirButton = new JButton("Importuj katalog i podkatalogi");
        importDirButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        context.eBus.post(new ChangeMainPanel(new ImportPanel(context, new File[]{browser.getCWD()}, deviceName, root)));
                    }
                });

            }
        });

        browser = new SwingFileBrowser(root);
        browser.addComponentToToolPanel(importSelectedButton);
        browser.addComponentToToolPanel(importDirButton);

        JPanel browserPanel = new JPanel();
        browserPanel.setLayout(new BorderLayout());
        browserPanel.add(browser, BorderLayout.CENTER);
        browserPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
        add(browserPanel, BorderLayout.CENTER);

        freeSpaceBar = new JProgressBar(0, 100);
        freeSpaceBar.setPreferredSize(new Dimension(1, 45));
        freeSpaceBar.setStringPainted(true);
        freeSpaceBar.setString("Odczytywanie ilości wolnego miejsca");
        freeSpaceBar.setValue(0);

        JPanel freeSpacePanel = new JPanel();
        freeSpacePanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        freeSpacePanel.setLayout(new BorderLayout());
        freeSpacePanel.add(freeSpaceBar, BorderLayout.CENTER);
        add(freeSpacePanel, BorderLayout.SOUTH);

        updateFreeSpaceAsync();
        System.out.println("carrier panel ready");
    }

    @Override
    public void activate() {
        repaint();
    }

    @Override
    public void inactivate() {
        browser.inactivate();
    }

    private void prepareGridRow(JPanel panel, String text, JComponent component) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("default", Font.BOLD, 16));
        label.setHorizontalAlignment(JLabel.RIGHT);
        panel.add(label);

        panel.add(component);
    }

    private void updateFreeSpaceAsync() {
        freeSpaceUpdateExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    long totalSpaceB = root.getTotalSpace();
                    long freeSpaceB = root.getUsableSpace();
                    long usedSpaceB = totalSpaceB - freeSpaceB;

                    int totalSpaceMB = (int) (totalSpaceB / 1024l / 1024l);
                    int freeSpaceMB = (int) (freeSpaceB / 1024l / 1024l);
                    int usedSpaceMB = (int) (usedSpaceB / 1024l / 1024l);

                    float totalSpaceGB = -1;
                    float freeSpaceGB = -1;
                    float usedSpaceGB = -1;

                    if (totalSpaceMB > 1024) {
                        totalSpaceGB = ((float) totalSpaceMB) / 1024f;
                    }

                    if (freeSpaceMB > 1024) {
                        freeSpaceGB = ((float) freeSpaceMB) / 1024f;
                    }

                    if (usedSpaceMB > 1024) {
                        usedSpaceGB = ((float) usedSpaceMB) / 1024f;
                    }

                    final int totalSpaceMB_ = totalSpaceMB;
                    final int usedSpaceMB_ = usedSpaceMB;
                    final float totalSpaceGB_ = totalSpaceGB;
                    final int freeSpaceMB_ = freeSpaceMB;
                    final float freeSpaceGB_ = freeSpaceGB;

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            freeSpaceBar.setMaximum(totalSpaceMB_);
                            freeSpaceBar.setValue(usedSpaceMB_);

                            DecimalFormat df = new DecimalFormat("#.0");

                            freeSpaceBar.setString("Wykorzystano: " + df.format(freeSpaceBar.getPercentComplete() * 100d) + "% "
                                    + "z " + (totalSpaceGB_ < 0 ? df.format(totalSpaceMB_) + "MB" : df.format(totalSpaceGB_) + "GB ")
                                    + "(Wolne: " + (freeSpaceGB_ < 0 ? df.format(freeSpaceMB_) + "MB" : df.format(freeSpaceGB_) + "GB ") + ")");

                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            freeSpaceBar.setString("Nie można odczytać ilości wolnego miejsca");
                            freeSpaceBar.setValue(0);
                        }
                    });
                }
            }
        });
    }
}
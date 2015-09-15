/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.windows;

import com.google.common.io.Files;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.panels.EventPanel;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.gui.util.ValidableLabel;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class RenameWindow {
    private final Event event;
    private final Context context;
    private final JFrame frame;

    public RenameWindow(Context context, Event event) {
        this.event = event;
        this.context = context;
        
        this.frame = new JFrame("Zmiana nazwy wydarzenia \"" + event.getName() + "\"");

        SwingUtilities.invokeLater(() -> {
            frame.setSize(500, 500);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new MainPanel());
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent evt) {
                    frame.setVisible(true);
                }
            });

            frame.setVisible(true);
        });

    }

    private final class MainPanel extends JPanel {
        public MainPanel() {
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

            JPanel infoPanel = new JPanel(new FlowLayout());
            JPanel newNamePanel = new JPanel(new FlowLayout());
            JPanel devicesPanel = new JPanel();
            JPanel progressPanel = new JPanel(new FlowLayout());
            JPanel finishPanel = new JPanel(new FlowLayout());
            
            JButton renameButton = new JButton("Zmień nazwę");
            renameButton.setEnabled(false);
            

            Carrier[] carriers = event.getLocalizations().stream().map(el -> el.getCarrier(context)).filter(c -> c != null).toArray(Carrier[]::new);

            /**
             *
             *
             *
             *
             * INFO PANEL *
             */
            String devicesListS = Arrays.stream(carriers).map(c -> c.getName()).reduce("", (a, b) -> a + ", " + b);
            if (!devicesListS.isEmpty()) {
                devicesListS = devicesListS.substring(2);
            }
            JLabel infoLabel = new JLabel("<html><p width=\"480\">Zmiana nazwy <b>" + event.getName() + "</b> "
                    + " będzie wymagała podłączenia następujących nośników: " + devicesListS + "</p>");
            infoPanel.add(infoLabel);

            /**
             *
             *
             *
             *
             * NEW NAME PANEL *
             */
            newNamePanel.add(new JLabel("Nowa nazwa: "));

            JTextField newNameField = new JTextField(event.getName());
            newNameField.setColumns(35);

            newNameField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    String newName = newNameField.getText();
                    if (!newName.equals(event.getName())) {
                        String properName = Event.makeNameValid(context, newName);
                        SwingUtilities.invokeLater(() -> {
                            if (!properName.equals(newName)) {
                                newNameField.setText(properName);
                            }
                        });
                    }
                }
            });
            newNamePanel.add(newNameField);

            /**
             *
             *
             *
             *
             *
             * DEVICES PANEL *
             */
            devicesPanel.setLayout(new FlowLayout());
            Map<String, ValidableLabel> deviceLabels = Collections.synchronizedMap(new HashMap<>());
            for (Carrier c : carriers) {
                deviceLabels.put(c.getName(), new ValidableLabel(c.isConnected(context), "Podłącz \"" + c.getName() + "\""));
            }
            context.deviceDetector.addWeakStorageDevicePresenceListener(new StorageDevicePresenceListener() {
                @Override
                public void storageDeviceConnected(File rootFile, String deviceName) {
                    if (deviceLabels.containsKey(deviceName)) {
                        deviceLabels.get(deviceName).setValid_TSafe(true);
                    }
                    if(deviceLabels.values().stream().allMatch(validableLabel -> validableLabel.isMarkerValid())) {
                        renameButton.setEnabled(true);
                    }
                }

                @Override
                public void storageDeviceDisconnected(File rootFile, String deviceName) {
                    if (deviceLabels.containsKey(deviceName)) {
                        deviceLabels.get(deviceName).setValid_TSafe(false);
                        renameButton.setEnabled(false);
                    }
                }
            });
            
            for(JLabel l : deviceLabels.values()) devicesPanel.add(l);
            
            if(deviceLabels.values().stream().allMatch(validableLabel -> validableLabel.isMarkerValid())) {
                renameButton.setEnabled(true);
            }
            
            /**
             * 
             * 
             * 
             * 
             * 
             * PROGRESS PANEL *
             */
            progressPanel.add(renameButton);
            JLabel progressLabel = new JLabel("<html>Proszę czekać, trwa zmienianie nazwy...");
            progressLabel.setVisible(false);
            progressPanel.add(progressLabel);
            
            renameButton.addActionListener((evt) -> {
                if(!newNameField.getText().equals(event.getName())) {
                    
                String newName = Event.makeNameValid(context, newNameField.getText());
                
                SwingUtilities.invokeLater(() -> {
                    newNameField.setEnabled(false);
                    newNameField.setEditable(false);
                    renameButton.setEnabled(false);
                    progressLabel.setVisible(true);
                });
                
                context.cachedExecutor.submit(() -> {
                    //we need to hold roots to prevent disconnecting devices
                    TwoTuple<File, File> [] paths = event.getLocalizations().stream().map(el -> el.getCarrier(context))
                            .map(c -> context.deviceDetector.getDeviceRoot(c.getName()))
                            .peek(f -> {if(f == null) throw new RuntimeException("Device root is null during event rename");})
                            .map(deviceRoot -> new TwoTuple<File, File>(deviceRoot, new File(event.getProperPath(deviceRoot))))
                            .toArray(TwoTuple[]::new);
                    //this must be done after getting old paths
                    event.setName(newName);
                    
                    for(TwoTuple<File, File> tuple : paths) {
                        File deviceRoot = tuple.getA();
                        if(deviceRoot == null || !deviceRoot.exists() || !deviceRoot.canRead()) throw new RuntimeException("Device root is null during event rename");
                        
                        File eventPath = tuple.getB();
                        if(!eventPath.exists() || !eventPath.canRead()) {
                            SwingUtilities.invokeLater(() -> {
                                progressLabel.setForeground(Color.RED.darker());
                                progressLabel.setText(progressLabel.getText()+"<br />Błąd: Nie można znaleźć folderu: \""+eventPath+"\".");
                            });
                            throw new RuntimeException("Could not find directory \""+eventPath+"\" while renaming event.");
                        }
                        
                        String newPath = event.getProperPath(deviceRoot);
                        try {
                            Files.move(eventPath, new File(newPath));
                        } catch (IOException ex) {
                            Logger.getLogger(RenameWindow.class.getName()).log(Level.SEVERE, null, ex);
                            throw new RuntimeException("Could not rename directory \""+eventPath+"\" to \""+newPath+"\" while renaming event.", ex);
                        }
                    }
                    
                    context.dbManager.executeInDBThread(() -> {
                        try {
                            context.dbManager.getDaos().getEventDao().update(event);
                            
                            SwingUtilities.invokeLater(() -> {
                                //finally done
                                context.eBus.post(new EventsNode.EventsListChanged());
                                context.eBus.post(new ChangeMainPanel(new EventPanel(context, event)));
                                frame.setVisible(false);
                            });
                        } catch (SQLException ex) {
                            Logger.getLogger(RenameWindow.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                });
                }
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
            add(newNamePanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(devicesPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(progressPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(finishPanel);
        }
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.windows;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
import pl.jblew.cpr.logic.integritycheck.Validator;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class RenameWindow {
    private final Event event;
    private final Context context;
    private final JFrame frame;
    private final AtomicBoolean windowCloseEnabled = new AtomicBoolean(true); 

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
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

            JPanel infoPanel = new JPanel(new FlowLayout());
            JPanel newNamePanel = new JPanel(new FlowLayout());
            JPanel devicesPanel = new JPanel();
            JPanel progressPanel = new JPanel(new FlowLayout());
            JPanel finishPanel = new JPanel(new FlowLayout());

            JButton renameButton = new JButton("Zmień nazwę");

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
                    + " Najlepiej od razu podłączyć nośniki: " + devicesListS + ". Jeżeli jednak któryś z nośników nie będzie podłączony,"
                    + " nazwa zostanie zmieniona przy najbliższym sprawdzaniu integralności nośnika (lub można ją wymuśić,"
                    + " klikając \"Sprawdź nośnik w panelu nośnika\".</p>");
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
                }

                @Override
                public void storageDeviceDisconnected(File rootFile, String deviceName) {
                    if (deviceLabels.containsKey(deviceName)) {
                        deviceLabels.get(deviceName).setValid_TSafe(false);
                    }
                }
            });

            deviceLabels.values().stream().forEachOrdered((l) -> devicesPanel.add(l));

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
                if (!newNameField.getText().equals(event.getName())) {

                    String newName = Event.makeNameValid(context, newNameField.getText());

                    SwingUtilities.invokeLater(() -> {
                        windowCloseEnabled.set(false);
                        newNameField.setEnabled(false);
                        newNameField.setEditable(false);
                        renameButton.setEnabled(false);
                        progressLabel.setVisible(true);
                    });

                    context.cachedExecutor.submit(() -> {
                        //we need to hold roots to prevent disconnecting devices
                        TwoTuple<Event_Localization, File>[] paths = event.getLocalizations().stream()
                                .map(el -> new TwoTuple<Event_Localization, Carrier>(el, el.getCarrier(context)))
                                .filter(tuple -> tuple.getB().isConnected(context))
                                .map(tuple -> new TwoTuple<Event_Localization, File>(tuple.getA(), context.deviceDetector.getDeviceRoot(tuple.getB().getName())))
                                .toArray(TwoTuple[]::new);
                        //this must be done after getting old paths
                        event.setName(newName);

                        context.dbManager.executeInDBThread(() -> {
                            try {
                                context.dbManager.getDaos().getEventDao().update(event);

                                boolean hadError = false;
                                for (TwoTuple<Event_Localization, File> tuple : paths) {
                                    Validator.validateEventLocalizationOrMarkForValidation(context, tuple.getA());
                                }

                                final boolean hadError_ = hadError;
                                SwingUtilities.invokeLater(() -> {
                                    windowCloseEnabled.set(true);
                                    //finally done
                                    context.eBus.post(new EventsNode.EventsListChanged());
                                    context.eBus.post(new ChangeMainPanel(new EventPanel(context, event)));
                                    if (!hadError_) {
                                        frame.setVisible(false);
                                    }
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

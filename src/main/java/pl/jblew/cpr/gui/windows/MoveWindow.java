/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.windows;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
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
import pl.jblew.cpr.gui.components.modal.CreateEventModal;
import pl.jblew.cpr.gui.panels.ProgressListPanel;
import pl.jblew.cpr.gui.util.CPRProgressBarUI;
import pl.jblew.cpr.gui.util.PanelDisabler;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.io.Mover;

/**
 *
 * @author teofil
 */
public class MoveWindow {
    private final Mover mover;
    private final Context context;
    private final JFrame frame;
    private final AtomicBoolean windowCloseEnabled = new AtomicBoolean(true);

    public MoveWindow(Context context, Event sourceEvent, MFile[] mfilesToMove) {
        this.mover = new Mover(context, sourceEvent, mfilesToMove);
        this.context = context;

        this.frame = new JFrame("Przenoszenie \"" + sourceEvent.getName() + "\"");

        SwingUtilities.invokeLater(() -> {
            frame.setSize(500, 500);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new MainPanel());
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent evt) {
                    if (!windowCloseEnabled.get()) {
                        frame.setVisible(true);
                    }
                }
            });

            frame.setVisible(true);
        });

    }

    private final class MainPanel extends JPanel {
        private final JPanel devicePanelsPanel;
        private final JPanel finishPanel;

        public MainPanel() {
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

            JPanel infoPanel = new JPanel(new FlowLayout());
            JPanel targetEventPanel = new JPanel(new FlowLayout());
            devicePanelsPanel = new JPanel();
            devicePanelsPanel.setLayout(new BoxLayout(devicePanelsPanel, BoxLayout.PAGE_AXIS));
            finishPanel = new JPanel(new BorderLayout());

            /**
             * INFO PANEL *
             */
            infoPanel.add(new JLabel("<html>Przenoszenie <b>" + mover.getMfilesToMove().length + "</b> plików z <b>" + mover.getSourceEvent().getName() + "</b>."));

            /**
             * TARGET EVENT SELECTION PANEL *
             */
            targetEventPanel.add(new JLabel("Wybierz docelowe wydarzenie:"));

            JComboBox eventTypeSelection = new JComboBox(new String[]{"Wybierz typ...", "POSEGREGOWANE", "NIEPOSEGREGOWANE"});
            targetEventPanel.add(eventTypeSelection);

            DefaultComboBoxModel eventSelectionModel = new DefaultComboBoxModel();
            JComboBox eventSelection = new JComboBox(eventSelectionModel);
            eventSelection.setEnabled(false);
            eventSelection.setMaximumRowCount(30);
            targetEventPanel.add(eventSelection);

            JButton eventConfirmButton = new JButton("Zatwierdź");
            eventConfirmButton.setEnabled(false);
            targetEventPanel.add(eventConfirmButton);

            eventTypeSelection.addActionListener((evt) -> {
                if (eventTypeSelection.getSelectedIndex() == 0) {//if user selected label
                    eventSelection.setEnabled(false);
                    PanelDisabler.setEnabled(devicePanelsPanel, false);
                    PanelDisabler.setEnabled(finishPanel, false);
                    return;
                }

                Event.Type eventType = Event.Type.UNSORTED; //for index 2
                if (eventTypeSelection.getSelectedIndex() == 1) {
                    eventType = Event.Type.SORTED;
                }

                Event[] eventList = Event.getAllEvents(context, eventType);
                eventSelectionModel.removeAllElements();
                Arrays.stream(eventList).forEach(e -> eventSelectionModel.addElement(e.getName()));
                eventSelectionModel.addElement("[+] Utwórz nowe wydarzenie");

                Arrays.stream(eventSelection.getActionListeners()).forEach(listener -> eventSelection.removeActionListener(listener));
                eventSelection.addActionListener((evt2) -> {
                    if (eventSelection.getSelectedIndex() == eventList.length) { //"create action" element
                        Event newEvent = CreateEventModal.showCreateEventModal(context.frame, context, false);
                        if (newEvent != null) {
                            eventSelection.setEnabled(false);
                            mover.setTargetEvent(newEvent);
                        }
                    } else if (eventSelection.getSelectedIndex() >= 0 && eventSelection.getSelectedIndex() < eventList.length) {
                        mover.setTargetEvent(eventList[eventSelection.getSelectedIndex()]);
                    }

                    if (mover.getTargetEvent() != null) {
                        eventConfirmButton.setEnabled(true);
                    }
                });

                eventSelection.setEnabled(true);
            });

            eventConfirmButton.addActionListener((evt) -> {
                if (mover.getTargetEvent() != null) {
                    PanelDisabler.setEnabled(infoPanel, false);
                    PanelDisabler.setEnabled(targetEventPanel, false);
                    PanelDisabler.setEnabled(devicePanelsPanel, true);
                    windowCloseEnabled.set(false);

                    Event_Localization[] sourceLocalizations = mover.getSourceEvent().getLocalizations().toArray(new Event_Localization[]{});
                    Event_Localization[] targetLocalizations = mover.getTargetEvent().getLocalizations().toArray(new Event_Localization[]{});

                    devicePanelsPanel.removeAll();
                    devicePanelsPanel.setLayout(new BoxLayout(devicePanelsPanel, BoxLayout.PAGE_AXIS));
                    JPanel topInfoPanel = new JPanel(new BorderLayout());
                    if (sourceLocalizations.length == 0) {
                        topInfoPanel.add(new JLabel("Błąd: Wydarzenie źródłowe nie posiada lokalizacji"));
                    } else if (targetLocalizations.length == 0) {
                        topInfoPanel.add(new JLabel("Błąd: Wydarzenie docelowe nie posiada lokalizacji"));
                    } else {
                        topInfoPanel.add(new JLabel("<html><p>Aby bezpiecznie przenieść pliki nie wyłączaj programu ani nie zamykaj tego asystenta!"
                                + " Postępuj zgodnie z instrukcjami na ekranie. Konieczne będzie skopiowanie plików pomiędzy urządzeniami:<br />"
                                + "<b>Urządzenia źródłowe: " + Arrays.stream(sourceLocalizations).map(el -> el.getCarrier(context).getName()).reduce("", (a, b) -> a + ", " + b) + "<br />"
                                + "<b>Urządzenia docelowe: " + Arrays.stream(targetLocalizations).map(el -> el.getCarrier(context).getName()).reduce("", (a, b) -> a + ", " + b) + "<br />"
                                + "</p>"));
                    }
                    devicePanelsPanel.add(topInfoPanel);
                    devicePanelsPanel.add(new JSeparator(JSeparator.HORIZONTAL));

                    devicePanelsPanel.revalidate();
                    devicePanelsPanel.repaint();

                    processStep(mover.getNextStep());
                }
            });

            /**
             * DEVICE PANELS PANEL *
             */
            devicePanelsPanel.add(new JLabel("Ładowanie"));

            /**
             * FINISH PANEL *
             */
            finishPanel.add(new JLabel("Gotowe!"), BorderLayout.CENTER);

            add(infoPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(targetEventPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(devicePanelsPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));
            add(finishPanel);
            add(new JSeparator(JSeparator.HORIZONTAL));

            PanelDisabler.setEnabled(infoPanel, true);
            PanelDisabler.setEnabled(targetEventPanel, true);
            PanelDisabler.setEnabled(devicePanelsPanel, false);
            PanelDisabler.setEnabled(finishPanel, false);
        }

        private void processStep(Mover.Step step) {
            SwingUtilities.invokeLater(() -> {
                Component prevComponent = devicePanelsPanel.getComponents()[devicePanelsPanel.getComponents().length - 1];
                if (prevComponent instanceof JPanel) {
                    PanelDisabler.setEnabled((JPanel) prevComponent, false); //disable prev panel
                }

                if (step == null) {
                    PanelDisabler.setEnabled(devicePanelsPanel, false);
                    PanelDisabler.setEnabled(finishPanel, true);
                    windowCloseEnabled.set(true);
                } else {

                    JPanel stepPanel = new JPanel(new FlowLayout());
                    devicePanelsPanel.add(stepPanel);
                    devicePanelsPanel.add(new JSeparator(JSeparator.HORIZONTAL));

                    stepPanel.add(new JLabel(step.msg));

                    if (step.notifyOnNewDevice) {
                        context.deviceDetector.executeOnDevicesChange(() -> {
                            processStep(mover.getNextStep());
                        });
                    }

                    if (step.isProcessable()) {
                        JProgressBar progressBar = new JProgressBar(0, 100);
                        ProgressListPanel.ProgressEntity progressEntity = new ProgressListPanel.ProgressEntity();
                        context.eBus.post(progressEntity);

                        progressBar.setUI(new CPRProgressBarUI());
                        progressBar.setPreferredSize(new Dimension(300, 60));
                        progressBar.setStringPainted(true);
                        stepPanel.add(progressBar);

                        context.cachedExecutor.execute(() -> step.processable.process((percent, msg, error) -> {
                            progressBar.setString(msg);
                            progressBar.setValue(percent);
                            progressBar.repaint();
                            progressBar.revalidate();

                            progressEntity.setPercent(percent);
                            progressEntity.setText("(Przenoszenie) " + msg);

                            if (percent == 100) {
                                progressEntity.markFinished();
                                processStep(mover.getNextStep());
                            }

                            if (error) {
                                progressEntity.markError();
                            }
                        }));
                    }
                }
            });

        }
    }

    public static interface ProgressChangedCallback {
        public void progressChanged(int percent, String msg, boolean error);
    }
}

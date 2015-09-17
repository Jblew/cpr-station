/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components.modal;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.panels.EventPanel;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;

/**
 *
 * @author teofil
 */
public class CreateEventModal {
    private CreateEventModal() {
    }

    public static Event showCreateEventModal(Container parent, Context context, boolean switchToNewEvent) {
        final AtomicReference<Event> result = new AtomicReference<>(null);
        try {
            Runnable r = () -> {
                JDialog dialog = new JDialog(context.frame, "", Dialog.ModalityType.DOCUMENT_MODAL);
                dialog.setSize(800, 200);
                dialog.setTitle("Utwórz nowe wydarzenie");

                JPanel contentPanel = new JPanel();
                contentPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
                contentPanel.setLayout(new FlowLayout());

                contentPanel.add(new JLabel("Wybierz urządzenie i nazwę wydarzenia.Później będziesz mógł skopiować to wydarzenie na więcej urządzeń."));
                contentPanel.add(new JSeparator(JSeparator.HORIZONTAL));

                Carrier[] connectedCarriers = context.deviceDetector.getConnectedOfCarriers(Carrier.getAllCarriers(context));
                JComboBox deviceSelection = new JComboBox(Arrays.stream(connectedCarriers).map(c -> c.getName()).toArray(String[]::new));
                contentPanel.add(deviceSelection);

                JTextField nameField = new JTextField("[" + DateTimeFormatter.ofPattern("YYYY.MM.dd ").format(LocalDateTime.now()) + "] ");
                nameField.setColumns(40);
                contentPanel.add(nameField);

                contentPanel.add(new JSeparator(JSeparator.HORIZONTAL));

                JPanel buttonPanel = new JPanel();
                buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

                JButton cancelButton = new JButton("Anuluj");
                buttonPanel.add(cancelButton);
                JButton createButton = new JButton("Utwórz wydarzenie");
                buttonPanel.add(createButton);

                contentPanel.add(buttonPanel);

                cancelButton.addActionListener((evt) -> dialog.setVisible(false));

                createButton.addActionListener((evt) -> {
                    dialog.setVisible(false);
                    String name = nameField.getText();
                    Carrier carrier = connectedCarriers[deviceSelection.getSelectedIndex()];
                    if (name != null && !name.isEmpty() && carrier != null) {
                        Event newEvent = Event.createEvent(context, Event.Type.SORTED, name, carrier);
                        if (newEvent == null) {
                            JOptionPane.showMessageDialog(parent, "Błąd podczas tworzenia wydarzenia!");
                        } else {
                            result.set(newEvent);
                            context.eBus.post(new EventsNode.EventsListChanged());
                            if (switchToNewEvent) {
                                EventPanel eventPanel = new EventPanel(context, newEvent);
                                ChangeMainPanel cmp = new ChangeMainPanel(eventPanel);
                                context.eBus.post(cmp);
                            }
                            
                        }
                    }
                });

                dialog.setContentPane(contentPanel);
                dialog.setVisible(true);

            };
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeAndWait(r);
            }
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(CreateEventModal.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result.get();
    }
}

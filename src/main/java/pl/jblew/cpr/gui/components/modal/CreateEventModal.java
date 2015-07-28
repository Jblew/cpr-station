/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components.modal;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.panels.EventPanel;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.logic.Event;

/**
 *
 * @author teofil
 */
public class CreateEventModal {
    private CreateEventModal() {}
    
    public static Event showCreateEventModal(Container parent, Context context) {
        final AtomicReference<Event> result = new AtomicReference<>(null);
        try {
            Runnable r = () -> {
                String name = JOptionPane.showInputDialog("Podaj nazwę nowego wydarzenia", "["+DateTimeFormatter.ofPattern("YYYY.MM.dd ").format(LocalDateTime.now())+"] ");
                if (name != null && !name.isEmpty()) {
                    Event newEvent = Event.createEvent(context, Event.Type.SORTED, name);
                    if (newEvent == null) {
                        JOptionPane.showMessageDialog(parent, "Błąd podczas tworzenia wydarzenia!");
                    } else {
                        result.set(newEvent);
                        context.eBus.post(new EventsNode.EventsListChanged());
                        context.eBus.post(new ChangeMainPanel(new EventPanel(context, newEvent)));
                    }
                }
            };
            if(SwingUtilities.isEventDispatchThread()) r.run();
            else SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(CreateEventModal.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result.get();
    }
}

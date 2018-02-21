/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.components.modal.CreateEventModal;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.util.ListenersManager;
import pl.jblew.cpr.util.TwoTuple;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.*;
import se.lesc.quicksearchpopup.QuickSearchPopup;

/**
 *
 * @author teofil
 */
public class SearchableEventPicker extends JPanel {
    private final Context context;
    private final JButton addNewButton;
    private final JTextField searchField;
    private final List<TwoTuple<String, Event>> eventNames = new ArrayList<>();
    private final ListenersManager<SelectionChangedListener> listenersManager = new ListenersManager<>();

    public SearchableEventPicker(Context context) {
        this.context = context;

        searchField = new JTextField(40);
        //searchField.setPreferredSize(new Dimension(400, 30));

        reloadEvents();

        QuickSearchPopup quickSearchPopup = new QuickSearchPopup(searchField, (name) -> {
            searchField.setText(name);
            listenersManager.callListeners((listener) -> {
                listener.selectionChanged(eventNames.stream()
                        .filter(tuple -> tuple.getA().equals(name))
                        .map(tuple -> tuple.getB()).findFirst().orElse(null));
            });
        });
        quickSearchPopup.setSearchRows(eventNames.stream().map(tuple -> tuple.getA()).sorted().toArray(String[]::new));

        addNewButton = new JButton("+");
        addNewButton.setForeground(Color.WHITE);
        addNewButton.setBackground(Color.GREEN.darker());
        addNewButton.setFont(addNewButton.getFont().deriveFont(Font.BOLD, 16));
        addNewButton.addActionListener((evt) -> {
            Event newEvent = CreateEventModal.showCreateEventModal(searchField, context, false);
            if (newEvent != null) {
                reloadEvents();
                quickSearchPopup.setSearchRows(eventNames.stream().map(tuple -> tuple.getA()).sorted().toArray(String[]::new));
            }
        });

        setLayout(new BorderLayout());
        add(searchField, BorderLayout.CENTER);
        add(addNewButton, BorderLayout.EAST);

        searchField.setPreferredSize(new Dimension(450, 30));
    }

    public void addSelectionListener(SelectionChangedListener listener) {
        listenersManager.addListener(listener);
    }

    public void removeSelectionListener(SelectionChangedListener listener) {
        listenersManager.removeListener(listener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        searchField.setEnabled(enabled);
        searchField.setEditable(enabled);
        addNewButton.setEnabled(enabled);
    }

    /*private void reloadModel(String content) {
     model.removeAllElements();
     synchronized (eventNames) {
     for (TwoTuple<String, Event> tuple : eventNames) {
     if (tuple.getA().toLowerCase().contains(content.toLowerCase())) {
     model.addElement(tuple.getA());
     }
     }
     }

     SwingUtilities.invokeLater(() -> {
     comboBox.revalidate();
     comboBox.repaint();
     });
     }*/
    private void reloadEvents() {
        synchronized (eventNames) {
            try {
                context.dbManager.executeInDBThreadAndWait(() -> {
                    try {
                        List<Event> events = context.dbManager.getDaos().getEventDao().queryForAll();
                        if (events.size() > 0) {
                            eventNames.clear();
                            events.stream().sorted().forEachOrdered(e -> eventNames.add(new TwoTuple<String, Event>(e.getDisplayName(), e)));
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(SearchableEventPicker.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } catch (InterruptedException ex) {
                Logger.getLogger(SearchableEventPicker.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //reloadModel("");
    }

    public static interface SelectionChangedListener extends ListenersManager.Listener {
        public void selectionChanged(Event evt);
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.treenodes;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.TreePanel;
import pl.jblew.cpr.gui.components.modal.CreateEventModal;
import pl.jblew.cpr.gui.panels.EventPanel;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.util.ListenersManager;
import pl.jblew.cpr.util.PrintableBusMessage;

/**
 *
 * @author teofil
 */
public class EventsNode extends TreePanel.IconTreeNode implements TreePanel.AddTopMargin {
    private final Map<Event, TreePanel.SelectableIconTreeNode> events = new HashMap<>();
    private final ListenersManager<NodeChangeListener> listenersManager = new ListenersManager<>();
    private final Context context;
    private final Event.Type eventType;
    private final EventsNode me = this;

    public EventsNode(Context context_, Event.Type eventType_) {
        super((eventType_ == Event.Type.SORTED ? "Posegregowane" : "Nieposegregowane"),
                new ImageIcon(TreePanel.class.getClassLoader().getResource("images/" + (eventType_ == Event.Type.SORTED ? "sorted-photos.png" : "unsorted-photos.png"))));
        context = context_;
        context.eBus.register(this);
        eventType = eventType_;
        eventsListChanged(null);
    }

    public void addNodeChangeListener(NodeChangeListener l) {
        listenersManager.addListener(l);
    }

    public void removeNodeChangeListener(NodeChangeListener l) {
        listenersManager.removeListener(l);
    }

    private void fireNodeChanged() {
        listenersManager.callListeners((NodeChangeListener listener) -> {
            listener.nodeChanged(me);
        });

    }

    public void filter(String s) {
        SwingUtilities.invokeLater(() -> {
            removeAllChildren();
            System.out.println("Filtering for " + s);
            events.keySet().stream().sorted().forEachOrdered(evt -> {
                if (s == null || s.isEmpty() || evt.getDisplayName().toLowerCase().contains(s.toLowerCase())) {
                    add(events.get(evt));
                }
            });

            if (eventType == Event.Type.SORTED) {
                addCreateNode();
            }

            fireNodeChanged();
        });

    }

    @Subscribe
    public void eventsListChanged(EventsListChanged evt) {
        context.dbManager.executeInDBThread(() -> {
            try {
                final List<Event> result = context.dbManager.getDaos().getEventDao().queryForEq("type", eventType);
                SwingUtilities.invokeLater(() -> {
                    removeAllChildren();
                    synchronized (events) {
                        events.clear();
                        result.stream().sorted().forEachOrdered((e) -> addEventNode(e));
                    }
                    if (eventType == Event.Type.SORTED) {
                        addCreateNode();
                    }

                    fireNodeChanged();
                });

            } catch (SQLException ex) {
                Logger.getLogger(CarriersNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private void addEventNode(final Event e) {
        TreePanel.SelectableIconTreeNode node = new TreePanel.SelectableIconTreeNode(e.getDisplayName(), new ImageIcon(TreePanel.class.getClassLoader().getResource("images/pc16.gif"))) {
            @Override
            public void nodeSelected(JTree tree) {
                context.eBus.post(new ChangeMainPanel(new EventPanel(context, e)));
            }
        };

        if (e.hasProblems()) {
            node.setColor(Color.RED);
        }

        add(node);
        synchronized (events) {
            events.put(e, node);
        }
    }
    
    private void addCreateNode() {
        final TreePanel.SelectableIconTreeNode node = new TreePanel.SelectableIconTreeNode("Dodaj wydarzenie", new ImageIcon(TreePanel.class.getClassLoader().getResource("images/add16.png"))) {
            @Override
            public void nodeSelected(JTree tree) {
                tree.setSelectionRow(0);
                CreateEventModal.showCreateEventModal(tree, context, true);
            }
        };
        add(node);
    }

    public static class EventsListChanged implements PrintableBusMessage {
        public EventsListChanged() {
        }
    }
}

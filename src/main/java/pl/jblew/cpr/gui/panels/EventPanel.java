/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import com.google.common.io.Files;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.IconLoader;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.components.MFileBrowser;
import pl.jblew.cpr.gui.components.modal.CreateEventModal;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.MFile_Event;
import pl.jblew.cpr.logic.MFile_Localization;

/**
 *
 * @author teofil
 */
public class EventPanel extends MainPanel {
    private final Context context;
    private final Event event;
    private final JLabel timespanLabel;
    private final JLabel numOfCopiesLabel;
    private final JPanel browserPanel;
    private final JLabel numOfPhotosLabel;
    private final JButton moveSelectedToEventButton;
    private final JButton moveAllToEventButton;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MFileBrowser browser;

    public EventPanel(Context context_, final Event event_) {
        this.context = context_;
        this.event = event_;

        this.moveSelectedToEventButton = new JButton("Przenieś zaznaczone");
        this.moveAllToEventButton = new JButton("Przenieś wszystkie");

        moveSelectedToEventButton.addActionListener((evt) -> {
            moveMFilesToEvent(browser.getSelectedMFiles());
        });

        moveAllToEventButton.addActionListener((evt) -> {
            moveMFilesToEvent(browser.getAllMFiles());
        });

        setLayout(new BorderLayout());

        this.timespanLabel = new JLabel("...");
        this.numOfCopiesLabel = new JLabel("...");
        this.numOfPhotosLabel = new JLabel("...");

        JPanel gridPanel = new JPanel();
        gridPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridLayout gridLayout = new GridLayout(0, 2);
        gridLayout.setHgap(10);
        gridLayout.setVgap(10);
        gridPanel.setLayout(gridLayout);

        JLabel nameLabel = new JLabel(event.getName());
        nameLabel.setIcon(IconLoader.EDIT_16.load());
        nameLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    String newName = JOptionPane.showInputDialog("Podaj nową nazwę:", event.getName());
                    if (newName != null && !newName.isEmpty()) {
                        Event renamedEvent = event.rename(context, newName);
                        context.eBus.post(new EventsNode.EventsListChanged());
                        context.eBus.post(new ChangeMainPanel(new EventPanel(context, renamedEvent)));
                    }
                }
            }
        });
        prepareGridRow(gridPanel, "Nazwa: ", nameLabel);
        prepareGridRow(gridPanel, "Zakres czasu: ", timespanLabel);
        prepareGridRow(gridPanel, "Ilość kopii: ", numOfCopiesLabel);
        prepareGridRow(gridPanel, "Ilość plików: ", numOfPhotosLabel);

        final JButton showSelectiveEventButton = new JButton("Pokaż WYBRANE");
        final JButton makeSelectiveEventButton = new JButton("Stwórz WYBRANE");
        final JButton makeCopyButton = new JButton("Utwórz kopię na innym nośniku");
        final JButton deleteFromDBButton = new JButton("Usuń z bazy danych");

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridLayout buttonPanelLayout = new GridLayout(0, 1);
        buttonPanelLayout.setHgap(10);
        buttonPanelLayout.setVgap(10);
        buttonPanel.setLayout(buttonPanelLayout);
        buttonPanel.add(showSelectiveEventButton);
        buttonPanel.add(makeSelectiveEventButton);
        buttonPanel.add(makeCopyButton);
        buttonPanel.add(deleteFromDBButton);

        GridLayout northPanelLayout = new GridLayout(1, 2);
        northPanelLayout.setVgap(10);
        JPanel northPanel = new JPanel(northPanelLayout);
        northPanel.add(gridPanel);
        northPanel.add(buttonPanel);
        add(northPanel, BorderLayout.NORTH);

        makeCopyButton.addActionListener((ActionEvent e) -> {
            context.eBus.post(new ChangeMainPanel(new ExportPanel(context, event)));
        });

        browserPanel = new JPanel();
        browserPanel.setLayout(new BorderLayout());
        browserPanel.add(new JLabel("..."), BorderLayout.CENTER);
        browserPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
        add(browserPanel, BorderLayout.CENTER);

        asyncLoadData();
    }

    @Override
    public void activate() {
        repaint();
    }

    @Override
    public void inactivate() {
    }

    private void prepareGridRow(JPanel panel, String text, JComponent component) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("default", Font.BOLD, 16));
        label.setHorizontalAlignment(JLabel.RIGHT);
        panel.add(label);

        panel.add(component);
    }

    private void asyncLoadData() {
        Event.FullEventData fed = event.getFullEventData(context);
        if (fed != null) {
            SwingUtilities.invokeLater(() -> {
                numOfPhotosLabel.setText(fed.mfiles.length + "");

                if (fed.startDate != null) {
                    DateTimeFormatter f = DateTimeFormatter.ofPattern("YYYY.MM.dd HH:ss");
                    String timeSpan = f.format(fed.startDate) + " - " + f.format(fed.endDate);
                    timespanLabel.setText(timeSpan);
                } else {
                    timespanLabel.setText("");

                }
                String warn = "";
                if (fed.minRedundancy < 2) {
                    warn = " (Koniecznie wykonaj dodatkową kopię na innym nośniku)";
                }
                numOfCopiesLabel.setText((fed.minRedundancy == fed.maxRedundancy ? fed.minRedundancy + "" : fed.minRedundancy + " - " + fed.maxRedundancy) + warn);

                browserPanel.removeAll();

                browser = new MFileBrowser(context, fed.mfiles, event);
                browser.addComponentToToolPanel(moveSelectedToEventButton);
                browser.addComponentToToolPanel(moveAllToEventButton);
                browserPanel.add(browser, BorderLayout.CENTER);
                browserPanel.revalidate();
                browserPanel.repaint();
            });
        }
    }

    private void moveMFilesToEvent(MFile[] mfilesToMove) {
        if (mfilesToMove != null && mfilesToMove.length > 0) {
            JDialog dialog = new JDialog(context.frame, "", Dialog.ModalityType.DOCUMENT_MODAL);
            dialog.setSize(400, 200);
            dialog.setTitle("Przenoszenie plików do innego wydarzenia");

            JPanel contentPanel = new JPanel();
            contentPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));

            JComboBox eventTypeSelection = new JComboBox(new String[]{"Wybierz typ...", "POSEGREGOWANE", "NIEPOSEGREGOWANE"});
            contentPanel.add(eventTypeSelection);

            DefaultComboBoxModel eventSelectionModel = new DefaultComboBoxModel(new String[]{"Najpierw wybierz typ..."});
            JComboBox eventSelection = new JComboBox(eventSelectionModel);
            eventSelection.setEnabled(false);
            contentPanel.add(eventSelection);

            DefaultComboBoxModel deviceSelectionModel = new DefaultComboBoxModel(new String[]{"Najpierw wybierz wydarzenie..."});
            JComboBox deviceSelection = new JComboBox(deviceSelectionModel);
            deviceSelection.setEnabled(false);
            contentPanel.add(deviceSelection);

            JPanel buttonPanel = new JPanel(new FlowLayout());

            JButton cancelButton = new JButton("Anuluj");
            buttonPanel.add(cancelButton);

            JButton proceedButton = new JButton("Przenieś");
            buttonPanel.add(proceedButton);
            proceedButton.setEnabled(false);

            contentPanel.add(buttonPanel);

            final AtomicReference<Event> selectedEvent = new AtomicReference<>(null);
            final AtomicReference<Carrier> selectedCarrier = new AtomicReference<>(null);

            eventTypeSelection.addActionListener((evt) -> {
                if (eventTypeSelection.getSelectedIndex() == 0) {//if user selected label
                    eventSelection.setEnabled(false);
                    deviceSelection.setEnabled(false);
                    proceedButton.setEnabled(false);
                    return;
                }

                Event.Type eventType_ = Event.Type.UNSORTED;
                if (eventTypeSelection.getSelectedIndex() == 1) {
                    eventType_ = Event.Type.SORTED;
                }
                final Event.Type eventType = eventType_;
                context.dbManager.executeInDBThread(() -> {
                    try {
                        final List<Event> result = context.dbManager.getDaos().getEventDao().queryForEq("type", eventType);
                        SwingUtilities.invokeLater(() -> {
                            eventSelection.setEnabled(true);
                            eventSelectionModel.removeAllElements();
                            result.stream().forEach((e) -> {
                                eventSelectionModel.addElement(e.getName());
                            });
                            eventSelectionModel.addElement("[+] Utwórz nowe wydarzenie");
                            Arrays.stream(eventSelection.getActionListeners()).forEach(listener -> eventSelection.removeActionListener(listener));
                            eventSelection.addActionListener((evt2) -> {
                                if (eventSelection.getSelectedIndex() == result.size()) {
                                    Event newEvent = CreateEventModal.showCreateEventModal(dialog, context);
                                    if (newEvent != null) {
                                        eventSelection.setEnabled(false);
                                        selectedEvent.set(newEvent);
                                    }
                                } else if (eventSelection.getSelectedIndex() >= 0 && eventSelection.getSelectedIndex() < result.size()) {
                                    selectedEvent.set(result.get(eventSelection.getSelectedIndex()));
                                }
                                
                                if(selectedEvent.get() != null) {
                                    Arrays.stream(deviceSelection.getActionListeners()).forEach(listener -> deviceSelection.removeActionListener(listener));
                                    deviceSelectionModel.removeAllElements();
                                    Carrier [] connectedCarriersSorted = context.deviceDetector.getConnectedCarriers(Carrier.getCarriersSortedByNumOfMFiles(context, selectedEvent.get().getMFiles(context)));
                                    Arrays.stream(connectedCarriersSorted).forEachOrdered(carrier -> deviceSelectionModel.addElement(carrier.getName()));
                                    deviceSelection.setEnabled(true);
                                    deviceSelection.addActionListener(evt3 -> {
                                        if(deviceSelection.getSelectedIndex() >= 0 && deviceSelection.getSelectedIndex() < connectedCarriersSorted.length) {
                                            Carrier c = connectedCarriersSorted[deviceSelection.getSelectedIndex()];
                                            selectedCarrier.set(c);
                                            proceedButton.setEnabled(true);
                                        }
                                    });
                                }
                            });
                        });
                    } catch (SQLException ex) {
                        Logger.getLogger(EventPanel.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            });

            cancelButton.addActionListener((evt) -> dialog.setVisible(false));

            proceedButton.addActionListener((evt) -> {
                Event targetEvent = selectedEvent.get();
                Carrier targetCarrier = selectedCarrier.get();
                if (targetEvent == null || targetCarrier == null) {
                    JOptionPane.showMessageDialog(dialog, "Nie wybrałeś wydarzenia lub urządzenia!");
                } else {
                    File targetDeviceRoot = context.deviceDetector.getDeviceRoot(targetCarrier.getName());
                    
                    if (targetEvent.getId() != event.getId()) {
                        Executors.newSingleThreadExecutor().submit(() -> {
                            SwingUtilities.invokeLater(() -> {
                                cancelButton.setEnabled(false);
                                proceedButton.setEnabled(false);
                                cancelButton.setText("Proszę czekać");
                                proceedButton.setText("Przenoszenie...");
                            });
                            Arrays.stream(mfilesToMove).forEach(mf -> {
                                try {
                                    File source = mf.getAccessibleFile(context);
                                    File targetFile = new File(mf.getProperPath(targetDeviceRoot, event));
                                    Files.copy(source, targetFile);
                                    
                                    context.dbManager.executeInDBThread(() -> {
                                        Dao<MFile_Event, Integer> mfile_eventDao = context.dbManager.getDaos().getMfile_EventDao();
                                        Dao<MFile_Localization, Integer> mfile_localizationDao = context.dbManager.getDaos().getMfile_Localization();
                                        try {
                                            /*CREATE MFile-Event*/
                                            MFile_Event newMfe = new MFile_Event();
                                            newMfe.setEvent(targetEvent);
                                            newMfe.setMfile(mf);
                                            mfile_eventDao.create(newMfe);
                                            
                                            /*DELETE old MFile-Event*/
                                            DeleteBuilder<MFile_Event, Integer> mfeDeleteBuilder = mfile_eventDao.deleteBuilder();
                                            mfeDeleteBuilder.where().eq("fileId", mf.getId()).and().eq("eventId", event.getId());
                                            mfeDeleteBuilder.delete();
                                            
                                            /*CREATE MFile-localization*/
                                            MFile_Localization newMfl = new MFile_Localization();
                                            newMfl.setCarrierId(targetCarrier.getId());
                                            newMfl.setMfile(mf);
                                            newMfl.setPath(targetDeviceRoot.toPath().relativize(targetFile.toPath()).toString());
                                            mfile_localizationDao.create(newMfl);
                                            
                                            /*DELETE old Mfile-localization*/
                                            DeleteBuilder<MFile_Localization, Integer> mflDeleteBuilder = mfile_localizationDao.deleteBuilder();
                                            ****mflDeleteBuilder.where().eq("fileId", mf.getId()).and().eq("carrierId", carrier.getId());
                                            ****mflDeleteBuilder.delete();
                                            **Którą lokalizację usunąć?
                                        } catch (SQLException ex) {
                                            Logger.getLogger(EventPanel.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    });
                                } catch (IOException ex) {
                                    Logger.getLogger(EventPanel.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            });
                            context.eBus.post(new ChangeMainPanel(new EventPanel(context, targetEvent)));
                            SwingUtilities.invokeLater(() -> {
                                dialog.setVisible(false);
                            });

                        });

                    }
                }
            });

            dialog.setContentPane(contentPanel);
            dialog.setVisible(true);

            System.out.println("Dialog finished");
        }
    }
}

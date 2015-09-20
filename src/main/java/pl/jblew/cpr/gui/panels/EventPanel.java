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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.IconLoader;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.components.browser.MFileBrowser;
import pl.jblew.cpr.gui.components.modal.FullScreenBrowser;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.gui.windows.MoveWindow;
import pl.jblew.cpr.gui.windows.RedundantCopyWindow;
import pl.jblew.cpr.gui.windows.RenameWindow;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.util.TimeUtils;

/**
 *
 * @author teofil
 */
public class EventPanel extends MainPanel {
    private final Context context;
    private final Event event;
    private final JLabel timespanLabel;
    private final JPanel browserPanel;
    private final JLabel numOfPhotosLabel;
    private final JButton moveSelectedToEventButton;
    private final JButton moveAllToEventButton;
    private final JButton fullScreenButton;
    private MFileBrowser browser;

    public EventPanel(Context context_, final Event event_) {
        this.context = context_;
        this.event = event_;

        /**
         * * LOAD EVENT DATA **
         */
        Carrier[] carriers = event.getLocalizations().stream().map(el -> el.getCarrier(context)).filter(c -> c != null).toArray(Carrier[]::new);

        /**
         * * PREPARE BUTTONS **
         */
        this.moveSelectedToEventButton = new JButton("Przenieś zaznaczone");
        this.moveAllToEventButton = new JButton("Przenieś wszystkie");
        this.fullScreenButton = new JButton("Pełen ekran");

        moveSelectedToEventButton.addActionListener((evt) -> {
            moveMFilesToEvent(Arrays.stream(browser.getSelectedLocalizedMFiles()).map(mfl -> mfl.getMFile()).toArray(MFile[]::new));
        });

        moveAllToEventButton.addActionListener((evt) -> {
            moveMFilesToEvent(Arrays.stream(browser.getAllLocalizedMFiles()).map(mfl -> mfl.getMFile()).toArray(MFile[]::new));
        });

        fullScreenButton.addActionListener((evt) -> {
            SwingUtilities.invokeLater(() -> {
                FullScreenBrowser fsb = new FullScreenBrowser(context, event);
                fsb.setVisible(true);
            });
        });

        /**
         * * PREPARE LABELS **
         */
        this.timespanLabel = new JLabel("...");
        this.numOfPhotosLabel = new JLabel("...");

        setLayout(new BorderLayout());

        JPanel infoPanel = new JPanel();
        infoPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.PAGE_AXIS));

        JLabel nameLabel = new JLabel(event.getName());
        nameLabel.setIcon(IconLoader.EDIT_16.load());
        nameLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    /*String newName = JOptionPane.showInputDialog("Podaj nową nazwę:", event.getName());
                    if (newName != null && !newName.isEmpty()) {
                        Event renamedEvent = event.rename(context, newName);
                        context.eBus.post(new EventsNode.EventsListChanged());
                        context.eBus.post(new ChangeMainPanel(new EventPanel(context, renamedEvent)));
                    }*/
                    new RenameWindow(context, event);
                }
            }
        });

        infoPanel.add(nameLabel);
        infoPanel.add(timespanLabel);

        JLabel numOfCopiesLabel = new JLabel("Kopie: " + carriers.length + (carriers.length == 1 ? " (Skopiuj na inny nośnik)" : ""));
        if (carriers.length == 1) {
            numOfCopiesLabel.setForeground(Color.RED);
        }
        infoPanel.add(numOfCopiesLabel);

        infoPanel.add(numOfPhotosLabel);

        String devicesListS = Arrays.stream(carriers).map(c -> c.getName()).reduce("", (a, b) -> a + ", " + b);
        if (!devicesListS.isEmpty()) {
            devicesListS = devicesListS.substring(2);
        }
        JLabel devicesListLabel = new JLabel("Nośniki: " + devicesListS);
        if (carriers.length == 1) {
            devicesListLabel.setForeground(Color.RED);
        }
        infoPanel.add(devicesListLabel);

        //final JButton showSelectiveEventButton = new JButton("Pokaż WYBRANE");
        //showSelectiveEventButton.setEnabled(false);
        //final JButton makeSelectiveEventButton = new JButton("Stwórz WYBRANE");
        //makeSelectiveEventButton.setEnabled(false);
        final JButton makeCopyButton = new JButton("Utwórz kopię na innym nośniku");
        if (carriers.length == 1) {
            makeCopyButton.setForeground(Color.WHITE);
            makeCopyButton.setBackground(Color.RED);
        }
        makeCopyButton.addActionListener((ActionEvent e) -> {
            new RedundantCopyWindow(context, event);
        });
        
        final JButton deleteFromDBButton = new JButton("Usuń z bazy danych");

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridLayout buttonPanelLayout = new GridLayout(0, 1);
        buttonPanelLayout.setHgap(5);
        buttonPanelLayout.setVgap(5);
        buttonPanel.setLayout(buttonPanelLayout);
        //buttonPanel.add(showSelectiveEventButton);
        //buttonPanel.add(makeSelectiveEventButton);
        buttonPanel.add(makeCopyButton);
        buttonPanel.add(deleteFromDBButton);

        deleteFromDBButton.addActionListener((evt) -> {
            int option = JOptionPane.showConfirmDialog(context.frame, "Czy jesteś pewien, że chcesz usunąć wydarzenie " + event.getName() + "?", "Usuwanie \"" + event.getName() + "\"", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                try {
                    event.delete(context, () -> {
                        SwingUtilities.invokeLater(() -> {
                            context.eBus.post(new EventsNode.EventsListChanged());
                            context.eBus.post(new ChangeMainPanel(new EmptyPanel()));
                        });
                    });
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(context.frame, e.getMessage());
                }
            }
        });
        
        JButton changeTypeButton = new JButton("Przenieś do "+(event.getType() == Event.Type.SORTED? "NIEPOSEGREGOWANYCH" : "POSEGREGOWANYCH"));
        changeTypeButton.addActionListener((evt) -> {
            event.setType((event.getType() == Event.Type.SORTED? Event.Type.UNSORTED : Event.Type.SORTED));
            event.update(context);
            SwingUtilities.invokeLater(() -> {
                            context.eBus.post(new EventsNode.EventsListChanged());
                            context.eBus.post(new ChangeMainPanel(new EventPanel(context, event)));
                        });
        });
        buttonPanel.add(changeTypeButton);

        GridLayout northPanelLayout = new GridLayout(1, 2);
        northPanelLayout.setVgap(10);
        JPanel northPanel = new JPanel(northPanelLayout);
        northPanel.add(infoPanel);
        northPanel.add(buttonPanel);
        add(northPanel, BorderLayout.NORTH);

        
        browserPanel = new JPanel();
        browserPanel.setLayout(new BorderLayout());
        browserPanel.add(new JLabel("..."), BorderLayout.CENTER);
        browserPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
        add(browserPanel, BorderLayout.CENTER);

        asyncLoadData(carriers);
    }

    @Override
    public void activate() {
        repaint();
    }

    @Override
    public void inactivate() {
        if(browser != null) browser.inactivate();
    }

    private void asyncLoadData(Carrier[] carriers) {
        context.cachedExecutor.submit(() -> {
            MFile.Localized[] mfiles = event.getLocalizedMFiles(context);

            SwingUtilities.invokeLater(() -> {
                browserPanel.removeAll();
            });

            if (mfiles.length > 0) {
                if (carriers.length == 0) {
                    SwingUtilities.invokeLater(() -> browserPanel.add(new JLabel("Brak nośników dla tego wydarzenia")));
                } else {
                    SwingUtilities.invokeLater(() -> {
                        numOfPhotosLabel.setText("Ilość zdjęć: "+mfiles.length);
                        
                        
                        LocalDateTime earliestDT = mfiles[0].getMFile().getDateTime();
                        LocalDateTime latestDT = mfiles[mfiles.length - 1].getMFile().getDateTime();
                        timespanLabel.setText(TimeUtils.formatDateRange(earliestDT, latestDT));
                    });

                    boolean hasAccessibleCarrier = Arrays.stream(carriers).filter(c -> c.isConnected(context)).findAny().isPresent();
                    if (hasAccessibleCarrier) {

                        SwingUtilities.invokeLater(() -> {
                            browser = new MFileBrowser(context, mfiles, event);
                            browser.addComponentToToolPanel(moveSelectedToEventButton);
                            browser.addComponentToToolPanel(moveAllToEventButton);
                            browser.addComponentToToolPanel(fullScreenButton);
                            browserPanel.add(browser, BorderLayout.CENTER);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> browserPanel.add(new JLabel("Aby zobaczyć zdjęcia podłącz przynajmniej jeden z nośników")));
                    }
                }
            } else {
                SwingUtilities.invokeLater(() -> browserPanel.add(new JLabel("To wydarzenie nie zawiera żadnych zdjęć")));
            }
            
            SwingUtilities.invokeLater(() -> {
                browserPanel.revalidate();
                browserPanel.repaint();
            });
        });
    }

    private void moveMFilesToEvent(MFile[] mfilesToMove) {
        if (mfilesToMove != null && mfilesToMove.length > 0) {
            new MoveWindow(context, event, mfilesToMove);
        }
    }
}

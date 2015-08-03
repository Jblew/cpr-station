/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.IconLoader;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.components.MFileBrowser;
import pl.jblew.cpr.gui.treenodes.EventsNode;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;

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
            moveMFilesToEvent(Arrays.stream(browser.getSelectedLocalizedMFiles()).map(mfl -> mfl.getMFile()).toArray(MFile[]::new));
        });

        moveAllToEventButton.addActionListener((evt) -> {
            moveMFilesToEvent(Arrays.stream(browser.getAllLocalizedMFiles()).map(mfl -> mfl.getMFile()).toArray(MFile[]::new));
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
        prepareGridRow(gridPanel, "Nośniki: ", new JLabel(event.getLocalizations().stream().map(el -> el.getCarrier(context).getName()).reduce("", (a, b) -> a + ", " + b).substring(2)));

        final JButton showSelectiveEventButton = new JButton("Pokaż WYBRANE");
        showSelectiveEventButton.setEnabled(false);
        final JButton makeSelectiveEventButton = new JButton("Stwórz WYBRANE");
        makeSelectiveEventButton.setEnabled(false);
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
        executor.submit(() -> {
            MFile.Localized[] mfiles = event.getLocalizedMFiles(context);
            //Arrays.stream(mfiles).forEachOrdered(mfl -> System.out.println(mfl));
            int redundancy = event.getRedundancy();
            System.out.println("MFiles.length=" + mfiles.length);
            if (mfiles.length > 0) {

                SwingUtilities.invokeLater(() -> {
                    numOfPhotosLabel.setText(mfiles.length + "");
                    LocalDateTime earliesDT = mfiles[0].getMFile().getDateTime();
                    LocalDateTime latestDT = mfiles[mfiles.length - 1].getMFile().getDateTime();
                    DateTimeFormatter f = DateTimeFormatter.ofPattern("YYYY.MM.dd HH:ss");
                    String timeSpan = f.format(earliesDT) + (earliesDT.equals(latestDT) ? "" : " - " + f.format(latestDT));
                    timespanLabel.setText(timeSpan);
                    timespanLabel.setText("");
                    String warn = "";

                    if (redundancy < 2) {
                        warn = " (Koniecznie wykonaj dodatkową kopię na innym nośniku)";
                    }
                    numOfCopiesLabel.setText(redundancy + warn);

                    browserPanel.removeAll();

                    browser = new MFileBrowser(context, mfiles, event);
                    browser.addComponentToToolPanel(moveSelectedToEventButton);
                    browser.addComponentToToolPanel(moveAllToEventButton);
                    browserPanel.add(browser, BorderLayout.CENTER);
                    browserPanel.revalidate();
                    browserPanel.repaint();
                });
            } else {
                browserPanel.removeAll();
                browserPanel.add(new JLabel("To wydarzenie nie zawiera żadnych zdjęć"));
                browserPanel.revalidate();
                browserPanel.repaint();
            }
        });
    }

    private void moveMFilesToEvent(MFile[] mfilesToMove) {
        if (mfilesToMove != null && mfilesToMove.length > 0) {
            context.eBus.post(new ChangeMainPanel(new MovePanel(context, event, mfilesToMove)));

        }
    }
}

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
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.components.MFileBrowser;
import pl.jblew.cpr.logic.Event;

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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    //private final MFileBrowser browser;

    public EventPanel(Context context_, final Event event_) {
        this.context = context_;
        this.event = event_;

        setLayout(new BorderLayout());

        timespanLabel = new JLabel("...");
        numOfCopiesLabel = new JLabel("...");
        numOfPhotosLabel = new JLabel("...");

        JPanel gridPanel = new JPanel();
        gridPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridLayout gridLayout = new GridLayout(0, 2);
        gridLayout.setHgap(10);
        gridLayout.setVgap(10);
        gridPanel.setLayout(gridLayout);
        prepareGridRow(gridPanel, "Nazwa: ", new JLabel(event.getName()));
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
        if(fed != null) {
                SwingUtilities.invokeLater(() -> {
                    numOfPhotosLabel.setText(fed.mfiles.length + "");

                    if (fed.startDate != null) {
                        SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd");
                        String timeSpan = f.format(fed.startDate) + " - " + f.format(fed.endDate);
                        timespanLabel.setText(timeSpan);
                    } else {
                        timespanLabel.setText("");
                        
                    }
                    String warn = "";
                    if(fed.minRedundancy < 2) {
                        warn = " (Koniecznie wykonaj dodatkową kopię na innym nośniku)";
                    }
                    numOfCopiesLabel.setText((fed.minRedundancy==fed.maxRedundancy? fed.minRedundancy+"" : fed.minRedundancy+" - "+fed.maxRedundancy)+warn);
                    
                    browserPanel.removeAll();
                    browserPanel.add(new MFileBrowser(context, fed.mfiles, event), BorderLayout.CENTER);
                    browserPanel.revalidate();
                    browserPanel.repaint();
                });
        }
    }
}

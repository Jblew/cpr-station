/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.db.DatabaseManager;
import pl.jblew.cpr.gui.ChangeMainPanel;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.components.MFileBrowser;
import pl.jblew.cpr.gui.components.SwingFileBrowser;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.MFile_Event;
import pl.jblew.cpr.logic.MFile_Localization;
import pl.jblew.cpr.logic.io.CarrierMaker;

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

        /*JButton importSelectedButton = new JButton("Utwórz wydarzenie z zaznaczonych");
         importSelectedButton.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
         SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
         File[] selectedFiles = browser.getSelectedFiles();
         if (selectedFiles.length > 0) {
         context.eBus.post(new ChangeMainPanel(new ImportPanel(context, selectedFiles, deviceName, root)));
         }
         }
         });

         }
         });
        

         browser = new SwingFileBrowser(root);
         browser.addComponentToToolPanel(importSelectedButton);*/
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
        context.dbManager.executeInDBThread(() -> {
            try {
                Dao<MFile, Integer> mfileDao = context.dbManager.getDaos().getMfileDao();// context.dbManager.getDaos().getMfile_EventDao().queryForEq("eventId", event.getId());
                Dao<MFile_Event, Integer> mfile_EventDao = context.dbManager.getDaos().getMfile_EventDao();
                QueryBuilder<MFile_Event, Integer> queryToJoin = mfile_EventDao.queryBuilder();
                queryToJoin.where().ge("eventId", event.getId());
                final List<MFile> mfiles = mfileDao.queryBuilder().query();
                //context.dbManager.getDaos().getMfile_EventDao().
                final AtomicReference<Date> earliestDate = new AtomicReference<>(null);
                final AtomicReference<Date> latestDate = new AtomicReference<>(null);
                final AtomicInteger minRedundancy = new AtomicInteger(1);
                final AtomicInteger maxRedundancy = new AtomicInteger(0);
                for (MFile mf : mfiles) {
                    if (earliestDate.get() == null || mf.getDate().before(earliestDate.get())) {
                        earliestDate.set(mf.getDate());
                    } else if (latestDate.get() == null || mf.getDate().after(earliestDate.get())) {
                        latestDate.set(mf.getDate());
                    }
                    
                    
                    int redundancy = 0;
                    Set<String> carrierIds = new HashSet<>();
                    for(MFile_Localization mfl : mf.getLocalizations()) {
                        if(!carrierIds.contains(mfl.getCarrierId()+"")) {
                            redundancy++;
                            carrierIds.add(mfl.getCarrierId()+"");
                        }
                    }
                    if(redundancy < minRedundancy.get()) minRedundancy.set(redundancy);
                    if(redundancy > maxRedundancy.get()) maxRedundancy.set(redundancy);
                }
                SwingUtilities.invokeLater(() -> {
                    numOfPhotosLabel.setText(mfiles.size() + "");

                    if (earliestDate.get() != null) {
                        SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd");
                        String timeSpan = f.format(earliestDate.get()) + " - " + f.format(latestDate.get());
                        timespanLabel.setText(timeSpan);
                    } else {
                        timespanLabel.setText("");
                        
                    }
                    String warn = "";
                    if(minRedundancy.get() < 2) {
                        warn = "Koniecznie wykonaj dodatkową kopię na innym nośniku";
                    }
                    numOfCopiesLabel.setText((minRedundancy.get()==maxRedundancy.get()? minRedundancy.get()+"" : minRedundancy.get()+" - "+maxRedundancy.get())+warn);
                    
                    browserPanel.removeAll();
                    browserPanel.add(new MFileBrowser(context, mfiles, event), BorderLayout.CENTER);
                    browserPanel.revalidate();
                    browserPanel.repaint();
                    System.out.println("!!!!ADDED MFileBrowser");
                });
            }catch (SQLException ex) {
                Logger.getLogger(EventPanel.class.getName()).log(Level.SEVERE, null, ex);
                }
        });
    }
}

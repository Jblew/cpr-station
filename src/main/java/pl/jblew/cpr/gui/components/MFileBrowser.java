/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.input.KeyCode;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.MFile_Localization;
import pl.jblew.cpr.logic.io.ThumbnailLoader;

/**
 *
 * @author teofil
 */
public class MFileBrowser extends JPanel {
    private final Context context;
    private final Lock dataLock = new ReentrantLock();
    private final List<MFile> mfiles;
    private final Map<MFile, MFileComponent> components = new HashMap<>();
    private final JTabbedPane tabbedPane;
    private final Event event;
    private final SingleBrowsingPanel singleBrowsingPanel;
    private final GridBrowsingPanel gridBrowsingPanel;
    private final ThumbnailLoader thumbnailLoader = new ThumbnailLoader(128, true);

    private final JPanel toolPanel;
    private final ImageIcon emptyImage;
    private final ExecutorService loadingExecutor = Executors.newSingleThreadExecutor();

    //private final ThumbnailLoader thumbnailLoader = new ThumbnailLoader(128);
    //private final Lock dataLock = new ReentrantLock();
    //private File[] children;
    //private final Map<File, FileComponent> components = new HashMap<>();
    public MFileBrowser(Context context_, List<MFile> mfiles_, Event event_) {
        this.mfiles = mfiles_;
        this.event = event_;
        this.context = context_;
        tabbedPane = new JTabbedPane();

        emptyImage = new ImageIcon(getClass().getClassLoader().getResource("images/empty128.gif"));

        singleBrowsingPanel = new SingleBrowsingPanel();
        gridBrowsingPanel = new GridBrowsingPanel();

        setBackground(Color.WHITE);
        setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        setLayout(new BorderLayout());

        JButton tilesViewButton = new JButton("", new ImageIcon(FileBrowser.class.getClassLoader().getResource("images/view-tiles16.png")));
        JButton singleViewButton = new JButton("", new ImageIcon(FileBrowser.class.getClassLoader().getResource("images/view-single16.png")));

        tilesViewButton.addActionListener((ActionEvent e) -> {
            SwingUtilities.invokeLater(() -> {
                tabbedPane.setSelectedIndex(0);
                for (MFileComponent mfc : components.values()) {
                    gridBrowsingPanel.addMFileComponent(mfc);
                }
                
            });
        });
        singleViewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        tabbedPane.setSelectedIndex(1);
                        for (MFileComponent mfc : components.values()) {
                            singleBrowsingPanel.addMFileComponent(mfc);
                        }
                    }
                });
            }
        });

        toolPanel = new JPanel();
        toolPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        toolPanel.add(tilesViewButton);
        toolPanel.add(singleViewButton);
        add(toolPanel, BorderLayout.NORTH);
        toolPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        /*browsingPanel = new AtomicReference<>();
         browsingPanel.set(singleBrowsingPanel);

         add(browsingPanel.get().getWhole(), BorderLayout.CENTER);
         */
        
        tabbedPane.setUI(new NoTabTabbedPaneUI());
        tabbedPane.addTab(null, gridBrowsingPanel.getWhole());

        //JPanel testPanel = new JPanel();
        //testPanel.setBackground(Color.RED);
        tabbedPane.addTab(null, singleBrowsingPanel);
        add(tabbedPane, BorderLayout.CENTER);

        loadFiles();

    }

    public void inactivate() {
        //browsingPanel.inactivate();
    }

    private void loadFiles() {
        dataLock.lock();
        try {
            components.clear();
            for (MFile mf : mfiles) {
                MFileComponent component = new MFileComponent(mf, thumbnailLoader);

                gridBrowsingPanel.addMFileComponent(component);
                components.put(mf, component);
                //System.out.println("Added mf component");
            }

            gridBrowsingPanel.revalidate();
            gridBrowsingPanel.repaint();

        } finally {
            dataLock.unlock();
        }
    }

    private class MFileComponent extends JToggleButton {
        private final MFile mfile;
        private final ThumbnailLoader thumbnailLoader;
        private final AtomicReference<OpenMFileCallback> callback = new AtomicReference<>();
        private final MFileComponent me = this;

        public MFileComponent(MFile file_, ThumbnailLoader thumbnailLoader_) {
            super();
            this.mfile = file_;
            this.thumbnailLoader = thumbnailLoader_;

            setText(mfile.getName());
            setIcon(emptyImage);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setPreferredSize(new Dimension(164, 164));

            addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        OpenMFileCallback clb = callback.get();
                        if (clb != null) {
                            clb.clickedOpen(me);
                        }
                    } else if (e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK) {
                        if (isFileSelected()) {
                            unselect();
                        } else {
                            select();
                        }
                    } else if (e.getModifiersEx() == InputEvent.SHIFT_DOWN_MASK) {
                        int selectionStart = 0;
                        int myIndex = 0;
                        dataLock.lock();
                        try {
                            for (int i = 0; i < mfiles.size(); i++) {
                                if (mfiles.get(i).equals(mfile)) {
                                    myIndex = i;
                                    break;
                                }
                            }

                            for (MFile f : components.keySet()) {
                                MFileComponent fc = components.get(f);
                                if (fc.isFileSelected()) {
                                    for (int i = 0; i < mfiles.size(); i++) {
                                        if (mfiles.get(i).equals(f)) {
                                            selectionStart = i;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }

                            for (int i = selectionStart; i <= myIndex; i++) {
                                MFile f = mfiles.get(i);
                                components.get(f).select();
                            }
                        } finally {
                            dataLock.unlock();
                        }
                    } else {
                        dataLock.lock();
                        try {
                            for (MFileComponent fc : components.values()) {
                                fc.unselect();
                            }
                        } finally {
                            dataLock.unlock();
                        }
                        select();
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                }

                @Override
                public void mouseExited(MouseEvent e) {
                }
            });

            loadingExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    context.dbManager.executeInDBThread(new Runnable() {
                        @Override
                        public void run() {
                            //System.out.println("Loading localizations (" + mfile.getName() + ")...");
                            for (MFile_Localization localization : mfile.getLocalizations()) {
                                //System.out.println("Checking localization " + localization.getPath());
                                File f = localization.getFile(context);
                                //System.out.println("Got file");
                                if (f != null && f.canRead()) {
                                    if (thumbnailLoader.canBeLoaded(f)) {
                                        thumbnailLoader.loadImage(f, new ThumbnailLoader.LoadedListener() {
                                            @Override
                                            public void thumbnailLoaded(final ImageIcon img) {
                                                SwingUtilities.invokeLater(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        setIcon(img);
                                                    }
                                                });
                                            }
                                        });
                                        break;
                                    }
                                }
                            }

                        }
                    });
                }
            });
        }

        public void select() {
            //selected.set(true);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setSelected(true);
                    //setForeground(Color.RED);
                }
            });
        }

        public void unselect() {
            //selected.set(false);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setSelected(false);
                    //setForeground(Color.BLACK);
                }
            });
        }

        public boolean isFileSelected() {
            return isSelected();
        }

        public void setOpenCalback(OpenMFileCallback oc) {
            callback.set(oc);
        }
    }

    interface OpenMFileCallback {
        public void clickedOpen(MFileComponent cmp);
    }

    private abstract class BrowsingPanel extends JPanel {
        public abstract JComponent getWhole();

        public abstract void addMFileComponent(MFileComponent mf);
    }

    private class GridBrowsingPanel extends BrowsingPanel implements OpenMFileCallback {
        private final JScrollPane scrollPane;

        public GridBrowsingPanel() {
            setLayout(new WrapLayout(WrapLayout.LEFT));
            setSize(new Dimension(500, 500));

            scrollPane = new JScrollPane(this);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            scrollPane.getVerticalScrollBar().setUnitIncrement(60);
            scrollPane.setBackground(Color.WHITE);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());

            //add(new JButton("Hehe"));
        }

        @Override
        public JComponent getWhole() {
            return scrollPane;
        }

        @Override
        public void addMFileComponent(MFileComponent mf) {
            mf.setOpenCalback(this);
            add(mf);
        }

        @Override
        public void clickedOpen(MFileComponent cmp) {
            tabbedPane.setSelectedIndex(1);
            for (MFileComponent mfc : components.values()) {
                singleBrowsingPanel.addMFileComponent(mfc);
            }
            singleBrowsingPanel.changeMFile(cmp.mfile, cmp);
        }

    }

    private class SingleBrowsingPanel extends BrowsingPanel implements OpenMFileCallback {
        private final AtomicReference<MFileComponent> currentComponent = new AtomicReference<>(null);
        private final AtomicReference<JComponent> browser = new AtomicReference<>(null);
        //private final AtomicInteger selectedIndex = new AtomicInteger(0);
        private final JScrollPane scrollPane;
        private final JPanel southPanel;
        private final SingleBrowsingPanel me = this;

        public SingleBrowsingPanel() {
            setLayout(new BorderLayout());

            browser.set(new JLabel("Ładowanie..."));
            add(browser.get(), BorderLayout.CENTER);

            southPanel = new JPanel();
            southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.LINE_AXIS));

            scrollPane = new JScrollPane(southPanel);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            scrollPane.getHorizontalScrollBar().setUnitIncrement(60);
            add(scrollPane, BorderLayout.SOUTH);

            //southPanel.add(scrollPane);
            //changeMFile(mfiles.get(0));
            this.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {
                }

                @Override
                public void keyPressed(KeyEvent e) {
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    MFileComponent leftComponent = null;
                    MFileComponent rightComponent = null;
                    MFileComponent prev = null;
                    for(Component c : southPanel.getComponents()) {
                        if(c instanceof MFileComponent) {
                            MFileComponent mfc = (MFileComponent)c;
                            if(mfc==currentComponent.get()) {
                                leftComponent = prev;
                            }
                            if(prev == currentComponent.get()) {
                                rightComponent = mfc;
                                break;
                            }
                            prev = mfc;
                            
                        }
                    }
                    
                    if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
                        changeMFile(rightComponent.mfile, rightComponent);
                    }
                    else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
                        changeMFile(leftComponent.mfile, leftComponent);
                    }
                }
            
            });
        }

        public void changeMFile(final MFile mfile, final MFileComponent mfc) {
            currentComponent.set(mfc);
            context.dbManager.executeInDBThread(new Runnable() {
                @Override
                public void run() {
                    for (MFile_Localization localization : mfile.getLocalizations()) {
                        final File f = localization.getFile(context);
                        if (f != null && f.canRead()) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    if (browser.get() != null) {
                                        remove(browser.get());
                                        PhotoBrowser newBrowser = new PhotoBrowser(f, PhotoBrowser.ScaleType.FIT);
                                        browser.set(newBrowser);
                                        add(newBrowser, BorderLayout.CENTER);
                                        revalidate();
                                        repaint();
                                    }
                                }
                            });
                            break;
                        }
                    }
                }
            });

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    scrollSouthToCenter(mfc);
                    mfc.select();
                    for(Component c : southPanel.getComponents()) {
                        if(c instanceof MFileComponent) {
                            MFileComponent mfc_ = (MFileComponent)c;
                            if(mfc_ != mfc) {
                                mfc_.unselect();
                            }
                        }
                    }
                }
            });

        }

        private void scrollSouthToCenter(MFileComponent mfc) {
            JViewport viewport = scrollPane.getViewport();
            Rectangle rect = mfc.getBounds();
            Rectangle viewRect = viewport.getViewRect();
            rect.setLocation(rect.x - viewRect.x, rect.y - viewRect.y);

            int centerX = (viewRect.width - rect.width) / 2;
            int centerY = (viewRect.height - rect.height) / 2;
            if (rect.x < centerX) {
                centerX = -centerX;
            }
            if (rect.y < centerY) {
                centerY = -centerY;
            }
            rect.translate(centerX, centerY);
            viewport.scrollRectToVisible(rect);
        }

        @Override
        public JComponent getWhole() {
            return this;
        }

        @Override
        public void addMFileComponent(MFileComponent mf) {
            mf.setOpenCalback(this);
            southPanel.add(mf);
            southPanel.revalidate();
            southPanel.repaint();
        }

        @Override
        public void clickedOpen(MFileComponent cmp) {
            changeMFile(cmp.mfile, cmp);
        }
    }
}

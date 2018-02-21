/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components.browser;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import javax.swing.*;
import javax.swing.border.*;
import pl.jblew.cpr.Settings;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.components.NoTabTabbedPaneUI;
import pl.jblew.cpr.gui.components.WrapLayout;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;
import pl.jblew.cpr.logic.io.ThumbnailLoader;
import pl.jblew.cpr.gui.util.*;

/**
 *
 * @author teofil
 */
public class MFileBrowser extends JPanel {
    private final Context context;
    private final Lock dataLock = new ReentrantLock();
    private final MFile.Localized[] localizedMFiles;
    private final Map<MFile.Localized, MFileComponent> components = new HashMap<>();
    private final JTabbedPane tabbedPane;
    private final Event event;
    private final SingleBrowsingPanel singleBrowsingPanel;
    private final GridBrowsingPanel gridBrowsingPanel;
    private final ThumbnailLoader thumbnailLoader = new ThumbnailLoader(Settings.THUMBNAIL_MAX_SIZE, true);

    private final JPanel toolPanel;
    private final ImageIcon emptyImage;

    public MFileBrowser(Context context_, MFile.Localized[] localizedMFiles, Event event_) {
        this.localizedMFiles = localizedMFiles;
        this.event = event_;
        this.context = context_;
        tabbedPane = new JTabbedPane();

        emptyImage = new ImageIcon(getClass().getClassLoader().getResource("images/empty128.gif"));

        singleBrowsingPanel = new SingleBrowsingPanel();
        this.addKeyListener(singleBrowsingPanel);
        gridBrowsingPanel = new GridBrowsingPanel();

        setBackground(Color.WHITE);
        setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        setLayout(new BorderLayout());

        JButton tilesViewButton = new JButton("", new ImageIcon(FileBrowser.class.getClassLoader().getResource("images/view-tiles16.png")));
        JButton singleViewButton = new JButton("", new ImageIcon(FileBrowser.class.getClassLoader().getResource("images/view-single16.png")));

        tilesViewButton.addActionListener((ActionEvent e) -> {
            SwingUtilities.invokeLater(() -> {
                tabbedPane.setSelectedIndex(0);
                components.values().stream().sorted().forEachOrdered((mfc) -> {
                    gridBrowsingPanel.addMFileComponent(mfc);
                });

            });
        });
        singleViewButton.addActionListener((ActionEvent e) -> {
            SwingUtilities.invokeLater(() -> {
                tabbedPane.setSelectedIndex(1);
                components.values().stream().sorted().forEachOrdered((mfc) -> {
                    singleBrowsingPanel.addMFileComponent(mfc);
                });
            });
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

        this.requestFocusInWindow();
    }

    public void addComponentToToolPanel(JComponent c) {
        toolPanel.add(c);
    }

    public void inactivate() {
        thumbnailLoader.stopAndInactivate();
    }

    public void showSingleView() {
        SwingUtilities.invokeLater(() -> {
            tabbedPane.setSelectedIndex(1);
            components.values().stream().sorted().forEachOrdered((mfc) -> {
                singleBrowsingPanel.addMFileComponent(mfc);
            });
        });
    }

    private void loadFiles() {
        dataLock.lock();
        try {
            components.clear();
            for (int i = 0; i < localizedMFiles.length; i++) {
                MFile.Localized mfl = localizedMFiles[i];
                MFileComponent component = new MFileComponent(mfl, thumbnailLoader);

                gridBrowsingPanel.addMFileComponent(component);
                components.put(mfl, component);
            }

            gridBrowsingPanel.revalidate();
            gridBrowsingPanel.repaint();
        } finally {
            dataLock.unlock();
        }
    }

    public MFile.Localized[] getSelectedLocalizedMFiles() {
        try {
            LinkedList<MFile.Localized> result = new LinkedList<>();
            if (SwingUtilities.isEventDispatchThread()) {
                components.values().stream().forEach((mfc) -> {
                    if (mfc.isFileSelected()) {
                        result.add(mfc.localizedMFile);
                    }
                });
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    components.values().stream().forEach((mfc) -> {
                        if (mfc.isFileSelected()) {
                            result.add(mfc.localizedMFile);
                        }
                    });
                });
            }
            return result.toArray(new MFile.Localized[]{});
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(MFileBrowser.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public MFile.Localized[] getAllLocalizedMFiles() {
        return localizedMFiles;
    }

    private class MFileComponent extends JButton implements Comparable<MFileComponent> {
        private final MFile.Localized localizedMFile;
        private final ThumbnailLoader thumbnailLoader;
        private final AtomicReference<OpenMFileCallback> callback = new AtomicReference<>();
        private final MFileComponent me = this;
        private final AtomicBoolean selected = new AtomicBoolean(false);
        //private final AtomicBoolean marked = new AtomicBoolean(false);

        public MFileComponent(MFile.Localized file_, ThumbnailLoader thumbnailLoader_) {
            super();
            this.localizedMFile = file_;
            this.thumbnailLoader = thumbnailLoader_;

            setText(localizedMFile.getMFile().getFilename());
            setIcon(emptyImage);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setPreferredSize(new Dimension(140, 140));
            //this.getModel().setArmed(false);
            //getModel()
            setEnabled(false);

            addMouseListener(new DoubleClickMouseListener() {
                @Override
                public void doubleLeftClick(MouseEvent e) {
                    OpenMFileCallback clb = callback.get();
                    if (clb != null) {
                        clb.clickedOpen(me);
                    }
                }

                @Override
                public void singleLeftClick(MouseEvent e) {
                    if (e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK) {
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
                            myIndex = IntStream.range(0, localizedMFiles.length).filter(i -> localizedMFiles[i].equals(localizedMFile)).findFirst().orElse(0);

                            MFileComponent firstSelected = components.values().stream().filter((MFileComponent mfc) -> mfc.isFileSelected()).findFirst().orElse(null);
                            if (firstSelected != null) {
                                selectionStart = IntStream.range(0, localizedMFiles.length).filter(i -> localizedMFiles[i].equals(firstSelected.localizedMFile)).findFirst().orElse(0);
                            }

                            IntStream.rangeClosed(Math.min(selectionStart, myIndex), Math.max(selectionStart, myIndex)).forEach(i -> components.get(localizedMFiles[i]).select());
                        } finally {
                            dataLock.unlock();
                        }
                    } else {
                        dataLock.lock();
                        try {
                            components.values().stream().forEach((fc) -> fc.unselect());
                        } finally {
                            dataLock.unlock();
                        }
                        select();
                    }
                }
            });

            context.dbManager.executeInDBThread(() -> {
                File f = localizedMFile.getFile();
                if (f != null) {
                    if (ThumbnailLoader.canBeLoaded(f)) {
                        thumbnailLoader.loadImage(f, (final ImageIcon img) -> {
                            SwingUtilities.invokeLater(() -> {
                                setIcon(img);
                                setDisabledIcon(img);
                            });
                        });
                    }
                }
            });
        }

        public void select() {
            selected.set(true);
            updateSelectionState();
        }

        public void unselect() {
            selected.set(false);
            updateSelectionState();
        }

        public void updateSelectionState() {
            SwingUtilities.invokeLater(() -> {
                if (selected.get()) {
                    setForeground(Color.RED);
                    setEnabled(true);
                } else {
                    setForeground(Color.BLACK);
                    setEnabled(false);
                }
            });
        }

        public boolean isFileSelected() {
            return selected.get();
        }

        public void setOpenCalback(OpenMFileCallback oc) {
            callback.set(oc);
        }

        @Override
        public int compareTo(MFileComponent cmp) {
            return localizedMFile.compareTo(cmp.localizedMFile);
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
            mf.updateSelectionState();
            add(mf);
        }

        @Override
        public void clickedOpen(MFileComponent cmp) {
            tabbedPane.setSelectedIndex(1);
            components.values().stream().sorted().forEachOrdered((mfc) -> {
                singleBrowsingPanel.addMFileComponent(mfc);
            });
            singleBrowsingPanel.changeMFile(cmp.localizedMFile, cmp);
        }

    }

    private class SingleBrowsingPanel extends BrowsingPanel implements OpenMFileCallback, KeyListener {
        private final AtomicReference<MFileComponent> currentComponent = new AtomicReference<>(null);
        private final PreloadingPhotoBrowser browser;
        private final PreloadingPhotoBrowser.PreloadableImage[] preloadables;
        private final JScrollPane scrollPane;
        private final JPanel southPanel;
        private final SingleBrowsingPanel me = this;

        public SingleBrowsingPanel() {
            setLayout(new BorderLayout());

            preloadables = Arrays.stream(localizedMFiles).map(mfl -> mfl.getFile()).filter(f -> f != null && f.canRead())
                    .map(f -> new PreloadingPhotoBrowser.PreloadableImage(f, null)).toArray(PreloadingPhotoBrowser.PreloadableImage[]::new);

            browser = new PreloadingPhotoBrowser(context, preloadables, 0, PreloadingPhotoBrowser.ScaleType.FIT);
            add(browser, BorderLayout.CENTER);

            southPanel = new JPanel();
            southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.LINE_AXIS));

            scrollPane = new JScrollPane(southPanel);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            scrollPane.getHorizontalScrollBar().setUnitIncrement(60);
            add(scrollPane, BorderLayout.SOUTH);

            //southPanel.add(scrollPane);
            //changeMFile(mfiles.get(0));
            this.addKeyListener(this);
        }

        public void changeMFile(final MFile.Localized localizedMFile, final MFileComponent mfc) {
            currentComponent.set(mfc);
            SwingUtilities.invokeLater(() -> {
                if (localizedMFile.getFile() != null) {
                    browser.changeImage(localizedMFile.getFile());
                }

                scrollSouthToCenter(mfc);
                me.requestFocusInWindow();
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
            mf.updateSelectionState();
            southPanel.add(mf);
            southPanel.revalidate();
            southPanel.repaint();
        }

        @Override
        public void clickedOpen(MFileComponent cmp) {
            changeMFile(cmp.localizedMFile, cmp);
        }

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
            for (Component c : southPanel.getComponents()) {
                if (c instanceof MFileComponent) {
                    MFileComponent mfc = (MFileComponent) c;
                    if (mfc == currentComponent.get()) {
                        leftComponent = prev;
                    }
                    if (prev == currentComponent.get()) {
                        rightComponent = mfc;
                        break;
                    }
                    prev = mfc;

                }
            }

            if (e.getKeyCode() == KeyEvent.VK_RIGHT && rightComponent != null) {
                changeMFile(rightComponent.localizedMFile, rightComponent);
            } else if (e.getKeyCode() == KeyEvent.VK_LEFT && leftComponent != null) {
                changeMFile(leftComponent.localizedMFile, leftComponent);
            }
        }
    }
}

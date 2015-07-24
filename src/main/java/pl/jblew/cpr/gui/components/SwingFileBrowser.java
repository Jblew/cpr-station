/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.MimetypesFileTypeMap;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import pl.jblew.cpr.logic.io.ThumbnailLoader;
import pl.jblew.cpr.util.ThumbnailGenerator;
import pl.jblew.cpr.util.TwoTuple;

/**
 *
 * @author teofil
 */
public class SwingFileBrowser extends JPanel {
    private File root;
    private final JPanel browsingPanel;
    private final JScrollPane scrollPane;
    private final AtomicReference<File> cwd;
    private final JLabel dirNameLabel;
    private final JPanel toolPanel;
    private final ImageIcon emptyImage;
    private final ImageIcon defaultImage;
    private final ImageIcon dirImage;

    private final ThumbnailLoader thumbnailLoader = new ThumbnailLoader(128);
    private final Lock dataLock = new ReentrantLock();
    private File[] children;
    private final Map<File, FileComponent> components = new HashMap<>();

    public SwingFileBrowser(File root_) {
        this.root = root_;
        this.cwd = new AtomicReference<>(root);

        defaultImage = new ImageIcon(getClass().getClassLoader().getResource("images/devices.png"));
        emptyImage = new ImageIcon(getClass().getClassLoader().getResource("images/empty128.gif"));
        dirImage = new ImageIcon(getClass().getClassLoader().getResource("images/directoryThumbnail.gif"));

        setBackground(Color.WHITE);
        setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        setLayout(new BorderLayout());

        JButton upButton = new JButton("Do góry", new ImageIcon(FileBrowser.class.getClassLoader().getResource("images/up16.png")));
        upButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File safeCwd = cwd.get();
                File parent = safeCwd.getParentFile();
                if (parent != null && !safeCwd.equals(root)) {
                    changeCWD(parent);
                }
            }
        });

        dirNameLabel = new JLabel("/" + root.toPath().relativize(cwd.get().toPath()).toString());
        //System.out.println("Relative path: "+relativePath);

        toolPanel = new JPanel();
        toolPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        toolPanel.add(upButton);
        toolPanel.add(dirNameLabel);
        add(toolPanel, BorderLayout.NORTH);
        toolPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        browsingPanel = new JPanel(new WrapLayout(WrapLayout.LEFT));
        browsingPanel.setSize(new Dimension(500, 500));

        scrollPane = new JScrollPane(browsingPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(60);
        scrollPane.setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());

        add(scrollPane, BorderLayout.CENTER);

        changeCWD(root);
    }

    public void inactivate() {
        //browsingPanel.inactivate();
    }

    public void changeCWD(File f) {
        cwd.set(f);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dirNameLabel.setText("/" + root.toPath().relativize(cwd.get().toPath()).toString());
            }
        });
        reloadFiles();
    }

    public File getCWD() {
        return cwd.get();
    }

    private void reloadFiles() {
        dataLock.lock();
        try {
            final File[] childrenSafe = cwd.get().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return !(name.startsWith("."));
                }
            });
            components.clear();
            thumbnailLoader.stopAll();
            children = childrenSafe;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    browsingPanel.removeAll();
                    for (File child : childrenSafe) {
                        final FileComponent cmp = new FileComponent(child);

                        if (child.isDirectory()) {
                            cmp.setIcon(dirImage);
                        } else if (thumbnailLoader.canBeLoaded(child)) {
                            thumbnailLoader.loadImage(child, new ThumbnailLoader.LoadedListener() {
                                @Override
                                public void thumbnailLoaded(final ImageIcon img) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            //long s = System.currentTimeMillis();
                                            //ImageIcon icon = new ImageIcon(img);
                                            //System.out.println("Icon creation time: "+(System.currentTimeMillis()-s)+"ms");
                                            //cmp.setImage(img);
                                            cmp.setIcon(img);
                                        }
                                    });
                                }
                            });
                        }

                        browsingPanel.add(cmp);
                        dataLock.lock();
                        try {
                            components.put(child, cmp);
                        } finally {
                            dataLock.unlock();
                        }
                    }
                }
            });
        } finally {
            dataLock.unlock();
        }
    }

    public void addComponentToToolPanel(JComponent c) {
        toolPanel.add(c);
    }

    public File[] getSelectedFiles() {
        try {
            final List<File> selectedFilesList = new LinkedList<>();
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    for (File f : components.keySet()) {
                        FileComponent fc = components.get(f);
                        if (fc.isFileSelected()) {
                            selectedFilesList.add(f);
                        }
                    }
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeAndWait(r);
            }
            File[] out = new File[selectedFilesList.size()];
            int i = 0;
            for (File f : selectedFilesList) {
                out[i] = f;
                i++;
            }
            return out;
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    private class FileComponent extends JToggleButton {
        private final File file;
        private AtomicBoolean selected = new AtomicBoolean(false);

        public FileComponent(File file_) {
            super();
            this.file = file_;
            setText(file.getName());
            setIcon(defaultImage);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setPreferredSize(new Dimension(164, 164));

            addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        if (file.isDirectory() && file.canRead()) {
                            changeCWD(file);
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
                            for (int i = 0; i < children.length; i++) {
                                if (children[i].equals(file)) {
                                    myIndex = i;
                                    break;
                                }
                            }

                            for (File f : components.keySet()) {
                                FileComponent fc = components.get(f);
                                if (fc.isFileSelected()) {
                                    for (int i = 0; i < children.length; i++) {
                                        if (children[i].equals(f)) {
                                            selectionStart = i;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }

                            for (int i = selectionStart; i <= myIndex; i++) {
                                File f = children[i];
                                components.get(f).select();
                            }
                        } finally {
                            dataLock.unlock();
                        }
                    } else {
                        dataLock.lock();
                        try {
                            for (FileComponent fc : components.values()) {
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
    }
}

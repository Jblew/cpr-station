/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.border.BevelBorder;

/**
 *
 * @author teofil
 */
public class FileBrowser extends JPanel {
    private File root;
    private final BrowsingPanel browsingPanel;
    private final JScrollPane scrollPane;
    private final AtomicReference<File> cwd;
    private final JLabel dirNameLabel;
    private final JPanel toolPanel;

    public FileBrowser(File root_) {
        this.root = root_;
        this.cwd = new AtomicReference<>(root);
        this.browsingPanel = new BrowsingPanel();

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
                    cwd.set(parent);
                    dirNameLabel.setText("/" + root.toPath().relativize(cwd.get().toPath()).toString());
                    browsingPanel.reloadFiles();
                    browsingPanel.selectionStart = -1;
                    browsingPanel.selectionEnd = -1;
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

        scrollPane = new JScrollPane(browsingPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    public void inactivate() {
        browsingPanel.inactivate();
    }

    public void reloadFiles() {
        browsingPanel.reloadFiles();
        browsingPanel.selectionStart = -1;
        browsingPanel.selectionEnd = -1;
    }
    
    public void addComponentToToolPanel(JComponent c) {
        toolPanel.add(c);
    }

    private class BrowsingPanel extends JPanel implements Scrollable {
        private final ImageIcon defaultImage;
        private final ImageIcon dirImage;
        private final JPopupMenu popupMenu;
        private static final int ITEM_SIZE = 148;
        private File[] children;
        private Image[] thumbnails;
        private int filesX;
        private int filesY;
        private int selectionStart = -1;
        private int selectionEnd = -1;

        public BrowsingPanel() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder());
            //setPreferredSize(new Dimension(0, 100));

            defaultImage = new ImageIcon(getClass().getClassLoader().getResource("images/defaultThumbnail.gif"));
            dirImage = new ImageIcon(getClass().getClassLoader().getResource("images/directoryThumbnail.gif"));

            reloadFiles();

            popupMenu = new JPopupMenu();

            addListeners();
        }

        @Override
        protected void paintComponent(Graphics g_) {
            Graphics2D g = (Graphics2D) g_;
            FontMetrics fontMetrics = g.getFontMetrics();
            int maxChars = (int) (((double) ITEM_SIZE) / (fontMetrics.getMaxCharBounds(g).getWidth() * 0.4d));
            Image defImg = defaultImage.getImage();
            Image dirImg = dirImage.getImage();

            g.setColor(Color.BLACK);
            Rectangle bounds = g.getClipBounds();

            int startRow = (int) Math.floor((float) bounds.getY() / (float) ITEM_SIZE);
            int stopRow = Math.min(filesY, (int) Math.ceil((float) (bounds.getY() + bounds.getHeight()) / (float) ITEM_SIZE));
            int fileNum = startRow * filesX;

            for (int iY = startRow; iY < stopRow; iY++) {
                for (int iX = 0; iX < filesX; iX++) {
                    int boxStartX = iX * ITEM_SIZE;
                    int boxStartY = iY * ITEM_SIZE;

                    g.setColor(Color.LIGHT_GRAY);
                    g.drawRect(boxStartX + 5, boxStartY + 5, ITEM_SIZE - 10, ITEM_SIZE - 10);

                    if (fileNum >= selectionStart && fileNum <= selectionEnd) {
                        g.setColor(Color.BLUE.brighter());
                        g.fillRect(boxStartX + 5, boxStartY + 5, ITEM_SIZE - 10, ITEM_SIZE - 10);
                    }

                    if(children[fileNum].isDirectory()) g.drawImage(dirImg, boxStartX + ITEM_SIZE / 2 - 64, boxStartY + ITEM_SIZE / 2 - 64, null);
                    else g.drawImage(defImg, boxStartX + ITEM_SIZE / 2 - 64, boxStartY + ITEM_SIZE / 2 - 64, null);
                    String name = children[fileNum].getName();
                    if (name.length() > maxChars - 1) {
                        name = name.substring(0, maxChars - 1) + "...";
                    }

                    int nameLen = (int) fontMetrics.stringWidth(name);
                    g.setColor(Color.BLACK);
                    g.drawString(name, boxStartX + ITEM_SIZE / 2 - nameLen / 2, boxStartY + ITEM_SIZE - 13);
                    fileNum++;
                }
            }
        }

        private void resized() {
            int numOfFiles = children.length;
            filesX = (int) Math.floor((float) getWidth() / (float) ITEM_SIZE);
            filesY = (int) Math.floor((float) numOfFiles / (float) filesX);
            int height = filesY * ITEM_SIZE;
            if (scrollPane != null) {
                this.setPreferredSize(new Dimension(scrollPane.getViewport().getWidth(), height));
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return this.getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ITEM_SIZE / 3;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ITEM_SIZE;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        private void addListeners() {
            this.addComponentListener(new ComponentListener() {
                @Override
                public void componentResized(ComponentEvent e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            resized();
                        }
                    });
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                }

                @Override
                public void componentShown(ComponentEvent e) {
                }

                @Override
                public void componentHidden(ComponentEvent e) {
                }
            });

            this.addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(final MouseEvent e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            int x = (int) Math.floor((float) e.getX() / (float) ITEM_SIZE);
                            int y = (int) Math.floor((float) e.getY() / (float) ITEM_SIZE);
                            int numOfFile = y * filesX + x;
                            File selected = children[numOfFile];
                            if (e.getButton() == MouseEvent.BUTTON1) {
                                if (e.getClickCount() == 2) {
                                    if (selected.isDirectory() && selected.canRead()) {
                                        cwd.set(selected);
                                        dirNameLabel.setText("/" + root.toPath().relativize(cwd.get().toPath()).toString());
                                        browsingPanel.selectionStart = -1;
                                        browsingPanel.selectionEnd = -1;
                                        reloadFiles();
                                    }
                                } else {
                                    if (e.getModifiersEx() == InputEvent.SHIFT_DOWN_MASK) {
                                        selectionEnd = numOfFile;
                                    } else {
                                        selectionStart = numOfFile;
                                        selectionEnd = numOfFile;
                                    }
                                    repaint();
                                }
                            } else if (e.getButton() == MouseEvent.BUTTON3) {
                                preparePopup(children[numOfFile]);
                                popupMenu.show(e.getComponent(), e.getX(), e.getY());
                            }
                        }
                    });
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

        private void preparePopup(File selected) {
            popupMenu.removeAll();
            JMenuItem openItem = new JMenuItem("Otwórz");
            popupMenu.add(openItem);
        }

        private void reloadFiles() {
            children = cwd.get().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return !(name.startsWith("."));
                }
            });

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    resized();
                    repaint();
                }
            });
        }

        private void inactivate() {
        }
    }

    /*private class FileComponent extends JComponent {
     private final File file;
        
     private FileComponent(File file) {
     this.file = file;
     this.setPreferredSize(new Dimension(200,200));
     }
        
     @Override
     protected void paintComponent(Graphics g) {
     g.setColor(Color.red);
     g.drawRect(2, 2, getWidth()-3, getHeight()-3);
     this.getVisibleRect();
     g.getClipBounds();
     }
     }*/
}

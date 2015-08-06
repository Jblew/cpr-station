/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components.browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.util.CollectionUtil;
import pl.jblew.cpr.util.ListenersManager;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public class PreloadingPhotoBrowser extends JPanel {
    private final JScrollPane scrollPane;
    private final ImagePanel imagePanel;
    private final PreloadableImage[] preloadables;
    private final AtomicInteger currentIndex;
    private final BlockingQueue<Integer> preloadingQueue = new LinkedBlockingQueue<>();
    private final ExecutorService loadingExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("preloading-photo-browser"));
    private final AtomicBoolean preloadingRunning = new AtomicBoolean(false);

    public PreloadingPhotoBrowser(PreloadableImage[] preloadables, int initialIndex, final ScaleType t) {
        this.preloadables = preloadables;
        currentIndex = new AtomicInteger(initialIndex);

        setLayout(new BorderLayout());

        imagePanel = new ImagePanel();
        

        scrollPane = new JScrollPane(imagePanel);
        add(scrollPane, BorderLayout.CENTER);

        changeImage(initialIndex);
        imagePanel.setScaleType(t);
    }

    public void setScaleType(final ScaleType t) {
        SwingUtilities.invokeLater(() -> {
            imagePanel.setScaleType(t);
        });
    }

    public void changeImage(File newFile) {
        int index = currentIndex.get();
        index = IntStream.range(0, preloadables.length).filter(i -> preloadables[i].imageFile.equals(newFile)).findAny().orElse(index);
        changeImage(index);
    }

    public void changeImage(int i) {
        synchronized (currentIndex) {
            int dir = Math.max(-1, Math.min(1, i - currentIndex.get()));//direction of change
            if (dir == 0) {
                dir = 1;
            }
            currentIndex.set(i);

            /**
             * PREPARE PRELOADING QUEUE *
             */
            preloadingQueue.clear();
            int[] indexesToPreloadOrdered = IntStream.of(i, i + 1 * dir, i - 1 * dir, i + 2 * dir, i + 3 * dir, i - 2 * dir, i + 4 * dir)
                    .filter(ci -> (ci >= 0 && ci < preloadables.length)).toArray();
            //6 3 [1] 2 4 5 7 - order of loading [1] is currently selected image

            //strongen already loaded images
            Arrays.stream(indexesToPreloadOrdered).mapToObj(ci -> preloadables[ci])
                    .forEachOrdered(preloadable -> preloadable.makeStrongIfWeakLoaded());

            //weaken other loaded images
            IntStream.range(0, preloadables.length).filter(ci -> !CollectionUtil.inArray(ci, indexesToPreloadOrdered))
                    .mapToObj(ci -> preloadables[ci]).forEachOrdered(preloadable -> preloadable.makeWeak());

            //preload not preloaded images
            Arrays.stream(indexesToPreloadOrdered)
                    .filter(ci -> preloadables[ci].getFullImage() == null).forEachOrdered(ci -> preloadingQueue.add(ci));

            if (!preloadingRunning.get()) {
                loadingExecutor.submit(() -> {
                    preloadingRunning.set(true);

                    while (true) {
                        try {
                            Integer nextNum = preloadingQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (nextNum == null) {
                                System.out.println("No more images to preload");
                                break;
                            } else {
                                preloadables[(int) nextNum].loadStrongFullImage();
                            }
                        } catch (InterruptedException ex) {
                            Logger.getLogger(PreloadingPhotoBrowser.class.getName()).log(Level.SEVERE, null, ex);
                            break;
                        }

                    }

                    preloadingRunning.set(false);
                });
            }
        }
        SwingUtilities.invokeLater(() -> {
            imagePanel.changeImage(preloadables[currentIndex.get()]);
        });

    }

    private class ImagePanel extends JPanel {
        private final AtomicInteger i = new AtomicInteger(0);
        private final AtomicReference<PreloadableImage> image = new AtomicReference<>(null);
        private final AtomicReference<ScaleType> scaleType = new AtomicReference<>(ScaleType.FIT);
        private final AffineTransform transform = new AffineTransform();
        private final ExecutorService loaderExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("PhotoBrowser-loader"));

        public ImagePanel() {

        }

        public void changeImage(PreloadableImage img) {
            image.set(img);
            if (img.getFullImage() == null) {
                img.addListener((loadedImg) -> {
                    updatePreferredSize();
                    revalidate();
                    repaint();
                });
            }
            SwingUtilities.invokeLater(() -> {
                updatePreferredSize();
                revalidate();
                repaint();
            });
        }

        @Override
        public void paintComponent(Graphics g_) {
            super.paintComponent(g_);
            Graphics2D g = (Graphics2D) g_;

            BufferedImage imgSafe = null;

            PreloadableImage preloadableSafe = image.get();
            if (preloadableSafe != null) {
                imgSafe = preloadableSafe.getFullImage();
            }

            if (imgSafe != null) {
                ScaleType scaleTypeSafe = scaleType.get();
                if (scaleTypeSafe == ScaleType.NATURAL) {
                    transform.setToIdentity();
                } else if (scaleTypeSafe == ScaleType.FIT) {
                    transform.setToIdentity();
                    float xRatio = (float) scrollPane.getViewport().getWidth() / (float) imgSafe.getWidth();
                    float yRatio = (float) scrollPane.getViewport().getHeight() / (float) imgSafe.getHeight();
                    float scaleRatio = Math.min(xRatio, yRatio);
                    transform.setToScale(scaleRatio, scaleRatio);
                } else if (scaleTypeSafe == ScaleType.FILL) {
                    transform.setToIdentity();
                    float xRatio = (float) scrollPane.getViewport().getWidth() / (float) imgSafe.getWidth();
                    float yRatio = (float) scrollPane.getViewport().getHeight() / (float) imgSafe.getHeight();
                    float scaleRatio = Math.max(xRatio, yRatio);
                    transform.setToScale(scaleRatio, scaleRatio);
                }

                g.drawImage(imgSafe, transform, null);
            } else {
                g.setColor(Color.RED);
                g.drawString("Image not loaded (" + i.getAndIncrement() + ")", getWidth() / 2, getHeight() / 2);
            }

            //g.setColor(Color.RED);
            //g.drawString("" + i.getAndIncrement(), getWidth() / 2, getHeight() / 2);
        }

        private void setScaleType(ScaleType t) {
            if (t == null) {
                throw new IllegalArgumentException("Scale type cannot be null");
            }

            scaleType.set(t);
            updatePreferredSize();
        }

        private void updatePreferredSize() {
            ScaleType t = scaleType.get();
            if (t == ScaleType.NATURAL) {
                BufferedImage imgSafe = null;

                PreloadableImage preloadableSafe = image.get();
                if (preloadableSafe != null) {
                    imgSafe = preloadableSafe.getFullImage();
                }
                if (imgSafe != null) {
                    setPreferredSize(new Dimension(imgSafe.getWidth(), imgSafe.getHeight()));
                }
            } else if (t == ScaleType.FIT) {
                System.out.println("scrollPane="+scrollPane);
                setPreferredSize(new Dimension(scrollPane.getViewport().getWidth(), scrollPane.getViewport().getHeight()));
            } else if (t == ScaleType.FILL) {
                setPreferredSize(new Dimension(scrollPane.getViewport().getWidth(), scrollPane.getViewport().getHeight()));
            }
            revalidate();
        }
    }

    public static class PreloadableImage {
        private final File imageFile;
        private final File thumbFile;
        private final AtomicReference<BufferedImage> thumbImage = new AtomicReference<>(null);
        private final AtomicReference<Object> fullImage = new AtomicReference<>(null);
        private final ListenersManager<PreloadableImage.ImageLoadedListener> listenersManager = new ListenersManager<>();

        public PreloadableImage(File imageFile, File thumbFile) {
            this.imageFile = imageFile;
            this.thumbFile = thumbFile;
        }

        /*public PreloadableImage(File imageFile, BufferedImage earlyThumbImage) {
         this.imageFile = imageFile;
         this.thumbFile = null;
         thumbImage.set(earlyThumbImage);
         }*/
        public BufferedImage getOrLoadThumbImage() {
            BufferedImage thumbImg = thumbImage.get();
            if (thumbImg != null) {
                return thumbImg;
            } else if (thumbFile != null && thumbFile.canRead()) {
                try {
                    thumbImg = ImageIO.read(thumbFile);
                    thumbImage.set(thumbImg);
                    return thumbImg;
                } catch (IOException ex) {
                    Logger.getLogger(PreloadingPhotoBrowser.class.getName()).log(Level.SEVERE, null, ex);
                    return null;
                }
            } else {
                return null;
            }
        }

        public BufferedImage getFullImage() {
            Object res = fullImage.get();
            if (res == null) {
                return null;
            } else if (res instanceof SoftReference) {
                SoftReference<BufferedImage> sRef = (SoftReference<BufferedImage>) res;
                return sRef.get();
            } else if (res instanceof BufferedImage) {
                return (BufferedImage) res;
            } else {
                return null;
            }
        }

        public BufferedImage loadStrongFullImage() {
            Object res = fullImage.get();
            boolean loadImage = false;
            if (res == null) {
                loadImage = true;
            } else if (res instanceof SoftReference) {
                SoftReference<BufferedImage> sRef = (SoftReference<BufferedImage>) res;
                BufferedImage img = sRef.get();
                if (img == null) {
                    loadImage = true;
                } else {
                    fullImage.set(img); //strongen reference to image
                    return img;
                }
            } else if (res instanceof BufferedImage) {
                return (BufferedImage) res; //already strong reference
            } else {
                return null;
            }

            if (loadImage && imageFile != null && imageFile.canRead()) {
                try {

                    BufferedImage fullImg = ImageIO.read(imageFile);
                    fullImage.set(fullImg);
                    listenersManager.callListeners((l) -> l.imageLoaded(fullImg));
                    System.out.println("Loaded image " + imageFile);
                    return fullImg;
                } catch (IOException ex) {
                    Logger.getLogger(PreloadingPhotoBrowser.class.getName()).log(Level.SEVERE, null, ex);
                    return null;
                }
            } else {
                return null;
            }
        }

        public void makeStrongIfWeakLoaded() {
            Object res = fullImage.get();
            if (res instanceof SoftReference) {
                SoftReference<BufferedImage> sRef = (SoftReference<BufferedImage>) res;
                BufferedImage img = sRef.get();
                if (img != null) {
                    fullImage.set(img); //strongen reference to image
                }
            }
        }

        public void makeWeak() {
            Object res = fullImage.get();
            if (res != null && res instanceof BufferedImage) {
                fullImage.set(new SoftReference<>((BufferedImage) res));
            }
        }

        public void addListener(ImageLoadedListener l) {
            listenersManager.addListener(l);
        }

        public void removeListener(ImageLoadedListener l) {
            listenersManager.removeListener(l);
        }

        public static interface ImageLoadedListener extends ListenersManager.Listener {
            public void imageLoaded(BufferedImage fullImg);
        }
    }

    public static enum ScaleType {
        FIT, FILL, NATURAL
    }
}

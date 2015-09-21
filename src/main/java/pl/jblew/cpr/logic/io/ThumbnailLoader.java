/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import com.google.common.collect.MapMaker;
import java.io.File;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import pl.jblew.cpr.Settings;
import pl.jblew.cpr.gui.components.browser.SwingFileBrowser;
import pl.jblew.cpr.util.TwoTuple;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 *
 * @author teofil
 */
public class ThumbnailLoader {
    private final boolean tryReadOrSave;
    private final int numOfProcessingThreads = 3;
    private final int maxSize;
    private final ExecutorService executor = Executors.newFixedThreadPool(numOfProcessingThreads);
    private final BlockingQueue<TwoTuple<File, LoadedListener>> loadingQueue = new LinkedBlockingQueue<>();
    private final ProcessingThread[] processingThreads = new ProcessingThread[numOfProcessingThreads];
    private static final Map<String, BufferedImage> weakCache = new MapMaker().weakValues().makeMap();

    public ThumbnailLoader(int maxSize) {
        this(maxSize, false);
    }

    public ThumbnailLoader(int maxSize, boolean trySave) {
        this.maxSize = maxSize;
        this.tryReadOrSave = trySave;
        for (int i = 0; i < numOfProcessingThreads; i++) {
            processingThreads[i] = new ProcessingThread();
        }
    }

    public void loadImage(File f, LoadedListener l) {
        loadingQueue.add(new TwoTuple<>(f, l));
        for (ProcessingThread pt : processingThreads) {
            if (!pt.running.get()) {
                pt.running.set(true);
                executor.submit(pt);
            }
        }
    }

    public void stopAndInactivate() {
        loadingQueue.clear();
        executor.shutdown();
    }

    public static interface LoadedListener {
        public void thumbnailLoaded(ImageIcon img);
    }

    private class ProcessingThread implements Runnable {
        public AtomicBoolean running = new AtomicBoolean(false);

        public ProcessingThread() {

        }

        @Override
        public void run() {
            running.set(true);

            try {
                TimeUnit.MILLISECONDS.sleep(250);
            } catch (InterruptedException ex) {
            }

            while (!loadingQueue.isEmpty()) {
                try {
                    TwoTuple<File, LoadedListener> loadingTuple = loadingQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (loadingTuple == null) {
                    } else {
                        File f = loadingTuple.getA();
                        loadThumbnail(f, tryReadOrSave, loadingTuple.getB());
                    }
                } catch (InterruptedException ex) {
                }
            }

            running.set(false);
        }
    }

    private static BufferedImage scaleImageLowQuality(BufferedImage loadedImage, int maxSize) {
        int newWidth;
        int newHeight;
        if (loadedImage.getWidth() > loadedImage.getHeight()) {
            newWidth = maxSize;
            newHeight = (int) Math.floor((float) maxSize * (float) loadedImage.getHeight() / (float) loadedImage.getWidth());
        } else {
            newHeight = maxSize;
            newWidth = (int) Math.floor((float) maxSize * (float) loadedImage.getWidth() / (float) loadedImage.getHeight());
        }

        BufferedImage buffer = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = buffer.createGraphics();
        g.drawImage(loadedImage, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return buffer;
    }

    private static BufferedImage scaleImageGoodQuality(BufferedImage img, int maxSize) {
        boolean progressiveBilinear = true;

        int targetWidth;
        int targetHeight;
        if (img.getWidth() > img.getHeight()) {
            targetWidth = maxSize;
            targetHeight = (int) Math.floor((float) maxSize * (float) img.getHeight() / (float) img.getWidth());
        } else {
            targetHeight = maxSize;
            targetWidth = (int) Math.floor((float) maxSize * (float) img.getWidth() / (float) img.getHeight());
        }

        int type = (img.getTransparency() == Transparency.OPAQUE)
                ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = img;
        BufferedImage scratchImage = null;
        Graphics2D g2 = null;
        int w, h;
        int prevW = ret.getWidth();
        int prevH = ret.getHeight();
        boolean isTranslucent = img.getTransparency() != Transparency.OPAQUE;
        if (progressiveBilinear) {
            // Use multi-step technique: start with original size, then
            // scale down in multiple passes with drawImage()
            // until the target size is reached
            w = img.getWidth();
            h = img.getHeight();
        } else {
            // Use one-step technique: scale directly from original
            // size to target size with a single drawImage() call
            w = targetWidth;
            h = targetHeight;
        }
        do {
            if (progressiveBilinear && w > targetWidth) {
                w /= 2;
                if (w < targetWidth) {
                    w = targetWidth;
                }
            }

            if (progressiveBilinear && h > targetHeight) {
                h /= 2;
                if (h < targetHeight) {
                    h = targetHeight;
                }
            }

            if (w < targetWidth) {
                w = targetWidth;
            }

            if (h < targetHeight) {
                h = targetHeight;
            }

            if (scratchImage == null || isTranslucent) {
                // Use a single scratch buffer for all iterations
                // and then copy to the final, correctly-sized image
                // before returning
                scratchImage = new BufferedImage(w, h, type);
                g2 = scratchImage.createGraphics();
            }
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(ret, 0, 0, w, h, 0, 0, prevW, prevH, null);
            prevW = w;
            prevH = h;

            ret = scratchImage;
        } while (w != targetWidth || h != targetHeight);

        if (g2 != null) {
            g2.dispose();
        }


        // If we used a scratch buffer that is larger than our target size,
        // create an image of the right size and copy the results into it
        if (targetWidth != ret.getWidth() || targetHeight != ret.getHeight()) {
            scratchImage = new BufferedImage(targetWidth, targetHeight, type);
            g2 = scratchImage.createGraphics();
            g2.drawImage(ret, 0, 0, null);
            g2.dispose();
            ret = scratchImage;
        }


        return ret;
    }

    public static void loadThumbnail(File f, boolean tryReadOrSave, LoadedListener listener) {
        if (canBeLoaded(f)) {
            try {
                String nameWithoutExtension = f.getName().substring(0, f.getName().lastIndexOf('.'));
                File parent = f.getParentFile();
                File possibleThumbDir = new File(parent.getAbsolutePath() + File.separator + ".thumb");
                File possibleThumbFile = new File(parent.getAbsolutePath() + File.separator + ".thumb"
                        + File.separator + nameWithoutExtension + ".jpg");

                boolean thumbnailLoaded = false;
                if (tryReadOrSave) {

                    if (possibleThumbFile.exists()) {
                        BufferedImage loadedImage = ImageIO.read(possibleThumbFile);
                        ImageIcon icon = new ImageIcon(loadedImage);
                        if (listener != null) {
                            listener.thumbnailLoaded(icon);
                        }
                        thumbnailLoaded = true;
                    }
                }

                if (!thumbnailLoaded) {
                    BufferedImage loadedImage = ImageIO.read(f);
                    BufferedImage scaled = null;
                    if (tryReadOrSave) {
                        scaled = scaleImageGoodQuality(loadedImage, Settings.THUMBNAIL_MAX_SIZE);
                    } else {
                        scaled = scaleImageLowQuality(loadedImage, Settings.THUMBNAIL_MAX_SIZE); //if we save thumbnail, we can do it in good quality
                    }
                    ImageIcon icon = new ImageIcon(scaled);
                    if (listener != null) {
                        listener.thumbnailLoaded(icon);
                    }
                    weakCache.put(f.getAbsolutePath(), scaled);
                    if (tryReadOrSave) {
                        if (!possibleThumbDir.exists()) {
                            possibleThumbDir.mkdirs();
                        }
                        if (possibleThumbDir.exists()) {
                            ImageIO.write(scaled, "jpg", possibleThumbFile);
                        }
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(SwingFileBrowser.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static boolean canBeLoaded(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".gif") || name.endsWith(".tif") || name.endsWith(".bmp");
    }

    public static BufferedImage seekImageInCache(File f) {
        return weakCache.get(f.getAbsolutePath());
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import com.google.common.collect.MapMaker;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import pl.jblew.cpr.Settings;
import pl.jblew.cpr.gui.components.browser.SwingFileBrowser;
import pl.jblew.cpr.util.TwoTuple;

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
    private final AtomicInteger numOfRunningThreads = new AtomicInteger(0);
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

    public void stopAll() {
        loadingQueue.clear();
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

    private static BufferedImage scaleImage(BufferedImage loadedImage, int maxSize) {
        int newWidth;
        int newHeight;
        if (loadedImage.getWidth() > loadedImage.getHeight()) {
            newWidth = maxSize;
            newHeight = (int) Math.floor((float)maxSize * (float) loadedImage.getHeight() / (float) loadedImage.getWidth());
        } else {
            newHeight = maxSize;
            newWidth = (int) Math.floor((float)maxSize * (float) loadedImage.getWidth() / (float) loadedImage.getHeight());
        }

        BufferedImage buffer = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = buffer.createGraphics();
        //g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.drawImage(loadedImage, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return buffer;
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
                    BufferedImage scaled = scaleImage(loadedImage, Settings.THUMBNAIL_MAX_SIZE);
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

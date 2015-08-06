/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.*;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public class PhotoBrowser extends JPanel {
    private final JScrollPane scrollPane;
    private final ImagePanel imagePanel;
    private final AtomicReference<File> imageUrl = new AtomicReference<>();
    private final PhotoBrowser me = this;

    public PhotoBrowser(File f, final ScaleType t) {
        imageUrl.set(f);

        setLayout(new BorderLayout());

        imagePanel = new ImagePanel();
        SwingUtilities.invokeLater(() -> {
            imagePanel.setScaleType(t);
            imagePanel.imageChanged();
        });

        scrollPane = new JScrollPane(imagePanel);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setScaleType(final ScaleType t) {
        SwingUtilities.invokeLater(() -> {
            imagePanel.setScaleType(t);
        });

    }

    /*public static class Builder {
     private Builder() {}
        
     public PhotoBrowser build() {
            
     }
     }*/
    private class ImagePanel extends JPanel {
        private final AtomicInteger i = new AtomicInteger(0);
        private final AtomicReference<BufferedImage> image = new AtomicReference<>();
        private final AtomicReference<ScaleType> scaleType = new AtomicReference<>(ScaleType.FIT);
        private final AffineTransform transform = new AffineTransform();
        private final ExecutorService loaderExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("PhotoBrowser-loader"));

        public ImagePanel() {

        }

        public void imageChanged() {
            loaderExecutor.submit(() -> {
                BufferedImage newImage = null;
                try {
                    newImage = ImageIO.read(imageUrl.get());
                } catch (IOException ex) {
                    Logger.getLogger(PhotoBrowser.class.getName()).log(Level.SEVERE, null, ex);
                }
                image.set(newImage);
                SwingUtilities.invokeLater(() -> {
                    updatePreferredSize();
                    revalidate();
                    repaint();
                });
            });

            /*if (newImage == null) {
             setPreferredSize(new Dimension(parentPhotoBrowser.getWidth(), parentPhotoBrowser.getHeight()));
             } else {
             setPreferredSize(new Dimension(newImage.getWidth(), newImage.getHeight()));
             }*/
        }

        @Override
        public void paintComponent(Graphics g_) {
            super.paintComponent(g_);
            Graphics2D g = (Graphics2D) g_;

            BufferedImage imgSafe = image.get();
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
                BufferedImage imgSafe = image.get();
                if (imgSafe != null) {
                    setPreferredSize(new Dimension(imgSafe.getWidth(), imgSafe.getHeight()));
                }
            } else if (t == ScaleType.FIT) {
                setPreferredSize(new Dimension(scrollPane.getViewport().getWidth(), scrollPane.getViewport().getHeight()));
            } else if (t == ScaleType.FILL) {
                setPreferredSize(new Dimension(scrollPane.getViewport().getWidth(), scrollPane.getViewport().getHeight()));
            }
            revalidate();
        }
    }

    public static enum ScaleType {
        FIT, FILL, NATURAL
    }
}

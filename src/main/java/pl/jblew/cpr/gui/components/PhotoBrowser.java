/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.*;
import pl.jblew.cpr.util.MessageToStatusBar;

/**
 *
 * @author teofil
 */
public class PhotoBrowser extends JPanel {
    private final JScrollPane scrollPane;
    private final ImagePanel imagePanel;
    private final AtomicReference<URL> imageUrl = new AtomicReference<>();
    private final PhotoBrowser parentPhotoBrowser = this;

    public PhotoBrowser(URL imageUrl_) {
        imageUrl.set(imageUrl_);

        setLayout(new BorderLayout());

        imagePanel = new ImagePanel();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                imagePanel.imageChanged();
            }
        });

        scrollPane = new JScrollPane(imagePanel);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setScaleType(final ScaleType t) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                imagePanel.setScaleType(t);
            }
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

        public ImagePanel() {

        }

        public void imageChanged() {
            BufferedImage newImage = null;
            try {
                newImage = ImageIO.read(imageUrl.get());
            } catch (IOException ex) {
                Logger.getLogger(PhotoBrowser.class.getName()).log(Level.SEVERE, null, ex);
            }
            image.set(newImage);
            /*if (newImage == null) {
                setPreferredSize(new Dimension(parentPhotoBrowser.getWidth(), parentPhotoBrowser.getHeight()));
            } else {
                setPreferredSize(new Dimension(newImage.getWidth(), newImage.getHeight()));
            }*/
            updatePreferredSize();
            
            System.out.println("image change finished, calling repaint");

            repaint();
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
                    float xRatio = parentPhotoBrowser.getWidth() / imgSafe.getWidth();
                    float yRatio = parentPhotoBrowser.getHeight() / imgSafe.getHeight();
                    float scaleRatio = Math.min(xRatio, yRatio);
                    transform.setToScale(scaleRatio, scaleRatio);
                } else if (scaleTypeSafe == ScaleType.FILL) {
                    transform.setToIdentity();
                    float xRatio = (float) parentPhotoBrowser.getWidth() / (float) imgSafe.getWidth();
                    float yRatio = (float) parentPhotoBrowser.getHeight() / (float) imgSafe.getHeight();
                    float scaleRatio = Math.max(xRatio, yRatio);
                    transform.setToScale(scaleRatio, scaleRatio);
                    //System.out.println("scaleRatio="+scaleRatio+"; xRatio="+xRatio+"; yRatio="+yRatio
                    //        +"; parentPhotoBrowser.getWidth()="+parentPhotoBrowser.getWidth()
                    //        +"; imgSafe.getWidth()="+imgSafe.getWidth());
                }

                g.drawImage(imgSafe, transform, null);
            }
            else {
                g.setColor(Color.RED);
                g.drawString("Image not loaded (" + i.getAndIncrement()+")", getWidth() / 2, getHeight() / 2);
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
                setPreferredSize(new Dimension(parentPhotoBrowser.getWidth(), parentPhotoBrowser.getHeight()));
            } else if (t == ScaleType.FILL) {
                setPreferredSize(new Dimension(parentPhotoBrowser.getWidth(), parentPhotoBrowser.getHeight()));
            }
        }
    }

    public static enum ScaleType {
        FIT, FILL, NATURAL
    }
}

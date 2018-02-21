/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.Color;

/**
 *
 * @author http://www.philreeve.com/java_high_quality_thumbnails.php
 */
public class ThumbnailGenerator {
    /*
     * Reads an image in a file and creates a thumbnail in another file.
     * largestDimension is the largest dimension of the thumbnail, the other
     * dimension is scaled accordingly. Utilises weighted stepping method to
     * gradually reduce the image size for better results, i.e. larger steps to
     * start with then smaller steps to finish with. Note: always writes a JPEG
     * because GIF is protected or something - so always make your outFilename
     * end in 'jpg'. PNG's with transparency are given white backgrounds
     */
    public static BufferedImage createThumbnail(BufferedImage inImage, int largestDimension) {
        double scale;
        int sizeDifference, originalImageLargestDim;
        //find biggest dimension
        if (inImage.getWidth(null) > inImage.getHeight(null)) {
            scale = (double) largestDimension / (double) inImage.getWidth(null);
            sizeDifference = inImage.getWidth(null) - largestDimension;
            originalImageLargestDim = inImage.getWidth(null);
        } else {
            scale = (double) largestDimension / (double) inImage.getHeight(null);
            sizeDifference = inImage.getHeight(null) - largestDimension;
            originalImageLargestDim = inImage.getHeight(null);
        }
        //create an image buffer to draw to
        BufferedImage outImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB); //arbitrary init so code compiles
        Graphics2D g2d;
        AffineTransform tx;
        if (scale < 1.0d) //only scale if desired size is smaller than original
        {
            int numSteps = sizeDifference / 100;
            int stepSize = sizeDifference / numSteps;
            int stepWeight = stepSize / 2;
            int heavierStepSize = stepSize + stepWeight;
            int lighterStepSize = stepSize - stepWeight;
            int currentStepSize, centerStep;
            double scaledW = inImage.getWidth(null);
            double scaledH = inImage.getHeight(null);
            if (numSteps % 2 == 1) //if there's an odd number of steps
            {
                centerStep = (int) Math.ceil((double) numSteps / 2d); //find the center step
            } else {
                centerStep = -1; //set it to -1 so it's ignored later
            }
            Integer intermediateSize = originalImageLargestDim, previousIntermediateSize = originalImageLargestDim;
            Integer calculatedDim;
            for (Integer i = 0; i < numSteps; i++) {
                if (i + 1 != centerStep) //if this isn't the center step
                {
                    if (i == numSteps - 1) //if this is the last step
                    {
                        //fix the stepsize to account for decimal place errors previously
                        currentStepSize = previousIntermediateSize - largestDimension;
                    } else {
                        if (numSteps - i > numSteps / 2) //if we're in the first half of the reductions
                        {
                            currentStepSize = heavierStepSize;
                        } else {
                            currentStepSize = lighterStepSize;
                        }
                    }
                } else //center step, use natural step size
                {
                    currentStepSize = stepSize;
                }
                intermediateSize = previousIntermediateSize - currentStepSize;
                scale = (double) intermediateSize / (double) previousIntermediateSize;
                scaledW = (int) scaledW * scale;
                scaledH = (int) scaledH * scale;
                outImage = new BufferedImage((int) scaledW, (int) scaledH, BufferedImage.TYPE_INT_RGB);
                g2d = outImage.createGraphics();
                g2d.setBackground(Color.WHITE);
                g2d.clearRect(0, 0, outImage.getWidth(), outImage.getHeight());
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                tx = new AffineTransform();
                tx.scale(scale, scale);
                g2d.drawImage(inImage, tx, null);
                g2d.dispose();
                //inImage = new ImageIcon(outImage).getImage();
                previousIntermediateSize = intermediateSize;
            }
        } else {
            //just copy the original
            outImage = new BufferedImage(inImage.getWidth(null), inImage.getHeight(null), BufferedImage.TYPE_INT_RGB);
            g2d = outImage.createGraphics();
            g2d.setBackground(Color.WHITE);
            g2d.clearRect(0, 0, outImage.getWidth(), outImage.getHeight());
            tx = new AffineTransform();
            tx.setToIdentity(); //use identity matrix so image is copied exactly
            g2d.drawImage(inImage, tx, null);
            g2d.dispose();
        }

        return outImage;
    }
}

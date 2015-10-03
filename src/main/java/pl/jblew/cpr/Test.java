/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import pl.jblew.cpr.logic.io.MD5Util;

/**
 *
 * @author teofil
 */
public class Test {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        File f1 = new File("/Users/teofil/cmp/EPSN0135.JPG");
        File f2 = new File("/Users/teofil/cmp/2006.07.06-10.54.40.jpg");

        try {
            FileInputStream is1 = new FileInputStream(f1);
            FileInputStream is2 = new FileInputStream(f2);

            int numOfDiffs = 0;
            int pos = 0;
            while (true) {
                int r1 = is1.read();
                int r2 = is2.read();

                if (r1 != r2) {
                    System.out.println(pos + ": " + r1 + " | " + r2);
                    numOfDiffs++;
                }

                if (r1 == -1 && r2 == -1) {
                    break;
                } else if (r1 == -1) {
                    System.out.println("F1 end is before F2 end");
                    numOfDiffs++;
                    break;
                } else if (r2 == -1) {
                    System.out.println("F2 end is before F1 end");
                    numOfDiffs++;
                    break;
                }
                pos++;
            }

            System.out.println("Num of diffs: " + numOfDiffs);

            is1.close();
            is2.close();

        } catch (IOException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }

        System.out.println("----IMAGE TRY----");

        try {
            BufferedImage img1 = ImageIO.read(f1);
            BufferedImage img2 = ImageIO.read(f2);

            if (img1.getWidth() == img2.getWidth() && img1.getHeight() == img2.getHeight()) {
                int numOfDiffs = 0;
                for (int y = 0; y < img1.getHeight(); y++) {
                    for (int x = 0; x < img1.getWidth(); x++) {
                        int rgb1 = img1.getRGB(x, y);
                        int rgb2 = img2.getRGB(x, y);

                        if (rgb1 != rgb2) {
                            System.out.println("(" + x + "," + y + "): " + rgb1 + " | " + rgb2);
                            numOfDiffs++;
                        }
                    }
                }
                System.out.println("Num of diffs: " + numOfDiffs);
            } else {
                System.out.println("Different image sizez!");
            }
        } catch (IOException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        System.out.println("----MD5 TRY----");
        String md5_1 = MD5Util.calculateMD5(f1);
        String md5_2 = MD5Util.calculateMD5(f2);
        
        if(md5_1.equals(md5_2)) {
            System.out.println("MD5's are equal: "+md5_1);
        }
        else {
            System.out.println(md5_1+" | "+md5_2);
        }
    }

}
//MD5: 09527C8E852EDA43EAB142B845830EBA

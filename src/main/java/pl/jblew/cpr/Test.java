/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author teofil
 */
public class Test {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        File f = new File("/Users/teofil/Desktop/a.png");
        long unixDatetime = f.lastModified();
        System.out.println("Unix: "+unixDatetime);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
        System.out.println("Old Date: "+sdf.format(new Date(unixDatetime)));
        System.out.println("New LocalDateTime: "+(LocalDateTime.ofEpochSecond(unixDatetime/1000l, 0, ZoneOffset.UTC).getYear()));
        
        
        File dir = new File("/Users/teofil/numbered");
        dir.mkdirs();
        
        for(int i = 0;i < 100;i++) {
            BufferedImage img = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1024, 768);
            
            
            
            g.setColor(Color.LIGHT_GRAY);
            
            for(int x = 0;x < 1024;x += 15) {
                for(int y = x/9;y < 768;y += 10) {
                    g.drawString(i+"", x, y);
                }
            }
            
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 150));
            g.drawString(""+i, 512, 768/2);
            
            g.dispose();
            
            try {
                ImageIO.write(img, "jpeg", new File(dir+File.separator+String.format("%1$" + 5 + "s", i + "").replace(' ', '0')+".jpg"));
            } catch (IOException ex) {
                Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println(i+"/100");
        }
    }

}

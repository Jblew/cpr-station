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
import java.util.stream.IntStream;
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
        
        final int WIDTH = 128;
        final int HEIGHT = 96;
        final int COUNT = 100*1000;
        
        IntStream.range(20*1000, 50*1000).parallel().forEach((i) -> {
            BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1024, 768);
            
            
            
            g.setColor(Color.LIGHT_GRAY);
            
            for(int x = 0;x < WIDTH;x += 50) {
                for(int y = x/9;y < HEIGHT;y += 10) {
                    g.drawString(i+"", x, y);
                }
            }
            
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 45));
            g.drawString(""+i, WIDTH/2, HEIGHT/2);
            
            g.dispose();
            
            try {
                ImageIO.write(img, "jpeg", new File(dir+File.separator+String.format("%1$" + (int)Math.log10(COUNT) + "s", i + "").replace(' ', '0')+".jpg"));
            } catch (IOException ex) {
                Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
            }
            if(i%10 == 0) System.out.println(i+"/"+COUNT);
        });
    }

}

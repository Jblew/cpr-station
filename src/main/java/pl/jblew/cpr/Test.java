/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr;

import com.drew.imaging.ImageProcessingException;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.util.ImageCreationDateLoader;

/**
 *
 * @author teofil
 */
public class Test {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        
        String [] imgUrls = new String [] {
            "/Users/teofil/Downloads/case13b.jpg",
            "/Users/teofil/Downloads/198.jpg",
            "/Users/teofil/Downloads/IMG_20150904_133816.jpg",
            "/Users/teofil/Downloads/IMG_20150903_132723.jpg",
            "/Users/teofil/Pictures/DSC05317.JPG"
        };
        
        for(String imgUrl : imgUrls) {
            File imageFile = new File(imgUrl);
            
            try {
                LocalDateTime dt = ImageCreationDateLoader.getCreationDateTime(imageFile);
                System.out.println(imgUrl+": "+dt);
                
                
            } catch (ImageProcessingException | IOException ex) {
                Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}

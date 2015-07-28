/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr;

import java.io.File;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

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
    }

}

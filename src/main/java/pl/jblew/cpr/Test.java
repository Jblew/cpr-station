/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 *
 * @author teofil
 */
public class Test {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        ArrayList<Long> list = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            list.add(System.currentTimeMillis());
            Thread.sleep(100);
        }

        Collections.sort(list, (Long o1, Long o2) -> {
            long lm1 = (long) o1;
            long lm2 = (long) o2;
            return (lm1 == lm2 ? 0 : (lm1 > lm2 ? 1 : 0));
        });
        
        for(Long l : list) {
            System.out.println(l+"");
        }
    }

}

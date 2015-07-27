/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

import java.text.DecimalFormat;
import javax.swing.SwingUtilities;

/**
 *
 * @author teofil
 */
public class FileSizeFormatter {
    private static final DecimalFormat df = new DecimalFormat("#.0");

    private FileSizeFormatter() {
    }

    public static String format(long filesize) {
        long sizeB = filesize;
        if (sizeB < 1024l) {
            return sizeB + "B";
        } else {
            long sizeKB = sizeB / 1024l;
            if (sizeKB < 1024l) {
                return sizeKB + "KB";
            } else {
                int sizeMB = (int) (sizeKB / 1024l);
                if (sizeMB < 1024) {
                    return sizeMB + "MB";
                } else {
                    float sizeGB = (float) sizeMB / 1024f;
                    if (sizeGB < 1024f) {
                        return df.format(sizeGB) + "GB";
                    } else {
                        float sizeTB = sizeGB / 1024f;
                        return df.format(sizeTB) + "TB";
                    }
                }
            }
        }
    }
}
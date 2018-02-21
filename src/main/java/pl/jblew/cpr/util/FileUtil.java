/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author teofil
 */
public final class FileUtil {
    private FileUtil() {
    }

    public static void deleteRecursively(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteRecursively(c);
            }
        }
        if (!f.delete()) {
            //throw new FileNotFoundException("Failed to delete file: " + f);
        }
    }
}

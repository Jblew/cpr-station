/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.file;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author teofil
 */
public class FileStructureUtil {
    public static final String [] REQUIRED_DIRS = new String [] {
        "POSEGREGOWANE",
        "POSEGREGOWANE"+File.separator+"Zdjęcia",
        "POSEGREGOWANE"+File.separator+"Zdjęcia"+File.separator+".thumb",
        "POSEGREGOWANE"+File.separator+"Filmy",
        "POSEGREGOWANE"+File.separator+"Filmy"+File.separator+".thumb",
        "NIEPOSEGREGOWANE",
        "NIEPOSEGREGOWANE"+File.separator+"Zdjęcia",
        "NIEPOSEGREGOWANE"+File.separator+"Zdjęcia"+File.separator+".thumb",
        "NIEPOSEGREGOWANE"+File.separator+"Filmy",
        "NIEPOSEGREGOWANE"+File.separator+"Filmy"+File.separator+".thumb"
    };

    public static boolean checkFileStructure(File root) {
        boolean allExist = true;
        
        for(String dir : REQUIRED_DIRS) {
            if(!(new File(root+File.separator+dir).exists())) {
                allExist = false;
                break;
            }
        }
         
        return allExist;
    }

    public static void createFileStructure(File root) {
        for(String dir : REQUIRED_DIRS) {
            File requiredFile = new File(root+File.separator+dir);
            if(!requiredFile.exists()) {
                requiredFile.mkdirs();
            }
        }
    }
    
}

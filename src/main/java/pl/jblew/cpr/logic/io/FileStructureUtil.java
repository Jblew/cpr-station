/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import java.io.File;

/**
 *
 * @author teofil
 */
public class FileStructureUtil {
    public static final String PATH_SORTED = "POSEGREGOWANE";
    public static final String PATH_SORTED_PHOTOS = PATH_SORTED + File.separator + "Zdjęcia";
    public static final String PATH_SORTED_PHOTOS_THUMBNAILS = PATH_SORTED_PHOTOS + File.separator + ".thumb";
    public static final String PATH_SORTED_VIDEOS = PATH_SORTED + File.separator + "Filmy";
    public static final String PATH_SORTED_VIDEOS_THUMBNAILS = PATH_SORTED_VIDEOS + File.separator + ".thumb";

    public static final String PATH_UNSORTED = "NIEPOSEGREGOWANE";
    public static final String PATH_UNSORTED_PHOTOS = PATH_UNSORTED + File.separator + "Zdjęcia";
    public static final String PATH_UNSORTED_PHOTOS_THUMBNAILS = PATH_UNSORTED_PHOTOS + File.separator + ".thumb";
    public static final String PATH_UNSORTED_VIDEOS = PATH_UNSORTED + File.separator + "Filmy";
    public static final String PATH_UNSORTED_VIDEOS_THUMBNAILS = PATH_UNSORTED_VIDEOS + File.separator + ".thumb";
    public static final String PATH_UNSORTED_AUTOIMPORT = PATH_UNSORTED + File.separator + "Automatyczny import";
    
    public static final String[] REQUIRED_DIRS = new String[]{
        PATH_SORTED,
        PATH_SORTED_PHOTOS,
        PATH_SORTED_PHOTOS_THUMBNAILS,
        PATH_SORTED_VIDEOS,
        PATH_SORTED_VIDEOS_THUMBNAILS,
        PATH_UNSORTED,
        PATH_UNSORTED_PHOTOS,
        PATH_UNSORTED_PHOTOS_THUMBNAILS,
        PATH_UNSORTED_VIDEOS,
        PATH_UNSORTED_VIDEOS_THUMBNAILS,
        PATH_UNSORTED_AUTOIMPORT
    };

    public static boolean checkFileStructure(File root) {
        boolean allExist = true;

        for (String dir : REQUIRED_DIRS) {
            if (!(new File(root + File.separator + dir).exists())) {
                allExist = false;
                break;
            }
        }

        return allExist;
    }

    public static void createFileStructure(File root) {
        for (String dir : REQUIRED_DIRS) {
            File requiredFile = new File(root + File.separator + dir);
            if (!requiredFile.exists()) {
                requiredFile.mkdirs();
            }
        }
    }

}

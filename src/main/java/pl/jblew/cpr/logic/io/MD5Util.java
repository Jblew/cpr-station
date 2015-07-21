/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author teofil
 */
public class MD5Util {
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private MD5Util() {}
    
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String calculateMD5(File f) {
        try {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(f.toPath())) {
                DigestInputStream dis = new DigestInputStream(is, md5Digest);
                int numBytes;
                byte [] bytes = new byte[2048];
		while ((numBytes = dis.read(bytes)) != -1) {
			
		}
            } catch (IOException ex) {
                Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
                return "";
            }
            byte[] digest = md5Digest.digest();
            String out =  bytesToHex(digest);
            //System.out.println("MD5 for "+f.getAbsolutePath()+" = "+out);
            return out;
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }
    }
}

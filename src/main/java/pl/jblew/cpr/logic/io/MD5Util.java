/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
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

    private MD5Util() {
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /*public static String calculateMD5(File f) {
     try {
     MessageDigest md5Digest = MessageDigest.getInstance("MD5");
     try (InputStream is = Files.newInputStream(f.toPath())) {
     DigestInputStream dis = new DigestInputStream(is, md5Digest);
     int numBytes;
     byte [] bytes = new byte[1024*10];
     dis.read(bytes);
                                
     dis.close();
     is.close();
     } catch (IOException ex) {
     Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
     return "";
     }
     byte[] digest = md5Digest.digest();
     String out =  bytesToHex(digest);
     return out;
     } catch (NoSuchAlgorithmException ex) {
     Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
     return "";
     }
     }*/
    public static String calculateMD5(File f) {
        try {
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            byte[] buf = new byte[1024 * 10];

            raf.readFully(buf, 0, buf.length / 2);
            raf.seek(raf.length() - buf.length / 2 - 1);
            raf.readFully(buf, buf.length / 2, buf.length / 2);
            raf.close();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                    DigestInputStream dis = new DigestInputStream(bais, md5Digest)) {
                byte[] bytes = new byte[buf.length];
                dis.read(bytes);
            }

            byte[] digest = md5Digest.digest();
            String out = bytesToHex(digest);
            return out;
        } catch (NoSuchAlgorithmException | IOException ex) {
            Logger.getLogger(Importer.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }
    }
}

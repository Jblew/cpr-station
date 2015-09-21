/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import java.util.HashMap;
import javax.swing.ImageIcon;

/**
 *
 * @author teofil
 */
public enum IconLoader {
    OK_16("ok16.png"),
    ERROR_16("error16.png"),
    EDIT_16("edit16.png"),
    LOGO_256("cpr-logo.256.png"),
    SEARCH_16("search16.png");
    
    
    private static final HashMap<IconLoader, ImageIcon> loaded = new HashMap<>();
    private final String filename;
    private IconLoader(String filename) {
        this.filename = filename;
    }
    
    public ImageIcon load() {
        if(loaded.containsKey(this)) return loaded.get(this);
        else {
            ImageIcon out = new ImageIcon(IconLoader.class.getClassLoader().getResource("images/"+filename));
            loaded.put(this, out);
            return out;
        }
    }
}

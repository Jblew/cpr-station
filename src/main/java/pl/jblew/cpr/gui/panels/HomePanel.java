/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.components.browser.PhotoBrowser;

/**
 *
 * @author teofil
 */
public class HomePanel extends MainPanel {
    public HomePanel() {
        setLayout(new BorderLayout());
        
        //PhotoBrowser photoBrowser = new PhotoBrowser(HomePanel.class.getClassLoader().getResource("images/welcome.jpg"));
        //photoBrowser.setScaleType(PhotoBrowser.ScaleType.FILL);
        //add(photoBrowser, BorderLayout.CENTER);
    }
    
    @Override
    public void activate() {

    }

    @Override
    public void inactivate() {

    }
    
}

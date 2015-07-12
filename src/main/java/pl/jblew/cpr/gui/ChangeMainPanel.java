/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import javax.swing.JPanel;

/**
 *
 * @author teofil
 */
public class ChangeMainPanel {
    
    private final MainPanel mainPanel;

    public ChangeMainPanel(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
    }    
    
    public MainPanel getMainPanel() {
        return mainPanel;
    }

}

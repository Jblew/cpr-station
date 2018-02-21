/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui;

import pl.jblew.cpr.util.PrintableBusMessage;

/**
 *
 * @author teofil
 */
public class ChangeMainPanel implements PrintableBusMessage {
    
    private final MainPanel mainPanel;

    public ChangeMainPanel(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
    }    
    
    public MainPanel getMainPanel() {
        return mainPanel;
    }

    @Override
    public String toString() {
        return "ChangeMainPanel{" + "mainPanel=" + mainPanel.getClass().getName() + '}';
    }
}

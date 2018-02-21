/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.awt.Color;
import javax.swing.JLabel;

/**
 *
 * @author teofil
 */
public class ErrorLabel extends JLabel {
    public ErrorLabel(String text) {
        super(text);
        
        this.setForeground(Color.RED);
    }
}

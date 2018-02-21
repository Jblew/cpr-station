/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 *
 * @author teofil
 */
public class BlinkButton extends JButton implements Blinker.Blinkable  {
    public BlinkButton() {
        Blinker.addBlinkable(this);
    }

    @Override
    public void blink() {
        SwingUtilities.invokeLater(() -> {
            if(this.isShowing()) {
                 
            }
        });
    }
}

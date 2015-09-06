/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.util;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicProgressBarUI;

/**
 *
 * @author teofil
 */
public class CPRProgressBarUI extends BasicProgressBarUI {
    private static final Color COLOR = Color.decode("#3465AA");
    
    @Override
    protected void paintDeterminate(Graphics g_, JComponent c) {
        Graphics g = (Graphics2D) g_;
        
        int amountFull = super.getAmountFull(c.getInsets(), c.getWidth()-4, c.getHeight());
        
        g.setColor(c.isEnabled()? (c.getForeground() == Color.RED? Color.RED : COLOR) : Color.LIGHT_GRAY);
        g.drawRect(0, 0, c.getWidth()-1, c.getHeight()-1);
        
        g.fillRect(2, 2, amountFull, c.getHeight()-4);
        
        g.setColor(c.isEnabled()? Color.BLACK : Color.LIGHT_GRAY);
        super.paintString(g, 0,0, c.getWidth(), c.getHeight(), amountFull, c.getInsets());
    }
    
}

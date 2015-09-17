/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.util;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.Timer;

/**
 *
 * @author teofil
 */
public abstract class DoubleClickMouseListener implements MouseListener {
    private boolean leftClick;
    private int clickCount;
    private boolean doubleClick;

    @Override
    public void mouseClicked(MouseEvent evt) {
        if (evt.getButton() == MouseEvent.BUTTON1) {
            leftClick = true;
            clickCount = 0;
            if (evt.getClickCount() == 2) {
                doubleClick = true;
            }
            Integer timerinterval = (Integer) Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");

            if(timerinterval == null) timerinterval = 600;
            
            Timer timer = new Timer(timerinterval, (ActionEvent evt1) -> {
                if (doubleClick) {
                    clickCount++;
                    if (clickCount == 2) {
                        doubleLeftClick(evt);
                        clickCount = 0;
                        doubleClick = false;
                        leftClick = false;
                    }
                    
                } else if (leftClick) {
                    singleLeftClick(evt);
                    leftClick = false;
                }
            });
            timer.setRepeats(false);
            timer.start();
            if (evt.getID() == MouseEvent.MOUSE_RELEASED) {
                timer.stop();
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    public abstract void singleLeftClick(MouseEvent e);
    public abstract void doubleLeftClick(MouseEvent e);
}

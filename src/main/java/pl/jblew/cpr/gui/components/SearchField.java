/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JTextField;
import pl.jblew.cpr.gui.IconLoader;

/**
 *
 * @author teofil
 */
public class SearchField extends JTextField {
    private String prevString = "";

    public SearchField(SearchField.Callback callback) {
        /*setForeground(Color.LIGHT_GRAY);
        setText("Szukaj...");
        prevString = "Szukaj";
*/
        Callback internalCallback = (s) -> {
            /*if (s.isEmpty()) {
                setText("Szukaj...");
                setForeground(Color.LIGHT_GRAY);
            } else if (s.equals("Szukaj...")) {
                setText("");
                setForeground(Color.BLACK);
            } else {
                setForeground(Color.BLACK);
            }*/
        };

        this.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                /*Logger.getLogger(getClass().getName()).info("kTyped="+getText());
                if (!getText().equals(prevString)) {
                    //internalCallback.callback(getText());
                    callback.callback(getText());
                    prevString = getText();
                }*/
            }

            @Override
            public void keyPressed(KeyEvent e) {
                //Logger.getLogger(getClass().getName()).info("kPressed="+getText());
                if (!getText().equals(prevString)) {
                    internalCallback.callback(getText());
                    callback.callback(getText());
                    prevString = getText();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                //Logger.getLogger(getClass().getName()).info("kReleased="+getText());
                if (!getText().equals(prevString)) {
                    internalCallback.callback(getText());
                    callback.callback(getText());
                    prevString = getText();
                }
            }

        });

        /*this.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (getText().equals("Szukaj...")) {
                    setText("");
                    setForeground(Color.BLACK);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (getText().equals("Szukaj...")) {
                    setText("");
                    setForeground(Color.BLACK);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (getText().equals("Szukaj...")) {
                    setText("");
                    setForeground(Color.BLACK);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });

        this.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (getText().equals("Szukaj...")) {
                    setText("");
                    setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (getText().isEmpty()) {
                    setText("Szukaj...");
                    setForeground(Color.LIGHT_GRAY);
                }
            }
        });*/
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int y = getHeight()/2-16/2;
        
        g.drawImage(IconLoader.SEARCH_16.load().getImage(), getWidth()-16-y, y, null);
    }
    
    

    public static interface Callback {
        public void callback(String searchText);
    }
}

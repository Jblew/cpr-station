/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.util;

import java.awt.BorderLayout;
import javax.swing.JPanel;

/**
 *
 * @author teofil
 */
public class SpacerPanel extends JPanel {
    public SpacerPanel() {
        setLayout(new BorderLayout());
        add(new JPanel(), BorderLayout.CENTER);
    }
}

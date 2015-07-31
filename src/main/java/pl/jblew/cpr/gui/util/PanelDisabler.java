/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.util;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 *
 * @author teofil
 */
public class PanelDisabler {
    private PanelDisabler() {
    }

    public static void setEnabled(Container panel, boolean enabled) {
        for (Component c : panel.getComponents()) {
            if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                jc.setEnabled(enabled);
                setEnabled(jc, enabled);
            }
        }
    }
}

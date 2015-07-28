/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.util;

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
        Arrays.stream(panel.getComponents()).filter(c -> (c instanceof JComponent)).forEach(c -> {
            JComponent jc = (JComponent) c;
            jc.setEnabled(enabled);
            setEnabled(jc, enabled);
        });
    }
}

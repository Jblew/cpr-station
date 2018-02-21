/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.util;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.gui.IconLoader;

/**
 *
 * @author teofil
 */
public class ValidableLabel extends JLabel {
    private final AtomicBoolean valid = new AtomicBoolean(false);
    
    public ValidableLabel(boolean valid, String text) {
        super(text);

        setValid_TSafe(valid);
    }

    public final void setValid_TSafe(boolean valid_) {
        valid.set(valid_);
        SwingUtilities.invokeLater(() -> {
            if (valid_) {
                setForeground(Color.GREEN.darker().darker());
                setIcon(IconLoader.OK_16.load());
            } else {
                setForeground(Color.RED.darker());
                setIcon(IconLoader.ERROR_16.load());
            }
        });
    }
    
    public final boolean isMarkerValid() {
        return valid.get();
    }
}

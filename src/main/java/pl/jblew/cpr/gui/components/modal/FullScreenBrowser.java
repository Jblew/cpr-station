/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components.modal;

import java.util.concurrent.Executors;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.components.browser.MFileBrowser;
import pl.jblew.cpr.logic.Event;
import pl.jblew.cpr.logic.MFile;

/**
 *
 * @author teofil
 */
public class FullScreenBrowser extends JFrame {

    public FullScreenBrowser(Context context, Event event) {
        setTitle(event.getName());
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setSize(1024, 768);
        
        JFrame me = this;
        
        Executors.newSingleThreadExecutor().submit(() -> {
            MFile.Localized[] mfiles = event.getLocalizedMFiles(context);
            if (mfiles.length > 0) {
                SwingUtilities.invokeLater(() -> {
                    MFileBrowser mfb = new MFileBrowser(context, mfiles, event);
                    mfb.showSingleView();
                    
                    me.setContentPane(mfb);
                    me.revalidate();
                    me.repaint();
                });
            }
        });
    }
    
}

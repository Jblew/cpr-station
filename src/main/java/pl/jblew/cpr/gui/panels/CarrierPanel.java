/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.windows.SynchronizeWindow;
import pl.jblew.cpr.logic.Carrier;

/**
 *
 * @author teofil
 */
public class CarrierPanel extends MainPanel {
    private final Context context;
    private final Carrier carrier;

    public CarrierPanel(Context context_, Carrier carrier_) {
        this.context = context_;
        this.carrier = carrier_;

        setLayout(new FlowLayout());
        
        JLabel carrierNameLabel = new JLabel("<html><p style=\"font-size:16px\">"+carrier.getName()+"</p>");
        add(carrierNameLabel);
        
        /**
         * 
         */
        JButton synchronizeEvents = new JButton("<html><p width=300>Utwórz kopię wszystkich wydarzeń z tego nośnika na innym nośniku</p>");
        synchronizeEvents.addActionListener((evt) -> {
            new SynchronizeWindow(context, carrier);
        });
        synchronizeEvents.setEnabled(carrier.isConnected(context));
        
        /**
         * 
         */
        JButton checkButton = new JButton("Sprawdź spójność");
        checkButton.addActionListener((evt) -> {
            context.getIntegrityChecker().getProcessor().check(carrier, context.deviceDetector.getDeviceRoot(carrier.getName()), carrier.getName());
        });
        checkButton.setEnabled(carrier.isConnected(context));
        
        /**
         * 
         */
        add(synchronizeEvents);
        add(checkButton);
    }

    @Override
    public void activate() {
        repaint();
    }

    @Override
    public void inactivate() {
    }
}

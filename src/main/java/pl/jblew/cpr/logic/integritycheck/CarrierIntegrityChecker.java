/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.integritycheck;

import java.io.File;
import java.time.LocalDateTime;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.file.StorageDevicePresenceListener;
import pl.jblew.cpr.logic.Carrier;
import pl.jblew.cpr.logic.Event_Localization;

/**
 *
 * @author teofil
 */
public class CarrierIntegrityChecker implements StorageDevicePresenceListener {
    private final Context context;
    private final IntegrityCheckerProcessor processor;

    public CarrierIntegrityChecker(Context context) {
        this.context = context;

        processor = new IntegrityCheckerProcessor(context);
    }

    @Override
    public void storageDeviceConnected(File rootFile, String deviceName) {
        context.dbManager.executeInDBThread(() -> {
            Carrier c = Carrier.forName(context, deviceName);
            if (c != null) {
                if (c.getLastChecked().isBefore(LocalDateTime.now().minusWeeks(2))) {
                    processor.check(c, rootFile, deviceName);
                }
            }
        });
    }

    @Override
    public void storageDeviceDisconnected(File rootFile, String deviceName) {

    }

    public static class FilesMissingOnCarrier {
        public final Carrier carrier;
        public final Event_Localization[] eventLocalizations;

        FilesMissingOnCarrier(Carrier carrier, Event_Localization[] eventLocalizations) {
            this.carrier = carrier;
            this.eventLocalizations = eventLocalizations;
        }
    }

}

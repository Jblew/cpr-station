/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.logic.io;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.treenodes.CarriersNode;
import pl.jblew.cpr.logic.Carrier;

/**
 *
 * @author teofil
 */
public class CarrierMaker {
    private CarrierMaker() {}
    
    public static Carrier getAndCheckCarrier(final Context c, final String deviceName, File deviceRoot) throws BadFileStructureException, CarrierNotWritableException {
        try {
            final AtomicReference<Carrier> carrier = new AtomicReference<>(null);
            c.dbManager.executeInDBThreadAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<Carrier> result = c.dbManager.getDaos().getCarrierDao().queryForEq("name", deviceName);
                        if(result.size() > 0) {
                            carrier.set(result.get(0));
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(CarrierMaker.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
            Carrier cr = carrier.get();
            if(cr != null) {
                if(!FileStructureUtil.checkFileStructure(deviceRoot)) throw new BadFileStructureException();
                if(!(deviceRoot.canWrite() && deviceRoot.canRead())) throw new CarrierNotWritableException();
            }
            return carrier.get();
            
        } catch (InterruptedException ex) {
            Logger.getLogger(CarrierMaker.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public static void tryMakeCarrier(Context context, String deviceName, File deviceRoot) throws CarrierNotWritableException, NotConnectedToDatabaseException {
        if(!(deviceRoot.canWrite() && deviceRoot.canRead())) throw new CarrierNotWritableException();
        if(!context.dbManager.isConnected()) throw new NotConnectedToDatabaseException();
    }
    
    public static Carrier makeCarrier(final Context context, final String deviceName, File deviceRoot) throws CannotCreateFileStructureException, CarrierNotWritableException, NotConnectedToDatabaseException, CannotSaveCarrierToDatabase {
        tryMakeCarrier(context, deviceName, deviceRoot);
        
        if(!FileStructureUtil.checkFileStructure(deviceRoot)) {
            FileStructureUtil.createFileStructure(deviceRoot);
            if(!FileStructureUtil.checkFileStructure(deviceRoot)) throw new CannotCreateFileStructureException();
        }
        
        final AtomicBoolean carrierSaved = new AtomicBoolean(false);
        
        final Carrier out = new Carrier();
        out.setName(deviceName);
        
        try {
            context.dbManager.executeInDBThreadAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<Carrier> result = context.dbManager.getDaos().getCarrierDao().queryForEq("name", deviceName);
                        if(result.isEmpty()) context.dbManager.getDaos().getCarrierDao().createIfNotExists(out);
                        carrierSaved.set(true);
                    } catch (SQLException ex) {
                        Logger.getLogger(CarrierMaker.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
            });
            if(!carrierSaved.get()) throw new CannotSaveCarrierToDatabase();
            
            context.eBus.post(new CarriersNode.CarriersListChanged());
            
            return out;
        } catch (InterruptedException ex) {
            throw new CannotSaveCarrierToDatabase();
        }
    }
    
    public static class BadFileStructureException extends Exception {
        
    }
    
    public static class CarrierNotWritableException extends Exception {
        
    }
    
    public static class CannotCreateFileStructureException extends Exception {
        
    }
    
    public static class CannotAddCarrierToDBException extends Exception {
        
    }
    
    public static class NotConnectedToDatabaseException extends Exception {
        
    }
    
    public static class CannotSaveCarrierToDatabase extends Exception {
    }
}

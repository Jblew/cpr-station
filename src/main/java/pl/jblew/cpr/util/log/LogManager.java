/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util.log;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import pl.jblew.cpr.util.Tail;

/**
 *
 * @author teofil
 */
public class LogManager {
    private final Tail<String> logTail = new Tail(200);
    
    public LogManager() {
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setFormatter(new SingleLineLogFormatter(false));
        }
        
        Formatter formatter = new SingleLineLogFormatter(false);
        Logger.getLogger("").addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logTail.add(formatter.format(record));
            }

            @Override
            public void flush() {
                
            }

            @Override
            public void close() throws SecurityException {
                
            }
        });
    }
    
    public Tail<String> getLogTail() {
        return logTail;
    }
}

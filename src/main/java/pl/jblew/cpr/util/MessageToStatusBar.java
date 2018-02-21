/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

/**
 *
 * @author teofil
 */
public class MessageToStatusBar implements PrintableBusMessage {
    private final Type type;
    private final String msg;
    
    public MessageToStatusBar(String msg_, Type type_) {
        msg = msg_;
        type = type_;
    }

    public Type getType() {
        return type;
    }

    public String getMsg() {
        return msg;
    }

    public static enum Type {
        ERROR, INFO
    }

    @Override
    public String toString() {
        return "<"+type.name()+"> "+msg;
    }
    
    
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ta klasa generuje kolejne liczby dla wielu wątków, mogące służyć jako
 * wewnętrzne identyfikatory, bezpieczne dla jednej sesji. Te identyfikatory,
 * warto np. dodawać do nazw wątków, tak, aby nie powtarzały się one.
 *
 * @author jblew
 */
public class IdManager {

    private static final AtomicInteger id = new AtomicInteger(0);
    //private static final AtomicLong multiSessionLong = new AtomicLong(0);

    private IdManager() {

    }

    public static int getSessionSafe() {
        return id.incrementAndGet();
    }

}
